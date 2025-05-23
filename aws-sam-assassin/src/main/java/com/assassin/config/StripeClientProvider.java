package com.assassin.config;

import com.stripe.Stripe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StripeClientProvider {

    private static final Logger LOG = LoggerFactory.getLogger(StripeClientProvider.class);
    private static final String STRIPE_API_KEY_ENV_VAR = "STRIPE_SECRET_KEY";
    private static boolean isInitialized = false;

    static {
        initialize();
    }

    private StripeClientProvider() {
        // Private constructor to prevent instantiation
    }

    public static synchronized void initialize() {
        if (isInitialized) {
            return;
        }
        String apiKey = System.getenv(STRIPE_API_KEY_ENV_VAR);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            LOG.error("Stripe API key ({}) is not set in environment variables.", STRIPE_API_KEY_ENV_VAR);
            // Depending on the application's needs, you might throw an exception here
            // or allow the application to run without Stripe functionality for certain environments (e.g., local testing without payments).
            // For now, we'll just log an error.
            return;
        }
        Stripe.apiKey = apiKey;
        LOG.info("Stripe SDK initialized successfully.");
        isInitialized = true;
    }

    /**
     * Checks if the Stripe SDK has been initialized.
     * This can be used by services before attempting Stripe operations.
     * @return true if initialized, false otherwise.
     */
    public static boolean isSdkInitialized() {
        if (!isInitialized) {
            // Attempt to initialize if not already, in case static block wasn't run or failed silently
            // This is a fallback, primary initialization should happen in the static block.
            initialize(); 
        }
        return isInitialized;
    }

    /**
     * Retrieves the Stripe API key from environment variables.
     * Note: This primarily serves to confirm the key is loaded, as Stripe.apiKey is set globally.
     * @return The Stripe API key if set, otherwise null.
     */
    public static String getApiKey() {
        return System.getenv(STRIPE_API_KEY_ENV_VAR);
    }
} 