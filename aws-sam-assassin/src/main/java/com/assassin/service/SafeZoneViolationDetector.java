package com.assassin.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.PersistenceException;
import com.assassin.model.Game;
import com.assassin.model.Player;
import com.assassin.model.SafeZone;

/**
 * Component responsible for detecting and handling safe zone violations.
 * Monitors player positions and enforces safe zone rules including immunity and scoring adjustments.
 */
public class SafeZoneViolationDetector {

    private static final Logger logger = LoggerFactory.getLogger(SafeZoneViolationDetector.class);
    
    private final SafeZoneService safeZoneService;
    private final PlayerDao playerDao;
    private final GameDao gameDao;
    private final NotificationService notificationService;

    /**
     * Violation types that can occur related to safe zones.
     */
    public enum ViolationType {
        ENTERED_SAFE_ZONE,
        EXITED_SAFE_ZONE,
        UNAUTHORIZED_ZONE_ACCESS,
        ELIMINATION_ATTEMPT_IN_SAFE_ZONE,
        SAFE_ZONE_EXPIRED
    }

    /**
     * Result of a safe zone violation check.
     */
    public static class ViolationCheckResult {
        private final boolean isViolation;
        private final ViolationType violationType;
        private final String message;
        private final SafeZone involvedZone;
        private final boolean playerIsProtected;

        public ViolationCheckResult(boolean isViolation, ViolationType violationType, String message, 
                                   SafeZone involvedZone, boolean playerIsProtected) {
            this.isViolation = isViolation;
            this.violationType = violationType;
            this.message = message;
            this.involvedZone = involvedZone;
            this.playerIsProtected = playerIsProtected;
        }

        // Static factory methods
        public static ViolationCheckResult noViolation(boolean playerIsProtected) {
            return new ViolationCheckResult(false, null, "No violation detected", null, playerIsProtected);
        }

        public static ViolationCheckResult violation(ViolationType type, String message, SafeZone zone, boolean playerIsProtected) {
            return new ViolationCheckResult(true, type, message, zone, playerIsProtected);
        }

        // Getters
        public boolean isViolation() { return isViolation; }
        public ViolationType getViolationType() { return violationType; }
        public String getMessage() { return message; }
        public SafeZone getInvolvedZone() { return involvedZone; }
        public boolean isPlayerProtected() { return playerIsProtected; }
    }

    // Constructor
    public SafeZoneViolationDetector(SafeZoneService safeZoneService, PlayerDao playerDao, 
                                   GameDao gameDao, NotificationService notificationService) {
        this.safeZoneService = safeZoneService;
        this.playerDao = playerDao;
        this.gameDao = gameDao;
        this.notificationService = notificationService;
    }

    /**
     * Checks if a player's position violates any safe zone rules.
     * 
     * @param gameId The game ID
     * @param playerId The player ID
     * @param latitude The player's current latitude
     * @param longitude The player's current longitude
     * @param timestamp The timestamp of the position check
     * @return ViolationCheckResult containing violation details and protection status
     * @throws PersistenceException if there's an error accessing data
     */
    public ViolationCheckResult checkPlayerPosition(String gameId, String playerId, 
                                                   double latitude, double longitude, long timestamp) 
            throws PersistenceException {
        
        logger.debug("Checking safe zone violations for player {} in game {} at ({}, {}) at time {}", 
                    playerId, gameId, latitude, longitude, timestamp);

        try {
            // Get all active safe zones for the game
            List<SafeZone> activeSafeZones = safeZoneService.getActiveZonesForGame(gameId, timestamp);
            
            boolean playerIsCurrentlyProtected = false;
            SafeZone protectingZone = null;

            // Check if player is currently in any safe zone
            for (SafeZone zone : activeSafeZones) {
                if (zone.containsLocation(latitude, longitude)) {
                    logger.debug("Player {} is inside safe zone {} of type {}", playerId, zone.getSafeZoneId(), zone.getType());
                    
                    // Check if player is authorized for this zone
                    if (zone.isPlayerAuthorized(playerId)) {
                        playerIsCurrentlyProtected = true;
                        protectingZone = zone;
                        
                        // Log the entry for audit purposes
                        logViolationEvent(gameId, playerId, ViolationType.ENTERED_SAFE_ZONE, 
                                        "Player entered authorized safe zone: " + zone.getName(), zone, timestamp);
                        
                        // Notify player of safe zone entry
                        notifyPlayerSafeZoneEvent(gameId, playerId, ViolationType.ENTERED_SAFE_ZONE, zone);
                        
                        break; // Player is protected, no need to check other zones
                    } else {
                        // Player is in a zone but not authorized
                        String message = String.format("Player %s attempted to access unauthorized %s safe zone: %s", 
                                                      playerId, zone.getType(), zone.getName());
                        logViolationEvent(gameId, playerId, ViolationType.UNAUTHORIZED_ZONE_ACCESS, message, zone, timestamp);
                        
                        return ViolationCheckResult.violation(ViolationType.UNAUTHORIZED_ZONE_ACCESS, 
                                                            "Unauthorized access to private safe zone", zone, false);
                    }
                }
            }

            // If player was previously in a safe zone but is no longer protected, they've exited
            if (!playerIsCurrentlyProtected) {
                Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
                if (playerOpt.isPresent()) {
                    Player player = playerOpt.get();
                    // Note: This would require tracking previous safe zone status in Player model
                    // For now, we'll just log the exit without detailed tracking
                    logger.debug("Player {} is not currently in any authorized safe zone in game {}", playerId, gameId);
                }
            }

            // Return the result with the protecting zone information
            if (playerIsCurrentlyProtected && protectingZone != null) {
                return new ViolationCheckResult(false, null, "Player is protected in safe zone", protectingZone, true);
            } else {
                return ViolationCheckResult.noViolation(false);
            }

        } catch (RuntimeException e) {
            logger.error("Error checking safe zone violations for player {} in game {}: {}", 
                        playerId, gameId, e.getMessage(), e);
            throw new PersistenceException("Error checking safe zone violations", e);
        }
    }

    /**
     * Checks if an elimination attempt violates safe zone rules.
     * 
     * @param gameId The game ID
     * @param attackerId The attacker's player ID
     * @param victimId The victim's player ID
     * @param attackerLatitude The attacker's latitude
     * @param attackerLongitude The attacker's longitude
     * @param victimLatitude The victim's latitude
     * @param victimLongitude The victim's longitude
     * @param timestamp The timestamp of the elimination attempt
     * @return ViolationCheckResult indicating if the elimination is allowed
     * @throws PersistenceException if there's an error accessing data
     */
    public ViolationCheckResult checkEliminationAttempt(String gameId, String attackerId, String victimId,
                                                       double attackerLatitude, double attackerLongitude,
                                                       double victimLatitude, double victimLongitude, 
                                                       long timestamp) throws PersistenceException {
        
        logger.info("Checking elimination attempt by {} against {} in game {} at time {}", 
                   attackerId, victimId, gameId, timestamp);

        // Check if victim is protected
        ViolationCheckResult victimCheck = checkPlayerPosition(gameId, victimId, victimLatitude, victimLongitude, timestamp);
        
        if (victimCheck.isPlayerProtected()) {
            String message = String.format("Elimination attempt blocked: victim %s is protected in safe zone %s", 
                                          victimId, victimCheck.getInvolvedZone().getName());
            
            logViolationEvent(gameId, attackerId, ViolationType.ELIMINATION_ATTEMPT_IN_SAFE_ZONE, 
                            message, victimCheck.getInvolvedZone(), timestamp);
            
            // Notify both players
            notifyPlayerSafeZoneEvent(gameId, victimId, ViolationType.ELIMINATION_ATTEMPT_IN_SAFE_ZONE, 
                                    victimCheck.getInvolvedZone());
            notifyAttackerBlocked(gameId, attackerId, victimId, victimCheck.getInvolvedZone());
            
            return ViolationCheckResult.violation(ViolationType.ELIMINATION_ATTEMPT_IN_SAFE_ZONE, 
                                                message, victimCheck.getInvolvedZone(), false);
        }

        // Also check if attacker is in a safe zone (some game rules might prevent attacking from safe zones)
        ViolationCheckResult attackerCheck = checkPlayerPosition(gameId, attackerId, attackerLatitude, attackerLongitude, timestamp);
        
        // For now, we'll allow attacking from safe zones, but log it
        if (attackerCheck.isPlayerProtected() && attackerCheck.getInvolvedZone() != null) {
            logger.info("Attacker {} is in safe zone {} but elimination attempt is allowed", 
                       attackerId, attackerCheck.getInvolvedZone().getName());
        }

        logger.info("Elimination attempt by {} against {} is ALLOWED - no safe zone violations", attackerId, victimId);
        return ViolationCheckResult.noViolation(false);
    }

    /**
     * Periodically checks for expired safe zones and handles cleanup.
     * This method should be called by a scheduled task.
     * 
     * @param gameId The game ID to check
     * @return The number of zones that were expired and deactivated
     * @throws PersistenceException if there's an error accessing data
     */
    public int handleExpiredSafeZones(String gameId) throws PersistenceException {
        logger.info("Checking for expired safe zones in game {}", gameId);
        
        try {
            int expiredCount = safeZoneService.cleanupExpiredTimedZones(gameId);
            
            if (expiredCount > 0) {
                logger.info("Deactivated {} expired safe zones in game {}", expiredCount, gameId);
                
                // Notify game participants about expired zones
                notifyGameExpiredZones(gameId, expiredCount);
            }
            
            return expiredCount;
            
        } catch (RuntimeException e) {
            logger.error("Error handling expired safe zones for game {}: {}", gameId, e.getMessage(), e);
            throw new PersistenceException("Error handling expired safe zones", e);
        }
    }

    /**
     * Logs a violation event for audit purposes.
     */
    private void logViolationEvent(String gameId, String playerId, ViolationType violationType, 
                                  String message, SafeZone zone, long timestamp) {
        logger.info("SAFE_ZONE_EVENT: Game={}, Player={}, Type={}, Zone={}, Message='{}', Time={}", 
                   gameId, playerId, violationType, 
                   zone != null ? zone.getSafeZoneId() : "none", message, timestamp);
        
        // TODO: Consider storing these events in a dedicated audit table for analytics
    }

    /**
     * Notifies a player about safe zone events.
     */
    private void notifyPlayerSafeZoneEvent(String gameId, String playerId, ViolationType eventType, SafeZone zone) {
        try {
            String message = buildNotificationMessage(eventType, zone);
            
            // Create a Notification object for the NotificationService
            com.assassin.model.Notification notification = new com.assassin.model.Notification();
            notification.setRecipientPlayerId(playerId);
            notification.setTitle("Safe Zone Alert");
            notification.setMessage(message);
            notification.setType("SAFE_ZONE_EVENT");
            notification.setGameId(gameId);
            notification.setTimestamp(Instant.now().toString());
            
            notificationService.sendNotification(notification);
            logger.debug("Sent safe zone notification to player {}: {}", playerId, message);
        } catch (RuntimeException e) {
            logger.warn("Failed to send safe zone notification to player {}: {}", playerId, e.getMessage());
        }
    }

    /**
     * Notifies an attacker that their elimination attempt was blocked.
     */
    private void notifyAttackerBlocked(String gameId, String attackerId, String victimId, SafeZone protectingZone) {
        try {
            String message = String.format("Elimination attempt blocked - your target is protected in safe zone '%s'", 
                                          protectingZone.getName());
            
            // Create a Notification object for the NotificationService
            com.assassin.model.Notification notification = new com.assassin.model.Notification();
            notification.setRecipientPlayerId(attackerId);
            notification.setTitle("Elimination Blocked");
            notification.setMessage(message);
            notification.setType("ELIMINATION_BLOCKED");
            notification.setGameId(gameId);
            notification.setTimestamp(Instant.now().toString());
            
            notificationService.sendNotification(notification);
            logger.debug("Sent elimination blocked notification to attacker {}", attackerId);
        } catch (RuntimeException e) {
            logger.warn("Failed to send elimination blocked notification to attacker {}: {}", attackerId, e.getMessage());
        }
    }

    /**
     * Notifies game participants about expired safe zones.
     */
    private void notifyGameExpiredZones(String gameId, int expiredCount) {
        try {
            // TODO: Implement game-wide notification system
            logger.info("Should notify all players in game {} about {} expired safe zones", gameId, expiredCount);
        } catch (RuntimeException e) {
            logger.warn("Failed to send expired zones notification for game {}: {}", gameId, e.getMessage());
        }
    }

    /**
     * Builds appropriate notification messages for different violation types.
     */
    private String buildNotificationMessage(ViolationType eventType, SafeZone zone) {
        switch (eventType) {
            case ENTERED_SAFE_ZONE:
                return String.format("You are now protected in safe zone '%s'", zone.getName());
            case EXITED_SAFE_ZONE:
                return String.format("You have left safe zone '%s' - you are no longer protected", zone.getName());
            case ELIMINATION_ATTEMPT_IN_SAFE_ZONE:
                return String.format("Someone tried to eliminate you while you were protected in safe zone '%s'", zone.getName());
            case UNAUTHORIZED_ZONE_ACCESS:
                return String.format("You are not authorized to enter safe zone '%s'", zone.getName());
            case SAFE_ZONE_EXPIRED:
                return String.format("Safe zone '%s' has expired and is no longer active", zone.getName());
            default:
                return "Safe zone event occurred";
        }
    }
} 