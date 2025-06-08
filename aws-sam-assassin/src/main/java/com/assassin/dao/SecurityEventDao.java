package com.assassin.dao;

import com.assassin.model.SecurityEvent;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for SecurityEvent operations.
 * Provides methods for tracking security events, rate limiting, and abuse detection.
 */
public interface SecurityEventDao {
    
    /**
     * Save a security event to the database.
     * 
     * @param securityEvent The security event to save
     * @return The saved security event
     */
    SecurityEvent saveSecurityEvent(SecurityEvent securityEvent);
    
    /**
     * Get security events for a specific IP address within a time range.
     * 
     * @param sourceIP The source IP address
     * @param startTime Start time (ISO 8601 format)
     * @param endTime End time (ISO 8601 format)
     * @return List of security events
     */
    List<SecurityEvent> getSecurityEventsByIP(String sourceIP, String startTime, String endTime);
    
    /**
     * Get security events for a specific user within a time range.
     * 
     * @param userID The user ID
     * @param startTime Start time (ISO 8601 format)
     * @param endTime End time (ISO 8601 format)
     * @return List of security events
     */
    List<SecurityEvent> getSecurityEventsByUser(String userID, String startTime, String endTime);
    
    /**
     * Get security events by event type within a time range.
     * 
     * @param eventType The event type
     * @param startTime Start time (ISO 8601 format)
     * @param endTime End time (ISO 8601 format)
     * @return List of security events
     */
    List<SecurityEvent> getSecurityEventsByType(String eventType, String startTime, String endTime);
    
    /**
     * Count security events for a specific IP address within a time range.
     * 
     * @param sourceIP The source IP address
     * @param startTime Start time (ISO 8601 format)
     * @param endTime End time (ISO 8601 format)
     * @return Count of security events
     */
    long countSecurityEventsByIP(String sourceIP, String startTime, String endTime);
    
    /**
     * Count security events for a specific user within a time range.
     * 
     * @param userID The user ID
     * @param startTime Start time (ISO 8601 format)
     * @param endTime End time (ISO 8601 format)
     * @return Count of security events
     */
    long countSecurityEventsByUser(String userID, String startTime, String endTime);
    
    /**
     * Count security events by event type for an IP within a time range.
     * 
     * @param sourceIP The source IP address
     * @param eventType The event type
     * @param startTime Start time (ISO 8601 format)
     * @param endTime End time (ISO 8601 format)
     * @return Count of security events
     */
    long countSecurityEventsByIPAndType(String sourceIP, String eventType, String startTime, String endTime);
    
    /**
     * Get the most recent security event for an IP address.
     * 
     * @param sourceIP The source IP address
     * @return Optional containing the most recent security event
     */
    Optional<SecurityEvent> getLatestSecurityEventByIP(String sourceIP);
    
    /**
     * Delete security events older than the specified timestamp.
     * Used for cleanup operations.
     * 
     * @param cutoffTime Cutoff time (ISO 8601 format)
     * @return Number of events deleted
     */
    int deleteOldSecurityEvents(String cutoffTime);
} 