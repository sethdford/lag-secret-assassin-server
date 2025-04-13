package com.assassin.util;

import com.google.gson.Gson;

/**
 * Simple utility class to represent a JSON error response.
 */
public class ErrorResponse {
    private String message;

    public ErrorResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Converts this object to its JSON representation.
     * 
     * @return JSON string representing the error.
     */
    public String toJson() {
        return new Gson().toJson(this);
    }
} 