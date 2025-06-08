package com.assassin.model;

import java.time.Instant;
import java.util.Objects;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Model representing a target assignment in the assassin game.
 * Tracks who is assigned to eliminate whom, when the assignment was made,
 * and the current status of the assignment.
 * 
 * This provides better tracking and audit trail compared to storing
 * target information directly in the Player model.
 */
@DynamoDbBean
public class TargetAssignment {

    // Primary key: assignmentId (unique identifier for each assignment)
    private String assignmentId;
    
    // Core assignment data
    private String gameId;           // Which game this assignment belongs to
    private String assignerId;       // Player who has the assignment (the assassin)
    private String targetId;         // Player who is the target (to be eliminated)
    private String assignmentDate;   // When this assignment was created (ISO 8601)
    private String status;           // ACTIVE, COMPLETED, CANCELLED, REASSIGNED
    
    // Optional metadata
    private String assignmentType;   // INITIAL, REASSIGNMENT, SPECIAL_ITEM
    private String previousAssignmentId; // If this is a reassignment, link to previous
    private String completedDate;    // When assignment was completed (ISO 8601)
    private String notes;            // Optional notes about the assignment
    
    // GSI constants
    private static final String GAME_ASSIGNER_INDEX = "GameAssignerIndex";
    private static final String GAME_TARGET_INDEX = "GameTargetIndex";
    private static final String GAME_STATUS_INDEX = "GameStatusIndex";

    /**
     * Assignment status enumeration.
     */
    public enum AssignmentStatus {
        ACTIVE,      // Assignment is currently active
        COMPLETED,   // Target was successfully eliminated
        CANCELLED,   // Assignment was cancelled (e.g., game ended)
        REASSIGNED   // Assignment was changed to a different target
    }

    /**
     * Assignment type enumeration.
     */
    public enum AssignmentType {
        INITIAL,        // Initial assignment when game starts
        REASSIGNMENT,   // Reassignment after elimination
        SPECIAL_ITEM    // Assignment changed via special item
    }

    // --- Primary Key ---

    @DynamoDbPartitionKey
    @DynamoDbAttribute("AssignmentId")
    public String getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }

    // --- Core Assignment Data ---

    @DynamoDbSecondaryPartitionKey(indexNames = {GAME_ASSIGNER_INDEX, GAME_TARGET_INDEX, GAME_STATUS_INDEX})
    @DynamoDbAttribute("GameId")
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    @DynamoDbSecondarySortKey(indexNames = {GAME_ASSIGNER_INDEX})
    @DynamoDbAttribute("AssignerId")
    public String getAssignerId() {
        return assignerId;
    }

    public void setAssignerId(String assignerId) {
        this.assignerId = assignerId;
    }

    @DynamoDbSecondarySortKey(indexNames = {GAME_TARGET_INDEX})
    @DynamoDbAttribute("TargetId")
    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    @DynamoDbAttribute("AssignmentDate")
    public String getAssignmentDate() {
        return assignmentDate;
    }

    public void setAssignmentDate(String assignmentDate) {
        this.assignmentDate = assignmentDate;
    }

    @DynamoDbSecondarySortKey(indexNames = {GAME_STATUS_INDEX})
    @DynamoDbAttribute("Status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // --- Optional Metadata ---

    @DynamoDbAttribute("AssignmentType")
    public String getAssignmentType() {
        return assignmentType;
    }

    public void setAssignmentType(String assignmentType) {
        this.assignmentType = assignmentType;
    }

    @DynamoDbAttribute("PreviousAssignmentId")
    public String getPreviousAssignmentId() {
        return previousAssignmentId;
    }

    public void setPreviousAssignmentId(String previousAssignmentId) {
        this.previousAssignmentId = previousAssignmentId;
    }

    @DynamoDbAttribute("CompletedDate")
    public String getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(String completedDate) {
        this.completedDate = completedDate;
    }

    @DynamoDbAttribute("Notes")
    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    // --- Convenience Methods ---

    /**
     * Checks if this assignment is currently active.
     */
    public boolean isActive() {
        return AssignmentStatus.ACTIVE.name().equalsIgnoreCase(status);
    }

    /**
     * Checks if this assignment has been completed.
     */
    public boolean isCompleted() {
        return AssignmentStatus.COMPLETED.name().equalsIgnoreCase(status);
    }

    /**
     * Marks this assignment as completed.
     */
    public void markCompleted() {
        this.status = AssignmentStatus.COMPLETED.name();
        this.completedDate = Instant.now().toString();
    }

    /**
     * Marks this assignment as reassigned.
     */
    public void markReassigned() {
        this.status = AssignmentStatus.REASSIGNED.name();
        this.completedDate = Instant.now().toString();
    }

    /**
     * Marks this assignment as cancelled.
     */
    public void markCancelled() {
        this.status = AssignmentStatus.CANCELLED.name();
        this.completedDate = Instant.now().toString();
    }

    /**
     * Creates a new assignment ID based on game, assigner, and timestamp.
     */
    public static String generateAssignmentId(String gameId, String assignerId) {
        return String.format("%s#%s#%d", gameId, assignerId, System.currentTimeMillis());
    }

    /**
     * Creates a new initial assignment.
     */
    public static TargetAssignment createInitialAssignment(String gameId, String assignerId, String targetId) {
        TargetAssignment assignment = new TargetAssignment();
        assignment.setAssignmentId(generateAssignmentId(gameId, assignerId));
        assignment.setGameId(gameId);
        assignment.setAssignerId(assignerId);
        assignment.setTargetId(targetId);
        assignment.setAssignmentDate(Instant.now().toString());
        assignment.setStatus(AssignmentStatus.ACTIVE.name());
        assignment.setAssignmentType(AssignmentType.INITIAL.name());
        return assignment;
    }

    /**
     * Creates a reassignment from a previous assignment.
     */
    public static TargetAssignment createReassignment(String gameId, String assignerId, String newTargetId, String previousAssignmentId) {
        TargetAssignment assignment = new TargetAssignment();
        assignment.setAssignmentId(generateAssignmentId(gameId, assignerId));
        assignment.setGameId(gameId);
        assignment.setAssignerId(assignerId);
        assignment.setTargetId(newTargetId);
        assignment.setAssignmentDate(Instant.now().toString());
        assignment.setStatus(AssignmentStatus.ACTIVE.name());
        assignment.setAssignmentType(AssignmentType.REASSIGNMENT.name());
        assignment.setPreviousAssignmentId(previousAssignmentId);
        return assignment;
    }

    // --- Standard Object Methods ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TargetAssignment that = (TargetAssignment) o;
        return Objects.equals(assignmentId, that.assignmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assignmentId);
    }

    @Override
    public String toString() {
        return "TargetAssignment{" +
                "assignmentId='" + assignmentId + '\'' +
                ", gameId='" + gameId + '\'' +
                ", assignerId='" + assignerId + '\'' +
                ", targetId='" + targetId + '\'' +
                ", status='" + status + '\'' +
                ", assignmentType='" + assignmentType + '\'' +
                ", assignmentDate='" + assignmentDate + '\'' +
                '}';
    }
} 