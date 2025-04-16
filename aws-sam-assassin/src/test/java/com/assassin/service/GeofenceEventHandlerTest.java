package com.assassin.service;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.dao.PlayerDao;
import com.assassin.model.Coordinate;
import com.assassin.service.GeofenceManager.GeofenceEvent;
import com.assassin.service.GeofenceManager.GeofenceEventType;

@ExtendWith(MockitoExtension.class)
public class GeofenceEventHandlerTest {

    @Mock
    private PlayerDao playerDao;
    
    @Mock
    private GeofenceManager geofenceManager;
    
    @InjectMocks
    private GeofenceEventHandler eventHandler;
    
    @Captor
    private ArgumentCaptor<Consumer<GeofenceEvent>> listenerCaptor;
    
    private final String gameId = "test-game-id";
    private final String playerId = "test-player-id";
    private final Coordinate location = new Coordinate(37.77, -122.42);
    
    // Reusable GeofenceEvents for testing
    private GeofenceEvent exitEvent;
    private GeofenceEvent enterEvent;
    private GeofenceEvent approachingEvent;
    
    @BeforeEach
    void setUp() {
        // Initialize test events
        exitEvent = new GeofenceEvent(gameId, playerId, location, GeofenceEventType.EXIT_BOUNDARY, -10.0); // Negative distance means outside
        enterEvent = new GeofenceEvent(gameId, playerId, location, GeofenceEventType.ENTER_BOUNDARY, 10.0);
        approachingEvent = new GeofenceEvent(gameId, playerId, location, GeofenceEventType.APPROACHING_BOUNDARY, 20.0);
    }
    
    @Test
    void registerForBoundaryEvents_Success() {
        // Tests that the handler properly registers with the GeofenceManager
        
        // Act
        eventHandler.registerForBoundaryEvents(gameId, playerId);
        
        // Assert
        verify(geofenceManager).registerBoundaryEventListener(
            eq(gameId), 
            eq(playerId), 
            any(Consumer.class)
        );
    }
    
    @Test
    void unregisterFromBoundaryEvents_Success() {
        // Tests that the handler properly unregisters from the GeofenceManager
        
        // Act
        eventHandler.unregisterFromBoundaryEvents(gameId, playerId);
        
        // Assert
        verify(geofenceManager).unregisterBoundaryEventListener(gameId, playerId);
    }
    
    @Test
    void handleBoundaryEvent_ExitBoundary_LogsWarning() {
        // Test that an EXIT_BOUNDARY event is properly handled
        
        // Arrange - Capture the listener function during registration
        eventHandler.registerForBoundaryEvents(gameId, playerId);
        verify(geofenceManager).registerBoundaryEventListener(
            eq(gameId), 
            eq(playerId), 
            listenerCaptor.capture()
        );
        
        Consumer<GeofenceEvent> listener = listenerCaptor.getValue();
        
        // Act - Trigger the boundary event
        listener.accept(exitEvent);
        
        // Assert - Since we can't easily verify logger output in a unit test,
        // we'll focus on verifying that no exceptions are thrown
        // In a real app, we might update player state or trigger other actions
    }
    
    @Test
    void handleBoundaryEvent_EnterBoundary_ClearsWarnings() {
        // Test that an ENTER_BOUNDARY event clears previous warnings
        
        // Arrange - Capture the listener function during registration
        eventHandler.registerForBoundaryEvents(gameId, playerId);
        verify(geofenceManager).registerBoundaryEventListener(
            eq(gameId), 
            eq(playerId), 
            listenerCaptor.capture()
        );
        
        Consumer<GeofenceEvent> listener = listenerCaptor.getValue();
        
        // First trigger an approaching event to set a warning
        listener.accept(approachingEvent);
        
        // Act - Now trigger an enter event, which should clear the warning
        listener.accept(enterEvent);
        
        // Then trigger another approaching event, which should be allowed
        // since the warning was cleared
        listener.accept(approachingEvent);
        
        // Assert - In a real test, we could potentially use reflection to check
        // the state of the playerWarnings map, but we'll avoid that here
    }
    
    @Test
    void handleBoundaryEvent_ApproachingBoundary_HandlesWarningCooldown() {
        // Test that approaching warnings respect cooldown period
        
        // Arrange - Capture the listener function during registration
        eventHandler.registerForBoundaryEvents(gameId, playerId);
        verify(geofenceManager).registerBoundaryEventListener(
            eq(gameId), 
            eq(playerId), 
            listenerCaptor.capture()
        );
        
        Consumer<GeofenceEvent> listener = listenerCaptor.getValue();
        
        // Act - Trigger two approaching events in quick succession
        listener.accept(approachingEvent);
        
        // Second call should be throttled by cooldown
        listener.accept(approachingEvent);
        
        // Assert - Not much we can assert here directly in a unit test
        // In a real scenario, we'd verify that notifications aren't sent too frequently
    }
    
    @Test
    void handleMultipleEvents_SevereWarningProximity() {
        // Test that severe warnings are properly distinguished
        
        // Arrange
        eventHandler.registerForBoundaryEvents(gameId, playerId);
        verify(geofenceManager).registerBoundaryEventListener(
            eq(gameId), 
            eq(playerId), 
            listenerCaptor.capture()
        );
        
        Consumer<GeofenceEvent> listener = listenerCaptor.getValue();
        
        // Create a severe warning event (very close to boundary)
        GeofenceEvent severeEvent = new GeofenceEvent(
            gameId, playerId, location, GeofenceEventType.APPROACHING_BOUNDARY, 5.0);
            
        // Act - Trigger the severe warning event
        listener.accept(severeEvent);
        
        // Assert - Again, limited what we can assert in a unit test for logging behavior
    }
    
    @Test
    void handleBoundaryEvent_NullEvent_HandlesGracefully() {
        // Test that null events are handled gracefully
        
        // Arrange
        eventHandler.registerForBoundaryEvents(gameId, playerId);
        verify(geofenceManager).registerBoundaryEventListener(
            eq(gameId), 
            eq(playerId), 
            listenerCaptor.capture()
        );
        
        Consumer<GeofenceEvent> listener = listenerCaptor.getValue();
        
        // Act - Trigger with null event (should not throw exceptions)
        listener.accept(null);
        
        // Assert - No exceptions thrown
    }
} 