package com.assassin.dao;

import com.assassin.model.SubscriptionTier;

import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for managing SubscriptionTier data.
 */
public interface SubscriptionTierDao {

    /**
     * Retrieves a subscription tier by its unique ID.
     *
     * @param tierId The ID of the tier to retrieve.
     * @return An Optional containing the SubscriptionTier if found, otherwise empty.
     */
    Optional<SubscriptionTier> getTierById(String tierId);

    /**
     * Retrieves all available subscription tiers.
     *
     * @return A list of all SubscriptionTiers. List may be empty if no tiers are defined.
     */
    List<SubscriptionTier> getAllTiers();

    /**
     * Retrieves all active subscription tiers, ordered by displayOrder.
     *
     * @return A list of active SubscriptionTiers.
     */
    List<SubscriptionTier> getAllActiveTiersOrdered();

    /**
     * Saves or updates a subscription tier.
     *
     * @param tier The SubscriptionTier object to save or update.
     */
    void saveTier(SubscriptionTier tier);

    /**
     * Deletes a subscription tier by its ID.
     *
     * @param tierId The ID of the tier to delete.
     * @return true if deletion was successful, false otherwise.
     */
    boolean deleteTier(String tierId);
} 