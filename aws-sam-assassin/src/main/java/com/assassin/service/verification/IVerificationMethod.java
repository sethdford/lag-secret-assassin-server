package com.assassin.service.verification;

import java.util.Map;

import com.assassin.model.Kill;
import com.assassin.model.VerificationMethod;

/**
 * Interface for kill verification methods.
 * Represents the Strategy pattern for different verification approaches.
 */
public interface IVerificationMethod {
    
    /**
     * Gets the verification method type this implementation handles.
     * 
     * @return The VerificationMethod enum value
     */
    VerificationMethod getMethodType();
    
    /**
     * Verifies a kill using the specific verification logic.
     * 
     * @param kill The kill record to verify
     * @param verificationInput Additional data required for verification
     * @param verifierId ID of the player/moderator performing verification
     * @return A VerificationResult containing the outcome and notes
     */
    VerificationResult verify(Kill kill, Map<String, String> verificationInput, String verifierId);
    
    /**
     * Checks if the required verification data is present in the input map.
     * 
     * @param verificationInput The input data for verification
     * @return true if all required data is present, false otherwise
     */
    boolean hasRequiredData(Map<String, String> verificationInput);
    
    /**
     * Determines if this verification method requires moderator review.
     * 
     * @return true if moderator review is required, false otherwise
     */
    default boolean requiresModeratorReview() {
        return getMethodType().requiresModeratorReview();
    }
    
    /**
     * Determines if this is an automatic verification that doesn't need manual steps.
     * 
     * @return true if the method is automatic, false otherwise
     */
    default boolean isAutomatic() {
        return getMethodType().isAutomatic();
    }
} 