package com.assassin.handlers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.model.Item;
import com.assassin.model.PlayerInventoryItem;
import com.assassin.service.ItemService;
import com.assassin.service.PlayerInventoryService;

@ExtendWith(MockitoExtension.class)
class ItemHandlerTest {

    @Mock
    private ItemService itemService;

    @Mock
    private PlayerInventoryService playerInventoryService;

    @Mock
    private Context context;

    private ItemHandler itemHandler;

    @BeforeEach
    void setUp() {
        itemHandler = new ItemHandler(itemService, playerInventoryService);
    }

    @Test
    void handleGetItems_ShouldReturnItemsList_WhenSuccessful() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/items");
        request.setHttpMethod("GET");

        Item testItem = new Item();
        testItem.setItemId("test-item-1");
        testItem.setName("Test Radar");
        testItem.setItemType(Item.ItemType.RADAR_SCAN);
        testItem.setPrice(1000L);

        List<Item> items = Arrays.asList(testItem);
        when(itemService.getAllItems()).thenReturn(items);

        // Act
        APIGatewayProxyResponseEvent response = itemHandler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("test-item-1"));
        assertTrue(response.getBody().contains("Test Radar"));
        verify(itemService).getAllItems();
    }

    @Test
    void handlePurchaseItem_ShouldReturnPurchaseInfo_WhenValidRequest() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/players/me/inventory/purchase");
        request.setHttpMethod("POST");
        request.setBody("{\"itemId\":\"test-item-1\",\"quantity\":1,\"gameId\":\"game-1\"}");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer mock-token");
        request.setHeaders(headers);

        // Mock RequestContext and Authorizer claims
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, String> claims = new HashMap<>();
        claims.put("sub", "test-player-1");
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        request.setRequestContext(requestContext);

        Item testItem = new Item();
        testItem.setItemId("test-item-1");
        testItem.setName("Test Radar");
        testItem.setPrice(1000L);
        testItem.setIsActive(true);

        when(itemService.getItemById("test-item-1")).thenReturn(Optional.of(testItem));

        // Act
        APIGatewayProxyResponseEvent response = itemHandler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("paymentRequired"));
        assertTrue(response.getBody().contains("test-item-1"));
        verify(itemService).getItemById("test-item-1");
    }

    @Test
    void handleGetInventory_ShouldReturnInventory_WhenValidRequest() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/players/me/inventory");
        request.setHttpMethod("GET");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer mock-token");
        request.setHeaders(headers);

        // Mock RequestContext and Authorizer claims
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, String> claims = new HashMap<>();
        claims.put("sub", "test-player-1");
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        request.setRequestContext(requestContext);

        PlayerInventoryItem inventoryItem = new PlayerInventoryItem();
        inventoryItem.setPlayerId("test-player-1");
        inventoryItem.setInventoryItemId("inv-item-1");
        inventoryItem.setItemId("test-item-1");
        inventoryItem.setQuantity(2);

        List<PlayerInventoryItem> inventory = Arrays.asList(inventoryItem);
        when(playerInventoryService.getPlayerInventory(anyString())).thenReturn(inventory);

        // Act
        APIGatewayProxyResponseEvent response = itemHandler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("inv-item-1"));
        assertTrue(response.getBody().contains("test-item-1"));
        verify(playerInventoryService).getPlayerInventory(anyString());
    }

    @Test
    void handleUseItem_ShouldReturnSuccess_WhenValidRequest() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/players/me/inventory/inv-item-1/use");
        request.setHttpMethod("POST");
        request.setBody("{\"gameId\":\"game-1\"}");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer mock-token");
        request.setHeaders(headers);
        
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("inventoryItemId", "inv-item-1");
        request.setPathParameters(pathParams);

        // Mock RequestContext and Authorizer claims
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, String> claims = new HashMap<>();
        claims.put("sub", "test-player-1");
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        request.setRequestContext(requestContext);

        Map<String, Object> useResult = new HashMap<>();
        useResult.put("success", true);
        useResult.put("message", "Item used successfully");
        useResult.put("itemType", "RADAR_SCAN");

        when(playerInventoryService.useItem(anyString(), eq("inv-item-1"), any(Map.class)))
            .thenReturn(useResult);

        // Act
        APIGatewayProxyResponseEvent response = itemHandler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("Item used successfully"));
        verify(playerInventoryService).useItem(anyString(), eq("inv-item-1"), any(Map.class));
    }

    @Test
    void handleInvalidPath_ShouldReturn404() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/invalid/path");
        request.setHttpMethod("GET");

        // Act
        APIGatewayProxyResponseEvent response = itemHandler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Resource not found"));
    }

    @Test
    void handlePurchaseItem_ShouldReturn400_WhenItemNotFound() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/players/me/inventory/purchase");
        request.setHttpMethod("POST");
        request.setBody("{\"itemId\":\"nonexistent\",\"quantity\":1}");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer mock-token");
        request.setHeaders(headers);

        // Mock RequestContext and Authorizer claims
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, String> claims = new HashMap<>();
        claims.put("sub", "test-player-1");
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        request.setRequestContext(requestContext);

        when(itemService.getItemById("nonexistent")).thenReturn(Optional.empty());

        // Act
        APIGatewayProxyResponseEvent response = itemHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Item not found"));
        verify(itemService).getItemById("nonexistent");
    }
} 