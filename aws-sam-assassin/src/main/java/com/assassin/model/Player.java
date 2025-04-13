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

    // Constants for GSI
    private static final String EMAIL_INDEX = "EmailIndex";

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
               '}';
    }
} 