package com.assassin.dao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.exception.PersistenceException;
import com.assassin.model.TargetAssignment;
import com.assassin.util.DynamoDbClientProvider;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

/**
 * DynamoDB implementation of TargetAssignmentDao.
 * Handles all database operations for target assignments using AWS SDK Enhanced DynamoDB.
 */
public class DynamoDbTargetAssignmentDao implements TargetAssignmentDao {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbTargetAssignmentDao.class);
    
    private final DynamoDbTable<TargetAssignment> assignmentTable;
    private final DynamoDbIndex<TargetAssignment> gameAssignerIndex;
    private final DynamoDbIndex<TargetAssignment> gameTargetIndex;
    private final DynamoDbIndex<TargetAssignment> gameStatusIndex;

    /**
     * Default constructor that initializes DynamoDB client and table.
     */
    public DynamoDbTargetAssignmentDao() {
        DynamoDbEnhancedClient enhancedClient = DynamoDbClientProvider.getEnhancedClient();
        this.assignmentTable = enhancedClient.table("TargetAssignments", TableSchema.fromBean(TargetAssignment.class));
        this.gameAssignerIndex = assignmentTable.index("GameAssignerIndex");
        this.gameTargetIndex = assignmentTable.index("GameTargetIndex");
        this.gameStatusIndex = assignmentTable.index("GameStatusIndex");
    }

    /**
     * Constructor for testing with custom table.
     */
    public DynamoDbTargetAssignmentDao(DynamoDbTable<TargetAssignment> assignmentTable) {
        this.assignmentTable = assignmentTable;
        this.gameAssignerIndex = assignmentTable.index("GameAssignerIndex");
        this.gameTargetIndex = assignmentTable.index("GameTargetIndex");
        this.gameStatusIndex = assignmentTable.index("GameStatusIndex");
    }

    @Override
    public void saveAssignment(TargetAssignment assignment) throws PersistenceException {
        try {
            logger.debug("Saving target assignment: {}", assignment.getAssignmentId());
            assignmentTable.putItem(assignment);
            logger.debug("Successfully saved target assignment: {}", assignment.getAssignmentId());
        } catch (DynamoDbException e) {
            logger.error("Failed to save target assignment {}: {}", assignment.getAssignmentId(), e.getMessage(), e);
            throw new PersistenceException("Failed to save target assignment: " + assignment.getAssignmentId(), e);
        }
    }

    @Override
    public Optional<TargetAssignment> getAssignmentById(String assignmentId) throws PersistenceException {
        try {
            logger.debug("Retrieving target assignment by ID: {}", assignmentId);
            Key key = Key.builder().partitionValue(assignmentId).build();
            TargetAssignment assignment = assignmentTable.getItem(key);
            
            if (assignment != null) {
                logger.debug("Found target assignment: {}", assignmentId);
                return Optional.of(assignment);
            } else {
                logger.debug("Target assignment not found: {}", assignmentId);
                return Optional.empty();
            }
        } catch (DynamoDbException e) {
            logger.error("Failed to retrieve target assignment {}: {}", assignmentId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve target assignment: " + assignmentId, e);
        }
    }

    @Override
    public Optional<TargetAssignment> getCurrentAssignmentForPlayer(String gameId, String assignerId) throws PersistenceException {
        try {
            logger.debug("Retrieving current assignment for player {} in game {}", assignerId, gameId);
            
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder()
                    .partitionValue(gameId)
                    .sortValue(assignerId)
                    .build()
            );

            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .filterExpression(software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                            .expression("#status = :activeStatus")
                            .putExpressionName("#status", "Status")
                            .putExpressionValue(":activeStatus", AttributeValue.builder().s(TargetAssignment.AssignmentStatus.ACTIVE.name()).build())
                            .build())
                    .build();

            List<TargetAssignment> assignments = gameAssignerIndex.query(queryRequest)
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .collect(Collectors.toList());

            if (assignments.isEmpty()) {
                logger.debug("No active assignment found for player {} in game {}", assignerId, gameId);
                return Optional.empty();
            } else if (assignments.size() == 1) {
                logger.debug("Found active assignment for player {} in game {}", assignerId, gameId);
                return Optional.of(assignments.get(0));
            } else {
                logger.warn("Multiple active assignments found for player {} in game {} - returning most recent", assignerId, gameId);
                // Return the most recent assignment
                return assignments.stream()
                        .max((a1, a2) -> a1.getAssignmentDate().compareTo(a2.getAssignmentDate()));
            }
        } catch (DynamoDbException e) {
            logger.error("Failed to retrieve current assignment for player {} in game {}: {}", assignerId, gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve current assignment for player: " + assignerId, e);
        }
    }

    @Override
    public List<TargetAssignment> getAssignmentsForGame(String gameId) throws PersistenceException {
        try {
            logger.debug("Retrieving all assignments for game: {}", gameId);
            
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(gameId).build()
            );

            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .build();

            List<TargetAssignment> assignments = gameStatusIndex.query(queryRequest)
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .collect(Collectors.toList());

            logger.debug("Found {} assignments for game {}", assignments.size(), gameId);
            return assignments;
        } catch (DynamoDbException e) {
            logger.error("Failed to retrieve assignments for game {}: {}", gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve assignments for game: " + gameId, e);
        }
    }

    @Override
    public List<TargetAssignment> getActiveAssignmentsForGame(String gameId) throws PersistenceException {
        try {
            logger.debug("Retrieving active assignments for game: {}", gameId);
            
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder()
                    .partitionValue(gameId)
                    .sortValue(TargetAssignment.AssignmentStatus.ACTIVE.name())
                    .build()
            );

            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .build();

            List<TargetAssignment> assignments = gameStatusIndex.query(queryRequest)
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .collect(Collectors.toList());

            logger.debug("Found {} active assignments for game {}", assignments.size(), gameId);
            return assignments;
        } catch (DynamoDbException e) {
            logger.error("Failed to retrieve active assignments for game {}: {}", gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve active assignments for game: " + gameId, e);
        }
    }

    @Override
    public List<TargetAssignment> getAssignmentsTargetingPlayer(String gameId, String targetId) throws PersistenceException {
        try {
            logger.debug("Retrieving assignments targeting player {} in game {}", targetId, gameId);
            
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder()
                    .partitionValue(gameId)
                    .sortValue(targetId)
                    .build()
            );

            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .build();

            List<TargetAssignment> assignments = gameTargetIndex.query(queryRequest)
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .collect(Collectors.toList());

            logger.debug("Found {} assignments targeting player {} in game {}", assignments.size(), targetId, gameId);
            return assignments;
        } catch (DynamoDbException e) {
            logger.error("Failed to retrieve assignments targeting player {} in game {}: {}", targetId, gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve assignments targeting player: " + targetId, e);
        }
    }

    @Override
    public List<TargetAssignment> getAssignmentHistoryForPlayer(String gameId, String assignerId) throws PersistenceException {
        try {
            logger.debug("Retrieving assignment history for player {} in game {}", assignerId, gameId);
            
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder()
                    .partitionValue(gameId)
                    .sortValue(assignerId)
                    .build()
            );

            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .build();

            List<TargetAssignment> assignments = gameAssignerIndex.query(queryRequest)
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .sorted((a1, a2) -> a2.getAssignmentDate().compareTo(a1.getAssignmentDate())) // Most recent first
                    .collect(Collectors.toList());

            logger.debug("Found {} assignments in history for player {} in game {}", assignments.size(), assignerId, gameId);
            return assignments;
        } catch (DynamoDbException e) {
            logger.error("Failed to retrieve assignment history for player {} in game {}: {}", assignerId, gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve assignment history for player: " + assignerId, e);
        }
    }

    @Override
    public void updateAssignmentStatus(String assignmentId, String newStatus) throws PersistenceException {
        try {
            logger.debug("Updating assignment {} status to {}", assignmentId, newStatus);
            
            TargetAssignment assignment = getAssignmentById(assignmentId)
                    .orElseThrow(() -> new PersistenceException("Assignment not found: " + assignmentId));
            
            assignment.setStatus(newStatus);
            if (!TargetAssignment.AssignmentStatus.ACTIVE.name().equalsIgnoreCase(newStatus)) {
                assignment.setCompletedDate(Instant.now().toString());
            }
            
            assignmentTable.putItem(assignment);
            logger.debug("Successfully updated assignment {} status to {}", assignmentId, newStatus);
        } catch (DynamoDbException e) {
            logger.error("Failed to update assignment {} status: {}", assignmentId, e.getMessage(), e);
            throw new PersistenceException("Failed to update assignment status: " + assignmentId, e);
        }
    }

    @Override
    public void completeAssignment(String assignmentId, String completionDate) throws PersistenceException {
        try {
            logger.debug("Completing assignment {} at {}", assignmentId, completionDate);
            
            TargetAssignment assignment = getAssignmentById(assignmentId)
                    .orElseThrow(() -> new PersistenceException("Assignment not found: " + assignmentId));
            
            assignment.setStatus(TargetAssignment.AssignmentStatus.COMPLETED.name());
            assignment.setCompletedDate(completionDate);
            
            assignmentTable.putItem(assignment);
            logger.debug("Successfully completed assignment {}", assignmentId);
        } catch (DynamoDbException e) {
            logger.error("Failed to complete assignment {}: {}", assignmentId, e.getMessage(), e);
            throw new PersistenceException("Failed to complete assignment: " + assignmentId, e);
        }
    }

    @Override
    public void cancelAllAssignmentsForGame(String gameId) throws PersistenceException {
        try {
            logger.info("Cancelling all active assignments for game: {}", gameId);
            
            List<TargetAssignment> activeAssignments = getActiveAssignmentsForGame(gameId);
            String cancellationTime = Instant.now().toString();
            
            for (TargetAssignment assignment : activeAssignments) {
                assignment.setStatus(TargetAssignment.AssignmentStatus.CANCELLED.name());
                assignment.setCompletedDate(cancellationTime);
                assignmentTable.putItem(assignment);
            }
            
            logger.info("Successfully cancelled {} assignments for game {}", activeAssignments.size(), gameId);
        } catch (DynamoDbException e) {
            logger.error("Failed to cancel assignments for game {}: {}", gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to cancel assignments for game: " + gameId, e);
        }
    }

    @Override
    public void deleteAssignment(String assignmentId) throws PersistenceException {
        try {
            logger.warn("Deleting assignment {} - this removes audit trail", assignmentId);
            Key key = Key.builder().partitionValue(assignmentId).build();
            assignmentTable.deleteItem(key);
            logger.debug("Successfully deleted assignment {}", assignmentId);
        } catch (DynamoDbException e) {
            logger.error("Failed to delete assignment {}: {}", assignmentId, e.getMessage(), e);
            throw new PersistenceException("Failed to delete assignment: " + assignmentId, e);
        }
    }

    @Override
    public long countAssignmentsByStatus(String gameId, String status) throws PersistenceException {
        try {
            logger.debug("Counting assignments for game {} with status {}", gameId, status);
            
            List<TargetAssignment> assignments;
            if (status == null) {
                assignments = getAssignmentsForGame(gameId);
            } else {
                QueryConditional queryConditional = QueryConditional.keyEqualTo(
                    Key.builder()
                        .partitionValue(gameId)
                        .sortValue(status)
                        .build()
                );

                QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .build();

                assignments = gameStatusIndex.query(queryRequest)
                        .stream()
                        .flatMap(page -> page.items().stream())
                        .collect(Collectors.toList());
            }
            
            long count = assignments.size();
            logger.debug("Found {} assignments for game {} with status {}", count, gameId, status);
            return count;
        } catch (DynamoDbException e) {
            logger.error("Failed to count assignments for game {} with status {}: {}", gameId, status, e.getMessage(), e);
            throw new PersistenceException("Failed to count assignments for game: " + gameId, e);
        }
    }

    @Override
    public List<String> validateAssignmentIntegrity(String gameId) throws PersistenceException {
        List<String> issues = new ArrayList<>();
        
        try {
            logger.debug("Validating assignment integrity for game: {}", gameId);
            
            List<TargetAssignment> allAssignments = getAssignmentsForGame(gameId);
            List<TargetAssignment> activeAssignments = allAssignments.stream()
                    .filter(TargetAssignment::isActive)
                    .collect(Collectors.toList());
            
            // Check for duplicate active assignments for the same player
            activeAssignments.stream()
                    .collect(Collectors.groupingBy(TargetAssignment::getAssignerId))
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().size() > 1)
                    .forEach(entry -> issues.add("Player " + entry.getKey() + " has multiple active assignments"));
            
            // Check for circular assignments (A targets B, B targets A)
            for (TargetAssignment assignment : activeAssignments) {
                String assignerId = assignment.getAssignerId();
                String targetId = assignment.getTargetId();
                
                boolean hasCircular = activeAssignments.stream()
                        .anyMatch(other -> other.getAssignerId().equals(targetId) && 
                                          other.getTargetId().equals(assignerId));
                
                if (hasCircular) {
                    issues.add("Circular assignment detected between " + assignerId + " and " + targetId);
                }
            }
            
            // Check for self-assignments
            activeAssignments.stream()
                    .filter(assignment -> assignment.getAssignerId().equals(assignment.getTargetId()))
                    .forEach(assignment -> issues.add("Self-assignment detected for player " + assignment.getAssignerId()));
            
            logger.debug("Assignment integrity validation completed for game {}: {} issues found", gameId, issues.size());
            return issues;
        } catch (RuntimeException e) {
            logger.error("Failed to validate assignment integrity for game {}: {}", gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to validate assignment integrity for game: " + gameId, e);
        }
    }
} 