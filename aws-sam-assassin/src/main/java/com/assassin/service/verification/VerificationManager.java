package com.assassin.service.verification;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.PlayerDao;
import com.assassin.model.Kill;
import com.assassin.model.VerificationMethod;

/**
 * Manages the verification process for kill records.
 * This class implements the Strategy pattern, delegating to the appropriate
 * verification method based on the kill record's verification method.
 */
public class VerificationManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VerificationManager.class);
    
    private final Map<VerificationMethod, IVerificationMethod> verificationMethods = new HashMap<>();
    private final PlayerDao playerDao;
    
    /**
     * Constructs a VerificationManager with the specified verification methods.
     *
     * @param playerDao DAO for fetching player data (needed by some verification methods)
     * @param methods Array of verification method implementations (optional)
     */
    public VerificationManager(PlayerDao playerDao, IVerificationMethod... methods) {
        if (playerDao == null) {
            throw new IllegalArgumentException("PlayerDao cannot be null for VerificationManager");
        }
        this.playerDao = playerDao;
        registerDefaultMethods();
        
        // Register provided methods
        if (methods != null) {
            for (IVerificationMethod method : methods) {
                registerMethod(method);
            }
        }
    }
    
    /**
     * Registers the default verification methods.
     */
    private void registerDefaultMethods() {
        registerMethod(new GpsVerificationMethod());
        registerMethod(new NfcVerificationMethod(this.playerDao));
        registerMethod(new PhotoVerificationMethod());
        // Add other default methods here if needed
    }
    
    /**
     * Registers a verification method.
     *
     * @param method The verification method implementation
     */
    public void registerMethod(IVerificationMethod method) {
        if (method != null) {
            verificationMethods.put(method.getMethodType(), method);
            logger.info("Registered verification method: {}", method.getMethodType());
        }
    }
    
    /**
     * Verifies a kill using the appropriate verification method.
     *
     * @param kill The kill record to verify
     * @param verificationInput Data required for verification
     * @param verifierId ID of the person verifying the kill
     * @return The result of the verification
     */
    public VerificationResult verifyKill(Kill kill, Map<String, String> verificationInput, String verifierId) {
        // Safeguard against nulls
        if (kill == null) {
            logger.error("Cannot verify null kill");
            return VerificationResult.invalidInput("Kill record is null");
        }
        
        if (verificationInput == null) {
            verificationInput = new HashMap<>();
        }
        
        // Get the appropriate verification method
        String methodStr = kill.getVerificationMethod();
        VerificationMethod method = VerificationMethod.fromString(methodStr);
        
        // Check for moderator action on a pending review
        if ("PENDING_REVIEW".equals(kill.getVerificationStatus())) {
            return handleModeratorAction(kill, verificationInput, verifierId);
        }
        
        // Get the verification method implementation
        IVerificationMethod verificationMethod = verificationMethods.get(method);
        if (verificationMethod == null) {
            logger.warn("No verification method implementation registered for: {}", method);
            return VerificationResult.invalidInput("Unsupported verification method: " + method);
        }
        
        // Perform the verification
        logger.info("Verifying kill using method {}: Killer={}, Victim={}, VerifierId={}",
                method, kill.getKillerID(), kill.getVictimID(), verifierId);
        return verificationMethod.verify(kill, verificationInput, verifierId);
    }
    
    /**
     * Handles moderator action for a kill pending review.
     *
     * @param kill The kill record pending review
     * @param verificationInput Data including moderator action
     * @param verifierId ID of the moderator
     * @return The result of the moderator action
     */
    private VerificationResult handleModeratorAction(Kill kill, Map<String, String> verificationInput, String verifierId) {
        String moderatorAction = verificationInput.get("moderatorAction");
        if (moderatorAction == null) {
            logger.warn("No moderator action provided for kill pending review: Killer={}, Time={}",
                    kill.getKillerID(), kill.getTime());
            return VerificationResult.invalidInput("No moderator action provided");
        }
        
        String moderatorNotes = verificationInput.getOrDefault("moderatorNotes", 
                "Reviewed by moderator " + verifierId + " at " + Instant.now());
        
        if ("APPROVE".equalsIgnoreCase(moderatorAction)) {
            logger.info("Moderator approved kill: Killer={}, Victim={}, Moderator={}",
                    kill.getKillerID(), kill.getVictimID(), verifierId);
            return VerificationResult.verified("Approved by moderator (" + verifierId + "): " + moderatorNotes);
        } else if ("REJECT".equalsIgnoreCase(moderatorAction)) {
            logger.info("Moderator rejected kill: Killer={}, Victim={}, Moderator={}",
                    kill.getKillerID(), kill.getVictimID(), verifierId);
            return VerificationResult.rejected("Rejected by moderator (" + verifierId + "): " + moderatorNotes);
        } else {
            logger.warn("Invalid moderator action '{}' for kill: Killer={}, Victim={}",
                    moderatorAction, kill.getKillerID(), kill.getVictimID());
            return VerificationResult.invalidInput("Invalid moderator action: " + moderatorAction);
        }
    }
    
    /**
     * Checks if a specific verification method is supported.
     *
     * @param method The verification method to check
     * @return true if the method is supported, false otherwise
     */
    public boolean isMethodSupported(VerificationMethod method) {
        return verificationMethods.containsKey(method);
    }
    
    /**
     * Gets a map of all supported verification methods.
     *
     * @return Map of verification method implementations
     */
    public Map<VerificationMethod, IVerificationMethod> getSupportedMethods() {
        return new HashMap<>(verificationMethods); // Return a copy to preserve encapsulation
    }
} 