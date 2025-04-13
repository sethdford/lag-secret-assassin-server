package com.assassin.service.verification;

/**
 * Represents the result of a kill verification attempt.
 */
public class VerificationResult {
    
    public enum Status {
        // Verification was successful
        VERIFIED,
        
        // Verification failed
        REJECTED,
        
        // Verification is pending moderator review
        PENDING_REVIEW,
        
        // Verification data was incomplete or invalid
        INVALID_INPUT
    }
    
    private final Status status;
    private final String notes;
    
    public VerificationResult(Status status, String notes) {
        this.status = status;
        this.notes = notes;
    }
    
    /**
     * Factory method for successful verification.
     */
    public static VerificationResult verified(String notes) {
        return new VerificationResult(Status.VERIFIED, notes);
    }
    
    /**
     * Factory method for rejected verification.
     */
    public static VerificationResult rejected(String notes) {
        return new VerificationResult(Status.REJECTED, notes);
    }
    
    /**
     * Factory method for verification pending moderator review.
     */
    public static VerificationResult pendingReview(String notes) {
        return new VerificationResult(Status.PENDING_REVIEW, notes);
    }
    
    /**
     * Factory method for invalid verification input.
     */
    public static VerificationResult invalidInput(String notes) {
        return new VerificationResult(Status.INVALID_INPUT, notes);
    }
    
    /**
     * Checks if the verification was successful.
     */
    public boolean isVerified() {
        return status == Status.VERIFIED;
    }
    
    /**
     * Checks if the verification was rejected.
     */
    public boolean isRejected() {
        return status == Status.REJECTED;
    }
    
    /**
     * Checks if the verification requires moderator review.
     */
    public boolean isPendingReview() {
        return status == Status.PENDING_REVIEW;
    }
    
    /**
     * Checks if the verification input was invalid.
     */
    public boolean isInvalidInput() {
        return status == Status.INVALID_INPUT;
    }
    
    /**
     * Gets the verification status.
     */
    public Status getStatus() {
        return status;
    }
    
    /**
     * Gets the notes associated with the verification result.
     */
    public String getNotes() {
        return notes;
    }
    
    /**
     * Converts the result status to a Kill verification status string.
     */
    public String toKillVerificationStatus() {
        switch(status) {
            case VERIFIED:
                return "VERIFIED";
            case REJECTED:
                return "REJECTED";
            case PENDING_REVIEW:
                return "PENDING_REVIEW";
            case INVALID_INPUT:
                return "PENDING"; // Treat invalid input as still pending
            default:
                return "PENDING";
        }
    }
} 