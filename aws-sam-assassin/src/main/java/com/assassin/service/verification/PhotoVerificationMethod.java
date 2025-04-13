package com.assassin.service.verification;

import com.assassin.model.Kill;
import com.assassin.model.VerificationMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Implements kill verification based on photo evidence submission.
 * This method does not automatically verify; it sets the status to PENDING_REVIEW
 * for manual moderator approval.
 */
public class PhotoVerificationMethod implements IVerificationMethod {

    private static final Logger logger = LoggerFactory.getLogger(PhotoVerificationMethod.class);

    @Override
    public VerificationMethod getMethodType() {
        return VerificationMethod.PHOTO;
    }

    @Override
    public boolean hasRequiredData(Map<String, String> verificationInput) {
        return verificationInput != null &&
               verificationInput.containsKey("photoUrl") &&
               !verificationInput.get("photoUrl").trim().isEmpty();
    }

    @Override
    public VerificationResult verify(Kill kill, Map<String, String> verificationInput, String verifierId) {
        logger.info("Performing Photo verification (submission check) for kill: Killer={}, Victim={}, Time={}",
                    kill.getKillerID(), kill.getVictimID(), kill.getTime());

        if (!hasRequiredData(verificationInput)) {
            logger.warn("Photo verification submission failed: Required photo URL data missing in input. Kill={}_{}",
                        kill.getKillerID(), kill.getTime());
            return VerificationResult.invalidInput("Required photo verification data (photoUrl) is missing or empty.");
        }

        String photoUrl = verificationInput.get("photoUrl");
        String notes = String.format("Photo submitted for review by %s. URL: %s", verifierId, photoUrl);

        // Photo verification always results in PENDING_REVIEW status initially.
        // The actual verification (approve/reject) is done by a moderator action.
        logger.info("Photo verification submission successful for Kill={}_{}. Status set to PENDING_REVIEW. Notes={}",
                    kill.getKillerID(), kill.getTime(), notes);

        return VerificationResult.pendingReview(notes);
    }

    // Note: requiresModeratorReview() and isAutomatic() are handled by the default methods
    // in IVerificationMethod based on getMethodType() returning PHOTO.
} 