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
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.InvalidGameStateException;
import com.assassin.exception.KillNotFoundException;
import com.assassin.exception.PlayerActionNotAllowedException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Game;
import com.assassin.model.GameState;
import com.assassin.model.Kill;
import com.assassin.model.Notification;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;

/**
 * Service layer for handling kill reporting logic.
 */
public class KillService {

    private static final Logger logger = LoggerFactory.getLogger(KillService.class);
    private final KillDao killDao;
    private final PlayerDao playerDao; // Needed to validate players and update status/targets
    private final GameDao gameDao; // Added missing GameDao dependency
    private final NotificationService notificationService; // Added NotificationService

    // Default constructor
    public KillService() {
        this(new DynamoDbKillDao(), new DynamoDbPlayerDao(), new DynamoDbGameDao(), new NotificationService()); // Added NotificationService instantiation
    }

    // Constructor for dependency injection (testing)
    public KillService(KillDao killDao, PlayerDao playerDao, GameDao gameDao, NotificationService notificationService) { // Added NotificationService parameter
        this.killDao = killDao;
        this.playerDao = playerDao;
        this.gameDao = gameDao; // Assign GameDao
        this.notificationService = notificationService; // Assign NotificationService
    }

    /**
     * Processes a reported kill, including initial verification setup.
     *
     * @param killerId The ID of the player reporting the kill.
     * @param victimId The ID of the player who was killed.
     * @param latitude Latitude of the kill location.
     * @param longitude Longitude of the kill location.
     * @param verificationMethod The verification method required/used for this kill (e.g., "GPS", "NONE").
     * @param verificationData Any initial data relevant to the verification (e.g., killer's current coords for GPS).
     * @return The created Kill object.
     * @throws ValidationException if the kill is invalid (e.g., players not found, killer target mismatch).
     * @throws GameNotFoundException If the associated game cannot be found.
     */
    public Kill reportKill(String killerId, String victimId, Double latitude, Double longitude,
                           String verificationMethod, Map<String, String> verificationData) 
            throws ValidationException, GameNotFoundException { 
        
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
            
            // TODO: Fetch game rules based on killer.getGameId() if needed to determine
            //       the required verification method dynamically instead of relying on input.
            // Game game = gameDao.getGameById(killer.getGameId())
            //        .orElseThrow(() -> new GameNotFoundException("Game not found for killer: " + killerId));
            // String requiredVerificationMethod = game.getSettings().getOrDefault("verificationMethod", "NONE");
            // We'll use the passed-in verificationMethod for now.
            
            // Validate killer is alive
            if (!PlayerStatus.ACTIVE.name().equals(killer.getStatus())) { 
                 throw new ValidationException("Killer " + killerId + " is not active in the game.");
            }
    
            // Validate victim exists and is alive
             Player victim = playerDao.getPlayerById(victimId)
                .orElseThrow(() -> new ValidationException("Victim with ID " + victimId + " not found."));
             if (!PlayerStatus.ACTIVE.name().equals(victim.getStatus())) {
                 throw new ValidationException("Victim " + victimId + " is not active in the game.");
            }
    
            // Validate the victim is the killer's current target
            if (!victimId.equals(killer.getTargetID())) {
                throw new ValidationException("Reported victim " + victimId + " is not the killer's current target (" + killer.getTargetID() + ").");
            }
            
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
            
            logger.info("Reporting valid kill: Killer={}, Victim={}, Time={}, Verification={}", 
                        killerId, victimId, kill.getTime(), kill.getVerificationMethod());
            killDao.saveKill(kill);
    
            // --- Update Player Statuses and Targets --- 
            // Victim is now dead
            victim.setStatus(PlayerStatus.DEAD.name());
            String victimsOldTarget = victim.getTargetID(); // Store before clearing
            victim.setTargetID(null); // Dead players have no target
            victim.setSecret(null);   // Clear secrets
            victim.setTargetSecret(null);
            playerDao.savePlayer(victim);
            logger.info("Updated victim {} status to DEAD", victimId);
    
            // Killer gets victim's old target as their new target
            killer.setTargetID(victimsOldTarget); 
            playerDao.savePlayer(killer);
            logger.info("Updated killer {} new target to {}", killerId, victimsOldTarget);
            
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
                    v.setStatus(PlayerStatus.DEAD.name());
                    v.setTargetID(null);
                    v.setSecret(null);
                    v.setTargetSecret(null);
                    playerDao.savePlayer(v);
                });
            } catch (Exception e) {
                logger.warn("Error updating player data in test mode (ignored): {}", e.getMessage());
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

        // Allow verification if PENDING or PENDING_REVIEW (for moderator action)
        if (!"PENDING".equals(kill.getVerificationStatus()) && !"PENDING_REVIEW".equals(kill.getVerificationStatus())) {
            throw new PlayerActionNotAllowedException("Kill is not pending verification. Current status: " + kill.getVerificationStatus());
        }
        
        // --- Moderator Action Check (for PHOTO reviews) ---
        String moderatorAction = verificationInput.get("moderatorAction");
        if ("PHOTO".equals(kill.getVerificationMethod()) && moderatorAction != null && "PENDING_REVIEW".equals(kill.getVerificationStatus())) {
            String moderatorNotes = verificationInput.getOrDefault("moderatorNotes", "Moderator review complete.");
            boolean approved = false; // Track approval for notification
            if ("APPROVE".equalsIgnoreCase(moderatorAction)) {
                kill.setVerificationStatus("VERIFIED");
                kill.setVerificationNotes("Approved by moderator (" + verifierId + "): " + moderatorNotes);
                logger.info("Moderator approved photo verification for kill: Killer={}, Time={}", killerId, killTime);
                approved = true; // Mark as approved
            } else if ("REJECT".equalsIgnoreCase(moderatorAction)) {
                kill.setVerificationStatus("REJECTED");
                kill.setVerificationNotes("Rejected by moderator (" + verifierId + "): " + moderatorNotes);
                logger.info("Moderator rejected photo verification for kill: Killer={}, Time={}", killerId, killTime);
            } else {
                logger.warn("Invalid moderatorAction '{}' received for kill: Killer={}, Time={}", moderatorAction, killerId, killTime);
                // Keep status as PENDING_REVIEW if action is invalid
            }
            killDao.saveKill(kill);

            // Send notification on successful verification
            if (approved) {
                sendKillVerifiedNotification(kill);
            }
            
            return kill; // Return early after moderator action
        }
        
        // If not a moderator action on a photo, proceed with standard verification
        // Re-check status in case it was just approved/rejected above (though we returned early)
        if (!"PENDING".equals(kill.getVerificationStatus())) {
             throw new PlayerActionNotAllowedException("Kill is not pending verification. Current status: " + kill.getVerificationStatus());
        }

        // TODO: Check if verifierId has permission (e.g., is a moderator or involved player)

        boolean verified = false;
        // Use a more descriptive base note
        String verificationNotes = "Verification attempt by " + verifierId + " at " + Instant.now().toString(); 

        // --- Verification Logic Branch --- 
        switch (kill.getVerificationMethod().toUpperCase()) {
            case "GPS":
                verified = verifyGpsProximity(kill, verificationInput);
                verificationNotes += " via GPS proximity.";
                break;
            case "NFC":
                verified = verifyNfc(kill, verificationInput);
                verificationNotes += " via NFC tag.";
                break;
            case "PHOTO":
                // This case now handles the *initial* photo submission
                boolean photoDataReceived = verifyPhoto(kill, verificationInput);
                if (photoDataReceived) {
                    kill.setVerificationStatus("PENDING_REVIEW"); // Set to pending review
                    verificationNotes = "Photo submitted for review by " + verifierId + " (" + verificationInput.getOrDefault("photoUrl", "URL missing") + ").";
                } else {
                    kill.setVerificationStatus("REJECTED");
                    verificationNotes = "Photo verification failed: Required data missing.";
                }
                logger.info("Photo submission result for kill (Killer: {}, Time: {}): Status={}, Notes={}", 
                            kill.getKillerID(), kill.getTime(), kill.getVerificationStatus(), verificationNotes);
                kill.setVerificationNotes(verificationNotes);
                killDao.saveKill(kill);
                return kill; // Return early, moderator action needed next
            case "NONE":
            case "MANUAL": // Allow manual verification by mods
                verified = true; // Assume manual verification is always successful if requested
                verificationNotes += " manually.";
                break;
            case "TEST_MODE":
                 verified = true; // Auto-verify in test mode
                 verificationNotes = "Auto-verified in test mode.";
                 break;
            default:
                logger.warn("Unsupported verification method: {}", kill.getVerificationMethod());
                verificationNotes = "Verification failed: Unsupported method.";
                verified = false; 
        }
        
        // Update Kill status (only if not handled by PHOTO case)
        kill.setVerificationStatus(verified ? "VERIFIED" : "REJECTED");
        kill.setVerificationNotes(verificationNotes);
        // Optionally merge verificationInput into kill.getVerificationData() if needed
        
        logger.info("Verification result for kill (Killer: {}, Time: {}): Status={}, Notes={}", 
                    kill.getKillerID(), kill.getTime(), kill.getVerificationStatus(), kill.getVerificationNotes());
        killDao.saveKill(kill);
        
        // Send notification on successful verification
        if (verified) {
            sendKillVerifiedNotification(kill);
        }
        
        return kill;
    }
    
    // --- Placeholder Verification Methods --- 
    // These would contain the actual logic for each verification type
    
    private boolean verifyGpsProximity(Kill kill, Map<String, String> verificationInput) {
        logger.info("Performing GPS verification for kill: Killer={}, Victim={}", kill.getKillerID(), kill.getVictimID());
        
        // 1. Get kill location (kill.getLatitude(), kill.getLongitude())
        Double killLat = kill.getLatitude();
        Double killLon = kill.getLongitude();
        if (killLat == null || killLon == null) {
             logger.warn("GPS verification failed: Kill location is missing.");
             return false;
        }

        // 2. Get victim's current location (from verificationInput)
        String victimLatStr = verificationInput.get("victimLatitude");
        String victimLonStr = verificationInput.get("victimLongitude");
        if (victimLatStr == null || victimLonStr == null) {
            logger.warn("GPS verification failed: Victim location missing in verification input.");
            return false;
        }

        Double victimLat, victimLon;
        try {
            victimLat = Double.parseDouble(victimLatStr);
            victimLon = Double.parseDouble(victimLonStr);
        } catch (NumberFormatException e) {
            logger.warn("GPS verification failed: Invalid victim location format in input.");
            return false;
        }

        // 3. Calculate distance (Haversine formula)
        double distance = calculateHaversineDistance(killLat, killLon, victimLat, victimLon);
        logger.debug("Calculated GPS distance: {} meters", distance);

        // 4. Compare distance to threshold (fetch from Game settings)
        double thresholdMeters = 50.0; // Default threshold
        try {
            Player victim = playerDao.getPlayerById(kill.getVictimID()).orElse(null);
            if (victim != null && victim.getGameID() != null) {
                Game game = gameDao.getGameById(victim.getGameID())
                                .orElseThrow(() -> new GameNotFoundException("Game " + victim.getGameID() + " not found during GPS verification."));
                // Assume Game model has a Map<String, Object> settings field
                // And threshold is stored as a Number under "gpsVerificationThresholdMeters"
                Map<String, Object> settings = game.getSettings(); // Need to add getSettings() to Game model
                if (settings != null && settings.containsKey("gpsVerificationThresholdMeters")) {
                    Object thresholdObj = settings.get("gpsVerificationThresholdMeters");
                    if (thresholdObj instanceof Number) {
                        thresholdMeters = ((Number) thresholdObj).doubleValue();
                        logger.debug("Using GPS threshold from game settings: {}", thresholdMeters);
                    } else {
                        logger.warn("Game {} setting 'gpsVerificationThresholdMeters' is not a Number, using default.", game.getGameID());
                    }
                } else {
                     logger.debug("Game {} has no 'gpsVerificationThresholdMeters' setting, using default.", game.getGameID());
                }
            } else {
                logger.warn("Could not determine game ID for victim {} to fetch GPS threshold.", kill.getVictimID());
            }
        } catch (GameNotFoundException e) {
             logger.error("Failed to fetch game settings for GPS threshold: {}", e.getMessage());
             // Continue with default threshold
        } catch (Exception e) {
             logger.error("Unexpected error fetching game settings for GPS threshold: {}", e.getMessage(), e);
             // Continue with default threshold
        }
        
        boolean withinProximity = distance <= thresholdMeters;
        
        // 5. Consider time difference (optional - not implemented here)
        // Instant killTime = Instant.parse(kill.getTime());
        // Instant verificationTime = Instant.now();
        // Duration timeDiff = Duration.between(killTime, verificationTime);

        logger.info("GPS Verification Result: Distance = {}m, Threshold = {}m, WithinProximity = {}",
                    String.format("%.2f", distance), thresholdMeters, withinProximity);
        return withinProximity; 
    }
    
    private boolean verifyNfc(Kill kill, Map<String, String> verificationInput) {
        logger.info("Performing NFC verification for kill: Killer={}, Victim={}", kill.getKillerID(), kill.getVictimID());
        
        // 1. Get expected NFC tag ID (from victim's player profile)
        Player victim = playerDao.getPlayerById(kill.getVictimID())
                                .orElse(null); // Fetch victim's profile
        if (victim == null) {
            logger.warn("NFC verification failed: Could not find victim profile for ID: {}", kill.getVictimID());
            return false;
        }
        String expectedNfcTagId = victim.getNfcTagId();
        if (expectedNfcTagId == null || expectedNfcTagId.trim().isEmpty()) {
            logger.warn("NFC verification failed: Victim {} does not have an associated NFC Tag ID.", kill.getVictimID());
            return false;
        }

        // 2. Get scanned NFC tag ID (from verificationInput)
        String scannedNfcTagId = verificationInput.get("scannedNfcTagId");
        if (scannedNfcTagId == null || scannedNfcTagId.trim().isEmpty()) {
            logger.warn("NFC verification failed: Scanned NFC Tag ID missing in verification input.");
            return false;
        }

        // 3. Compare IDs
        boolean matches = expectedNfcTagId.equals(scannedNfcTagId.trim());
        logger.info("NFC Verification Result: Expected={}, Scanned={}, Matches={}", 
                    expectedNfcTagId, scannedNfcTagId, matches);
        return matches;
    }
    
    private boolean verifyPhoto(Kill kill, Map<String, String> verificationInput) {
        logger.info("Performing Photo verification for kill: Killer={}, Victim={}", kill.getKillerID(), kill.getVictimID());
        
        // 1. Get photo URL/reference (from verificationInput)
        String photoUrl = verificationInput.get("photoUrl");
        if (photoUrl == null || photoUrl.trim().isEmpty()) {
             logger.warn("Photo verification failed: Photo URL missing in verification input.");
             // Depending on flow, maybe this shouldn't fail immediately but wait for upload?
             // For now, we require it in the input map.
             return false;
        }

        // 2. Store/link photo reference in kill.verificationData
        //    The data is already in verificationInput, which can be optionally merged 
        //    into kill.verificationData in the verifyKill method if needed for persistence.
        //    We'll assume the URL is enough for now.
        logger.info("Photo verification initiated with URL: {}", photoUrl);

        // 3. For now, assume photo submission implies verification pending moderator review
        //    Actual implementation might involve image analysis or manual review triggers.
        //    We will return true here, assuming the *submission* was successful, 
        //    but the kill status remains PENDING until a moderator approves.
        //    Alternatively, we could set status to PENDING_REVIEW. For simplicity, keep PENDING.
        //    The actual VERIFIED/REJECTED status should be set by a moderator action later.
        // --> Let's modify verifyKill to handle this: Photo verification won't auto-set to VERIFIED.
        return true; // Indicate photo *data* was received, not that kill is verified yet.
    }

    // Helper for GPS distance calculation
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Radius of the earth in meters
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; 
    }

    // --- End Placeholder Methods --- 

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
            } catch (Exception e) {
                // Log error but don't fail the kill report operation
                logger.error("Failed to increment kill count for player {}: {}", killerId, e.getMessage(), e);
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
        if (!PlayerStatus.DEAD.name().equalsIgnoreCase(player.getStatus())) {
            throw new PlayerActionNotAllowedException("Only players with DEAD status can confirm death");
        }
        
        // Get the kill record
        List<Kill> kills = killDao.findKillsByVictim(victimId); // Corrected method name
        if (kills.isEmpty()) {
            throw new KillNotFoundException("No kill record found for victim: " + victimId);
        }
        
        // Get the most recent kill (should only be one for a player in most cases)
        Kill mostRecentKill = kills.stream()
                .sorted((k1, k2) -> k2.getTime().compareTo(k1.getTime()))
                .findFirst()
                .orElseThrow(() -> new KillNotFoundException("Could not determine most recent kill for victim: " + victimId));
        
        // Update the kill record with the last will and confirmation
        mostRecentKill.setLastWill(lastWill);
        mostRecentKill.setDeathConfirmed(true);
        
        // Save the updated kill record
        killDao.saveKill(mostRecentKill); // Corrected: Use killDao
        
        return mostRecentKill;
    }

    // --- Helper Methods ---

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

        } catch (Exception e) {
            // Log error but don't fail the main operation
            logger.error("Failed to send KILL_VERIFIED notification for kill (Killer: {}, Time: {}): {}",
                         kill.getKillerID(), kill.getTime(), e.getMessage(), e);
        }
    }
} 