package com.assassin.dao;

import java.util.List;
import java.util.Optional;

import com.assassin.model.Notification;

/**
 * Data Access Object interface for Notification entities.
 */
public interface NotificationDao {

    /**
     * Saves a notification record.
     *
     * @param notification The Notification object to save.
     */
    void saveNotification(Notification notification);

    /**
     * Retrieves a specific notification by its composite key.
     *
     * @param recipientPlayerId The recipient's player ID (partition key).
     * @param timestamp The notification timestamp (sort key).
     * @return An Optional containing the Notification if found, otherwise empty.
     */
    Optional<Notification> getNotification(String recipientPlayerId, String timestamp);

    /**
     * Finds all notifications for a specific player, optionally after a given timestamp.
     *
     * @param recipientPlayerId The ID of the player whose notifications to retrieve.
     * @param sinceTimestamp Optional ISO 8601 timestamp to filter notifications after this time.
     * @param limit The maximum number of notifications to return.
     * @return A list of Notification objects, ordered by timestamp (typically descending).
     */
    List<Notification> findNotificationsByPlayer(String recipientPlayerId, String sinceTimestamp, int limit);

    // Potentially add methods for updating status (e.g., mark as read)
    // void updateNotificationStatus(String recipientPlayerId, String timestamp, String newStatus);
} 