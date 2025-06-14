package com.assassin.util;

import java.lang.annotation.*;

/**
 * Annotation to document API endpoints for automatic OpenAPI generation.
 * Apply this to handler methods to automatically include them in API documentation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface ApiEndpoint {
    /**
     * HTTP method (GET, POST, PUT, DELETE, etc.)
     */
    String method() default "GET";
    
    /**
     * API path (e.g., "/games/{gameId}")
     */
    String path() default "";
    
    /**
     * Brief summary of what the endpoint does
     */
    String summary() default "";
    
    /**
     * Detailed description of the endpoint
     */
    String description() default "";
    
    /**
     * Tags for grouping endpoints in documentation
     */
    String[] tags() default {};
    
    /**
     * Response status codes and descriptions
     */
    ApiResponse[] responses() default {};
    
    /**
     * Whether authentication is required
     */
    boolean authenticated() default true;
    
    /**
     * Request body class (if applicable)
     */
    Class<?> requestBody() default Void.class;
    
    /**
     * Response body class
     */
    Class<?> responseBody() default Void.class;
    
    /**
     * Query parameters
     */
    ApiParam[] queryParams() default {};
    
    /**
     * Path parameters
     */
    ApiParam[] pathParams() default {};
}