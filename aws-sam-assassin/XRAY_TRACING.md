# AWS X-Ray Distributed Tracing Implementation

This document describes the comprehensive AWS X-Ray distributed tracing implementation for the Assassin Game API.

## Overview

The X-Ray tracing implementation provides end-to-end observability across the entire application stack, including:

- Lambda handlers
- Service layer business logic
- DAO layer database operations
- External service calls (Stripe, AWS services)
- Custom performance monitoring and error tracking

## Architecture

### Core Components

1. **XRayConfig** - Central configuration and initialization
2. **XRayTraceUtils** - Utility methods for creating traced operations
3. **ExternalServiceTracing** - Specialized tracing for external services
4. **TracedLambdaHandler** - Base class for Lambda handlers
5. **TracedBaseService** - Base class for service layer
6. **TracedBaseDao** - Base class for DAO layer

### Dependencies Added

```xml
<!-- AWS X-Ray SDK Core -->
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-xray-recorder-sdk-core</artifactId>
    <version>2.15.1</version>
</dependency>

<!-- AWS X-Ray SQL Interceptor -->
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-xray-recorder-sdk-sql</artifactId>
    <version>2.15.1</version>
</dependency>

<!-- AWS X-Ray AWS SDK Interceptor -->
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-xray-recorder-sdk-aws-sdk-v2</artifactId>
    <version>2.15.1</version>
</dependency>

<!-- AWS X-Ray Apache HTTP Client Interceptor -->
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-xray-recorder-sdk-apache-http</artifactId>
    <version>2.15.1</version>
</dependency>
```

## Implementation Details

### 1. Lambda Handler Tracing

All Lambda handlers should extend `TracedLambdaHandler`:

```java
public class GameHandler extends TracedLambdaHandler {
    @Override
    protected APIGatewayProxyResponseEvent handleRequestWithTracing(
            APIGatewayProxyRequestEvent request, Context context) {
        // Handler implementation
    }
    
    @Override
    protected Map<String, String> getCustomAnnotations(APIGatewayProxyRequestEvent request) {
        return Map.of("game.handler", "true", "api.version", "v1");
    }
}
```

**Features:**
- Automatic request/response tracing
- Error handling and correlation
- Performance metrics
- Request context extraction
- Custom annotations and metadata

### 2. Service Layer Tracing

Service classes should extend `TracedBaseService`:

```java
public class GameService extends TracedBaseService {
    public void startGameAndAssignTargets(String gameId) {
        traceVoidBusinessOperation("startGameAndAssignTargets", "Game", gameId, () -> {
            // Business logic implementation
        });
    }
}
```

**Available Tracing Methods:**
- `traceBusinessOperation()` - General business operations
- `traceValidation()` - Validation operations
- `traceTransformation()` - Data transformation
- `traceCalculation()` - Calculations
- `traceAggregation()` - Data aggregation
- `traceNotification()` - Notification operations

### 3. DAO Layer Tracing

DAO classes should extend `TracedBaseDao`:

```java
public class DynamoDbGameDao extends TracedBaseDao implements GameDao {
    @Override
    protected String getTableName() {
        return System.getenv("GAMES_TABLE_NAME");
    }
    
    @Override
    public void saveGame(Game game) {
        tracePutOperation("gameId=" + game.getGameID(), () -> {
            gameTable.putItem(game);
            return null;
        });
    }
}
```

**Available Tracing Methods:**
- `traceGetOperation()` - DynamoDB GetItem
- `tracePutOperation()` - DynamoDB PutItem
- `traceUpdateOperation()` - DynamoDB UpdateItem
- `traceDeleteOperation()` - DynamoDB DeleteItem
- `traceQueryOperation()` - DynamoDB Query
- `traceScanOperation()` - DynamoDB Scan
- `traceBatchGetOperation()` - DynamoDB BatchGetItem
- `traceBatchWriteOperation()` - DynamoDB BatchWriteItem
- `traceTransactionOperation()` - DynamoDB TransactWrite

### 4. External Service Tracing

#### Stripe Operations

```java
PaymentIntent paymentIntent = ExternalServiceTracing.traceStripeOperation(
    "create_payment_intent", 
    gameId + "_" + playerId,
    () -> PaymentIntent.create(params)
);
```

#### AWS Service Operations

```java
DetectToxicContentResponse response = ExternalServiceTracing.traceAwsServiceOperation(
    "Comprehend", 
    "detectToxicContent", 
    "text_length=" + content.length(),
    () -> comprehendClient.detectToxicContent(request)
);
```

#### Generic HTTP API Calls

```java
ResponseType result = ExternalServiceTracing.traceHttpApiCall(
    "ExternalService",
    "operation_name", 
    "https://api.example.com",
    "POST",
    () -> httpClient.post(request)
);
```

#### WebSocket Operations

```java
ResultType result = ExternalServiceTracing.traceWebSocketOperation(
    "send_message",
    connectionId,
    () -> apiGatewayClient.postToConnection(request)
);
```

## Sampling Configuration

Custom sampling rules are configured in `src/main/resources/sampling-rules.json`:

```json
{
  "version": 2,
  "default": {
    "fixed_target": 1,
    "rate": 0.1
  },
  "rules": [
    {
      "description": "High priority operations - always trace",
      "service_name": "assassin-game-api",
      "fixed_target": 2,
      "rate": 1.0,
      "attributes": {
        "business.function": "kill_verification"
      }
    },
    {
      "description": "Payment operations - always trace for financial compliance",
      "service_name": "assassin-game-api",
      "http_method": "*",
      "url_path": "/payments*",
      "fixed_target": 2,
      "rate": 1.0
    }
  ]
}
```

### Sampling Strategy

- **High Priority (100% sampling):**
  - Kill verification operations
  - Payment operations
  - Admin operations
  - Error cases

- **Medium Priority (30-50% sampling):**
  - Authentication operations
  - Game management operations
  - Player management operations

- **Low Priority (5% sampling):**
  - Location updates (high volume)

- **Minimal Priority (1% sampling):**
  - Health checks

## Annotations and Metadata

### Standard Annotations
Automatically added to all segments:
- `service` - Service name
- `operation` - Operation being performed
- `resource` - Resource identifier
- `version` - Service version
- `stage` - Deployment stage
- `region` - AWS region

### Custom Annotations
Operation-specific annotations:
- `business.function` - Business function name
- `business.entity_type` - Entity type being processed
- `aws.operation` - AWS operation name
- `aws.table_name` - DynamoDB table name
- `http.method` - HTTP method
- `http.path` - Request path
- `error` - Error flag

### Metadata Categories

1. **Request Context:**
   - User ID
   - Request ID
   - HTTP method and path
   - Query parameters
   - Headers (excluding sensitive)

2. **Performance Metrics:**
   - Operation duration
   - Success/error counts
   - Resource utilization

3. **Business Context:**
   - Entity IDs
   - Operation parameters
   - Validation results

4. **Error Details:**
   - Error type and message
   - Error code
   - Stack trace information

## Environment Variables

Required environment variables for X-Ray:

- `_X_AMZN_TRACE_ID` - Automatically set by Lambda runtime
- `AWS_REGION` - AWS region for service calls
- `STAGE` - Deployment stage (dev, staging, prod)

## Performance Monitoring

### Key Metrics Tracked

1. **Response Times:**
   - Handler execution time
   - Database operation duration
   - External service call latency

2. **Error Rates:**
   - Total error count
   - Error rate by operation
   - Error distribution by type

3. **Throughput:**
   - Requests per second
   - Operations per second
   - Success rate

4. **Resource Utilization:**
   - Lambda memory usage
   - Database capacity consumption
   - External service quota usage

### Custom Metrics

```java
// Add performance metric
XRayTraceUtils.addPerformanceMetric("operation_duration", duration, "milliseconds");

// Add business metric
XRayTraceUtils.addPerformanceMetric("games_created", 1, "count");
```

## Error Tracking

### Automatic Error Tracking
- Exceptions are automatically captured and added to traces
- Error details include type, message, and stack trace
- Custom error metadata is added for external service errors

### Custom Error Details
```java
XRayTraceUtils.addErrorDetails("ValidationError", "Invalid input", "400");
```

## Deployment Considerations

### Lambda Configuration
Ensure Lambda functions have the necessary IAM permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "xray:PutTraceSegments",
        "xray:PutTelemetryRecords"
      ],
      "Resource": "*"
    }
  ]
}
```

### Environment Setup
1. Enable X-Ray tracing on Lambda functions
2. Configure X-Ray daemon (automatically available in Lambda)
3. Set up appropriate sampling rules
4. Configure alerts and dashboards in X-Ray console

## Monitoring and Alerting

### Key Dashboards
1. **Service Map** - Visual representation of service dependencies
2. **Trace Analysis** - Detailed trace analysis and filtering
3. **Response Time Trends** - Performance over time
4. **Error Analysis** - Error patterns and root cause analysis

### Recommended Alerts
1. High error rate (>5%)
2. High response time (>2 seconds)
3. Service dependency failures
4. Unusual traffic patterns

## Best Practices

1. **Use Meaningful Segment Names:** Use descriptive names that clearly identify the operation
2. **Add Context-Rich Annotations:** Include business-relevant annotations for filtering
3. **Minimize Metadata Size:** Avoid large objects in metadata to prevent performance impact
4. **Handle Failures Gracefully:** Ensure tracing failures don't impact business logic
5. **Use Appropriate Sampling:** Balance observability with performance and cost
6. **Monitor Trace Volume:** Keep an eye on trace volume to manage costs

## Troubleshooting

### Common Issues

1. **Missing Traces:**
   - Check IAM permissions
   - Verify X-Ray is enabled on Lambda
   - Check sampling configuration

2. **Performance Impact:**
   - Review sampling rates
   - Check metadata size
   - Monitor trace volume

3. **Missing Annotations:**
   - Verify annotation key/value format
   - Check for null values
   - Ensure proper segment context

### Debug Mode
Enable debug logging by setting environment variable:
```
LOG_LEVEL=DEBUG
```

## Cost Optimization

1. **Optimize Sampling:** Use lower sampling rates for high-volume, low-priority operations
2. **Filter Unnecessary Traces:** Exclude health checks and monitoring endpoints
3. **Monitor Usage:** Regular review of trace volume and costs
4. **Use Annotations Wisely:** Focus on annotations that provide business value

## Future Enhancements

1. **Custom Service Map Visualization:** Enhanced service dependency mapping
2. **Business Process Tracing:** End-to-end business process visibility
3. **ML-Based Anomaly Detection:** Automated detection of unusual patterns
4. **Integration with Application Metrics:** Correlation with CloudWatch metrics
5. **Custom Trace Analysis Tools:** Specialized analysis for game-specific patterns