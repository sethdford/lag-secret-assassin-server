package com.assassin.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.model.Notification;
import com.assassin.util.DynamoDbClientProvider;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

@ExtendWith(MockitoExtension.class)
class DynamoDbNotificationDaoTest {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbNotificationDaoTest.class);
    private static final String TEST_TABLE_NAME = "TestNotifications";
    private static final String TEST_RECIPIENT_ID = "player-123";
    private static final String TEST_NOTIFICATION_ID = "notif-abc";

    @Mock
    private DynamoDbClient mockDynamoDbClient; // Standard client mock

    @Mock
    private DynamoDbEnhancedClient mockEnhancedClient; // Enhanced client mock

    @Mock
    private DynamoDbTable<Notification> mockNotificationTable; // Table mock

    // We need to inject the DAO itself, but its constructor creates clients.
    // We'll handle this manually or potentially refactor DAO constructor later.
    private DynamoDbNotificationDao notificationDao;

    @BeforeEach
    void setUp() {
        // Mock the static client provider to return our mocked client
        // Use try-with-resources for MockedStatic
        try (MockedStatic<DynamoDbClientProvider> mockedProvider = mockStatic(DynamoDbClientProvider.class);
             MockedStatic<DynamoDbEnhancedClient> mockedEnhancedBuilder = mockStatic(DynamoDbEnhancedClient.class)) {
            
            mockedProvider.when(DynamoDbClientProvider::getClient).thenReturn(mockDynamoDbClient);
            
            // Mock the enhanced client builder chain
            DynamoDbEnhancedClient.Builder mockBuilder = mock(DynamoDbEnhancedClient.Builder.class);
            mockedEnhancedBuilder.when(DynamoDbEnhancedClient::builder).thenReturn(mockBuilder);
            when(mockBuilder.dynamoDbClient(any(DynamoDbClient.class))).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockEnhancedClient);

            // Mock the table creation part
            when(mockEnhancedClient.table(anyString(), any(TableSchema.class))).thenReturn(mockNotificationTable);
            
            // Set the environment variable for the table name *before* creating the DAO instance
            // Note: This is tricky in tests. Consider dependency injection for table name.
            // For now, we rely on the mocked enhancedClient returning the table mock regardless of name.
             System.setProperty("NOTIFICATIONS_TABLE_NAME", TEST_TABLE_NAME);

            // Now instantiate the DAO - it will use the mocked clients and table
            notificationDao = new DynamoDbNotificationDao();

             System.clearProperty("NOTIFICATIONS_TABLE_NAME"); // Clean up immediately

        } catch (Exception e) {
            // Catch potential reflection or other errors during static mocking setup
             logger.error("Error during static mock setup: {}", e.getMessage(), e);
             fail("Static mock setup failed");
        }
         logger.info("Setup complete. Mocked Notification Table: {}", mockNotificationTable);
    }

    @Test
    void markNotificationAsRead_Success() {
        logger.info("Testing markNotificationAsRead_Success");
        // Arrange
        Notification existingNotification = new Notification();
        existingNotification.setRecipientPlayerId(TEST_RECIPIENT_ID);
        existingNotification.setNotificationId(TEST_NOTIFICATION_ID);
        existingNotification.setStatus("UNREAD");

        Notification updatedNotification = new Notification();
        updatedNotification.setRecipientPlayerId(TEST_RECIPIENT_ID);
        updatedNotification.setNotificationId(TEST_NOTIFICATION_ID);
        updatedNotification.setStatus("READ");

        // Mock the updateItem call to return the updated notification
        // We need to capture the Consumer lambda to check its behavior if needed
        // For simplicity, we just mock the return value based on any Consumer.
        when(mockNotificationTable.updateItem(any(Consumer.class))).thenReturn(updatedNotification);
        
        // Act
        Optional<Notification> result = notificationDao.markNotificationAsRead(TEST_RECIPIENT_ID, TEST_NOTIFICATION_ID);

        // Assert
        assertTrue(result.isPresent(), "Result should be present");
        assertEquals("READ", result.get().getStatus(), "Notification status should be updated to READ");
        assertEquals(TEST_NOTIFICATION_ID, result.get().getNotificationId());
        
        // Verify updateItem was called on the mock table
        verify(mockNotificationTable).updateItem(any(Consumer.class));
        logger.info("Test markNotificationAsRead_Success finished");
    }

    @Test
    void markNotificationAsRead_NotFound() {
         logger.info("Testing markNotificationAsRead_NotFound");
        // Arrange
        // Mock updateItem to return null, simulating the item not being found or update failing without error
        when(mockNotificationTable.updateItem(any(Consumer.class))).thenReturn(null);

        // Act
        Optional<Notification> result = notificationDao.markNotificationAsRead(TEST_RECIPIENT_ID, "nonexistent-id");

        // Assert
        assertFalse(result.isPresent(), "Result should be empty when notification is not found");
        verify(mockNotificationTable).updateItem(any(Consumer.class));
         logger.info("Test markNotificationAsRead_NotFound finished");
    }

    @Test
    void markNotificationAsRead_DynamoDbException() {
         logger.info("Testing markNotificationAsRead_DynamoDbException");
        // Arrange
        // Mock updateItem to throw a DynamoDbException
        when(mockNotificationTable.updateItem(any(Consumer.class))).thenThrow(DynamoDbException.builder().message("Test DB Error").build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notificationDao.markNotificationAsRead(TEST_RECIPIENT_ID, TEST_NOTIFICATION_ID);
        }, "Should throw RuntimeException on DynamoDbException");
        
        assertTrue(exception.getMessage().contains("Failed to mark notification as read"), "Exception message should indicate failure");
        verify(mockNotificationTable).updateItem(any(Consumer.class));
         logger.info("Test markNotificationAsRead_DynamoDbException finished");
    }

    @Test
    void markNotificationAsRead_NullRecipientId() {
         logger.info("Testing markNotificationAsRead_NullRecipientId");
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            notificationDao.markNotificationAsRead(null, TEST_NOTIFICATION_ID);
        }, "Should throw IllegalArgumentException for null recipient ID");
        
        assertEquals("recipientPlayerId cannot be null or empty", exception.getMessage());
        verify(mockNotificationTable, never()).updateItem(any(Consumer.class)); // Ensure DB not called
         logger.info("Test markNotificationAsRead_NullRecipientId finished");
    }
    
    @Test
    void markNotificationAsRead_EmptyRecipientId() {
         logger.info("Testing markNotificationAsRead_EmptyRecipientId");
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            notificationDao.markNotificationAsRead("", TEST_NOTIFICATION_ID);
        }, "Should throw IllegalArgumentException for empty recipient ID");
        
        assertEquals("recipientPlayerId cannot be null or empty", exception.getMessage());
        verify(mockNotificationTable, never()).updateItem(any(Consumer.class));
         logger.info("Test markNotificationAsRead_EmptyRecipientId finished");
    }

    @Test
    void markNotificationAsRead_NullNotificationId() {
         logger.info("Testing markNotificationAsRead_NullNotificationId");
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            notificationDao.markNotificationAsRead(TEST_RECIPIENT_ID, null);
        }, "Should throw IllegalArgumentException for null notification ID");
        
        assertEquals("notificationId cannot be null or empty", exception.getMessage());
        verify(mockNotificationTable, never()).updateItem(any(Consumer.class));
         logger.info("Test markNotificationAsRead_NullNotificationId finished");
    }
    
    @Test
    void markNotificationAsRead_EmptyNotificationId() {
         logger.info("Testing markNotificationAsRead_EmptyNotificationId");
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            notificationDao.markNotificationAsRead(TEST_RECIPIENT_ID, "");
        }, "Should throw IllegalArgumentException for empty notification ID");
        
        assertEquals("notificationId cannot be null or empty", exception.getMessage());
        verify(mockNotificationTable, never()).updateItem(any(Consumer.class));
         logger.info("Test markNotificationAsRead_EmptyNotificationId finished");
    }

    // TODO: Add tests for other DAO methods (saveNotification, getNotification, findNotificationsByPlayer)
} 