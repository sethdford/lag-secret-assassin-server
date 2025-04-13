package com.assassin.service.verification;

import com.assassin.dao.PlayerDao;
import com.assassin.model.Kill;
import com.assassin.model.Player;
import com.assassin.model.VerificationMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NfcVerificationMethodTest {

    @Mock
    private PlayerDao mockPlayerDao;

    @InjectMocks
    private NfcVerificationMethod nfcVerificationMethod;

    private Kill testKill;
    private Player testVictim;
    private String verifierId = "test-verifier";
    private static final String VICTIM_ID = "victim-nfc-1";
    private static final String CORRECT_NFC_TAG = "nfc-tag-12345";
    private static final String INCORRECT_NFC_TAG = "nfc-tag-wrong";

    @BeforeEach
    void setUp() {
        // Note: @InjectMocks creates nfcVerificationMethod instance with mockPlayerDao

        testKill = new Kill();
        testKill.setKillerID("killer-nfc-1");
        testKill.setVictimID(VICTIM_ID);
        testKill.setTime("2024-01-01T13:00:00Z");
        testKill.setVerificationMethod(VerificationMethod.NFC.name());

        testVictim = new Player();
        testVictim.setPlayerID(VICTIM_ID);
        testVictim.setPlayerName("Victim NFC");
        testVictim.setNfcTagId(CORRECT_NFC_TAG);
    }

    @Test
    void getMethodType_shouldReturnNfc() {
        assertEquals(VerificationMethod.NFC, nfcVerificationMethod.getMethodType());
    }

    @Test
    void hasRequiredData_shouldReturnTrueWhenDataPresent() {
        Map<String, String> input = new HashMap<>();
        input.put("scannedNfcTagId", CORRECT_NFC_TAG);
        assertTrue(nfcVerificationMethod.hasRequiredData(input));
    }

    @Test
    void hasRequiredData_shouldReturnFalseWhenTagIdMissing() {
        Map<String, String> input = new HashMap<>();
        assertFalse(nfcVerificationMethod.hasRequiredData(input));
    }

    @Test
    void hasRequiredData_shouldReturnFalseWhenInputNull() {
        assertFalse(nfcVerificationMethod.hasRequiredData(null));
    }

    @Test
    void verify_shouldReturnVerifiedWhenTagsMatch() {
        // Arrange
        when(mockPlayerDao.getPlayerById(VICTIM_ID)).thenReturn(Optional.of(testVictim));
        Map<String, String> input = new HashMap<>();
        input.put("scannedNfcTagId", CORRECT_NFC_TAG);

        // Act
        VerificationResult result = nfcVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.VERIFIED, result.getStatus());
        assertTrue(result.getNotes().contains("NFC Verification"));
        assertTrue(result.getNotes().contains("Expected="+CORRECT_NFC_TAG));
    }

    @Test
    void verify_shouldReturnRejectedWhenTagsDoNotMatch() {
        // Arrange
        when(mockPlayerDao.getPlayerById(VICTIM_ID)).thenReturn(Optional.of(testVictim));
        Map<String, String> input = new HashMap<>();
        input.put("scannedNfcTagId", INCORRECT_NFC_TAG);

        // Act
        VerificationResult result = nfcVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.REJECTED, result.getStatus());
        assertTrue(result.getNotes().contains("does not match victim's registered tag"));
    }

    @Test
    void verify_shouldReturnRejectedWhenVictimHasNoTagId() {
        // Arrange
        testVictim.setNfcTagId(null);
        when(mockPlayerDao.getPlayerById(VICTIM_ID)).thenReturn(Optional.of(testVictim));
        Map<String, String> input = new HashMap<>();
        input.put("scannedNfcTagId", CORRECT_NFC_TAG);

        // Act
        VerificationResult result = nfcVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.REJECTED, result.getStatus());
        assertTrue(result.getNotes().contains("does not have an NFC Tag ID configured"));
    }

    @Test
    void verify_shouldReturnRejectedWhenVictimNotFound() {
        // Arrange
        when(mockPlayerDao.getPlayerById(VICTIM_ID)).thenReturn(Optional.empty());
        Map<String, String> input = new HashMap<>();
        input.put("scannedNfcTagId", CORRECT_NFC_TAG);

        // Act
        VerificationResult result = nfcVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.REJECTED, result.getStatus());
        assertTrue(result.getNotes().contains("Victim profile not found"));
    }

    @Test
    void verify_shouldReturnRejectedWhenDaoThrowsException() {
        // Arrange
        when(mockPlayerDao.getPlayerById(VICTIM_ID)).thenThrow(new RuntimeException("DAO Error"));
        Map<String, String> input = new HashMap<>();
        input.put("scannedNfcTagId", CORRECT_NFC_TAG);

        // Act
        VerificationResult result = nfcVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.REJECTED, result.getStatus());
        assertTrue(result.getNotes().contains("Could not retrieve victim data"));
    }

    @Test
    void verify_shouldReturnInvalidInputWhenScannedTagMissing() {
        // Arrange
        // No need to mock DAO as it shouldn't be called
        Map<String, String> input = new HashMap<>(); // Missing scannedNfcTagId

        // Act
        VerificationResult result = nfcVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.INVALID_INPUT, result.getStatus());
        assertTrue(result.getNotes().contains("Required NFC verification data"));
    }
}
