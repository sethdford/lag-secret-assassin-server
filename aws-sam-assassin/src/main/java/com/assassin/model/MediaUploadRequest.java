package com.assassin.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request model for media file uploads.
 * Contains all necessary information to create a presigned URL for secure uploads.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadRequest {
    
    /**
     * Type of media being uploaded
     */
    private MediaType mediaType;
    
    /**
     * Original filename (for reference only)
     */
    private String filename;
    
    /**
     * Content type (MIME type) of the file
     */
    private String contentType;
    
    /**
     * File size in bytes
     */
    private Long fileSizeBytes;
    
    /**
     * Optional game ID if the media is associated with a specific game
     */
    private String gameId;
    
    /**
     * Additional metadata to store with the file
     */
    private Map<String, String> metadata;
    
    /**
     * Whether to automatically process the file after upload
     * (e.g., generate thumbnails, run content moderation)
     */
    private Boolean autoProcess;
    
    /**
     * Expiration time in hours (optional, for temporary files)
     */
    private Integer expirationHours;
    
    /**
     * Tags to apply to the S3 object
     */
    private Map<String, String> tags;
    
    /**
     * Validate the upload request
     */
    public boolean isValid() {
        return mediaType != null 
            && filename != null && !filename.trim().isEmpty()
            && contentType != null && !contentType.trim().isEmpty()
            && fileSizeBytes != null && fileSizeBytes > 0
            && fileSizeBytes <= mediaType.getMaxSizeBytes();
    }
    
    /**
     * Get the file extension from the filename
     */
    public String getFileExtension() {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * Check if the content type is allowed for the media type
     */
    public boolean isContentTypeAllowed() {
        if (contentType == null || mediaType == null) {
            return false;
        }
        
        String lowerContentType = contentType.toLowerCase();
        
        switch (mediaType) {
            case PROFILE_IMAGE:
            case KILL_VERIFICATION_PHOTO:
            case GAME_SCREENSHOT:
                return lowerContentType.startsWith("image/") && 
                       (lowerContentType.contains("jpeg") || 
                        lowerContentType.contains("jpg") || 
                        lowerContentType.contains("png") || 
                        lowerContentType.contains("webp"));
                        
            case KILL_VERIFICATION_VIDEO:
                return lowerContentType.startsWith("video/") && 
                       (lowerContentType.contains("mp4") || 
                        lowerContentType.contains("mov") || 
                        lowerContentType.contains("quicktime"));
                        
            case GDPR_EXPORT:
                return lowerContentType.contains("json") || 
                       lowerContentType.contains("csv") || 
                       lowerContentType.contains("pdf") ||
                       lowerContentType.contains("zip");
                       
            case SYSTEM_LOGS:
                return lowerContentType.contains("text") || 
                       lowerContentType.contains("log") ||
                       lowerContentType.contains("zip") ||
                       lowerContentType.contains("octet-stream");
                       
            default:
                return false;
        }
    }
}