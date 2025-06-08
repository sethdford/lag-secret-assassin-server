package com.assassin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityMetricsPublisherTest {

    @Mock
    private CloudWatchClient mockCloudWatchClient;

    private SecurityMetricsPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SecurityMetricsPublisher(mockCloudWatchClient);
    }

    @Test
    void testPublishSecurityEventsCount() {
        // Arrange
        when(mockCloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
            .thenReturn(PutMetricDataResponse.builder().build());

        // Act
        publisher.publishSecurityEventsCount(15);

        // Assert
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(captor.capture());

        PutMetricDataRequest request = captor.getValue();
        assertEquals("AssassinGame/Security", request.namespace());
        assertEquals(1, request.metricData().size());
        
        MetricDatum metric = request.metricData().get(0);
        assertEquals("SecurityEventsCount", metric.metricName());
        assertEquals(15.0, metric.value());
        assertEquals(StandardUnit.COUNT, metric.unit());
    }

    @Test
    void testPublishBlockedEntitiesCount() {
        // Arrange
        when(mockCloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
            .thenReturn(PutMetricDataResponse.builder().build());

        // Act
        publisher.publishBlockedEntitiesCount(3);

        // Assert
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(captor.capture());

        PutMetricDataRequest request = captor.getValue();
        MetricDatum metric = request.metricData().get(0);
        assertEquals("BlockedEntitiesCount", metric.metricName());
        assertEquals(3.0, metric.value());
        assertEquals(StandardUnit.COUNT, metric.unit());
    }

    @Test
    void testPublishMaxThreatScore() {
        // Arrange
        when(mockCloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
            .thenReturn(PutMetricDataResponse.builder().build());

        // Act
        publisher.publishMaxThreatScore(85);

        // Assert
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(captor.capture());

        PutMetricDataRequest request = captor.getValue();
        MetricDatum metric = request.metricData().get(0);
        assertEquals("ThreatScoreMax", metric.metricName());
        assertEquals(85.0, metric.value());
        assertEquals(StandardUnit.NONE, metric.unit());
    }

    @Test
    void testPublishIPThreatScore() {
        // Arrange
        when(mockCloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
            .thenReturn(PutMetricDataResponse.builder().build());

        // Act
        publisher.publishIPThreatScore("192.168.1.100", 75);

        // Assert
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(captor.capture());

        PutMetricDataRequest request = captor.getValue();
        MetricDatum metric = request.metricData().get(0);
        assertEquals("IPThreatScore", metric.metricName());
        assertEquals(75.0, metric.value());
        assertEquals(StandardUnit.NONE, metric.unit());
        
        // Check dimension
        assertEquals(1, metric.dimensions().size());
        Dimension dimension = metric.dimensions().get(0);
        assertEquals("IPAddress", dimension.name());
        assertEquals("192.168.1.100", dimension.value());
    }

    @Test
    void testPublishSecurityEventByType() {
        // Arrange
        when(mockCloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
            .thenReturn(PutMetricDataResponse.builder().build());

        // Act
        publisher.publishSecurityEventByType("EXCESSIVE_REQUESTS", 12);

        // Assert
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(captor.capture());

        PutMetricDataRequest request = captor.getValue();
        MetricDatum metric = request.metricData().get(0);
        assertEquals("SecurityEventsByType", metric.metricName());
        assertEquals(12.0, metric.value());
        
        // Check dimension
        assertEquals(1, metric.dimensions().size());
        Dimension dimension = metric.dimensions().get(0);
        assertEquals("EventType", dimension.name());
        assertEquals("EXCESSIVE_REQUESTS", dimension.value());
    }

    @Test
    void testPublishEndpointAbuseMetric() {
        // Arrange
        when(mockCloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
            .thenReturn(PutMetricDataResponse.builder().build());

        // Act
        publisher.publishEndpointAbuseMetric("/api/login", 8);

        // Assert
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(captor.capture());

        PutMetricDataRequest request = captor.getValue();
        MetricDatum metric = request.metricData().get(0);
        assertEquals("EndpointAbuse", metric.metricName());
        assertEquals(8.0, metric.value());
        
        // Check dimension
        assertEquals(1, metric.dimensions().size());
        Dimension dimension = metric.dimensions().get(0);
        assertEquals("Endpoint", dimension.name());
        assertEquals("/api/login", dimension.value());
    }

    @Test
    void testPublishGeographicAnomaly() {
        // Arrange
        when(mockCloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
            .thenReturn(PutMetricDataResponse.builder().build());

        // Act
        publisher.publishGeographicAnomaly("Unknown", 5);

        // Assert
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(captor.capture());

        PutMetricDataRequest request = captor.getValue();
        MetricDatum metric = request.metricData().get(0);
        assertEquals("GeographicAnomalies", metric.metricName());
        assertEquals(5.0, metric.value());
        
        // Check dimension
        assertEquals(1, metric.dimensions().size());
        Dimension dimension = metric.dimensions().get(0);
        assertEquals("Country", dimension.name());
        assertEquals("Unknown", dimension.value());
    }

    @Test
    void testPublishBatchMetrics() {
        // Arrange
        when(mockCloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
            .thenReturn(PutMetricDataResponse.builder().build());

        List<SecurityMetricsPublisher.SecurityMetric> metrics = List.of(
            new SecurityMetricsPublisher.SecurityMetric("TestMetric1", 10, "COUNT"),
            new SecurityMetricsPublisher.SecurityMetric("TestMetric2", 20, "NONE", Map.of("TestDim", "TestValue")),
            new SecurityMetricsPublisher.SecurityMetric("TestMetric3", 30, "COUNT")
        );

        // Act
        publisher.publishBatchMetrics(metrics);

        // Assert
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient).putMetricData(captor.capture());

        PutMetricDataRequest request = captor.getValue();
        assertEquals("AssassinGame/Security", request.namespace());
        assertEquals(3, request.metricData().size());
        
        // Check first metric
        MetricDatum metric1 = request.metricData().get(0);
        assertEquals("TestMetric1", metric1.metricName());
        assertEquals(10.0, metric1.value());
        assertEquals(StandardUnit.COUNT, metric1.unit());
        assertTrue(metric1.dimensions().isEmpty());
        
        // Check second metric with dimension
        MetricDatum metric2 = request.metricData().get(1);
        assertEquals("TestMetric2", metric2.metricName());
        assertEquals(20.0, metric2.value());
        assertEquals(StandardUnit.NONE, metric2.unit());
        assertEquals(1, metric2.dimensions().size());
        assertEquals("TestDim", metric2.dimensions().get(0).name());
        assertEquals("TestValue", metric2.dimensions().get(0).value());
    }

    @Test
    void testPublishBatchMetrics_LargeBatch() {
        // Arrange - Create 25 metrics to test batching (CloudWatch limit is 20 per request)
        when(mockCloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
            .thenReturn(PutMetricDataResponse.builder().build());

        List<SecurityMetricsPublisher.SecurityMetric> metrics = new java.util.ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            metrics.add(new SecurityMetricsPublisher.SecurityMetric("TestMetric" + i, i, "COUNT"));
        }

        // Act
        publisher.publishBatchMetrics(metrics);

        // Assert - Should make 2 calls (20 + 5)
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(mockCloudWatchClient, times(2)).putMetricData(captor.capture());

        List<PutMetricDataRequest> requests = captor.getAllValues();
        assertEquals(20, requests.get(0).metricData().size()); // First batch
        assertEquals(5, requests.get(1).metricData().size());  // Second batch
    }

    @Test
    void testPublishMetric_CloudWatchException() {
        // Arrange
        when(mockCloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
            .thenThrow(CloudWatchException.builder()
                .message("CloudWatch API error")
                .build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            publisher.publishSecurityEventsCount(10));
        
        assertTrue(exception.getMessage().contains("Failed to publish security metric"));
        verify(mockCloudWatchClient).putMetricData(any(PutMetricDataRequest.class));
    }

    @Test
    void testPublishBatchMetrics_CloudWatchException() {
        // Arrange
        when(mockCloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
            .thenThrow(CloudWatchException.builder()
                .message("Batch CloudWatch API error")
                .build());

        List<SecurityMetricsPublisher.SecurityMetric> metrics = List.of(
            new SecurityMetricsPublisher.SecurityMetric("TestMetric", 10, "COUNT")
        );

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            publisher.publishBatchMetrics(metrics));
        
        assertTrue(exception.getMessage().contains("Failed to publish security metrics"));
        verify(mockCloudWatchClient).putMetricData(any(PutMetricDataRequest.class));
    }

    @Test
    void testSecurityMetric_Constructor() {
        // Test constructor with dimensions
        Map<String, String> dimensions = Map.of("TestDim", "TestValue");
        SecurityMetricsPublisher.SecurityMetric metric = 
            new SecurityMetricsPublisher.SecurityMetric("TestMetric", 42.5, "PERCENT", dimensions);
        
        assertEquals("TestMetric", metric.getName());
        assertEquals(42.5, metric.getValue());
        assertEquals("PERCENT", metric.getUnit());
        assertEquals(dimensions, metric.getDimensions());
        assertNotNull(metric.getTimestamp());
    }

    @Test
    void testSecurityMetric_ConstructorWithoutDimensions() {
        // Test constructor without dimensions
        SecurityMetricsPublisher.SecurityMetric metric = 
            new SecurityMetricsPublisher.SecurityMetric("SimpleMetric", 100, "COUNT");
        
        assertEquals("SimpleMetric", metric.getName());
        assertEquals(100.0, metric.getValue());
        assertEquals("COUNT", metric.getUnit());
        assertTrue(metric.getDimensions().isEmpty());
        assertNotNull(metric.getTimestamp());
    }

    @Test
    void testDefaultConstructor() {
        // This test verifies the default constructor works (though it will fail without environment variables)
        assertDoesNotThrow(() -> {
            // Just testing that the class can be instantiated
            // In real AWS environment, this would work with proper credentials
            SecurityMetricsPublisher defaultPublisher = new SecurityMetricsPublisher();
            assertNotNull(defaultPublisher);
        });
    }
} 