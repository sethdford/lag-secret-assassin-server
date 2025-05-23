package com.assassin.model;

public class ModerationRequest {

    private String contentId; // Unique ID for the content being moderated (e.g., killId, imageId)
    private String textContent; // Optional: Text content to moderate (e.g., chat message, description)
    private String imageUrl;    // Optional: URL of an image to moderate
    // private byte[] imageBytes; // Optional: Image bytes if not using URL (consider S3 pre-signed URL instead for Lambda)
    private String userId;      // Optional: User ID associated with the content for context/tracking
    private String gameId;      // Optional: Game ID associated with the content

    public ModerationRequest(String contentId, String imageUrl) {
        this.contentId = contentId;
        this.imageUrl = imageUrl;
    }

    public ModerationRequest(String contentId, String textContent, String imageUrl) {
        this.contentId = contentId;
        this.textContent = textContent;
        this.imageUrl = imageUrl;
    }

    // Getters and Setters
    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
} 