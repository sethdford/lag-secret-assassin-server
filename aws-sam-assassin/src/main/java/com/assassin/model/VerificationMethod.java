package com.assassin.model;

/**
 * Enum defining the methods by which a kill can be verified.
 */
public enum VerificationMethod {
    /**
     * Verified manually by a game admin or moderator.
     */
    MANUAL,

    /**
     * Verified automatically based on GPS proximity.
     */
    GPS,

    /**
     * Verified automatically based on NFC tag scan.
     */
    NFC,

    /**
     * Requires photo evidence submission for manual review.
     */
    PHOTO,

    /**
     * Deprecated/General: Verified automatically (prefer specific GPS/NFC).
     */
    AUTOMATIC, // Keep for backward compatibility or general case?

    /**
      * Deprecated/General: Reported by the killer (prefer specific PHOTO).
      */
    SELF_REPORT, // Keep for backward compatibility or general case?

    /**
     * No verification method specified or applicable.
     */
    NONE,

    /**
     * Used for testing purposes only.
     */
    TEST_MODE;

    /**
     * Parses a string to a VerificationMethod, case-insensitive.
     * Defaults to SELF_REPORT if the string is invalid or null.
     *
     * @param methodString The string representation of the method.
     * @return The corresponding VerificationMethod or SELF_REPORT as default.
     */
    public static VerificationMethod fromString(String methodString) {
        if (methodString == null) {
            return SELF_REPORT; // Or throw an exception if null is invalid
        }
        try {
            return VerificationMethod.valueOf(methodString.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Log this potentially?
            return SELF_REPORT; // Default for unknown strings
        }
    }

    /**
     * Checks if this verification method typically requires moderator review.
     * E.g., SELF_REPORT with photo evidence.
     *
     * @return true if moderator review is usually needed, false otherwise.
     */
    public boolean requiresModeratorReview() {
        // PHOTO requires review. MANUAL implies direct admin action.
        return this == MANUAL || this == PHOTO;
    }

    /**
     * Checks if this is considered an automatic verification method.
     *
     * @return true if the method is AUTOMATIC, false otherwise.
     */
    public boolean isAutomatic() {
        // GPS and NFC are automatic.
        return this == AUTOMATIC || this == GPS || this == NFC;
    }
} 