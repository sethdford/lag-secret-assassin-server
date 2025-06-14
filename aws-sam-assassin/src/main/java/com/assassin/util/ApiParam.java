package com.assassin.util;

import java.lang.annotation.*;

/**
 * Annotation to document API parameters
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
@Documented
public @interface ApiParam {
    /**
     * Parameter name
     */
    String name();
    
    /**
     * Parameter description
     */
    String description() default "";
    
    /**
     * Whether the parameter is required
     */
    boolean required() default false;
    
    /**
     * Parameter type
     */
    String type() default "string";
    
    /**
     * Example value
     */
    String example() default "";
}