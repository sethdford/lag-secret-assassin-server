package com.assassin.service;

import com.assassin.config.ModerationConfig;
import com.assassin.exception.ModerationException;
import com.assassin.model.ModerationFlag;
import com.assassin.model.ModerationRequest;
import com.assassin.model.ModerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.*;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AWS-based implementation of ContentModerationService using Rekognition and Comprehend
 */
public class AwsContentModerationService implements ContentModerationService {
    
    private static final Logger LOG = LoggerFactory.getLogger(AwsContentModerationService.class);
    
    private final RekognitionClient rekognitionClient;
    private final ComprehendClient comprehendClient;
    private final ModerationCacheService cacheService;
    private final ModerationConfig config;
    
    public AwsContentModerationService() {
        this.rekognitionClient = RekognitionClient.builder()
                .region(Region.US_EAST_1)
                .build();
        this.comprehendClient = ComprehendClient.builder()
                .region(Region.US_EAST_1)
                .build();
        this.cacheService = new ModerationCacheService();
        this.config = ModerationConfig.fromEnvironment();
        
        LOG.info("AwsContentModerationService initialized with config: {}", config);
    }
    
    // Constructor for testing
    public AwsContentModerationService(RekognitionClient rekognitionClient, 
                                     ComprehendClient comprehendClient,
                                     ModerationCacheService cacheService,
                                     ModerationConfig config) {
        this.rekognitionClient = rekognitionClient;
        this.comprehendClient = comprehendClient;
        this.cacheService = cacheService;
        this.config = config;
        
        LOG.info("AwsContentModerationService initialized with injected dependencies");
    }

    @Override
    public CompletableFuture<ModerationResult> moderateContent(String textContent, List<String> mediaUrls) {
        LOG.info("Moderating combined content: text={}, mediaCount={}", 
                textContent != null ? "present" : "none", 
                mediaUrls != null ? mediaUrls.size() : 0);
        
        List<CompletableFuture<List<ModerationFlag>>> futures = new ArrayList<>();
        
        // Add text moderation if present
        if (textContent != null && !textContent.trim().isEmpty()) {
            futures.add(moderateTextContent(textContent));
        }
        
        // Add image moderation if present
        if (mediaUrls != null && !mediaUrls.isEmpty()) {
            for (String url : mediaUrls) {
                futures.add(moderateImageUrl(url));
            }
        }
        
        if (futures.isEmpty()) {
            LOG.warn("No content provided for moderation");
            return CompletableFuture.completedFuture(
                new ModerationResult(ModerationResult.Status.APPROVED, 
                                   ModerationResult.ContentType.COMBINED, 
                                   Collections.emptyList()));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<ModerationFlag> allFlags = futures.stream()
                            .map(CompletableFuture::join)
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    
                    return createModerationResult(allFlags, ModerationResult.ContentType.COMBINED);
                })
                .exceptionally(throwable -> {
                    LOG.error("Error during combined content moderation", throwable);
                    throw new ModerationException("Failed to moderate combined content", throwable);
                });
    }

    @Override
    public CompletableFuture<ModerationResult> moderateText(String content) {
        if (content == null || content.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                new ModerationResult(ModerationResult.Status.APPROVED, 
                                   ModerationResult.ContentType.TEXT, 
                                   Collections.emptyList()));
        }
        
        LOG.info("Moderating text content of length: {}", content.length());
        
        return moderateTextContent(content)
                .thenApply(flags -> createModerationResult(flags, ModerationResult.ContentType.TEXT))
                .exceptionally(throwable -> {
                    LOG.error("Error during text moderation", throwable);
                    throw ModerationException.textError(throwable.getMessage(), throwable);
                });
    }

    @Override
    public CompletableFuture<ModerationResult> moderateImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return CompletableFuture.completedFuture(
                new ModerationResult(ModerationResult.Status.APPROVED, 
                                   ModerationResult.ContentType.IMAGE, 
                                   Collections.emptyList()));
        }
        
        LOG.info("Moderating {} images", imageUrls.size());
        
        List<CompletableFuture<List<ModerationFlag>>> futures = imageUrls.stream()
                .map(this::moderateImageUrl)
                .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<ModerationFlag> allFlags = futures.stream()
                            .map(CompletableFuture::join)
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    
                    return createModerationResult(allFlags, ModerationResult.ContentType.IMAGE);
                })
                .exceptionally(throwable -> {
                    LOG.error("Error during image moderation", throwable);
                    throw ModerationException.imageError(throwable.getMessage(), throwable);
                });
    }

    @Override
    public boolean isContentAppropriate(ModerationResult result) {
        return result != null && result.getStatus() == ModerationResult.Status.APPROVED;
    }

    @Override
    public ModerationResult moderateImage(ModerationRequest request) {
        if (request == null || request.getImageUrl() == null) {
            return new ModerationResult(ModerationResult.Status.APPROVED, 
                                      ModerationResult.ContentType.IMAGE, 
                                      Collections.emptyList());
        }
        
        try {
            CompletableFuture<ModerationResult> future = moderateImages(List.of(request.getImageUrl()));
            return future.get(); // Block and wait for result
        } catch (Exception e) {
            LOG.error("Error in synchronous image moderation", e);
            throw ModerationException.imageError("Synchronous image moderation failed", e);
        }
    }

    @Override
    public ModerationResult moderateText(ModerationRequest request) {
        if (request == null || request.getTextContent() == null) {
            return new ModerationResult(ModerationResult.Status.APPROVED, 
                                      ModerationResult.ContentType.TEXT, 
                                      Collections.emptyList());
        }
        
        try {
            CompletableFuture<ModerationResult> future = moderateText(request.getTextContent());
            return future.get(); // Block and wait for result
        } catch (Exception e) {
            LOG.error("Error in synchronous text moderation", e);
            throw ModerationException.textError("Synchronous text moderation failed", e);
        }
    }
    
    /**
     * Moderate text content using AWS Comprehend
     */
    private CompletableFuture<List<ModerationFlag>> moderateTextContent(String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (text == null || text.trim().isEmpty()) {
                    return Collections.emptyList();
                }
                
                // Check cache first
                String cacheKey = "text:" + generateContentHash(text);
                Optional<List<ModerationFlag>> cached = cacheService.getFlags(cacheKey);
                if (cached.isPresent()) {
                    LOG.debug("Cache hit for text moderation");
                    return cached.get();
                }
                
                List<ModerationFlag> flags = new ArrayList<>();
                
                // Detect toxic content using Comprehend
                DetectToxicContentRequest toxicRequest = DetectToxicContentRequest.builder()
                        .textSegments(TextSegment.builder()
                                .text(text)
                                .build())
                        .languageCode("en")
                        .build();
                
                DetectToxicContentResponse toxicResponse = comprehendClient.detectToxicContent(toxicRequest);
                
                toxicResponse.resultList().forEach(result -> {
                    result.labels().forEach(label -> {
                        if (label.score() >= config.getTextModerationThreshold()) {
                            flags.add(new ModerationFlag(
                                    label.name().toString(),
                                    label.score(),
                                    "Comprehend",
                                    Map.of("toxicityType", label.name().toString())
                            ));
                        }
                    });
                });
                
                // Cache the result if caching is enabled
                if (config.isCacheEnabled()) {
                    cacheService.putFlags(cacheKey, flags, config.getCacheExpiration());
                }
                
                LOG.debug("Text moderation completed with {} flags", flags.size());
                return flags;
                
            } catch (Exception e) {
                LOG.error("Error moderating text content: {}", e.getMessage(), e);
                throw ModerationException.textError(e.getMessage(), e);
            }
        });
    }
    
    /**
     * Moderate image content using AWS Rekognition
     */
    private CompletableFuture<List<ModerationFlag>> moderateImageUrl(String imageUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check cache first
                String cacheKey = "image:" + generateContentHash(imageUrl);
                Optional<List<ModerationFlag>> cached = cacheService.getFlags(cacheKey);
                if (cached.isPresent()) {
                    LOG.debug("Cache hit for image moderation: {}", imageUrl);
                    return cached.get();
                }
                
                // Parse S3 URL to get bucket and key
                String[] s3Info = parseS3Url(imageUrl);
                String bucket = s3Info[0];
                String key = s3Info[1];
                
                // Call Rekognition DetectModerationLabels
                DetectModerationLabelsRequest request = DetectModerationLabelsRequest.builder()
                        .image(Image.builder()
                                .s3Object(S3Object.builder()
                                        .bucket(bucket)
                                        .name(key)
                                        .build())
                                .build())
                        .minConfidence((float) config.getImageModerationThreshold())
                        .build();
                
                DetectModerationLabelsResponse response = rekognitionClient.detectModerationLabels(request);
                
                List<ModerationFlag> flags = response.moderationLabels().stream()
                        .map(label -> new ModerationFlag(
                                label.name(),
                                label.confidence(),
                                "Rekognition",
                                Map.of(
                                        "parentName", label.parentName() != null ? label.parentName() : "",
                                        "imageUrl", imageUrl
                                )
                        ))
                        .collect(Collectors.toList());
                
                // Cache the result if caching is enabled
                if (config.isCacheEnabled()) {
                    cacheService.putFlags(cacheKey, flags, config.getCacheExpiration());
                }
                
                LOG.debug("Image moderation completed for {} with {} flags", imageUrl, flags.size());
                return flags;
                
            } catch (Exception e) {
                LOG.error("Error moderating image {}: {}", imageUrl, e.getMessage(), e);
                throw ModerationException.imageError("Failed to moderate image: " + imageUrl, e);
            }
        });
    }
    
    /**
     * Create a ModerationResult from flags
     */
    private ModerationResult createModerationResult(List<ModerationFlag> flags, ModerationResult.ContentType contentType) {
        ModerationResult result = new ModerationResult();
        result.setContentType(contentType);
        result.setFlags(flags);
        
        if (flags.isEmpty()) {
            result.setStatus(ModerationResult.Status.APPROVED);
            result.setReason("No inappropriate content detected");
        } else {
            // Determine status based on highest confidence flag
            double maxConfidence = flags.stream()
                    .mapToDouble(ModerationFlag::getConfidence)
                    .max()
                    .orElse(0.0);
            
            if (maxConfidence >= config.getImageModerationThreshold()) {
                result.setStatus(ModerationResult.Status.REJECTED);
                result.setReason("Content flagged as inappropriate with high confidence");
            } else if (maxConfidence >= config.getManualReviewThreshold()) {
                result.setStatus(ModerationResult.Status.PENDING_MANUAL_REVIEW);
                result.setReason("Content flagged for manual review");
            } else {
                result.setStatus(ModerationResult.Status.APPROVED);
                result.setReason("Content approved despite low-confidence flags");
            }
        }
        
        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("flagCount", flags.size());
        metadata.put("maxConfidence", result.getConfidenceScore());
        metadata.put("sources", flags.stream()
                .map(ModerationFlag::getSource)
                .distinct()
                .collect(Collectors.toList()));
        result.setMetadata(metadata);
        
        LOG.info("Created moderation result: status={}, flagCount={}, maxConfidence={}", 
                result.getStatus(), flags.size(), result.getConfidenceScore());
        
        return result;
    }
    
    /**
     * Parse S3 URL to extract bucket and key
     */
    private String[] parseS3Url(String s3Url) {
        try {
            if (s3Url.contains(".s3.") && s3Url.contains(".amazonaws.com/")) {
                // Format: https://bucket-name.s3.region.amazonaws.com/key
                String bucket = s3Url.split("\\.")[0].substring(s3Url.lastIndexOf("//") + 2);
                String key = s3Url.substring(s3Url.indexOf(".amazonaws.com/") + 15);
                return new String[]{bucket, key};
            } else if (s3Url.contains("s3.amazonaws.com/")) {
                // Format: https://s3.amazonaws.com/bucket-name/key
                String[] parts = s3Url.substring(s3Url.indexOf("s3.amazonaws.com/") + 17).split("/", 2);
                return new String[]{parts[0], parts[1]};
            }
            throw new IllegalArgumentException("Unsupported S3 URL format: " + s3Url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid S3 URL format: " + s3Url, e);
        }
    }
    
    /**
     * Generate a hash for content to use as cache key
     */
    private String generateContentHash(String content) {
        return String.valueOf(content.hashCode());
    }
    
    /**
     * Shutdown method to clean up resources
     */
    public void shutdown() {
        try {
            cacheService.shutdown();
            rekognitionClient.close();
            comprehendClient.close();
            LOG.info("AwsContentModerationService shutdown completed");
        } catch (Exception e) {
            LOG.error("Error during service shutdown", e);
        }
    }
} 