package com.assassin.service;

import com.assassin.config.ModerationConfig;
import com.assassin.exception.ModerationException;
import com.assassin.model.ModerationFlag;
import com.assassin.model.ModerationRequest;
import com.assassin.model.ModerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.*;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwsContentModerationServiceTest {

    @Mock
    private RekognitionClient rekognitionClient;

    @Mock
    private ComprehendClient comprehendClient;

    @Mock
    private ModerationCacheService cacheService;

    private ModerationConfig config;
    private AwsContentModerationService moderationService;

    @BeforeEach
    void setUp() {
        config = ModerationConfig.defaultConfig();
        moderationService = new AwsContentModerationService(
                rekognitionClient, comprehendClient, cacheService, config);
    }

    @Test
    void testModerateImages_WithCleanContent_ReturnsApproved() {
        // Arrange
        List<String> imageUrls = List.of("https://test-bucket.s3.amazonaws.com/clean-image.jpg");
        when(cacheService.getFlags(any())).thenReturn(Optional.empty());
        
        DetectModerationLabelsResponse response = DetectModerationLabelsResponse.builder()
                .moderationLabels(Collections.emptyList())
                .build();
        when(rekognitionClient.detectModerationLabels(any(DetectModerationLabelsRequest.class)))
                .thenReturn(response);

        // Act
        CompletableFuture<ModerationResult> future = moderationService.moderateImages(imageUrls);
        ModerationResult result = future.join();

        // Assert
        assertEquals(ModerationResult.Status.APPROVED, result.getStatus());
        assertEquals(ModerationResult.ContentType.IMAGE, result.getContentType());
        assertTrue(result.getFlags().isEmpty());
    }

    @Test
    void testModerateImages_WithInappropriateContent_ReturnsRejected() {
        // Arrange
        List<String> imageUrls = List.of("https://test-bucket.s3.amazonaws.com/test-image.jpg");
        when(cacheService.getFlags(any())).thenReturn(Optional.empty());
        
        ModerationLabel moderationLabel = ModerationLabel.builder()
                .name("Explicit Nudity")
                .confidence(95.0f)
                .parentName("Explicit Nudity")
                .build();
        
        DetectModerationLabelsResponse response = DetectModerationLabelsResponse.builder()
                .moderationLabels(List.of(moderationLabel))
                .build();
        when(rekognitionClient.detectModerationLabels(any(DetectModerationLabelsRequest.class)))
                .thenReturn(response);

        // Act
        CompletableFuture<ModerationResult> future = moderationService.moderateImages(imageUrls);
        ModerationResult result = future.join();

        // Assert
        assertEquals(ModerationResult.Status.REJECTED, result.getStatus());
        assertEquals(ModerationResult.ContentType.IMAGE, result.getContentType());
        assertFalse(result.getFlags().isEmpty());
        assertEquals("Explicit Nudity", result.getFlags().get(0).getFlagType());
        assertEquals(95.0, result.getFlags().get(0).getConfidence(), 0.01);
        assertEquals("Rekognition", result.getFlags().get(0).getSource());
    }

    @Test
    void testCacheHit_ReturnsFromCache() {
        // Arrange
        String textContent = "Cached content";
        ModerationFlag cachedFlag = new ModerationFlag("TEST", 90.0, "Comprehend", Collections.emptyMap());
        when(cacheService.getFlags(any())).thenReturn(Optional.of(List.of(cachedFlag)));

        // Act
        CompletableFuture<ModerationResult> future = moderationService.moderateText(textContent);
        ModerationResult result = future.join();

        // Assert
        assertEquals(ModerationResult.Status.REJECTED, result.getStatus());
        assertEquals(1, result.getFlags().size());
        assertEquals("TEST", result.getFlags().get(0).getFlagType());
        
        // Verify no AWS service calls were made
        verifyNoInteractions(comprehendClient);
        verifyNoInteractions(rekognitionClient);
    }

    @Test
    void testModerateImage_SynchronousMethod_Success() {
        // Arrange
        ModerationRequest request = new ModerationRequest("test-id", "https://test-bucket.s3.amazonaws.com/test.jpg");
        when(cacheService.getFlags(any())).thenReturn(Optional.empty());
        
        DetectModerationLabelsResponse response = DetectModerationLabelsResponse.builder()
                .moderationLabels(Collections.emptyList())
                .build();
        when(rekognitionClient.detectModerationLabels(any(DetectModerationLabelsRequest.class)))
                .thenReturn(response);

        // Act
        ModerationResult result = moderationService.moderateImage(request);

        // Assert
        assertEquals(ModerationResult.Status.APPROVED, result.getStatus());
        assertEquals(ModerationResult.ContentType.IMAGE, result.getContentType());
    }

    @Test
    void testIsContentAppropriate_WithApprovedResult_ReturnsTrue() {
        // Arrange
        ModerationResult result = new ModerationResult(
                ModerationResult.Status.APPROVED, 
                ModerationResult.ContentType.TEXT, 
                Collections.emptyList());

        // Act & Assert
        assertTrue(moderationService.isContentAppropriate(result));
    }

    @Test
    void testIsContentAppropriate_WithRejectedResult_ReturnsFalse() {
        // Arrange
        ModerationResult result = new ModerationResult(
                ModerationResult.Status.REJECTED, 
                ModerationResult.ContentType.TEXT, 
                Collections.emptyList());

        // Act & Assert
        assertFalse(moderationService.isContentAppropriate(result));
    }

    @Test
    void testModerateText_WithNullContent_ReturnsApproved() {
        // Act
        CompletableFuture<ModerationResult> future = moderationService.moderateText((String) null);
        ModerationResult result = future.join();

        // Assert
        assertEquals(ModerationResult.Status.APPROVED, result.getStatus());
        assertEquals(ModerationResult.ContentType.TEXT, result.getContentType());
        assertTrue(result.getFlags().isEmpty());
    }

    @Test
    void testModerateImages_WithEmptyList_ReturnsApproved() {
        // Act
        CompletableFuture<ModerationResult> future = moderationService.moderateImages(Collections.emptyList());
        ModerationResult result = future.join();

        // Assert
        assertEquals(ModerationResult.Status.APPROVED, result.getStatus());
        assertEquals(ModerationResult.ContentType.IMAGE, result.getContentType());
        assertTrue(result.getFlags().isEmpty());
    }

    @Test
    void testConfigurationValues() {
        // Assert configuration is properly loaded
        assertEquals(80.0, config.getImageModerationThreshold());
        assertEquals(0.7, config.getTextModerationThreshold());
        assertEquals(50.0, config.getManualReviewThreshold());
        assertTrue(config.isCacheEnabled());
        assertEquals(Duration.ofHours(24), config.getCacheExpiration());
    }

    @Test
    void testModerationException_Creation() {
        // Test exception factory methods
        ModerationException imageError = ModerationException.imageError("Test image error", new RuntimeException("cause"));
        assertEquals("IMAGE_MODERATION_ERROR", imageError.getErrorCode());
        assertTrue(imageError.getMessage().contains("Test image error"));

        ModerationException textError = ModerationException.textError("Test text error", new RuntimeException("cause"));
        assertEquals("TEXT_MODERATION_ERROR", textError.getErrorCode());
        assertTrue(textError.getMessage().contains("Test text error"));

        ModerationException configError = ModerationException.configError("Config issue");
        assertEquals("CONFIG_ERROR", configError.getErrorCode());

        ModerationException serviceError = ModerationException.serviceUnavailable("TestService", new RuntimeException());
        assertEquals("SERVICE_UNAVAILABLE", serviceError.getErrorCode());
    }
} 