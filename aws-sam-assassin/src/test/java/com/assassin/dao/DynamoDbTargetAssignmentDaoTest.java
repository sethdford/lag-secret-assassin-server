package com.assassin.dao;

import com.assassin.model.TargetAssignment;
import com.assassin.exception.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

public class DynamoDbTargetAssignmentDaoTest {

    @Mock
    private DynamoDbEnhancedClient mockEnhancedClient;

    @Mock
    private DynamoDbTable<TargetAssignment> mockTable;

    @Mock
    private DynamoDbIndex<TargetAssignment> mockGameAssignerIndex;

    @Mock
    private DynamoDbIndex<TargetAssignment> mockGameTargetIndex;
    
    @Mock
    private DynamoDbIndex<TargetAssignment> mockGameStatusIndex;

    private DynamoDbTargetAssignmentDao dao;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockEnhancedClient.table(anyString(), any(TableSchema.class))).thenReturn(mockTable);
        when(mockTable.index("GameAssignerIndex")).thenReturn(mockGameAssignerIndex);
        when(mockTable.index("GameTargetIndex")).thenReturn(mockGameTargetIndex);
        when(mockTable.index("GameStatusIndex")).thenReturn(mockGameStatusIndex);
        dao = new DynamoDbTargetAssignmentDao(mockTable);
    }

    @Test
    public void testSaveAssignment() {
        TargetAssignment assignment = new TargetAssignment();
        assignment.setAssignmentId("test-assignment-id");
        
        dao.saveAssignment(assignment);
        
        verify(mockTable).putItem(assignment);
    }

    @Test
    public void testGetAssignmentById_whenFound() {
        TargetAssignment assignment = new TargetAssignment();
        assignment.setAssignmentId("test-id");
        when(mockTable.getItem(any(Key.class))).thenReturn(assignment);

        Optional<TargetAssignment> result = dao.getAssignmentById("test-id");

        assertTrue(result.isPresent());
        assertEquals("test-id", result.get().getAssignmentId());
    }

    @Test
    public void testGetAssignmentById_whenNotFound() {
        when(mockTable.getItem(any(Key.class))).thenReturn(null);

        Optional<TargetAssignment> result = dao.getAssignmentById("test-id");

        assertFalse(result.isPresent());
    }

    @Test
    public void testGetCurrentAssignmentForPlayer_whenFound() {
        TargetAssignment assignment = new TargetAssignment();
        assignment.setAssignerId("player-1");
        assignment.setGameId("game-1");
        
        Page<TargetAssignment> page = Page.create(Collections.singletonList(assignment));
        SdkIterable<Page<TargetAssignment>> sdkIterable = () -> Stream.of(page).iterator();
        when(mockGameAssignerIndex.query(any(QueryEnhancedRequest.class))).thenReturn(sdkIterable);

        Optional<TargetAssignment> result = dao.getCurrentAssignmentForPlayer("game-1", "player-1");

        assertTrue(result.isPresent());
        assertEquals("player-1", result.get().getAssignerId());
    }
    
    @Test
    public void testSaveAssignment_throwsPersistenceException() {
        TargetAssignment assignment = new TargetAssignment();
        doThrow(DynamoDbException.class).when(mockTable).putItem(assignment);

        assertThrows(PersistenceException.class, () -> {
            dao.saveAssignment(assignment);
        });
    }
}
