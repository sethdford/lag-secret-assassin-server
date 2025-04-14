package com.assassin.integration;

import java.util.UUID;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

/**
 * A mock implementation of the AWS Lambda Context object for testing purposes.
 * Provides default values for context properties.
 */
public class TestContext implements Context {

    @Override
    public String getAwsRequestId() {
        return "test-request-" + UUID.randomUUID().toString();
    }

    @Override
    public String getLogGroupName() {
        return "/aws/lambda/test-function";
    }

    @Override
    public String getLogStreamName() {
        return "test-log-stream-" + UUID.randomUUID().toString();
    }

    @Override
    public String getFunctionName() {
        return "test-function";
    }

    @Override
    public String getFunctionVersion() {
        return "$LATEST";
    }

    @Override
    public String getInvokedFunctionArn() {
        return "arn:aws:lambda:us-east-1:123456789012:function:test-function";
    }

    @Override
    public CognitoIdentity getIdentity() {
        // Return null or a mock CognitoIdentity if needed for specific tests
        return null;
    }

    @Override
    public ClientContext getClientContext() {
        // Return null or a mock ClientContext if needed
        return null;
    }

    @Override
    public int getRemainingTimeInMillis() {
        // Return a reasonable default value, e.g., 5 minutes
        return 5 * 60 * 1000;
    }

    @Override
    public int getMemoryLimitInMB() {
        return 512; // Default memory limit
    }

    @Override
    public LambdaLogger getLogger() {
        // Provide a simple logger implementation that prints to System.out
        return new LambdaLogger() {
            @Override
            public void log(String message) {
                System.out.println(message);
            }

            @Override
            public void log(byte[] message) {
                 System.out.println(new String(message));
            }
        };
    }
} 