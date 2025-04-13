package com.assassin.service.verification;

import com.assassin.dao.PlayerDao;
import com.assassin.model.Kill;
import com.assassin.model.Player;
import com.assassin.model.VerificationMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Implements kill verification based on NFC tag scanning.
 */
public class NfcVerificationMethod implements IVerificationMethod {

    private static final Logger logger = LoggerFactory.getLogger(NfcVerificationMethod.class);
    private final PlayerDao playerDao;

    /**
     * Constructor for NfcVerificationMethod.
     * @param playerDao DAO to fetch player data (specifically victim's NFC tag ID).
     */
    public NfcVerificationMethod(PlayerDao playerDao) {
        this.playerDao = playerDao;
    }

    @Override
    public VerificationMethod getMethodType() {
        return VerificationMethod.NFC;
    }

    @Override
    public boolean hasRequiredData(Map<String, String> verificationInput) {
        return verificationInput != null &&
               verificationInput.containsKey("scannedNfcTagId");
    }

    @Override
    public VerificationResult verify(Kill kill, Map<String, String> verificationInput, String verifierId) {
        logger.info("Performing NFC verification for kill: Killer={}, Victim={}, Time={}",
                    kill.getKillerID(), kill.getVictimID(), kill.getTime());

        if (!hasRequiredData(verificationInput)) {
            logger.warn("NFC verification failed: Required scanned NFC tag ID missing in input. Kill={}_{}",
                        kill.getKillerID(), kill.getTime());
            return VerificationResult.invalidInput("Required NFC verification data (scannedNfcTagId) is missing.");
        }

        String scannedNfcTagId = verificationInput.get("scannedNfcTagId").trim();

        // Fetch victim's profile to get the expected NFC tag ID
        Player victim;
        try {
            victim = playerDao.getPlayerById(kill.getVictimID())
                   .orElse(null); // Handle case where victim might not be found
        } catch (Exception e) {
            logger.error("NFC verification failed: Error fetching victim profile for ID: {}. Kill={}_{}",
                         kill.getVictimID(), kill.getKillerID(), kill.getTime(), e);
            return VerificationResult.rejected("Could not retrieve victim data for NFC verification.");
        }

        if (victim == null) {
            logger.warn("NFC verification failed: Could not find victim profile for ID: {}. Kill={}_{}",
                        kill.getVictimID(), kill.getKillerID(), kill.getTime());
            return VerificationResult.rejected("Victim profile not found.");
        }

        String expectedNfcTagId = victim.getNfcTagId();
        if (expectedNfcTagId == null || expectedNfcTagId.trim().isEmpty()) {
            logger.warn("NFC verification failed: Victim {} does not have an associated NFC Tag ID. Kill={}_{}",
                        kill.getVictimID(), kill.getKillerID(), kill.getTime());
            return VerificationResult.rejected("Victim does not have an NFC Tag ID configured.");
        }

        boolean matches = expectedNfcTagId.equals(scannedNfcTagId);
        String notes = String.format("NFC Verification: Expected=%s, Scanned=%s. Verified by %s.",
                                     expectedNfcTagId, scannedNfcTagId, verifierId);

        logger.info("NFC Verification Result for Kill={}_{}: Matches={}, Notes={}",
                    kill.getKillerID(), kill.getTime(), matches, notes);

        if (matches) {
            return VerificationResult.verified(notes);
        } else {
            return VerificationResult.rejected(notes + " Scanned tag ID does not match victim's registered tag.");
        }
    }
} 