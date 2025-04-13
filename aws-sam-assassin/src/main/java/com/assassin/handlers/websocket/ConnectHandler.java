package com.assassin.handlers.websocket;

import java.util.Map;
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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class ConnectHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private static final Logger logger = LoggerFactory.getLogger(ConnectHandler.class);
    private static final String TABLE_NAME = System.getenv("CONNECTIONS_TABLE_NAME");
    private final DynamoDbTable<WebSocketConnection> connectionsTable;

    public ConnectHandler() {
        DynamoDbClient ddbClient = DynamoDbClientProvider.getClient();
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(ddbClient)
            .build();
        this.connectionsTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(WebSocketConnection.class));
    }

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {
        String connectionId = event.getRequestContext().getConnectionId();
        logger.info("WebSocket connect request received. ConnectionId: {}", connectionId);

        // Extract playerId from query parameters (Simple approach)
        String playerId = null;
        Map<String, String> queryParams = event.getQueryStringParameters();
        if (queryParams != null && queryParams.containsKey("playerId")) {
            playerId = queryParams.get("playerId");
            logger.info("Attempting to associate connection {} with PlayerId: {}", connectionId, playerId);
        } else {
            logger.warn("No playerId found in query parameters for connection {}. Connection will not be associated.", connectionId);
            // Optionally, reject the connection if playerId is required
            // APIGatewayV2WebSocketResponse errorResponse = new APIGatewayV2WebSocketResponse();
            // errorResponse.setStatusCode(400);
            // errorResponse.setBody("playerId query parameter is required");
            // return errorResponse;
        }

        WebSocketConnection connection = new WebSocketConnection();
        connection.setConnectionId(connectionId);
        if (playerId != null && !playerId.isEmpty()) {
            connection.setPlayerId(playerId);
        }

        try {
            connectionsTable.putItem(connection);
            if (playerId != null) {
                logger.info("Successfully saved connection {} associated with player {}", connectionId, playerId);
            } else {
                logger.info("Successfully saved unassociated connection {}", connectionId);
            }
        } catch (Exception e) {
            logger.error("Failed to save connection {}: {}", connectionId, e.getMessage(), e);
            // Return 500 to indicate failure
            APIGatewayV2WebSocketResponse errorResponse = new APIGatewayV2WebSocketResponse();
            errorResponse.setStatusCode(500);
            errorResponse.setBody("Failed to connect");
            return errorResponse;
        }

        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(200);
        response.setBody("Connected."); // Body is optional for connect/disconnect
        return response;
    }
}