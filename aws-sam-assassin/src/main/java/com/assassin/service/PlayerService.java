package com.assassin.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.PlayerPersistenceException;
import com.assassin.model.Player;

/**
 * Service for player-related operations.
 */
public class PlayerService {

    private static final Logger logger = LoggerFactory.getLogger(PlayerService.class);
    private final PlayerDao playerDao;

    /**
     * Default constructor.
     */
    public PlayerService() {
        this.playerDao = new DynamoDbPlayerDao();
    }

    /**
     * Constructor with dependency injection for testability.
     *
     * @param playerDao The player DAO
     */
    public PlayerService(PlayerDao playerDao) {
        this.playerDao = playerDao;
    }

    /**
     * Syncs a federated user's profile data to the Player table.
     * If the player already exists (based on the Cognito user ID), updates the profile.
     * If the player doesn't exist, creates a new player record.
     *
     * @param userId The Cognito user ID (sub claim)
     * @param email The user's email address
     * @param name The user's display name
     * @param provider The identity provider name (e.g., "Google", "Facebook", "Apple")
     * @param userAttributes Additional user attributes from Cognito
     * @return The synced Player object
     * @throws PlayerPersistenceException if there's an error persisting the player
     */
    public Player syncFederatedUserToPlayer(String userId, String email, String name, String provider, 
                                           Map<String, String> userAttributes) 
            throws PlayerPersistenceException {
        
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be empty");
        }
        
        Player player;
        try {
            // Try to find the player by ID (which is the Cognito user ID)
            player = playerDao.findPlayerById(userId);
            if (player != null) {
                logger.info("Found existing player with ID: {}", userId);
                // Update existing player with latest profile data
                updatePlayerProfile(player, email, name, provider, userAttributes);
            } else {
                // Player doesn't exist, create a new one
                logger.info("Creating new player for federated user ID: {}", userId);
                player = createPlayerFromFederatedUser(userId, email, name, provider, userAttributes);
            }
            
            // Save updated or new player
            playerDao.savePlayer(player);
            logger.info("Successfully synced federated user profile for user: {}, provider: {}", userId, provider);
            
            return player;
        } catch (Exception e) {
            logger.error("Error syncing federated user to player: {}", e.getMessage(), e);
            throw new PlayerPersistenceException("Failed to sync federated user profile: " + e.getMessage(), e);
        }
    }
    
    /**
     * Updates an existing player's profile with data from a federated login.
     */
    private void updatePlayerProfile(Player player, String email, String name, String provider, 
                                     Map<String, String> userAttributes) {
        // Only update email if it changed and isn't empty
        if (email != null && !email.trim().isEmpty() && !email.equals(player.getEmail())) {
            player.setEmail(email);
        }
        
        // Only update name if it changed and isn't empty
        if (name != null && !name.trim().isEmpty() && !name.equals(player.getPlayerName())) {
            player.setPlayerName(name);
        }
        
        // Set or update social provider info if needed
        // We could store this in an attribute or as a note in the player record
        
        // If the player has no status yet, default to PENDING
        if (player.getStatus() == null || player.getStatus().isEmpty()) {
            player.setStatus("PENDING");
        }
        
        // Set player as active
        player.setActive(true);
    }
    
    /**
     * Creates a new Player object for a federated user.
     */
    private Player createPlayerFromFederatedUser(String userId, String email, String name, String provider, 
                                                Map<String, String> userAttributes) {
        Player player = new Player();
        player.setPlayerID(userId); // Use the Cognito user ID as the player ID
        
        // Set required fields
        player.setEmail(email != null ? email : "");
        player.setPlayerName(name != null ? name : "Player " + userId.substring(0, 8));
        
        // Set default values
        player.setStatus("PENDING"); // New players start as pending until joined to a game
        player.setKillCount(0);
        player.setActive(true);
        
        // Generate a default secret if needed
        player.setSecret(generateSecret());
        
        return player;
    }
    
    /**
     * Generates a random secret for a player.
     */
    private String generateSecret() {
        // Simple random word generation - in production, consider a better word generation approach
        String[] adjectives = {"quick", "lazy", "clever", "brave", "silent", "dark", "bright", "hidden"};
        String[] nouns = {"fox", "dog", "shadow", "assassin", "hunter", "ghost", "warrior", "ninja"};
        
        String adjective = adjectives[(int) (Math.random() * adjectives.length)];
        String noun = nouns[(int) (Math.random() * nouns.length)];
        String randomDigits = String.format("%03d", (int) (Math.random() * 1000));
        
        return adjective + "-" + noun + "-" + randomDigits;
    }
} 