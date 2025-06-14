package com.assassin.model;

/**
 * Enumeration of supported media types in the Assassin Game system.
 * Each type has specific validation rules and storage policies.
 */
public enum MediaType {
    /**
     * Player profile images (avatars)
     * - Max size: 2MB
     * - Formats: JPG, PNG, WEBP
     * - Dimensions: 512x512 max
     */
    PROFILE_IMAGE("profile", "image", 2 * 1024 * 1024, 512, 512),
    
    /**
     * Kill verification photos
     * - Max size: 5MB
     * - Formats: JPG, PNG
     * - Dimensions: 1920x1080 max
     */
    KILL_VERIFICATION_PHOTO("kill-verification", "image", 5 * 1024 * 1024, 1920, 1080),
    
    /**
     * Kill verification videos
     * - Max size: 50MB
     * - Formats: MP4, MOV
     * - Duration: 30 seconds max
     */
    KILL_VERIFICATION_VIDEO("kill-verification", "video", 50 * 1024 * 1024, 1920, 1080),
    
    /**
     * Game screenshots
     * - Max size: 3MB
     * - Formats: JPG, PNG
     * - Dimensions: 1920x1080 max
     */
    GAME_SCREENSHOT("screenshots", "image", 3 * 1024 * 1024, 1920, 1080),
    
    /**
     * GDPR data exports
     * - Max size: 100MB
     * - Formats: JSON, CSV, PDF
     * - No dimension limits
     */
    GDPR_EXPORT("gdpr-exports", "document", 100 * 1024 * 1024, 0, 0),
    
    /**
     * System logs and backups
     * - Max size: 500MB
     * - Formats: LOG, TXT, ZIP
     * - No dimension limits
     */
    SYSTEM_LOGS("system-logs", "document", 500 * 1024 * 1024, 0, 0);
    
    private final String folder;
    private final String category;
    private final long maxSizeBytes;
    private final int maxWidth;
    private final int maxHeight;
    
    MediaType(String folder, String category, long maxSizeBytes, int maxWidth, int maxHeight) {
        this.folder = folder;
        this.category = category;
        this.maxSizeBytes = maxSizeBytes;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }
    
    public String getFolder() {
        return folder;
    }
    
    public String getCategory() {
        return category;
    }
    
    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }
    
    public int getMaxWidth() {
        return maxWidth;
    }
    
    public int getMaxHeight() {
        return maxHeight;
    }
    
    public boolean isImage() {
        return "image".equals(category);
    }
    
    public boolean isVideo() {
        return "video".equals(category);
    }
    
    public boolean isDocument() {
        return "document".equals(category);
    }
    
    /**
     * Get the S3 key prefix for this media type
     * @param userId User ID for organization
     * @param gameId Game ID for organization (optional)
     * @return S3 key prefix
     */
    public String getS3KeyPrefix(String userId, String gameId) {
        StringBuilder prefix = new StringBuilder();
        prefix.append(folder).append("/");
        
        if (gameId != null && !gameId.isEmpty()) {
            prefix.append("games/").append(gameId).append("/");
        }
        
        prefix.append("users/").append(userId).append("/");
        return prefix.toString();
    }
}