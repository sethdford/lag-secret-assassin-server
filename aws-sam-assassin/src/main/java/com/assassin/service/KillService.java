package com.assassin.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbKillDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.GameDao;
import com.assassin.dao.KillDao;
import com.assassin.dao.PlayerDao;
import com.assassin.dao.DynamoDbSafeZoneDao;
import com.assassin.dao.SafeZoneDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.InvalidGameStateException;
import com.assassin.exception.KillNotFoundException;
import com.assassin.exception.KillPersistenceException;
import com.assassin.exception.PersistenceException;
import com.assassin.exception.PlayerActionNotAllowedException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.PlayerPersistenceException;
import com.assassin.exception.SafeZoneException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.GameState;
import com.assassin.model.Kill;
import com.assassin.model.Notification;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;
import com.assassin.service.verification.VerificationManager;
import com.assassin.service.verification.VerificationResult;
import com.assassin.util.GeoUtils;
import com.assassin.service.SafeZoneViolationDetector.ViolationCheckResult;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

// Added import for ContentModerationService
import com.assassin.service.ContentModerationService;
import com.assassin.model.ModerationRequest;
import com.assassin.model.ModerationResult;

/**
 * Service layer for handling kill reporting logic.
 */
public class KillService {

    private static final Logger logger = LoggerFactory.getLogger(KillService.class);
    private final KillDao killDao;
    private final PlayerDao playerDao; // Needed to validate players and update status/targets
    private final GameDao gameDao; // Added missing GameDao dependency
    private final NotificationService notificationService; // Added NotificationService
    private final VerificationManager verificationManager; // Add VerificationManager dependency
    private final MapConfigurationService mapConfigurationService; // Replace SafeZoneService with MapConfigurationService
    private final SafeZoneViolationDetector violationDetector; // Add SafeZoneViolationDetector
    private final ContentModerationService contentModerationService; // Added ContentModerationService

    // Main constructor with all dependencies
    public KillService(KillDao killDao, PlayerDao playerDao, GameDao gameDao, 
                       NotificationService notificationService, VerificationManager verificationManager, 
                       MapConfigurationService mapConfigurationService, 
                       SafeZoneViolationDetector violationDetector,
                       ContentModerationService contentModerationService) {
        this.killDao = killDao;
        this.playerDao = playerDao;
        this.gameDao = gameDao; 
        this.notificationService = notificationService;
        this.verificationManager = verificationManager;
        this.mapConfigurationService = mapConfigurationService;
        this.violationDetector = violationDetector;
        this.contentModerationService = contentModerationService;
    }

    // Constructor for DI with enhancedClient - builds some internal services
    public KillService(KillDao killDao, PlayerDao playerDao, GameDao gameDao, 
                       NotificationService notificationService, VerificationManager verificationManager, 
                       DynamoDbEnhancedClient enhancedClient) {
        this(killDao, playerDao, gameDao, notificationService, verificationManager,
             new MapConfigurationService(
                (gameDao != null) ? gameDao : new DynamoDbGameDao(enhancedClient),
                null, // gameZoneStateDao - can be null if MapConfigService handles it
                new DynamoDbSafeZoneDao(enhancedClient),
                null  // shrinkingZoneService - can be null
             ),
             new SafeZoneViolationDetector(
                new SafeZoneService(new DynamoDbSafeZoneDao(enhancedClient)), // SZV needs SZS with DAO
                playerDao, gameDao, notificationService
             ),
             new AwsContentModerationService() // Default moderation service
        );
    }

    // Simplified constructor for external DI (e.g., tests focusing on some parts)
    public KillService(KillDao killDao, PlayerDao playerDao, GameDao gameDao, 
                       NotificationService notificationService, VerificationManager verificationManager, 
                       MapConfigurationService mapConfigurationService) {
        this(killDao, playerDao, gameDao, notificationService, verificationManager, mapConfigurationService,
             // Create default violation detector based on the provided mapConfigService's internal SafeZoneService
             // This assumes MapConfigurationService exposes or uses a SafeZoneService internally that can be accessed or reconstructed.
             // For simplicity, we'll new it up here. If mapConfigService has a getSafeZoneService(), use that.
             new SafeZoneViolationDetector(new SafeZoneService(), playerDao, gameDao, notificationService), 
             new AwsContentModerationService()
        );
         // Note: The SafeZoneViolationDetector created above might not use the same SafeZoneService instance
         // as the one implicitly used by the mapConfigurationService if it also creates its own. 
         // This could be an issue if they need to share state or DAO instances. 
         // Prefer the main constructor for full control.
    }

    // Constructor for even simpler DI (often used in older tests or basic setup)
    public KillService(KillDao killDao, PlayerDao playerDao, GameDao gameDao, NotificationService notificationService) {
        this(killDao, playerDao, gameDao, notificationService, 
            new VerificationManager(playerDao, gameDao), // Default VerificationManager
            new MapConfigurationService(gameDao, null, new DynamoDbSafeZoneDao(), null), // Default MapConfigService
            new SafeZoneViolationDetector(new SafeZoneService(), playerDao, gameDao, notificationService), // Default ViolationDetector
            new AwsContentModerationService() // Default ModerationService
        );
    }

    // Default constructor creating all default dependencies
    public KillService() {
        this(new DynamoDbKillDao(), 
             new DynamoDbPlayerDao(), 
             new DynamoDbGameDao(), 
             new NotificationService(), 
             new VerificationManager(new DynamoDbPlayerDao(), new DynamoDbGameDao()), 
             new MapConfigurationService(new DynamoDbGameDao(), null, new DynamoDbSafeZoneDao(), null),
             // Default SafeZoneViolationDetector needs its dependencies. 
             // SafeZoneService can be default. PlayerDao, GameDao, NotifService are created above.
             new SafeZoneViolationDetector(new SafeZoneService(), new DynamoDbPlayerDao(), new DynamoDbGameDao(), new NotificationService()),
             new AwsContentModerationService()
        );
    }

    /**
     * Reports a new kill.
     *
     * @param killerId ID of the player reporting the kill
     * @param victimId ID of the victim player
     * @param latitude Optional latitude of kill location
     * @param longitude Optional longitude of kill location
     * @param verificationMethod Method used for verification (e.g., "GPS", "NFC", "PHOTO")
     * @param verificationData Additional data for verification
     * @return The newly created Kill object
     * @throws ValidationException If validation fails
     * @throws PlayerNotFoundException If killer or victim not found
     * @throws PlayerActionNotAllowedException If player status prevents the action
     * @throws PersistenceException If database error occurs
     * @throws SafeZoneException If the kill is attempted within a safe zone
     */
    public Kill reportKill(String killerId, String victimId, Double latitude, Double longitude,
                           String verificationMethod, Map<String, String> verificationData)
            throws ValidationException, PlayerNotFoundException, PlayerActionNotAllowedException, PersistenceException, SafeZoneException {
        
        if (killerId == null || victimId == null || killerId.equals(victimId)) {
            throw new ValidationException("Invalid killer or victim ID.");
        }
        // Basic location validation (could be more sophisticated)
        if (latitude == null || longitude == null) {
            throw new ValidationException("Latitude and Longitude are required.");
        }
        if (verificationMethod == null || verificationMethod.trim().isEmpty()) {
            throw new ValidationException("Verification method cannot be empty.");
        }

        // Check if we should run in test mode (skip game rule validation)
        boolean isTestEnvironment = Boolean.parseBoolean(System.getProperty("ASSASSIN_TEST_MODE", "false"));
        
        if (!isTestEnvironment) {
            // Fetch Game first to potentially get rules (e.g., required verification method)
            // Assume Player holds gameId. This might need adjustment based on actual data model.
            Player killer = playerDao.getPlayerById(killerId)
                    .orElseThrow(() -> new ValidationException("Killer with ID " + killerId + " not found."));
            
            // Validate killer is alive
            if (!PlayerStatus.ACTIVE.name().equals(killer.getStatus())) { 
                 throw new ValidationException("Killer " + killerId + " is not active in the game.");
            }
    
            // Validate victim exists and is alive
             Player victim = playerDao.getPlayerById(victimId)
                .orElseThrow(() -> new ValidationException("Victim with ID " + victimId + " not found."));
             if (!PlayerStatus.ACTIVE.name().equals(victim.getStatus())) {
                 if (PlayerStatus.PENDING_DEATH.name().equals(victim.getStatus())) {
                     throw new ValidationException("Victim " + victimId + " already has a pending kill report awaiting verification.");
                 } else {
                     throw new ValidationException("Victim " + victimId + " is not active in the game (Status: " + victim.getStatus() + ").");
                 }
            }
            
            // Ensure players are in the same game
            String gameId = killer.getGameID();
            if (gameId == null || gameId.trim().isEmpty()) {
                throw new ValidationException("Killer " + killerId + " is not associated with a game.");
            }
            if (!gameId.equals(victim.getGameID())) {
                throw new ValidationException("Killer and victim are not in the same game.");
            }

            // Fetch the game to check boundaries and status
            Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game with ID " + gameId + " not found."));

            // Check if game is active
            if (!GameState.ACTIVE.name().equals(game.getStatus())) {
                throw new InvalidGameStateException("Game " + gameId + " is not active. Cannot report kill.");
            }

            // *** Boundary Check ***
            if (!mapConfigurationService.isCoordinateInGameBoundary(gameId, new Coordinate(latitude, longitude))) {
                    logger.warn("Kill reported outside game boundary for game {}. Location: ({}, {}). Killer: {}. Victim: {}", 
                                gameId, latitude, longitude, killerId, victimId);
                    throw new ValidationException("Kill location is outside the defined game boundary.");
                }
                logger.debug("Kill location ({}, {}) is within game boundary for game {}", latitude, longitude, gameId);
            // *** End Boundary Check ***
    
            // Validate the victim is the killer's current target
            if (!victimId.equals(killer.getTargetID())) {
                throw new ValidationException("Reported victim " + victimId + " is not the killer's current target (" + killer.getTargetID() + ").");
            }
            
            // *** Enhanced Safe Zone Violation Check using ViolationDetector ***
            if (latitude != null && longitude != null) {
                long currentTime = System.currentTimeMillis();
                
                // Use the enhanced violation detector to check elimination attempt
                ViolationCheckResult violationResult = violationDetector.checkEliminationAttempt(
                    gameId, killerId, victimId,
                    latitude, longitude, // Both attacker and victim at same location for this check
                    latitude, longitude, // In reality, victim location might be different
                    currentTime
                );
                
                if (violationResult.isViolation()) {
                    logger.warn("Kill attempt failed due to safe zone violation: Killer={}, Victim={}, Location=({}, {}), Violation={}", 
                               killerId, victimId, latitude, longitude, violationResult.getViolationType());
                    throw new SafeZoneException("Kill cannot be reported: " + violationResult.getMessage());
                }
                
                logger.debug("Safe zone violation check passed for kill at location ({}, {}) in game {}. Killer: {}, Victim: {}", 
                           latitude, longitude, gameId, killerId, victimId);
            } else {
                 // This case should ideally not happen due to earlier validation, but log if it does.
                logger.warn("Skipping safe zone check due to missing coordinates for kill in game {} (Killer: {}, Victim: {})", 
                          gameId, killerId, victimId);
            }
            // *** End Enhanced Safe Zone Check ***
            
            // --- Kill is valid, proceed --- 
    
            // Create Kill record
            Kill kill = new Kill();
            kill.setKillerID(killerId);
            kill.setVictimID(victimId);
            kill.setTime(Instant.now().toString());
            kill.setLatitude(latitude);
            kill.setLongitude(longitude);
            // Set verification details
            kill.setVerificationMethod(verificationMethod.toUpperCase());
            kill.setVerificationStatus("PENDING"); // Default status
            kill.setVerificationData(verificationData); // Store any provided data
            
            // *** Content Moderation for Photo Kills ***
            if ("PHOTO".equalsIgnoreCase(verificationMethod) && verificationData != null && verificationData.containsKey("photoUrl")) {
                String photoUrl = verificationData.get("photoUrl");
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    logger.info("Moderating photo kill proof: {}", photoUrl);
                    ModerationRequest moderationRequest = new ModerationRequest(kill.getKillerID() + "_" + kill.getTime(), photoUrl);
                    moderationRequest.setUserId(killerId);
                    moderationRequest.setGameId(gameId); // Use gameId from killer object

                    ModerationResult moderationResult = contentModerationService.moderateImage(moderationRequest);
                    kill.setModerationStatus(moderationResult.getStatus().name());
                    // Store some details from moderation, ensure moderationDetails map is initialized
                    if (kill.getModerationDetails() == null) {
                        kill.setModerationDetails(new java.util.HashMap<>());
                    }
                    kill.getModerationDetails().put("moderationProvider", "AWS Content Moderation"); // Example
                    kill.getModerationDetails().put("aiDecisionDetails", moderationResult.getReason());
                    if (moderationResult.getFlags() != null && !moderationResult.getFlags().isEmpty()) {
                        String flaggedLabels = moderationResult.getFlags().stream()
                            .map(flag -> flag.getFlagType())
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
                        kill.getModerationDetails().put("aiFlaggedLabels", flaggedLabels);
                    }
                    kill.getModerationDetails().put("aiConfidence", String.valueOf(moderationResult.getConfidenceScore()));

                    if (moderationResult.getStatus() == ModerationResult.Status.REJECTED) {
                        logger.warn("Photo kill proof REJECTED by content moderation. Kill by {} of {} at {}. Reason: {}", killerId, victimId, kill.getTime(), moderationResult.getReason());
                        kill.setVerificationStatus("REJECTED"); // Directly reject the kill
                        kill.setVerificationNotes("Kill proof rejected by content moderation: " + moderationResult.getReason());
                        // Potentially throw an exception here or handle differently based on game rules.
                        // For now, we will save the kill as REJECTED due to content.
                    } else if (moderationResult.getStatus() == ModerationResult.Status.PENDING_MANUAL_REVIEW) {
                        logger.info("Photo kill proof PENDING MANUAL REVIEW by content moderation. Kill by {} of {} at {}. Reason: {}", killerId, victimId, kill.getTime(), moderationResult.getReason());
                        // Keep kill.verificationStatus as PENDING, but notes/moderationStatus reflect AI flag.
                        kill.setVerificationNotes("Kill proof flagged by AI for manual review: " + moderationResult.getReason());
                    } else {
                        // APPROVED by moderation service
                        logger.info("Photo kill proof APPROVED by content moderation for kill by {} of {} at {}.", killerId, victimId, kill.getTime());
                        // No change to verificationStatus yet, it remains PENDING for other verification steps.
                    }
                }
            }
            // *** End Content Moderation ***
            
            // *** Set the KillStatusPartition based on initial status ***
            kill.setKillStatusPartition(kill.getVerificationStatus()); // Initial status is PENDING
            
            logger.info("Reporting valid kill: Killer={}, Victim={}, Time={}, Verification={}", 
                        killerId, victimId, kill.getTime(), kill.getVerificationMethod());
            killDao.saveKill(kill);
    
            // --- Update Player Status to PENDING_DEATH --- 
            // Victim is now pending death (awaiting confirmation)
            victim.setStatus(PlayerStatus.PENDING_DEATH.name());
            // Do NOT clear target or secrets yet - this will happen on death confirmation
            // Do NOT reassign targets yet - this will happen on death confirmation
            playerDao.savePlayer(victim);
            logger.info("Updated victim {} status to PENDING_DEATH", victimId);
            
            // --- Increment Killer's Kill Count ---
            incrementKillerCount(killerId);
            
            return kill;
        } else {
            // Test mode - simplified validation
            logger.info("Test mode enabled, skipping game rule validation");
            
            // Create Kill record with minimal validation
            Kill kill = new Kill();
            kill.setKillerID(killerId);
            kill.setVictimID(victimId);
            kill.setTime(Instant.now().toString());
            kill.setLatitude(latitude);
            kill.setLongitude(longitude);
            kill.setVerificationMethod(verificationMethod != null ? verificationMethod.toUpperCase() : "TEST_MODE");
            kill.setVerificationStatus("PENDING");
            kill.setVerificationData(verificationData);
            
            logger.info("Reporting test kill: Killer={}, Victim={}, Time={}", killerId, victimId, kill.getTime());
            killDao.saveKill(kill);
            
            // Try to update players if they exist, but don't fail if they don't
            try {
                playerDao.getPlayerById(killerId).ifPresent(k -> {
                    // Update killer stats if needed for testing
                    playerDao.savePlayer(k);
                });
                
                playerDao.getPlayerById(victimId).ifPresent(v -> {
                    // Update victim stats if needed for testing
                    v.setStatus(PlayerStatus.PENDING_DEATH.name());
                    // In test mode, keep target and secrets for now
                    playerDao.savePlayer(v);
                });
            } catch (PlayerNotFoundException | PlayerPersistenceException e) {
                logger.warn("Error updating player data in test mode (ignored): {}", e.getMessage());
            } catch (RuntimeException e) {
                logger.warn("Unexpected error updating player data in test mode (ignored): {}", e.getMessage());
            }
            
            return kill;
        }
    }

    /**
     * Verifies a pending kill based on the method and provided data.
     * Placeholder for different verification strategies.
     * 
     * @param killId The ID of the kill (or maybe killerId + time as composite key)
     * @param verifierId The ID of the player/moderator performing the verification
     * @param verificationInput Additional data required for verification (e.g., photo upload, NFC data)
     * @return The updated Kill object with status VERIFIED or REJECTED.
     * @throws KillNotFoundException
     * @throws ValidationException
     * @throws PlayerActionNotAllowedException
     */
    public Kill verifyKill(String killerId, String killTime, String verifierId, Map<String, String> verificationInput)
            throws KillNotFoundException, ValidationException, PlayerActionNotAllowedException {
                
        Kill kill = getKill(killerId, killTime); // Fetches the kill or throws KillNotFoundException

        // Allow verification only if PENDING or PENDING_REVIEW (for moderator action)
        if (!"PENDING".equals(kill.getVerificationStatus()) && !"PENDING_REVIEW".equals(kill.getVerificationStatus())) {
            throw new PlayerActionNotAllowedException("Kill is not pending verification. Current status: " + kill.getVerificationStatus());
        }
        
        // *** Edge Case Validation: Check victim's current status ***
        String victimId = kill.getVictimID();
        Optional<Player> victimOpt = playerDao.getPlayerById(victimId);
        if (victimOpt.isPresent()) {
            Player victim = victimOpt.get();
            String currentStatus = victim.getStatus();
            
            // If victim is no longer in PENDING_DEATH, something has changed
            if (!PlayerStatus.PENDING_DEATH.name().equals(currentStatus)) {
                if (PlayerStatus.DEAD.name().equals(currentStatus)) {
                    logger.warn("Attempting to verify kill for victim {} who is already DEAD. Kill may have been verified elsewhere.", victimId);
                    // Allow verification to proceed but log the warning
                } else if (PlayerStatus.ACTIVE.name().equals(currentStatus)) {
                    logger.warn("Attempting to verify kill for victim {} who has been restored to ACTIVE status. Kill may have been rejected elsewhere.", victimId);
                    // This could indicate a concurrent rejection - proceed with caution
                } else {
                    logger.warn("Attempting to verify kill for victim {} with unexpected status: {}. Proceeding with verification.", victimId, currentStatus);
                }
            }
        } else {
            logger.warn("Victim {} not found during kill verification. Proceeding with verification anyway.", victimId);
        }
        
        // --- Delegate Verification to VerificationManager --- 
        logger.info("Delegating kill verification to VerificationManager: Killer={}, Time={}, Verifier={}, Method={}",
                    killerId, killTime, verifierId, kill.getVerificationMethod());
                    
        VerificationResult result = verificationManager.verifyKill(kill, verificationInput, verifierId);
        
        // Update Kill status and notes based on verification result
        kill.setVerificationStatus(result.toKillVerificationStatus());
        kill.setVerificationNotes(result.getNotes());
        
        // *** Update KillStatusPartition based on the final verification status ***
        kill.setKillStatusPartition(kill.getVerificationStatus());
        
        logger.info("Verification result from manager for kill (Killer: {}, Time: {}): Status={}, Notes={}", 
                    kill.getKillerID(), kill.getTime(), kill.getVerificationStatus(), kill.getVerificationNotes());
                    
        // Save the updated kill record
        killDao.saveKill(kill);
        
        // *** Handle Final Status Change and Target Reassignment ***
        if (result.isVerified()) {
            // Kill is verified - finalize the death and reassign targets
            finalizeKillAndReassignTargets(kill);
            sendKillVerifiedNotification(kill);
        } else if ("REJECTED".equals(kill.getVerificationStatus())) {
            // Kill is rejected - restore victim to ACTIVE status
            restoreVictimFromPendingDeath(kill);
        }
        // If still PENDING, no status change needed
        
        return kill;
    }
    
    // --- Placeholder Verification Methods --- 
    // These would contain the actual logic for each verification type
    /* Remove the following methods as they are now handled by IVerificationMethod implementations
    private boolean verifyGpsProximity(Kill kill, Map<String, String> verificationInput) {
        // ... implementation removed ...
    }
    
    private boolean verifyNfc(Kill kill, Map<String, String> verificationInput) {
        // ... implementation removed ...
    }
    
    private boolean verifyPhoto(Kill kill, Map<String, String> verificationInput) {
        // ... implementation removed ...
    }

    // Helper for GPS distance calculation
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        // ... implementation removed ...
    }
    */

    /**
     * Gets all kills where the specified player was the killer.
     *
     * @param killerId The ID of the killer player.
     * @return A list of Kill objects.
     */
    public List<Kill> getKillsByKiller(String killerId) {
        logger.info("Getting kills by killer: {}", killerId);
        return killDao.findKillsByKiller(killerId);
    }

    /**
     * Gets all kills where the specified player was the victim.
     *
     * @param victimId The ID of the victim player.
     * @return A list of Kill objects.
     */
    public List<Kill> getKillsByVictim(String victimId) {
        logger.info("Getting kills by victim: {}", victimId);
        return killDao.findKillsByVictim(victimId);
    }

    /**
     * Gets a list of the most recent kills across all players.
     *
     * @param limit The maximum number of kills to return.
     * @return A list of Kill objects.
     */
    public List<Kill> findRecentKills(int limit) {
        logger.info("Getting recent kills with limit: {}", limit);
        return killDao.findRecentKills(limit);
    }

    /**
     * Gets all kills.
     * WARNING: This can be resource-intensive.
     *
     * @return A list of all Kill objects.
     */
    public List<Kill> getAllKills() {
        logger.info("Getting all kills");
        return killDao.getAllKills();
    }

    /**
     * Gets a specific kill by its composite key.
     *
     * @param killerId The ID of the killer.
     * @param time The timestamp of the kill.
     * @return The Kill object.
     * @throws KillNotFoundException if the kill is not found.
     */
    public Kill getKill(String killerId, String time) throws KillNotFoundException {
        logger.info("Getting kill by key: Killer={}, Time={}", killerId, time);
        return killDao.getKill(killerId, time)
                      .orElseThrow(() -> new KillNotFoundException("Kill not found with killer ID " + killerId + " and time " + time));
    }

    /**
     * Gets all kills for a specific game.
     *
     * @param gameId The ID of the game.
     * @return A list of Kill objects.
     * @throws GameNotFoundException if the game doesn't exist (optional check)
     */
    public List<Kill> getKillsByGameId(String gameId) throws GameNotFoundException {
        logger.info("Getting kills for gameID: {}", gameId);
        // Optional: Validate game exists first
        // gameDao.getGameById(gameId).orElseThrow(() -> new GameNotFoundException("Game not found: " + gameId));
        return killDao.findKillsByGameId(gameId);
    }

    // Potentially add methods like:
    // List<Kill> getKillsInGame(String gameId); // Would require gameId on Kill model + GSI

    // Added helper method to call the specific DAO implementation method
    private void incrementKillerCount(String killerId) {
         if (playerDao instanceof DynamoDbPlayerDao) {
            try {
                ((DynamoDbPlayerDao) playerDao).incrementPlayerKillCount(killerId);
            } catch (PlayerNotFoundException | PlayerPersistenceException e) {
                // Log error but don't fail the kill report operation
                logger.error("Failed to increment kill count for player {}: {}", killerId, e.getMessage(), e);
            } catch (RuntimeException e) {
                // Log error but don't fail the kill report operation
                logger.error("Unexpected error incrementing kill count for player {}: {}", killerId, e.getMessage(), e);
            }
        } else {
            logger.warn("Cannot increment kill count: PlayerDao is not an instance of DynamoDbPlayerDao.");
        }
    }

    /**
     * Confirms a player's death and sets their "last will" message
     *
     * @param gameId    the ID of the game
     * @param victimId  the ID of the player confirming their death
     * @param lastWill  the player's last will message
     * @return          the updated Kill object
     * @throws GameNotFoundException           if the game does not exist
     * @throws PlayerNotFoundException         if the player does not exist
     * @throws KillNotFoundException           if no kill record exists for this victim
     * @throws InvalidGameStateException       if the game is not in the ACTIVE state
     * @throws PlayerActionNotAllowedException if the player is not in a KILLED state
     */
    public Kill confirmDeath(String gameId, String victimId, String lastWill) 
            throws GameNotFoundException, PlayerNotFoundException, 
                   KillNotFoundException, InvalidGameStateException, 
                   PlayerActionNotAllowedException {
        
        // Get the game and validate it's active
        Optional<Game> gameOpt = gameDao.getGameById(gameId); // Corrected method name
        if (!gameOpt.isPresent()) {
            throw new GameNotFoundException("Game not found with ID: " + gameId);
        }
        
        Game game = gameOpt.get();
        // Corrected: Use getStatus() and compare with enum name
        if (!GameState.ACTIVE.name().equalsIgnoreCase(game.getStatus())) {
            throw new InvalidGameStateException("Cannot confirm death in a game that is not active");
        }
        
        // Get the player and validate it's the victim
        Optional<Player> playerOpt = playerDao.getPlayerById(victimId); // Corrected method name and removed gameId
        if (!playerOpt.isPresent()) {
            throw new PlayerNotFoundException("Player not found with ID: " + victimId);
        }
        
        Player player = playerOpt.get();
        if (!PlayerStatus.PENDING_DEATH.name().equalsIgnoreCase(player.getStatus()) && 
            !PlayerStatus.DEAD.name().equalsIgnoreCase(player.getStatus())) {
            logger.warn("Player {} attempted to confirm death in game {}, but is not PENDING_DEATH or DEAD (Status: {}).", victimId, gameId, player.getStatus());
            throw new PlayerActionNotAllowedException("Cannot confirm death, player status is not PENDING_DEATH or DEAD.");
        }
        
        // 4. Find the Kill record for this victim in this game
        // Use the renamed method returning Optional<Kill>
        Kill killRecord = killDao.findKillRecordByVictimAndGame(victimId, gameId)
                .orElseThrow(() -> {
                     logger.warn("Could not find kill record for victim {} in game {} for death confirmation.", victimId, gameId);
                     return new KillNotFoundException("No kill record found for victim " + victimId + " in game " + gameId);
                 });
        
        // Update the kill record with the last will and confirmation
        killRecord.setLastWill(lastWill);
        killRecord.setDeathConfirmed(true);
        
        // Save the updated kill record
        killDao.saveKill(killRecord); // Corrected: Use killDao
        
        return killRecord;
    }

    // --- Helper Methods ---

    /**
     * Finalizes a verified kill by changing victim status to DEAD and reassigning targets.
     *
     * @param kill The verified kill.
     */
    private void finalizeKillAndReassignTargets(Kill kill) {
        try {
            String victimId = kill.getVictimID();
            String killerId = kill.getKillerID();
            
            // Get the victim and killer players
            Optional<Player> victimOpt = playerDao.getPlayerById(victimId);
            Optional<Player> killerOpt = playerDao.getPlayerById(killerId);
            
            if (!victimOpt.isPresent() || !killerOpt.isPresent()) {
                logger.error("Cannot finalize kill - victim or killer not found. Victim: {}, Killer: {}", victimId, killerId);
                return;
            }
            
            Player victim = victimOpt.get();
            Player killer = killerOpt.get();
            
            // Verify victim is in PENDING_DEATH status
            if (!PlayerStatus.PENDING_DEATH.name().equals(victim.getStatus())) {
                logger.warn("Cannot finalize kill - victim {} is not in PENDING_DEATH status. Current status: {}", victimId, victim.getStatus());
                return;
            }
            
            // Store victim's old target before clearing
            String victimsOldTarget = victim.getTargetID();
            
            // Update victim status to DEAD and clear their data
            victim.setStatus(PlayerStatus.DEAD.name());
            victim.setTargetID(null);
            victim.setSecret(null);
            victim.setTargetSecret(null);
            playerDao.savePlayer(victim);
            logger.info("Finalized victim {} status to DEAD", victimId);
            
            // Reassign victim's old target to the killer
            if (victimsOldTarget != null && !victimsOldTarget.isEmpty()) {
                killer.setTargetID(victimsOldTarget);
                playerDao.savePlayer(killer);
                logger.info("Reassigned killer {} new target to {}", killerId, victimsOldTarget);
            } else {
                logger.warn("Victim {} had no target to reassign to killer {}", victimId, killerId);
            }
            
        } catch (PlayerNotFoundException | PlayerPersistenceException e) {
            logger.error("Error finalizing kill and reassigning targets for kill by {} at {}: {}", 
                        kill.getKillerID(), kill.getTime(), e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("Unexpected error finalizing kill and reassigning targets for kill by {} at {}: {}", 
                        kill.getKillerID(), kill.getTime(), e.getMessage(), e);
        }
    }
    
    /**
     * Handles verification timeout for pending kills by automatically rejecting them.
     * This method should be called periodically to clean up stale pending kills.
     *
     * @param timeoutMinutes The number of minutes after which a pending kill should timeout
     * @return The number of kills that were timed out
     */
    public int handleVerificationTimeouts(int timeoutMinutes) {
        logger.info("Checking for verification timeouts (timeout: {} minutes)", timeoutMinutes);
        int timeoutCount = 0;
        
        try {
            // Calculate the cutoff time
            long cutoffTime = System.currentTimeMillis() - (timeoutMinutes * 60 * 1000);
            
            // Get all pending kills (this would need to be implemented in the DAO)
            // TODO: Implement findKillsByStatus method in KillDao
            // List<Kill> pendingKills = killDao.findKillsByStatus("PENDING");
            List<Kill> pendingKills = new java.util.ArrayList<>(); // Placeholder until DAO method is implemented
            
            for (Kill kill : pendingKills) {
                try {
                    // Parse the kill time and check if it's older than the cutoff
                    long killTime = Instant.parse(kill.getTime()).toEpochMilli();
                    
                    if (killTime < cutoffTime) {
                        logger.info("Timing out pending kill: Killer={}, Victim={}, Time={}", 
                                   kill.getKillerID(), kill.getVictimID(), kill.getTime());
                        
                        // Update kill status to REJECTED due to timeout
                        kill.setVerificationStatus("REJECTED");
                        kill.setVerificationNotes("Kill verification timed out after " + timeoutMinutes + " minutes");
                        kill.setKillStatusPartition(kill.getVerificationStatus());
                        killDao.saveKill(kill);
                        
                        // Restore victim from PENDING_DEATH to ACTIVE
                        restoreVictimFromPendingDeath(kill);
                        
                        timeoutCount++;
                    }
                } catch (KillNotFoundException | KillPersistenceException | PlayerNotFoundException | PlayerPersistenceException e) {
                    logger.error("Error processing timeout for kill by {} at {}: {}", 
                                kill.getKillerID(), kill.getTime(), e.getMessage(), e);
                } catch (RuntimeException e) {
                    logger.error("Unexpected error processing timeout for kill by {} at {}: {}", 
                                kill.getKillerID(), kill.getTime(), e.getMessage(), e);
                }
            }
            
            logger.info("Processed {} verification timeouts", timeoutCount);
            
        } catch (KillPersistenceException e) {
            logger.error("Error retrieving pending kills for timeout handling: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("Unexpected error handling verification timeouts: {}", e.getMessage(), e);
        }
        
        return timeoutCount;
    }

    /**
     * Restores a victim from PENDING_DEATH status back to ACTIVE when a kill is rejected.
     *
     * @param kill The rejected kill.
     */
    private void restoreVictimFromPendingDeath(Kill kill) {
        try {
            String victimId = kill.getVictimID();
            
            // Get the victim player
            Optional<Player> victimOpt = playerDao.getPlayerById(victimId);
            
            if (!victimOpt.isPresent()) {
                logger.error("Cannot restore victim - victim {} not found", victimId);
                return;
            }
            
            Player victim = victimOpt.get();
            
            // Verify victim is in PENDING_DEATH status
            if (!PlayerStatus.PENDING_DEATH.name().equals(victim.getStatus())) {
                logger.warn("Cannot restore victim - victim {} is not in PENDING_DEATH status. Current status: {}", victimId, victim.getStatus());
                return;
            }
            
            // Restore victim to ACTIVE status
            victim.setStatus(PlayerStatus.ACTIVE.name());
            playerDao.savePlayer(victim);
            logger.info("Restored victim {} from PENDING_DEATH to ACTIVE status", victimId);
            
        } catch (PlayerNotFoundException | PlayerPersistenceException e) {
            logger.error("Error restoring victim from pending death for kill by {} at {}: {}", 
                        kill.getKillerID(), kill.getTime(), e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("Unexpected error restoring victim from pending death for kill by {} at {}: {}", 
                        kill.getKillerID(), kill.getTime(), e.getMessage(), e);
        }
    }

    /**
     * Sends a notification to the killer when their kill is verified.
     * @param kill The verified kill object.
     */
    private void sendKillVerifiedNotification(Kill kill) {
        try {
            // Fetch killer details to get name for the message (optional)
            Player killer = playerDao.getPlayerById(kill.getKillerID())
                                     .orElse(null); // Handle case where killer might not be found (unlikely)
            String killerName = (killer != null) ? killer.getPlayerName() : kill.getKillerID();

            // Fetch victim details for the message
            Player victim = playerDao.getPlayerById(kill.getVictimID())
                                    .orElse(null);
            String victimName = (victim != null) ? victim.getPlayerName() : kill.getVictimID();


            String message = String.format("Your kill of %s has been verified! Your new target is %s.",
                                           victimName,
                                           (killer != null && killer.getTargetID() != null) ? killer.getTargetID() : "being assigned"); // Include new target ID if available


            Map<String, String> data = Map.of(
                "killId", kill.getKillerID() + "_" + kill.getTime(), // Use composite key as identifier
                "killerId", kill.getKillerID(),
                "victimId", kill.getVictimID(),
                "verificationMethod", kill.getVerificationMethod(),
                "newTargetId", (killer != null && killer.getTargetID() != null) ? killer.getTargetID() : "" // Include new target ID
            );

            Notification notification = new Notification(
                kill.getKillerID(), // Recipient is the killer
                "KILL_VERIFIED",
                message,
                data
            );

            notificationService.sendNotification(notification);
            logger.info("Sent KILL_VERIFIED notification to killer: {}", kill.getKillerID());

        } catch (PlayerNotFoundException e) {
            // Log error but don't fail the main operation
            logger.error("Failed to send KILL_VERIFIED notification - killer not found (Killer: {}, Time: {}): {}",
                         kill.getKillerID(), kill.getTime(), e.getMessage(), e);
        } catch (RuntimeException e) {
            // Log error but don't fail the main operation
            logger.error("Unexpected error sending KILL_VERIFIED notification for kill (Killer: {}, Time: {}): {}",
                         kill.getKillerID(), kill.getTime(), e.getMessage(), e);
        }
    }
} 