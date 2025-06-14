package com.assassin.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Response model for media upload requests.
 * Contains presigned URL and upload instructions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadResponse {
    
    /**
     * Unique media ID for tracking the upload
     */
    private String mediaId;
    
    /**
     * Presigned URL for uploading the file to S3
     */
    private String uploadUrl;
    
    /**
     * HTTP method to use for the upload (typically PUT)
     */
    private String method;
    
    /**
     * Required headers for the upload request
     */
    private Map<String, String> headers;
    
    /**
     * Form fields for multipart uploads (if applicable)
     */
    private Map<String, String> formFields;
    
    /**
     * When the presigned URL expires
     */
    private Instant expiresAt;
    
    /**
     * Maximum file size allowed for this upload
     */
    private Long maxFileSizeBytes;
    
    /**
     * Instructions for the client
     */
    private String instructions;
    
    /**
     * Callback URL to notify when upload is complete (optional)
     */
    private String callbackUrl;
    
    /**
     * Success redirect URL (for browser uploads)
     */
    private String successRedirectUrl;
    
    /**
     * Error redirect URL (for browser uploads)
     */
    private String errorRedirectUrl;
    
    /**
     * Whether the upload requires additional processing
     */
    private Boolean requiresProcessing;
    
    /**
     * Estimated processing time in seconds
     */
    private Integer estimatedProcessingTimeSeconds;
    
    /**
     * Check if the presigned URL has expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
    
    /**
     * Get time remaining until expiration in seconds
     */
    public long getSecondsUntilExpiration() {
        if (expiresAt == null) {
            return 0;
        }
        return java.time.Duration.between(Instant.now(), expiresAt).getSeconds();
    }
}