package com.assassin.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Base class for all Assassin API integration tests.
 * Provides common functionality for making HTTP requests to the API.
 */
public abstract class AssassinApiIntegrationTestBase {

    private static final String DEFAULT_API_ENDPOINT = "http://localhost:3000";
    private static final String API_ENDPOINT_ENV_VAR = "ASSASSIN_API_ENDPOINT";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    private final String apiEndpoint;
    private final HttpClient httpClient;
    private final Gson gson;
    
    /**
     * Constructor initializes the HTTP client and JSON serializer.
     */
    public AssassinApiIntegrationTestBase() {
        // Get API endpoint from environment variable or use default
        this.apiEndpoint = System.getenv(API_ENDPOINT_ENV_VAR) != null 
            ? System.getenv(API_ENDPOINT_ENV_VAR) 
            : DEFAULT_API_ENDPOINT;
            
        // Initialize HTTP client with default timeouts
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
            .build();
            
        // Initialize GSON for JSON serialization/deserialization
        this.gson = new GsonBuilder().create();
    }
    
    /**
     * Creates a unique test name based on a prefix and a random UUID.
     *
     * @param prefix The prefix for the test name
     * @return A unique test name
     */
    protected String createUniqueTestName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Makes an HTTP request to the Assassin API.
     *
     * @param method The HTTP method (GET, POST, PUT, DELETE)
     * @param path The API path, starting with "/"
     * @param requestBody The request body object (will be serialized to JSON)
     * @param responseType The class to deserialize the response into
     * @param <T> The response type
     * @return The deserialized response
     * @throws IOException If an I/O error occurs
     */
    protected <T> T callApi(String method, String path, Object requestBody, Class<T> responseType) 
            throws IOException {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpoint + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
            
            // Add request body for POST, PUT methods
            if (requestBody != null && (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
                String jsonBody = gson.toJson(requestBody);
                requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
            } else {
                // For GET, DELETE without body
                requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            
            HttpResponse<String> response = httpClient.send(
                requestBuilder.build(), 
                HttpResponse.BodyHandlers.ofString()
            );
            
            // Check for error status codes
            if (response.statusCode() >= 400) {
                throw new IOException("API request failed with status " + response.statusCode() + 
                    ": " + response.body());
            }
            
            // If no response type is specified or response body is empty, return null
            if (responseType == null || response.body().isEmpty()) {
                return null;
            }
            
            // Deserialize the response body
            return gson.fromJson(response.body(), responseType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("API request was interrupted", e);
        }
    }
    
    /**
     * Convenience method to make a GET request to the API and deserialize the response.
     *
     * @param path The API path
     * @param responseType The response type class
     * @param <T> The response type
     * @return The deserialized response
     * @throws IOException If an I/O error occurs
     */
    protected <T> T get(String path, Class<T> responseType) throws IOException {
        return callApi("GET", path, null, responseType);
    }
    
    /**
     * Convenience method to make a POST request to the API with a request body.
     *
     * @param path The API path
     * @param requestBody The request body
     * @param responseType The response type class
     * @param <T> The response type
     * @return The deserialized response
     * @throws IOException If an I/O error occurs
     */
    protected <T> T post(String path, Object requestBody, Class<T> responseType) throws IOException {
        return callApi("POST", path, requestBody, responseType);
    }
    
    /**
     * Convenience method to make a PUT request to the API with a request body.
     *
     * @param path The API path
     * @param requestBody The request body
     * @param responseType The response type class
     * @param <T> The response type
     * @return The deserialized response
     * @throws IOException If an I/O error occurs
     */
    protected <T> T put(String path, Object requestBody, Class<T> responseType) throws IOException {
        return callApi("PUT", path, requestBody, responseType);
    }
    
    /**
     * Convenience method to make a DELETE request to the API.
     *
     * @param path The API path
     * @throws IOException If an I/O error occurs
     */
    protected void delete(String path) throws IOException {
        callApi("DELETE", path, null, null);
    }

    /**
     * Generates a test image in Base64 format for photo verification.
     */
    protected String generateTestImageBase64() {
        // In a real test, generate or load a small valid image file
        byte[] dummyImageData = "test-image-data".getBytes();
        return Base64.getEncoder().encodeToString(dummyImageData);
    }
} 