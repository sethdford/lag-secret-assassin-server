package com.assassin.service.verification;

import com.assassin.model.Kill;
import com.assassin.model.VerificationMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PhotoVerificationMethodTest {

    private PhotoVerificationMethod photoVerificationMethod;
    private Kill testKill;
    private String verifierId = "test-verifier";
    private static final String PHOTO_URL = "https://example.com/photo.jpg";

    @BeforeEach
    void setUp() {
        photoVerificationMethod = new PhotoVerificationMethod();
        testKill = new Kill();
        testKill.setKillerID("killer-photo-1");
        testKill.setVictimID("victim-photo-1");
        testKill.setTime("2024-01-01T14:00:00Z");
        testKill.setVerificationMethod(VerificationMethod.PHOTO.name());
    }

    @Test
    void getMethodType_shouldReturnPhoto() {
        assertEquals(VerificationMethod.PHOTO, photoVerificationMethod.getMethodType());
    }

    @Test
    void hasRequiredData_shouldReturnTrueWhenPhotoUrlPresent() {
        Map<String, String> input = new HashMap<>();
        input.put("photoUrl", PHOTO_URL);
        assertTrue(photoVerificationMethod.hasRequiredData(input));
    }

    @Test
    void hasRequiredData_shouldReturnFalseWhenPhotoUrlMissing() {
        Map<String, String> input = new HashMap<>();
        assertFalse(photoVerificationMethod.hasRequiredData(input));
    }

    @Test
    void hasRequiredData_shouldReturnFalseWhenPhotoUrlEmpty() {
        Map<String, String> input = new HashMap<>();
        input.put("photoUrl", "");
        assertFalse(photoVerificationMethod.hasRequiredData(input));
    }

     @Test
    void hasRequiredData_shouldReturnFalseWhenPhotoUrlBlank() {
        Map<String, String> input = new HashMap<>();
        input.put("photoUrl", "   "); // Blank space
        assertFalse(photoVerificationMethod.hasRequiredData(input));
    }

    @Test
    void hasRequiredData_shouldReturnFalseWhenInputNull() {
        assertFalse(photoVerificationMethod.hasRequiredData(null));
    }

    @Test
    void verify_shouldReturnPendingReviewWhenPhotoUrlPresent() {
        // Arrange
        Map<String, String> input = new HashMap<>();
        input.put("photoUrl", PHOTO_URL);

        // Act
        VerificationResult result = photoVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.PENDING_REVIEW, result.getStatus());
        assertTrue(result.getNotes().contains("Photo submitted for review"));
        assertTrue(result.getNotes().contains(PHOTO_URL));
    }

    @Test
    void verify_shouldReturnInvalidInputWhenPhotoUrlMissing() {
        // Arrange
        Map<String, String> input = new HashMap<>(); // Missing photoUrl

        // Act
        VerificationResult result = photoVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.INVALID_INPUT, result.getStatus());
        assertTrue(result.getNotes().contains("Required photo verification data"));
    }

    @Test
    void verify_shouldReturnInvalidInputWhenPhotoUrlEmpty() {
        // Arrange
        Map<String, String> input = new HashMap<>();
        input.put("photoUrl", "");

        // Act
        VerificationResult result = photoVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.INVALID_INPUT, result.getStatus());
        assertTrue(result.getNotes().contains("Required photo verification data"));
    }
} 