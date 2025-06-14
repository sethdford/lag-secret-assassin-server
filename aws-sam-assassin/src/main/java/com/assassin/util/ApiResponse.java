package com.assassin.util;

import java.lang.annotation.*;

/**
 * Annotation to document API response codes and descriptions
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
@Documented
public @interface ApiResponse {
    /**
     * HTTP status code
     */
    int code() default 200;
    
    /**
     * Description of the response
     */
    String description() default "";
    
    /**
     * Response body class (if different from default)
     */
    Class<?> responseBody() default Void.class;
}