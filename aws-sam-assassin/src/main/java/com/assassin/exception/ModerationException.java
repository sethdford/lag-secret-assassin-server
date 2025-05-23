package com.assassin.exception;

/**
 * Exception thrown when content moderation operations fail
 */
public class ModerationException extends RuntimeException {
    
    private final String errorCode;
    
    public ModerationException(String message) {
        super(message);
        this.errorCode = "MODERATION_ERROR";
    }
    
    public ModerationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "MODERATION_ERROR";
    }
    
    public ModerationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public ModerationException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * Factory method for image moderation errors
     */
    public static ModerationException imageError(String message, Throwable cause) {
        return new ModerationException("Image moderation failed: " + message, "IMAGE_MODERATION_ERROR", cause);
    }
    
    /**
     * Factory method for text moderation errors
     */
    public static ModerationException textError(String message, Throwable cause) {
        return new ModerationException("Text moderation failed: " + message, "TEXT_MODERATION_ERROR", cause);
    }
    
    /**
     * Factory method for configuration errors
     */
    public static ModerationException configError(String message) {
        return new ModerationException("Moderation configuration error: " + message, "CONFIG_ERROR");
    }
    
    /**
     * Factory method for service unavailable errors
     */
    public static ModerationException serviceUnavailable(String service, Throwable cause) {
        return new ModerationException("Moderation service unavailable: " + service, "SERVICE_UNAVAILABLE", cause);
    }
} 