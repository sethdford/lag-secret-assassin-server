package com.assassin.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a media file stored in S3 with metadata tracked in DynamoDB.
 * Supports various media types including images, videos, and documents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class MediaFile {
    
    /**
     * Unique identifier for the media file
     */
    private String mediaId;
    
    /**
     * ID of the user who owns this media file
     */
    private String userId;
    
    /**
     * Optional game ID if the media is associated with a specific game
     */
    private String gameId;
    
    /**
     * Type of media (profile image, kill verification, etc.)
     */
    private MediaType mediaType;
    
    /**
     * Original filename provided by the user
     */
    private String originalFilename;
    
    /**
     * S3 bucket name where the file is stored
     */
    private String bucketName;
    
    /**
     * S3 key/path where the file is stored
     */
    private String s3Key;
    
    /**
     * Content type (MIME type) of the file
     */
    private String contentType;
    
    /**
     * File size in bytes
     */
    private Long fileSizeBytes;
    
    /**
     * Image width in pixels (for images only)
     */
    private Integer width;
    
    /**
     * Image height in pixels (for images only)
     */
    private Integer height;
    
    /**
     * Duration in seconds (for videos only)
     */
    private Integer durationSeconds;
    
    /**
     * Processing status (PENDING, PROCESSING, COMPLETED, FAILED)
     */
    private String processingStatus;
    
    /**
     * Content moderation status (PENDING, APPROVED, REJECTED)
     */
    private String moderationStatus;
    
    /**
     * Moderation confidence score (0-100)
     */
    private Double moderationConfidence;
    
    /**
     * CloudFront URL for optimized delivery
     */
    private String cdnUrl;
    
    /**
     * Thumbnail S3 key (for images and videos)
     */
    private String thumbnailS3Key;
    
    /**
     * Thumbnail CloudFront URL
     */
    private String thumbnailCdnUrl;
    
    /**
     * Additional metadata as key-value pairs
     */
    private Map<String, String> metadata;
    
    /**
     * When the file was uploaded
     */
    private Instant uploadedAt;
    
    /**
     * When the file was last accessed
     */
    private Instant lastAccessedAt;
    
    /**
     * When the file expires and should be deleted (optional)
     */
    private Instant expiresAt;
    
    /**
     * Whether the file has been soft-deleted
     */
    private Boolean deleted;
    
    /**
     * When the file was soft-deleted
     */
    private Instant deletedAt;
    
    @DynamoDbPartitionKey
    public String getMediaId() {
        return mediaId;
    }
    
    @DynamoDbSortKey
    public String getUserId() {
        return userId;
    }
    
    /**
     * Check if the file is an image type
     */
    public boolean isImage() {
        return mediaType != null && mediaType.isImage();
    }
    
    /**
     * Check if the file is a video type
     */
    public boolean isVideo() {
        return mediaType != null && mediaType.isVideo();
    }
    
    /**
     * Check if the file is a document type
     */
    public boolean isDocument() {
        return mediaType != null && mediaType.isDocument();
    }
    
    /**
     * Check if the file has been processed successfully
     */
    public boolean isProcessed() {
        return "COMPLETED".equals(processingStatus);
    }
    
    /**
     * Check if the file has been approved by moderation
     */
    public boolean isApproved() {
        return "APPROVED".equals(moderationStatus);
    }
    
    /**
     * Check if the file is expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
    
    /**
     * Check if the file is soft-deleted
     */
    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }
    
    /**
     * Get the public URL for accessing the file
     * Uses CDN URL if available, otherwise falls back to S3 URL
     */
    public String getPublicUrl() {
        return cdnUrl != null ? cdnUrl : generateS3Url();
    }
    
    /**
     * Get the thumbnail URL if available
     */
    public String getThumbnailUrl() {
        return thumbnailCdnUrl != null ? thumbnailCdnUrl : null;
    }
    
    /**
     * Generate the direct S3 URL (not recommended for public access)
     */
    private String generateS3Url() {
        if (bucketName == null || s3Key == null) {
            return null;
        }
        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, s3Key);
    }
}