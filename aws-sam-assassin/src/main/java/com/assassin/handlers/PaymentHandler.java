package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.config.StripeClientProvider;
import com.assassin.dao.TransactionDao;
import com.assassin.dao.DynamoDbTransactionDao; // Assuming this implementation
import com.assassin.model.Transaction;
import com.assassin.util.ApiGatewayResponseBuilder;
import com.assassin.util.RequestUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PaymentHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentHandler.class);
    private static final Gson GSON = new Gson();
    private final TransactionDao transactionDao;
    private static final long DEFAULT_ENTRY_FEE_AMOUNT_CENTS = 1000; // Example: 10.00 USD, in cents
    private static final String DEFAULT_CURRENCY = "usd";

    public PaymentHandler() {
        // Initialize Stripe SDK
        StripeClientProvider.initialize(); 
        this.transactionDao = new DynamoDbTransactionDao(); 
    }

    // Constructor for testing with mock DAO
    public PaymentHandler(TransactionDao transactionDao) {
        StripeClientProvider.initialize(); 
        this.transactionDao = transactionDao;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String path = request.getPath();
        String httpMethod = request.getHttpMethod();
        LOG.info("Received {} request for path: {}", httpMethod, path);

        try {
            if (!StripeClientProvider.isSdkInitialized()) {
                LOG.error("Stripe SDK not initialized. Cannot process payment.");
                return ApiGatewayResponseBuilder.buildErrorResponse(500, "Payment processing system is currently unavailable.");
            }

            // Payment routes
            Map<String, String> pathParameters = request.getPathParameters();
            
            // /games/{gameId}/pay-entry-fee - Direct payment with payment method
            if (pathParameters != null && pathParameters.containsKey("gameId") && path.endsWith("/pay-entry-fee") && "POST".equals(httpMethod)) {
                return handlePayEntryFeeRequest(request, context);
            }
            
            // /games/{gameId}/create-payment-intent - Create intent for client-side completion
            if (pathParameters != null && pathParameters.containsKey("gameId") && path.endsWith("/create-payment-intent") && "POST".equals(httpMethod)) {
                return handleCreatePaymentIntentRequest(request, context);
            }
            
            // /payments/{paymentIntentId}/confirm - Confirm payment intent
            if (pathParameters != null && pathParameters.containsKey("paymentIntentId") && path.endsWith("/confirm") && "POST".equals(httpMethod)) {
                return handleConfirmPaymentRequest(request, context);
            }

            return ApiGatewayResponseBuilder.buildErrorResponse(404, "Not Found: The requested payment resource or action was not found.");
        } catch (Exception e) {
            LOG.error("Error processing payment request: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Internal server error during payment processing.");
        }
    }

    private APIGatewayProxyResponseEvent handlePayEntryFeeRequest(APIGatewayProxyRequestEvent request, Context context) {
        LOG.info("Handling pay entry fee request...");
        String gameId = request.getPathParameters().get("gameId");
        String playerId = RequestUtils.getPlayerIdFromRequest(request);

        if (playerId == null || playerId.trim().isEmpty()) {
            return ApiGatewayResponseBuilder.buildErrorResponse(400, "Player ID is missing or invalid.");
        }

        if (gameId == null || gameId.trim().isEmpty()) {
            return ApiGatewayResponseBuilder.buildErrorResponse(400, "Game ID is missing or invalid.");
        }

        String paymentMethodId = null;
        Long amountCents = DEFAULT_ENTRY_FEE_AMOUNT_CENTS;
        String currency = DEFAULT_CURRENCY;

        try {
            // 1. Parse request body for payment details
            JsonObject requestBody = GSON.fromJson(request.getBody(), JsonObject.class);
            paymentMethodId = requestBody.has("paymentMethodId") ? requestBody.get("paymentMethodId").getAsString() : null;
            amountCents = requestBody.has("amount") ? requestBody.get("amount").getAsLong() : DEFAULT_ENTRY_FEE_AMOUNT_CENTS; // Amount in cents
            currency = requestBody.has("currency") ? requestBody.get("currency").getAsString() : DEFAULT_CURRENCY;

            if (paymentMethodId == null) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "paymentMethodId is required.");
            }

            LOG.info("Attempting to create PaymentIntent for Game ID: {}, Player ID: {}, Amount: {} {}, PaymentMethodID: {}", 
                gameId, playerId, amountCents, currency, paymentMethodId);

            // 2. Create a PaymentIntent with Stripe
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountCents) // Stripe expects amount in cents
                .setCurrency(currency)
                .setPaymentMethod(paymentMethodId)
                .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL) 
                .setConfirm(true) 
                .putMetadata("gameId", gameId)
                .putMetadata("playerId", playerId)
                .setDescription("Entry fee for Assassin Game: " + gameId)
                .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);
            LOG.info("Stripe PaymentIntent created with ID: {}, Status: {}", paymentIntent.getId(), paymentIntent.getStatus());

            // 3. Record the transaction in DynamoDB
            Transaction transaction = new Transaction();
            transaction.setTransactionId(UUID.randomUUID().toString());
            transaction.setPlayerId(playerId);
            transaction.setGameId(gameId);
            transaction.setItemId(gameId); 
            transaction.setTransactionType(Transaction.TransactionType.ENTRY_FEE);
            transaction.setAmount(amountCents); // Store amount in cents (Long)
            transaction.setCurrency(currency.toUpperCase());
            transaction.setPaymentGateway("Stripe");
            transaction.setGatewayTransactionId(paymentIntent.getId());
            transaction.setPaymentMethodDetails(paymentIntent.getPaymentMethod()); 
            transaction.setDescription("Entry fee for game " + gameId);
            transaction.initializeTimestamps(); // Sets createdAt and updatedAt

            if ("succeeded".equals(paymentIntent.getStatus())) {
                transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
                transactionDao.saveTransaction(transaction);
                LOG.info("Transaction {} recorded successfully for PaymentIntent {}", transaction.getTransactionId(), paymentIntent.getId());
                return ApiGatewayResponseBuilder.buildResponse(200, GSON.toJson(Map.of(
                    "message", "Payment successful and entry fee recorded.",
                    "transactionId", transaction.getTransactionId(),
                    "paymentIntentId", paymentIntent.getId(),
                    "status", paymentIntent.getStatus(),
                    "clientSecret", paymentIntent.getClientSecret()
                )));
            } else if ("requires_action".equals(paymentIntent.getStatus()) || "requires_confirmation".equals(paymentIntent.getStatus())){
                transaction.setStatus(Transaction.TransactionStatus.PENDING);
                 transactionDao.saveTransaction(transaction); 
                return ApiGatewayResponseBuilder.buildResponse(200, GSON.toJson(Map.of(
                    "message", "Payment requires further action.",
                    "transactionId", transaction.getTransactionId(),
                    "paymentIntentId", paymentIntent.getId(),
                    "clientSecret", paymentIntent.getClientSecret(), 
                    "status", paymentIntent.getStatus()
                )));
            } else {
                transaction.setStatus(Transaction.TransactionStatus.FAILED);
                transaction.putMetadata("stripeError", "PaymentIntent status: " + paymentIntent.getStatus());
                transactionDao.saveTransaction(transaction);
                LOG.warn("PaymentIntent {} for Game ID {} / Player {} resulted in status: {}. Transaction recorded as FAILED.", 
                    paymentIntent.getId(), gameId, playerId, paymentIntent.getStatus());
                return ApiGatewayResponseBuilder.buildResponse(400, GSON.toJson(Map.of(
                    "message", "Payment processing failed or requires attention.",
                    "transactionId", transaction.getTransactionId(),
                    "paymentIntentId", paymentIntent.getId(),
                    "status", paymentIntent.getStatus()
                )));
            }

        } catch (StripeException e) {
            LOG.error("Stripe API error during payment for Game ID {} / Player {}: {}. Code: {}, Param: {}, Type: {}", 
                gameId, playerId, e.getMessage(), e.getCode(), 
                (e.getStripeError() != null ? e.getStripeError().getParam() : "N/A"), 
                (e.getStripeError() != null ? e.getStripeError().getType() : "N/A"), e);
            Transaction failedTx = new Transaction();
            failedTx.setTransactionId(UUID.randomUUID().toString());
            failedTx.setPlayerId(playerId); 
            failedTx.setGameId(gameId); 
            failedTx.setTransactionType(Transaction.TransactionType.ENTRY_FEE);
            failedTx.setStatus(Transaction.TransactionStatus.FAILED);
            failedTx.setAmount(amountCents); // Store amount in cents
            failedTx.setCurrency(currency.toUpperCase());
            failedTx.setPaymentGateway("Stripe");
            failedTx.setDescription("Failed entry fee attempt for game " + gameId);
            failedTx.putMetadata("stripeErrorMessage", e.getMessage());
            if (e.getStripeError() != null) {
                failedTx.putMetadata("stripeErrorCode", e.getStripeError().getCode());
                failedTx.putMetadata("stripeErrorType", e.getStripeError().getType());
                failedTx.putMetadata("stripeErrorParam", e.getStripeError().getParam());
            }
            failedTx.initializeTimestamps();
            transactionDao.saveTransaction(failedTx);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Payment processing failed due to an issue with the payment provider. Error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error during payment processing for Game ID {} / Player {}: {}", gameId, playerId, e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "An unexpected error occurred while processing your payment.");
        }
    }

    /**
     * Create a PaymentIntent for client-side completion with multiple payment method support
     * This allows for Apple Pay, Google Pay, and other client-side payment methods
     */
    private APIGatewayProxyResponseEvent handleCreatePaymentIntentRequest(APIGatewayProxyRequestEvent request, Context context) {
        LOG.info("Handling create payment intent request...");
        String gameId = request.getPathParameters().get("gameId");
        String playerId = RequestUtils.getPlayerIdFromRequest(request);

        if (playerId == null || playerId.trim().isEmpty()) {
            return ApiGatewayResponseBuilder.buildErrorResponse(400, "Player ID is missing or invalid.");
        }

        if (gameId == null || gameId.trim().isEmpty()) {
            return ApiGatewayResponseBuilder.buildErrorResponse(400, "Game ID is missing or invalid.");
        }

        try {
            // Parse request body for payment details
            JsonObject requestBody = GSON.fromJson(request.getBody(), JsonObject.class);
            Long amountCents = requestBody.has("amount") ? requestBody.get("amount").getAsLong() : DEFAULT_ENTRY_FEE_AMOUNT_CENTS;
            String currency = requestBody.has("currency") ? requestBody.get("currency").getAsString() : DEFAULT_CURRENCY;
            
            // Support for automatic payment methods (Apple Pay, Google Pay, etc.)
            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency(currency)
                .putMetadata("gameId", gameId)
                .putMetadata("playerId", playerId)
                .setDescription("Entry fee for Assassin Game: " + gameId)
                .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.AUTOMATIC);
            
            // Enable automatic payment methods if requested
            if (requestBody.has("enableAutomaticPaymentMethods") && requestBody.get("enableAutomaticPaymentMethods").getAsBoolean()) {
                paramsBuilder.setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build()
                );
            }

            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());
            LOG.info("PaymentIntent created with ID: {} for client-side completion", paymentIntent.getId());

            // Create a pending transaction record
            Transaction transaction = new Transaction();
            transaction.setTransactionId(UUID.randomUUID().toString());
            transaction.setPlayerId(playerId);
            transaction.setGameId(gameId);
            transaction.setItemId(gameId);
            transaction.setTransactionType(Transaction.TransactionType.ENTRY_FEE);
            transaction.setAmount(amountCents);
            transaction.setCurrency(currency.toUpperCase());
            transaction.setPaymentGateway("Stripe");
            transaction.setGatewayTransactionId(paymentIntent.getId());
            transaction.setStatus(Transaction.TransactionStatus.PENDING);
            transaction.setDescription("Entry fee for game " + gameId);
            transaction.initializeTimestamps();
            
            transactionDao.saveTransaction(transaction);
            LOG.info("Pending transaction {} created for PaymentIntent {}", transaction.getTransactionId(), paymentIntent.getId());

            return ApiGatewayResponseBuilder.buildResponse(200, GSON.toJson(Map.of(
                "clientSecret", paymentIntent.getClientSecret(),
                "paymentIntentId", paymentIntent.getId(),
                "transactionId", transaction.getTransactionId(),
                "status", paymentIntent.getStatus()
            )));

        } catch (StripeException e) {
            LOG.error("Stripe API error creating PaymentIntent for Game ID {} / Player {}: {}", gameId, playerId, e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Failed to create payment intent: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error creating PaymentIntent for Game ID {} / Player {}: {}", gameId, playerId, e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "An unexpected error occurred while creating payment intent.");
        }
    }

    /**
     * Confirm a PaymentIntent and update transaction status
     */
    private APIGatewayProxyResponseEvent handleConfirmPaymentRequest(APIGatewayProxyRequestEvent request, Context context) {
        LOG.info("Handling confirm payment request...");
        String paymentIntentId = request.getPathParameters().get("paymentIntentId");
        String playerId = RequestUtils.getPlayerIdFromRequest(request);

        if (paymentIntentId == null || paymentIntentId.trim().isEmpty()) {
            return ApiGatewayResponseBuilder.buildErrorResponse(400, "Payment Intent ID is missing or invalid.");
        }

        if (playerId == null || playerId.trim().isEmpty()) {
            return ApiGatewayResponseBuilder.buildErrorResponse(400, "Player ID is missing or invalid.");
        }

        try {
            // Retrieve the PaymentIntent from Stripe
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            LOG.info("Retrieved PaymentIntent {} with status: {}", paymentIntentId, paymentIntent.getStatus());

            // Find the corresponding transaction - we'll need to query by player first
            // Since we don't have a direct getByGatewayId method, we'll search player transactions
            List<Transaction> playerTransactions = transactionDao.getTransactionsByPlayerId(playerId, 50, null);
            Transaction transaction = playerTransactions.stream()
                .filter(tx -> paymentIntentId.equals(tx.getGatewayTransactionId()))
                .findFirst()
                .orElse(null);
            
            if (transaction == null) {
                LOG.error("No transaction found for PaymentIntent ID: {} and Player: {}", paymentIntentId, playerId);
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Transaction not found for this payment intent.");
            }

            // Verify the player owns this transaction
            if (!playerId.equals(transaction.getPlayerId())) {
                LOG.error("Player {} attempted to confirm transaction owned by {}", playerId, transaction.getPlayerId());
                return ApiGatewayResponseBuilder.buildErrorResponse(403, "You are not authorized to confirm this payment.");
            }

            // Update transaction status based on PaymentIntent status
            if ("succeeded".equals(paymentIntent.getStatus())) {
                transactionDao.updateTransactionStatus(
                    transaction.getTransactionId(), 
                    Transaction.TransactionStatus.COMPLETED,
                    paymentIntentId,
                    paymentIntent.getPaymentMethod()
                );
                LOG.info("Transaction {} completed successfully", transaction.getTransactionId());
                
                return ApiGatewayResponseBuilder.buildResponse(200, GSON.toJson(Map.of(
                    "message", "Payment confirmed successfully",
                    "transactionId", transaction.getTransactionId(),
                    "status", "completed"
                )));
            } else if ("requires_action".equals(paymentIntent.getStatus())) {
                return ApiGatewayResponseBuilder.buildResponse(200, GSON.toJson(Map.of(
                    "message", "Payment requires additional action",
                    "clientSecret", paymentIntent.getClientSecret(),
                    "status", paymentIntent.getStatus()
                )));
            } else {
                transactionDao.updateTransactionStatus(
                    transaction.getTransactionId(),
                    Transaction.TransactionStatus.FAILED,
                    null,
                    null
                );
                LOG.warn("Payment confirmation failed for PaymentIntent {}: status {}", paymentIntentId, paymentIntent.getStatus());
                
                return ApiGatewayResponseBuilder.buildResponse(400, GSON.toJson(Map.of(
                    "message", "Payment confirmation failed",
                    "status", paymentIntent.getStatus()
                )));
            }

        } catch (StripeException e) {
            LOG.error("Stripe API error confirming payment {}: {}", paymentIntentId, e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Failed to confirm payment: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error confirming payment {}: {}", paymentIntentId, e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "An unexpected error occurred while confirming payment.");
        }
    }
} 