package com.assassin.handlers.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.assassin.model.WebSocketConnection;
import com.assassin.util.DynamoDbClientProvider;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.core.SdkBytes;

import java.net.URI;

public class DefaultMessageHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultMessageHandler.class);
    private static final String TABLE_NAME = System.getenv("CONNECTIONS_TABLE_NAME");
    // private final DynamoDbTable<WebSocketConnection> connectionsTable; // Not used yet
    private final ApiGatewayManagementApiClient apiGatewayManagementApiClient;

    public DefaultMessageHandler() {
        // DynamoDbClient ddbClient = DynamoDbClientProvider.getClient();
        // DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
        //     .dynamoDbClient(ddbClient)
        //     .build();
        // this.connectionsTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(WebSocketConnection.class));

        // Note: API Gateway Management API client needs the correct endpoint
        // It's constructed dynamically in the handleRequest method
        this.apiGatewayManagementApiClient = null; 
    }

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {
        String connectionId = event.getRequestContext().getConnectionId();
        String messageBody = event.getBody();
        String domainName = event.getRequestContext().getDomainName();
        String stage = event.getRequestContext().getStage();
        
        logger.info("Default message received. ConnectionId: {}, Domain: {}, Stage: {}, Body: {}", 
            connectionId, domainName, stage, messageBody);

        // Example: Echo the message back to the sender
        // Construct the endpoint URL for the API Gateway Management API
        String endpointUrl = String.format("https://%s/%s", domainName, stage);
        
        ApiGatewayManagementApiClient managementApiClient = ApiGatewayManagementApiClient.builder()
            .endpointOverride(URI.create(endpointUrl))
            // Use default region or configure explicitly if needed
            // .region(Region.of(System.getenv("AWS_REGION"))) 
            .build();

        try {
            PostToConnectionRequest postRequest = PostToConnectionRequest.builder()
                .connectionId(connectionId)
                .data(SdkBytes.fromUtf8String("You sent: " + messageBody))
                .build();
            managementApiClient.postToConnection(postRequest);
            logger.info("Echoed message back to connection: {}", connectionId);
        } catch (RuntimeException e) {
            logger.error("Failed to post message to connection {}: {}", connectionId, e.getMessage(), e);
            // Handle specific exceptions like GoneException if the connection no longer exists
        }

        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(200);
        // No body needed for default message handling unless sending specific confirmation
        return response;
    }
} 