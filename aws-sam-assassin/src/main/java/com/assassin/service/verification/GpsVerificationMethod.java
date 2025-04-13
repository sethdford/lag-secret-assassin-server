package com.assassin.service.verification;

import com.assassin.model.Kill;
import com.assassin.model.VerificationMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Implements kill verification based on GPS proximity.
 */
public class GpsVerificationMethod implements IVerificationMethod {

    private static final Logger logger = LoggerFactory.getLogger(GpsVerificationMethod.class);
    private static final double DEFAULT_PROXIMITY_THRESHOLD_METERS = 50.0;
    private static final double EARTH_RADIUS_METERS = 6371000; // Approx Earth radius in meters

    @Override
    public VerificationMethod getMethodType() {
        return VerificationMethod.GPS;
    }

    @Override
    public boolean hasRequiredData(Map<String, String> verificationInput) {
        return verificationInput != null &&
               verificationInput.containsKey("victimLatitude") &&
               verificationInput.containsKey("victimLongitude");
    }

    @Override
    public VerificationResult verify(Kill kill, Map<String, String> verificationInput, String verifierId) {
        logger.info("Performing GPS verification for kill: Killer={}, Victim={}, Time={}",
                    kill.getKillerID(), kill.getVictimID(), kill.getTime());

        Double killLat = kill.getLatitude();
        Double killLon = kill.getLongitude();

        if (killLat == null || killLon == null) {
            logger.warn("GPS verification failed: Kill location is missing. Kill={}_{}", kill.getKillerID(), kill.getTime());
            return VerificationResult.invalidInput("Kill location data is missing.");
        }

        if (!hasRequiredData(verificationInput)) {
            logger.warn("GPS verification failed: Required victim location data missing in input. Kill={}_{}", kill.getKillerID(), kill.getTime());
            return VerificationResult.invalidInput("Required GPS verification data (victimLatitude, victimLongitude) is missing.");
        }

        String victimLatStr = verificationInput.get("victimLatitude");
        String victimLonStr = verificationInput.get("victimLongitude");

        Double victimLat, victimLon;
        try {
            victimLat = Double.parseDouble(victimLatStr);
            victimLon = Double.parseDouble(victimLonStr);
        } catch (NumberFormatException e) {
            logger.warn("GPS verification failed: Invalid victim location format. Lat='{}', Lon='{}'. Kill={}_{}",
                        victimLatStr, victimLonStr, kill.getKillerID(), kill.getTime(), e);
            return VerificationResult.invalidInput("Invalid format for victim GPS coordinates.");
        }

        // TODO: Fetch threshold from game settings instead of using default
        double thresholdMeters = DEFAULT_PROXIMITY_THRESHOLD_METERS;

        double distance = calculateHaversineDistance(killLat, killLon, victimLat, victimLon);
        logger.debug("Calculated GPS distance: {} meters for Kill={}_{}", String.format("%.2f", distance), kill.getKillerID(), kill.getTime());

        boolean withinProximity = distance <= thresholdMeters;

        String notes = String.format("GPS Verification: Distance=%.2fm, Threshold=%.1fm. Verified by %s.",
                                     distance, thresholdMeters, verifierId);
        logger.info("GPS Verification Result for Kill={}_{}: WithinProximity={}, Notes={}",
                    kill.getKillerID(), kill.getTime(), withinProximity, notes);

        if (withinProximity) {
            return VerificationResult.verified(notes);
        } else {
            return VerificationResult.rejected(notes + " Distance exceeds threshold.");
        }
    }

    /**
     * Calculates the great-circle distance between two points
     * on the earth (specified in decimal degrees) using the Haversine formula.
     *
     * @param lat1 Latitude of point 1
     * @param lon1 Longitude of point 1
     * @param lat2 Latitude of point 2
     * @param lon2 Longitude of point 2
     * @return The distance between the two points in meters.
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.pow(Math.sin(dLat / 2), 2) +
                   Math.pow(Math.sin(dLon / 2), 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }
} 