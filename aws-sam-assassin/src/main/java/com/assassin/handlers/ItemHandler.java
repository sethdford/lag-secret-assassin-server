package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.service.ItemService;
import com.assassin.service.PlayerInventoryService;
import com.assassin.model.Item;
import com.assassin.model.PlayerInventoryItem;
import com.assassin.util.ApiGatewayResponseBuilder;
import com.assassin.util.RequestUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

/**
 * Handler for item and inventory-related API endpoints.
 * Provides REST API for listing items, purchasing items, viewing inventory, and using items.
 */
public class ItemHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ItemHandler.class);
    private static final Gson gson = new Gson();
    
    private final ItemService itemService;
    private final PlayerInventoryService playerInventoryService;

    /**
     * Default constructor.
     */
    public ItemHandler() {
        this.itemService = new ItemService();
        this.playerInventoryService = new PlayerInventoryService();
    }

    /**
     * Constructor for dependency injection (testing).
     */
    public ItemHandler(ItemService itemService, PlayerInventoryService playerInventoryService) {
        this.itemService = itemService;
        this.playerInventoryService = playerInventoryService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String path = request.getPath();
        String httpMethod = request.getHttpMethod();
        Map<String, String> pathParameters = request.getPathParameters();
        
        logger.info("Processing {} request to path: {}", httpMethod, path);

        try {
            // Route based on path and method
            if (path.matches("/items") && "GET".equals(httpMethod)) {
                return handleGetItems(request);
            } else if (path.matches("/players/me/inventory/purchase") && "POST".equals(httpMethod)) {
                return handlePurchaseItem(request);
            } else if (path.matches("/players/me/inventory") && "GET".equals(httpMethod)) {
                return handleGetPlayerInventory(request);
            } else if (path.matches("/players/me/inventory/[^/]+/use") && "POST".equals(httpMethod)) {
                return handleUseItem(request);
            } else {
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Resource not found");
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Client error processing request: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(400, e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing request: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Internal server error");
        }
    }

    /**
     * Handles GET /items - List all available items.
     */
    private APIGatewayProxyResponseEvent handleGetItems(APIGatewayProxyRequestEvent request) {
        try {
            logger.info("Getting all available items");
            
            Map<String, String> queryParameters = request.getQueryStringParameters();
            String itemTypeFilter = null;
            
            if (queryParameters != null && queryParameters.containsKey("type")) {
                itemTypeFilter = queryParameters.get("type");
            }

            List<Item> items;
            if (itemTypeFilter != null && !itemTypeFilter.trim().isEmpty()) {
                try {
                    Item.ItemType itemType = Item.ItemType.valueOf(itemTypeFilter.toUpperCase());
                    items = itemService.getItemsByType(itemType);
                    logger.info("Retrieved {} items of type {}", items.size(), itemType);
                } catch (IllegalArgumentException e) {
                    return ApiGatewayResponseBuilder.buildErrorResponse(400, "Invalid item type: " + itemTypeFilter);
                }
            } else {
                items = itemService.getAllItems();
                logger.info("Retrieved {} items", items.size());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("items", items);
            response.put("count", items.size());

            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));

        } catch (Exception e) {
            logger.error("Error getting items: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Failed to retrieve items");
        }
    }

    /**
     * Handles POST /players/me/inventory/purchase - Purchase an item.
     */
    private APIGatewayProxyResponseEvent handlePurchaseItem(APIGatewayProxyRequestEvent request) {
        try {
            String playerId = RequestUtils.getPlayerIdFromRequest(request);
            if (playerId == null || playerId.trim().isEmpty()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(401, "Player authentication required");
            }

            logger.info("Processing item purchase for player: {}", playerId);

            // Parse request body
            JsonObject requestBody = gson.fromJson(request.getBody(), JsonObject.class);
            if (requestBody == null) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Request body is required");
            }

            if (!requestBody.has("itemId")) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "itemId is required");
            }

            String itemId = requestBody.get("itemId").getAsString();
            int quantity = requestBody.has("quantity") ? requestBody.get("quantity").getAsInt() : 1;
            String gameId = requestBody.has("gameId") ? requestBody.get("gameId").getAsString() : null;

            if (quantity <= 0) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Quantity must be positive");
            }

            logger.info("Purchase request: itemId={}, quantity={}, gameId={}", itemId, quantity, gameId);

            // For now, we'll create a response indicating that the purchase should be processed 
            // through the payment system. In a real implementation, this would integrate with
            // the PaymentHandler to process the purchase.
            
            // First, validate that the item exists and get its details
            Optional<Item> itemOpt = itemService.getItemById(itemId);
            if (itemOpt.isEmpty()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Item not found: " + itemId);
            }
            
            Item item = itemOpt.get();
            if (!item.isAvailable()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Item is not available for purchase");
            }
            
            // Calculate total cost
            long totalCostCents = item.getPrice() * quantity;
            
            Map<String, Object> purchaseInfo = new HashMap<>();
            purchaseInfo.put("itemId", itemId);
            purchaseInfo.put("itemName", item.getName());
            purchaseInfo.put("quantity", quantity);
            purchaseInfo.put("unitPrice", item.getPrice());
            purchaseInfo.put("totalCost", totalCostCents);
            purchaseInfo.put("currency", "USD");
            if (gameId != null) {
                purchaseInfo.put("gameId", gameId);
            }
            purchaseInfo.put("message", "Item purchase details calculated. Use /games/{gameId}/pay-entry-fee endpoint to complete purchase.");
            purchaseInfo.put("paymentRequired", true);
            
            logger.info("Generated purchase info for player {}: {}x {} (${} total)", 
                playerId, quantity, item.getName(), totalCostCents / 100.0);
            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(purchaseInfo));

        } catch (Exception e) {
            logger.error("Error processing item purchase: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Failed to process item purchase");
        }
    }

    /**
     * Handles GET /players/me/inventory - Get player's inventory.
     */
    private APIGatewayProxyResponseEvent handleGetPlayerInventory(APIGatewayProxyRequestEvent request) {
        try {
            String playerId = RequestUtils.getPlayerIdFromRequest(request);
            if (playerId == null || playerId.trim().isEmpty()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(401, "Player authentication required");
            }

            logger.info("Getting inventory for player: {}", playerId);

            Map<String, String> queryParameters = request.getQueryStringParameters();
            String gameId = null;
            boolean activeOnly = false;
            
            if (queryParameters != null) {
                gameId = queryParameters.get("gameId");
                activeOnly = "true".equalsIgnoreCase(queryParameters.get("activeOnly"));
            }

            List<PlayerInventoryItem> inventory;
            if (gameId != null && !gameId.trim().isEmpty()) {
                inventory = playerInventoryService.getPlayerInventoryForGame(playerId, gameId);
                logger.info("Retrieved {} inventory items for player {} in game {}", inventory.size(), playerId, gameId);
            } else if (activeOnly) {
                inventory = playerInventoryService.getActivePlayerInventory(playerId);
                logger.info("Retrieved {} active inventory items for player {}", inventory.size(), playerId);
            } else {
                inventory = playerInventoryService.getPlayerInventory(playerId);
                logger.info("Retrieved {} inventory items for player {}", inventory.size(), playerId);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("inventory", inventory);
            response.put("count", inventory.size());
            response.put("playerId", playerId);
            if (gameId != null) {
                response.put("gameId", gameId);
            }

            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));

        } catch (Exception e) {
            logger.error("Error getting player inventory: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Failed to retrieve inventory");
        }
    }

    /**
     * Handles POST /players/me/inventory/{inventoryItemId}/use - Use an inventory item.
     */
    private APIGatewayProxyResponseEvent handleUseItem(APIGatewayProxyRequestEvent request) {
        try {
            String playerId = RequestUtils.getPlayerIdFromRequest(request);
            if (playerId == null || playerId.trim().isEmpty()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(401, "Player authentication required");
            }

            Map<String, String> pathParameters = request.getPathParameters();
            if (pathParameters == null || !pathParameters.containsKey("inventoryItemId")) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "inventoryItemId path parameter is required");
            }

            String inventoryItemId = pathParameters.get("inventoryItemId");
            logger.info("Using inventory item {} for player {}", inventoryItemId, playerId);

            // Parse optional context from request body
            Map<String, Object> context = new HashMap<>();
            if (request.getBody() != null && !request.getBody().trim().isEmpty()) {
                try {
                    JsonObject requestBody = gson.fromJson(request.getBody(), JsonObject.class);
                    if (requestBody != null) {
                        // Convert JsonObject to Map for context
                        for (String key : requestBody.keySet()) {
                            context.put(key, requestBody.get(key).getAsString());
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse request body as context, proceeding without context: {}", e.getMessage());
                }
            }

            // Use the item
            Map<String, Object> useResult = playerInventoryService.useItem(playerId, inventoryItemId, context);
            
            boolean success = (boolean) useResult.getOrDefault("success", false);
            if (success) {
                logger.info("Successfully used inventory item {} for player {}", inventoryItemId, playerId);
                return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(useResult));
            } else {
                String message = (String) useResult.getOrDefault("message", "Failed to use item");
                String reason = (String) useResult.getOrDefault("reason", "UNKNOWN");
                logger.warn("Failed to use inventory item {} for player {}: {} ({})", 
                    inventoryItemId, playerId, message, reason);
                
                int statusCode = "ITEM_UNUSABLE".equals(reason) ? 400 : 500;
                return ApiGatewayResponseBuilder.buildErrorResponse(statusCode, message);
            }

        } catch (Exception e) {
            logger.error("Error using inventory item: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Failed to use inventory item");
        }
    }
} 