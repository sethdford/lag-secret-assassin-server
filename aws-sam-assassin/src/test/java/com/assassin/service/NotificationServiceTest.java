package com.assassin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.NotificationDao;
import com.assassin.model.Notification;

import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceTest.class);
    private static final String TEST_RECIPIENT_ID = "player-456";
    private static final String TEST_NOTIFICATION_ID = "notif-xyz";

    @Mock
    private NotificationDao mockNotificationDao;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        // Basic setup, NotificationService uses the injected mock DAO
        logger.info("Setting up NotificationServiceTest");
    }

    @Test
    void markNotificationAsRead_Success() {
        logger.info("Testing markNotificationAsRead_Success in Service");
        // Arrange
        Notification updatedNotification = new Notification();
        updatedNotification.setRecipientPlayerId(TEST_RECIPIENT_ID);
        updatedNotification.setNotificationId(TEST_NOTIFICATION_ID);
        updatedNotification.setStatus("READ");
        
        // Mock the DAO call to return the successfully updated notification
        when(mockNotificationDao.markNotificationAsRead(eq(TEST_RECIPIENT_ID), eq(TEST_NOTIFICATION_ID)))
            .thenReturn(Optional.of(updatedNotification));

        // Act
        boolean result = notificationService.markNotificationAsRead(TEST_RECIPIENT_ID, TEST_NOTIFICATION_ID);

        // Assert
        assertTrue(result, "Service should return true on successful DAO update");
        verify(mockNotificationDao).markNotificationAsRead(TEST_RECIPIENT_ID, TEST_NOTIFICATION_ID);
        logger.info("Test markNotificationAsRead_Success finished");
    }

    @Test
    void markNotificationAsRead_DaoReturnsEmpty() {
        logger.info("Testing markNotificationAsRead_DaoReturnsEmpty in Service");
        // Arrange
        // Mock the DAO call to return empty, indicating the notification wasn't found or updated
        when(mockNotificationDao.markNotificationAsRead(eq(TEST_RECIPIENT_ID), eq(TEST_NOTIFICATION_ID)))
            .thenReturn(Optional.empty());

        // Act
        boolean result = notificationService.markNotificationAsRead(TEST_RECIPIENT_ID, TEST_NOTIFICATION_ID);

        // Assert
        assertFalse(result, "Service should return false if DAO returns empty");
        verify(mockNotificationDao).markNotificationAsRead(TEST_RECIPIENT_ID, TEST_NOTIFICATION_ID);
        logger.info("Test markNotificationAsRead_DaoReturnsEmpty finished");
    }

    @Test
    void markNotificationAsRead_DaoThrowsIllegalArgumentException() {
        logger.info("Testing markNotificationAsRead_DaoThrowsIllegalArgumentException in Service");
        // Arrange
        // Mock the DAO call to throw an exception (e.g., due to invalid ID format in DAO)
        when(mockNotificationDao.markNotificationAsRead(anyString(), anyString()))
            .thenThrow(new IllegalArgumentException("Invalid ID format in DAO"));

        // Act
        boolean result = notificationService.markNotificationAsRead(TEST_RECIPIENT_ID, TEST_NOTIFICATION_ID);

        // Assert
        assertFalse(result, "Service should return false when DAO throws IllegalArgumentException");
        verify(mockNotificationDao).markNotificationAsRead(TEST_RECIPIENT_ID, TEST_NOTIFICATION_ID);
        logger.info("Test markNotificationAsRead_DaoThrowsIllegalArgumentException finished");
    }
    
    @Test
    void markNotificationAsRead_DaoThrowsRuntimeException() {
        logger.info("Testing markNotificationAsRead_DaoThrowsRuntimeException in Service");
        // Arrange
        // Mock the DAO call to throw a general RuntimeException (e.g., from DynamoDB interaction)
        when(mockNotificationDao.markNotificationAsRead(anyString(), anyString()))
            .thenThrow(new RuntimeException("DAO Database Error"));

        // Act
        boolean result = notificationService.markNotificationAsRead(TEST_RECIPIENT_ID, TEST_NOTIFICATION_ID);

        // Assert
        assertFalse(result, "Service should return false when DAO throws RuntimeException");
        verify(mockNotificationDao).markNotificationAsRead(TEST_RECIPIENT_ID, TEST_NOTIFICATION_ID);
        logger.info("Test markNotificationAsRead_DaoThrowsRuntimeException finished");
    }

    @Test
    void markNotificationAsRead_NullRecipientId() {
        logger.info("Testing markNotificationAsRead_NullRecipientId in Service");
        // Act
        // Call service directly with invalid input
        // The service method itself should handle this, but the DAO mock ensures no DB call
        boolean result = notificationService.markNotificationAsRead(null, TEST_NOTIFICATION_ID);

        // Assert
        assertFalse(result, "Service should return false for null recipient ID");
        verify(mockNotificationDao, never()).markNotificationAsRead(any(), anyString()); // Verify DAO not called
        logger.info("Test markNotificationAsRead_NullRecipientId finished");
    }

    @Test
    void markNotificationAsRead_NullNotificationId() {
        logger.info("Testing markNotificationAsRead_NullNotificationId in Service");
        // Act
        boolean result = notificationService.markNotificationAsRead(TEST_RECIPIENT_ID, null);

        // Assert
        assertFalse(result, "Service should return false for null notification ID");
        verify(mockNotificationDao, never()).markNotificationAsRead(anyString(), any()); // Verify DAO not called
        logger.info("Test markNotificationAsRead_NullNotificationId finished");
    }

    // TODO: Add tests for other service methods (sendNotification, getNotification, getNotificationsForPlayer)
} 