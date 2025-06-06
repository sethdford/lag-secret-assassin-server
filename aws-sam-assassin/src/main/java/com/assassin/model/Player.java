package com.assassin.model;

import java.util.Objects;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

/**
 * Represents an item in the Players DynamoDB table.
 */
@DynamoDbBean
public class Player {

    private String playerID; // Partition Key
    private String email;    // GSI Partition Key (EmailIndex)
    private String playerName;
    private String targetID;
    private String targetName;
    private String targetSecret; // Secret needed to kill *this* player
    private String secret;       // Secret needed for *this* player to kill their target
    private String lastWill;
    private String gameID;
    private Integer killCount = 0; // Initialize to 0, Use Integer for DynamoDB
    private String leaderboardStatusPartition; // GSI PK (e.g., "STATUS#ACTIVE", "GLOBAL")
    private String status;   // e.g., PENDING, ACTIVE, DEAD, WINNER
    private Long version; // Version attribute for optimistic locking
    private String passwordHash; // Hashed password for authentication
    private Boolean active; // Whether the account is active
    private String nfcTagId; // Optional NFC Tag ID associated with the player for verification

    // Location related fields
    private Double lastKnownLatitude;
    private Double lastKnownLongitude;
    private String locationTimestamp; // ISO 8601 format
    private Double locationAccuracy;

    // Shrinking Zone related fields
    private String firstEnteredOutOfZoneTimestamp; // ISO 8601 format
    private String lastZoneDamageTimestamp; // ISO 8601 format

    // Constants for GSI
    private static final String EMAIL_INDEX = "EmailIndex";
    private static final String GAME_ID_INDEX = "GameIdIndex";
    private static final String TARGET_ID_INDEX = "TargetIdIndex";
    private static final String KILL_COUNT_INDEX = "KillCountIndex";

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PlayerID") // Explicit attribute name matching schema
    public String getPlayerID() {
        return playerID;
    }

    public void setPlayerID(String playerID) {
        this.playerID = playerID;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {EMAIL_INDEX})
    @DynamoDbAttribute("Email")
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @DynamoDbAttribute(value = "PlayerName") // Explicit mapping if different from field name
    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {TARGET_ID_INDEX})
    @DynamoDbAttribute("TargetID")
    public String getTargetID() {
        return targetID;
    }

    public void setTargetID(String targetID) {
        this.targetID = targetID;
    }

    @DynamoDbAttribute("TargetName")
    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    @DynamoDbAttribute("TargetSecret")
    public String getTargetSecret() {
        return targetSecret;
    }

    public void setTargetSecret(String targetSecret) {
        this.targetSecret = targetSecret;
    }

    @DynamoDbAttribute("Secret")
    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    @DynamoDbAttribute("LastWill")
    public String getLastWill() {
        return lastWill;
    }

    public void setLastWill(String lastWill) {
        this.lastWill = lastWill;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {GAME_ID_INDEX})
    @DynamoDbAttribute("GameID")
    public String getGameID() {
        return gameID;
    }

    public void setGameID(String gameID) {
        this.gameID = gameID;
    }

    @DynamoDbAttribute("Status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        updateLeaderboardPartition();
    }

    @DynamoDbSecondarySortKey(indexNames = "KillCountIndex")
    @DynamoDbAttribute("KillCount")
    public Integer getKillCount() {
        return (killCount == null) ? 0 : killCount;
    }

    public void setKillCount(Integer killCount) {
        this.killCount = (killCount == null) ? 0 : killCount;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "KillCountIndex")
    @DynamoDbAttribute("LeaderboardStatusPartition")
    public String getLeaderboardStatusPartition() {
        if (this.leaderboardStatusPartition == null && this.status != null) {
            updateLeaderboardPartition();
        }
        return leaderboardStatusPartition;
    }

    public void setLeaderboardStatusPartition(String leaderboardStatusPartition) {
        this.leaderboardStatusPartition = leaderboardStatusPartition;
    }

    private void updateLeaderboardPartition() {
        if ("ACTIVE".equalsIgnoreCase(this.status)) {
            this.leaderboardStatusPartition = "STATUS#ACTIVE";
        } else {
            this.leaderboardStatusPartition = "STATUS#INACTIVE";
        }
    }

    public void incrementKillCount() {
        this.killCount = getKillCount() + 1;
    }

    // Temporarily disable version attribute until dependency issue resolved
    // @DynamoDbVersionAttribute
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
    
    @DynamoDbAttribute("PasswordHash")
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    
    @DynamoDbAttribute("Active")
    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @DynamoDbAttribute("NfcTagId")
    public String getNfcTagId() {
        return nfcTagId;
    }

    public void setNfcTagId(String nfcTagId) {
        this.nfcTagId = nfcTagId;
    }

    @DynamoDbAttribute("LastKnownLatitude")
    public Double getLatitude() {
        return lastKnownLatitude;
    }

    public void setLatitude(Double latitude) {
        this.lastKnownLatitude = latitude;
    }

    @DynamoDbAttribute("LastKnownLongitude")
    public Double getLongitude() {
        return lastKnownLongitude;
    }

    public void setLongitude(Double longitude) {
        this.lastKnownLongitude = longitude;
    }

    @DynamoDbAttribute("LocationTimestamp")
    public String getLocationTimestamp() {
        return locationTimestamp;
    }

    public void setLocationTimestamp(String locationTimestamp) {
        this.locationTimestamp = locationTimestamp;
    }

    @DynamoDbAttribute("LocationAccuracy")
    public Double getLocationAccuracy() {
        return locationAccuracy;
    }

    public void setLocationAccuracy(Double locationAccuracy) {
        this.locationAccuracy = locationAccuracy;
    }

    @DynamoDbAttribute("FirstEnteredOutOfZoneTimestamp")
    public String getFirstEnteredOutOfZoneTimestamp() {
        return firstEnteredOutOfZoneTimestamp;
    }

    public void setFirstEnteredOutOfZoneTimestamp(String firstEnteredOutOfZoneTimestamp) {
        this.firstEnteredOutOfZoneTimestamp = firstEnteredOutOfZoneTimestamp;
    }

    @DynamoDbAttribute("LastZoneDamageTimestamp")
    public String getLastZoneDamageTimestamp() {
        return lastZoneDamageTimestamp;
    }

    public void setLastZoneDamageTimestamp(String lastZoneDamageTimestamp) {
        this.lastZoneDamageTimestamp = lastZoneDamageTimestamp;
    }

    // Subscription related fields
    private String currentSubscriptionTierId; // Current subscription tier (basic, hunter, assassin, elite)
    private String subscriptionValidUntil;    // ISO 8601 format - when subscription expires
    private String stripeSubscriptionId;      // Stripe subscription ID for billing management

    @DynamoDbAttribute("CurrentSubscriptionTierId")
    public String getCurrentSubscriptionTierId() {
        return currentSubscriptionTierId != null ? currentSubscriptionTierId : SubscriptionTier.BASIC.getTierId();
    }

    public void setCurrentSubscriptionTierId(String currentSubscriptionTierId) {
        this.currentSubscriptionTierId = currentSubscriptionTierId;
    }

    @DynamoDbAttribute("SubscriptionValidUntil")
    public String getSubscriptionValidUntil() {
        return subscriptionValidUntil;
    }

    public void setSubscriptionValidUntil(String subscriptionValidUntil) {
        this.subscriptionValidUntil = subscriptionValidUntil;
    }

    @DynamoDbAttribute("StripeSubscriptionId")
    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
    }

    /**
     * Get the player's current subscription tier as an enum.
     * Defaults to BASIC if no tier is set or if the tier is invalid.
     */
    public SubscriptionTier getSubscriptionTier() {
        return SubscriptionTier.fromTierId(currentSubscriptionTierId);
    }

    /**
     * Set the player's subscription tier using the enum.
     */
    public void setSubscriptionTier(SubscriptionTier tier) {
        this.currentSubscriptionTierId = tier != null ? tier.getTierId() : SubscriptionTier.BASIC.getTierId();
    }

    /**
     * Check if the player's subscription is currently active (not expired).
     */
    public boolean hasActiveSubscription() {
        if (subscriptionValidUntil == null) {
            // If no expiration is set, treat Basic tier as always active
            return SubscriptionTier.fromTierId(currentSubscriptionTierId) == SubscriptionTier.BASIC;
        }
        
        try {
            java.time.Instant expirationTime = java.time.Instant.parse(subscriptionValidUntil);
            return java.time.Instant.now().isBefore(expirationTime);
        } catch (Exception e) {
            // If we can't parse the expiration time, default to expired
            return false;
        }
    }

    /**
     * Check if the player has access to a specific subscription benefit.
     */
    public boolean hasSubscriptionBenefit(String benefit) {
        if (!hasActiveSubscription()) {
            return SubscriptionTier.BASIC.hasBenefit(benefit);
        }
        return getSubscriptionTier().hasBenefit(benefit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(playerID, player.playerID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerID);
    }

    @Override
    public String toString() {
        return "Player{" +
               "playerID='" + playerID + '\'' +
               ", email='" + email + '\'' +
               ", playerName='" + playerName + '\'' +
               ", targetID='" + targetID + '\'' +
               ", status='" + status + '\'' +
               ", locationTimestamp='" + locationTimestamp + '\'' +
               ", subscriptionTier='" + getSubscriptionTier().getTierId() + '\'' +
               ", subscriptionActive=" + hasActiveSubscription() +
               '}';
    }
} 