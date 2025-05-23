package com.assassin.service;

import com.assassin.model.ModerationRequest;
import com.assassin.model.ModerationResult;
// Potentially add an exception class like ModerationException if needed

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for content moderation services.
 * Implementations will connect to AI moderation providers like AWS Rekognition.
 */
public interface ContentModerationService {

    /**
     * Moderates an image provided via a URL.
     *
     * @param request The moderation request containing the image URL and other context.
     * @return A ModerationResult indicating if the image is approved, rejected, or needs manual review.
     */
    ModerationResult moderateImage(ModerationRequest request);

    /**
     * Moderates text content.
     *
     * @param request The moderation request containing the text content and other context.
     * @return A ModerationResult indicating if the text is approved, rejected, or needs manual review.
     */
    ModerationResult moderateText(ModerationRequest request); // Placeholder, can be expanded

    // We could also add methods that take image bytes directly, but for Lambda,
    // it's often better to have images in S3 and pass URLs or S3 object keys.

    /**
     * Moderate both text content and media URLs together
     * 
     * @param textContent The text content to moderate (can be null)
     * @param mediaUrls List of media URLs to moderate (can be null or empty)
     * @return CompletableFuture containing the moderation result
     */
    CompletableFuture<ModerationResult> moderateContent(String textContent, List<String> mediaUrls);
    
    /**
     * Moderate text content only
     * 
     * @param content The text content to moderate
     * @return CompletableFuture containing the moderation result
     */
    CompletableFuture<ModerationResult> moderateText(String content);
    
    /**
     * Moderate images only
     * 
     * @param imageUrls List of image URLs to moderate
     * @return CompletableFuture containing the moderation result
     */
    CompletableFuture<ModerationResult> moderateImages(List<String> imageUrls);
    
    /**
     * Check if content is appropriate based on configured thresholds
     * 
     * @param result The moderation result to evaluate
     * @return true if content is appropriate, false otherwise
     */
    boolean isContentAppropriate(ModerationResult result);
} 