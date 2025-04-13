package com.assassin.service.verification;

import com.assassin.dao.PlayerDao;
import com.assassin.model.Kill;
import com.assassin.model.VerificationMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationManagerTest {

    @Mock
    private PlayerDao mockPlayerDao;
    @Mock
    private GpsVerificationMethod mockGpsMethod;
    @Mock
    private NfcVerificationMethod mockNfcMethod;
    @Mock
    private PhotoVerificationMethod mockPhotoMethod;

    private VerificationManager verificationManager;
    private Kill testKill;
    private Map<String, String> testInput;
    private String verifierId = "test-verifier";

    @BeforeEach
    void setUp() {
        // Configure mocks to return their type
        when(mockGpsMethod.getMethodType()).thenReturn(VerificationMethod.GPS);
        when(mockNfcMethod.getMethodType()).thenReturn(VerificationMethod.NFC);
        when(mockPhotoMethod.getMethodType()).thenReturn(VerificationMethod.PHOTO);

        // Create VerificationManager instance, explicitly passing mocks
        // We pass PlayerDao even though the manager itself doesn't use it directly,
        // because the real constructor requires it to pass to real default methods.
        // In a pure unit test with mocked strategies, it's less critical, but we'll keep the signature.
        verificationManager = new VerificationManager(mockPlayerDao, mockGpsMethod, mockNfcMethod, mockPhotoMethod);

        testKill = new Kill();
        testKill.setKillerID("killer-mgr-1");
        testKill.setVictimID("victim-mgr-1");
        testKill.setTime("2024-01-01T15:00:00Z");
        testKill.setVerificationStatus("PENDING");

        testInput = new HashMap<>();
        testInput.put("someData", "value");
    }

    @Test
    void constructor_shouldThrowExceptionWhenPlayerDaoIsNull() {
        assertThrows(IllegalArgumentException.class,
                     () -> new VerificationManager(null), // Pass null PlayerDao
                     "VerificationManager constructor should require PlayerDao");
    }

    @Test
    void verifyKill_shouldDelegateToGpsMethod() {
        // Arrange
        testKill.setVerificationMethod(VerificationMethod.GPS.name());
        VerificationResult expectedResult = VerificationResult.verified("GPS Verified");
        when(mockGpsMethod.verify(eq(testKill), eq(testInput), eq(verifierId))).thenReturn(expectedResult);

        // Act
        VerificationResult actualResult = verificationManager.verifyKill(testKill, testInput, verifierId);

        // Assert
        assertSame(expectedResult, actualResult);
        verify(mockGpsMethod).verify(testKill, testInput, verifierId);
        verifyNoInteractions(mockNfcMethod, mockPhotoMethod);
    }

    @Test
    void verifyKill_shouldDelegateToNfcMethod() {
        // Arrange
        testKill.setVerificationMethod(VerificationMethod.NFC.name());
        VerificationResult expectedResult = VerificationResult.verified("NFC Verified");
        when(mockNfcMethod.verify(eq(testKill), eq(testInput), eq(verifierId))).thenReturn(expectedResult);

        // Act
        VerificationResult actualResult = verificationManager.verifyKill(testKill, testInput, verifierId);

        // Assert
        assertSame(expectedResult, actualResult);
        verify(mockNfcMethod).verify(testKill, testInput, verifierId);
        verifyNoInteractions(mockGpsMethod, mockPhotoMethod);
    }

    @Test
    void verifyKill_shouldDelegateToPhotoMethodWhenPending() {
        // Arrange
        testKill.setVerificationMethod(VerificationMethod.PHOTO.name());
        testKill.setVerificationStatus("PENDING"); // Explicitly set
        VerificationResult expectedResult = VerificationResult.pendingReview("Photo Submitted");
        when(mockPhotoMethod.verify(eq(testKill), eq(testInput), eq(verifierId))).thenReturn(expectedResult);

        // Act
        VerificationResult actualResult = verificationManager.verifyKill(testKill, testInput, verifierId);

        // Assert
        assertSame(expectedResult, actualResult);
        verify(mockPhotoMethod).verify(testKill, testInput, verifierId);
        verifyNoInteractions(mockGpsMethod, mockNfcMethod);
    }

    @Test
    void verifyKill_shouldHandleModeratorApprovalWhenPendingReview() {
        // Arrange
        testKill.setVerificationMethod(VerificationMethod.PHOTO.name()); // Method could be anything in review
        testKill.setVerificationStatus("PENDING_REVIEW");
        Map<String, String> moderatorInput = new HashMap<>();
        moderatorInput.put("moderatorAction", "APPROVE");
        moderatorInput.put("moderatorNotes", "Looks good!");
        String moderatorId = "mod-1";

        // Act
        VerificationResult actualResult = verificationManager.verifyKill(testKill, moderatorInput, moderatorId);

        // Assert
        assertNotNull(actualResult);
        assertEquals(VerificationResult.Status.VERIFIED, actualResult.getStatus());
        assertTrue(actualResult.getNotes().contains("Approved by moderator"));
        assertTrue(actualResult.getNotes().contains(moderatorId));
        assertTrue(actualResult.getNotes().contains("Looks good!"));
        // Verify no other methods were called
        verifyNoInteractions(mockGpsMethod, mockNfcMethod, mockPhotoMethod);
    }

    @Test
    void verifyKill_shouldHandleModeratorRejectionWhenPendingReview() {
        // Arrange
        testKill.setVerificationMethod(VerificationMethod.PHOTO.name());
        testKill.setVerificationStatus("PENDING_REVIEW");
        Map<String, String> moderatorInput = new HashMap<>();
        moderatorInput.put("moderatorAction", "REJECT");
        moderatorInput.put("moderatorNotes", "Too blurry");
        String moderatorId = "mod-2";

        // Act
        VerificationResult actualResult = verificationManager.verifyKill(testKill, moderatorInput, moderatorId);

        // Assert
        assertNotNull(actualResult);
        assertEquals(VerificationResult.Status.REJECTED, actualResult.getStatus());
        assertTrue(actualResult.getNotes().contains("Rejected by moderator"));
        assertTrue(actualResult.getNotes().contains(moderatorId));
        assertTrue(actualResult.getNotes().contains("Too blurry"));
        verifyNoInteractions(mockGpsMethod, mockNfcMethod, mockPhotoMethod);
    }

    @Test
    void verifyKill_shouldReturnInvalidInputForMissingModeratorActionWhenPendingReview() {
        // Arrange
        testKill.setVerificationMethod(VerificationMethod.PHOTO.name());
        testKill.setVerificationStatus("PENDING_REVIEW");
        Map<String, String> moderatorInput = new HashMap<>(); // Missing moderatorAction
        String moderatorId = "mod-3";

        // Act
        VerificationResult actualResult = verificationManager.verifyKill(testKill, moderatorInput, moderatorId);

        // Assert
        assertNotNull(actualResult);
        assertEquals(VerificationResult.Status.INVALID_INPUT, actualResult.getStatus());
        assertTrue(actualResult.getNotes().contains("No moderator action provided"));
        verifyNoInteractions(mockGpsMethod, mockNfcMethod, mockPhotoMethod);
    }

    @Test
    void verifyKill_shouldReturnInvalidInputForInvalidModeratorActionWhenPendingReview() {
        // Arrange
        testKill.setVerificationMethod(VerificationMethod.PHOTO.name());
        testKill.setVerificationStatus("PENDING_REVIEW");
        Map<String, String> moderatorInput = new HashMap<>();
        moderatorInput.put("moderatorAction", "MAYBE"); // Invalid action
        String moderatorId = "mod-4";

        // Act
        VerificationResult actualResult = verificationManager.verifyKill(testKill, moderatorInput, moderatorId);

        // Assert
        assertNotNull(actualResult);
        assertEquals(VerificationResult.Status.INVALID_INPUT, actualResult.getStatus());
        assertTrue(actualResult.getNotes().contains("Invalid moderator action: MAYBE"));
        verifyNoInteractions(mockGpsMethod, mockNfcMethod, mockPhotoMethod);
    }


    @Test
    void verifyKill_shouldReturnInvalidInputForUnsupportedMethod() {
        // Arrange
        testKill.setVerificationMethod("TELEPATHY"); // Unsupported method

        // Act
        VerificationResult actualResult = verificationManager.verifyKill(testKill, testInput, verifierId);

        // Assert
        assertNotNull(actualResult);
        assertEquals(VerificationResult.Status.INVALID_INPUT, actualResult.getStatus());
        assertTrue(actualResult.getNotes().contains("Unsupported verification method: NONE")); // fromString defaults unknown to NONE
        verifyNoInteractions(mockGpsMethod, mockNfcMethod, mockPhotoMethod);
    }

    @Test
    void verifyKill_shouldReturnInvalidInputForNullKill() {
        // Act
        VerificationResult actualResult = verificationManager.verifyKill(null, testInput, verifierId);

        // Assert
        assertNotNull(actualResult);
        assertEquals(VerificationResult.Status.INVALID_INPUT, actualResult.getStatus());
        assertEquals("Kill record is null", actualResult.getNotes());
        verifyNoInteractions(mockGpsMethod, mockNfcMethod, mockPhotoMethod);
    }

    @Test
    void verifyKill_shouldUseEmptyMapForNullInput() {
        // Arrange
        testKill.setVerificationMethod(VerificationMethod.GPS.name());
        VerificationResult expectedResult = VerificationResult.verified("GPS Verified");
        // Expect verify to be called with an empty map, not null
        when(mockGpsMethod.verify(eq(testKill), eq(new HashMap<>()), eq(verifierId))).thenReturn(expectedResult);

        // Act
        VerificationResult actualResult = verificationManager.verifyKill(testKill, null, verifierId); // Pass null input

        // Assert
        assertSame(expectedResult, actualResult);
        verify(mockGpsMethod).verify(testKill, new HashMap<>(), verifierId);
    }

    @Test
    void isMethodSupported_shouldReturnTrueForRegisteredMethods() {
        assertTrue(verificationManager.isMethodSupported(VerificationMethod.GPS));
        assertTrue(verificationManager.isMethodSupported(VerificationMethod.NFC));
        assertTrue(verificationManager.isMethodSupported(VerificationMethod.PHOTO));
    }

    @Test
    void isMethodSupported_shouldReturnFalseForUnregisteredMethods() {
        assertFalse(verificationManager.isMethodSupported(VerificationMethod.MANUAL));
        assertFalse(verificationManager.isMethodSupported(VerificationMethod.NONE));
        assertFalse(verificationManager.isMethodSupported(VerificationMethod.TEST_MODE));
    }

    @Test
    void getSupportedMethods_shouldReturnMapOfRegisteredMethods() {
        Map<VerificationMethod, IVerificationMethod> supported = verificationManager.getSupportedMethods();
        assertEquals(3, supported.size());
        assertTrue(supported.containsKey(VerificationMethod.GPS));
        assertTrue(supported.containsKey(VerificationMethod.NFC));
        assertTrue(supported.containsKey(VerificationMethod.PHOTO));
        assertSame(mockGpsMethod, supported.get(VerificationMethod.GPS));
        assertSame(mockNfcMethod, supported.get(VerificationMethod.NFC));
        assertSame(mockPhotoMethod, supported.get(VerificationMethod.PHOTO));
    }
} 