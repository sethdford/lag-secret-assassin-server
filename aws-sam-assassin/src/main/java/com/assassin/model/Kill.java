package com.assassin.model;

import java.util.Map;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Represents an item in the Kills DynamoDB table.
 */
@DynamoDbBean
public class Kill {

    private String killerID;  // Partition Key
    private String time;      // Sort Key & Secondary Sort Key (VictimID-Time-index)
    private String victimID;  // Secondary Partition Key (VictimID-Time-index)
    private Double latitude;
    private Double longitude;
    private String lastWill;  // Victim's "last will" message when they confirm death
    private boolean deathConfirmed = false; // Whether the victim has confirmed their death

    // New fields for enhanced verification
    private String verificationMethod; // e.g., "GPS", "NFC", "PHOTO", "NONE"
    private String verificationStatus = "PENDING"; // e.g., "PENDING", "VERIFIED", "REJECTED"
    private Map<String, String> verificationData; // Stores method-specific data (e.g., photo URL, NFC tag ID)
    private String verificationNotes; // Moderator notes regarding verification status
    private String gameId; // Added to associate kill with a game
    private String killStatusPartition; // Partition key for StatusTimeIndex GSI

    // New fields for content moderation
    private String moderationStatus; // e.g., APPROVED, REJECTED_CONTENT, PENDING_MANUAL_REVIEW
    private Map<String, String> moderationDetails; // e.g., AI confidence, flagged labels from Rekognition

    @DynamoDbPartitionKey
    @DynamoDbAttribute("KillerID")
    public String getKillerID() {
        return killerID;
    }

    public void setKillerID(String killerID) {
        this.killerID = killerID;
    }

    @DynamoDbSortKey
    @DynamoDbSecondarySortKey(indexNames = {"VictimID-Time-index", "GameID-Time-index", "StatusTimeIndex"})
    @DynamoDbAttribute("Time") // Stored as ISO 8601 String
    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "VictimID-Time-index")
    @DynamoDbAttribute("VictimID")
    public String getVictimID() {
        return victimID;
    }

    public void setVictimID(String victimID) {
        this.victimID = victimID;
    }

    @DynamoDbAttribute("Latitude")
    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    @DynamoDbAttribute("Longitude")
    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    @DynamoDbAttribute("LastWill")
    public String getLastWill() {
        return lastWill;
    }

    public void setLastWill(String lastWill) {
        this.lastWill = lastWill;
    }

    @DynamoDbAttribute("DeathConfirmed")
    public boolean isDeathConfirmed() {
        return deathConfirmed;
    }

    public void setDeathConfirmed(boolean deathConfirmed) {
        this.deathConfirmed = deathConfirmed;
    }

    @DynamoDbAttribute("VerificationMethod")
    public String getVerificationMethod() {
        return verificationMethod;
    }

    public void setVerificationMethod(String verificationMethod) {
        this.verificationMethod = verificationMethod;
    }

    @DynamoDbAttribute("VerificationStatus")
    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    @DynamoDbAttribute("VerificationData")
    public Map<String, String> getVerificationData() {
        return verificationData;
    }

    public void setVerificationData(Map<String, String> verificationData) {
        this.verificationData = verificationData;
    }

    @DynamoDbAttribute("VerificationNotes")
    public String getVerificationNotes() {
        return verificationNotes;
    }

    public void setVerificationNotes(String verificationNotes) {
        this.verificationNotes = verificationNotes;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "GameID-Time-index")
    @DynamoDbAttribute("GameID")
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "StatusTimeIndex")
    @DynamoDbAttribute("KillStatusPartition")
    public String getKillStatusPartition() {
        return killStatusPartition;
    }

    public void setKillStatusPartition(String killStatusPartition) {
        this.killStatusPartition = killStatusPartition;
    }

    @DynamoDbAttribute("ModerationStatus")
    public String getModerationStatus() {
        return moderationStatus;
    }

    public void setModerationStatus(String moderationStatus) {
        this.moderationStatus = moderationStatus;
    }

    @DynamoDbAttribute("ModerationDetails")
    public Map<String, String> getModerationDetails() {
        return moderationDetails;
    }

    public void setModerationDetails(Map<String, String> moderationDetails) {
        this.moderationDetails = moderationDetails;
    }

     // Consider adding toString(), equals(), and hashCode() methods
} 