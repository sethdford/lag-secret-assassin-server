package com.assassin.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.Objects;
import java.util.Map; // For potential price map

/**
 * Represents a subscription tier for the Assassin Game.
 * This could be stored in a DynamoDB table if tiers are managed dynamically,
 * or defined as constants/enums if they are static.
 */
@DynamoDbBean
public class SubscriptionTier {

    private String tierId; // e.g., "basic", "hunter", "assassin", "elite"
    private String name; // e.g., "Basic Plan", "Hunter Tier"
    private String description;
    private Long monthlyPriceCents; // Price in cents to avoid floating point issues
    private String currency; // e.g., "USD"
    private SubscriptionBenefits benefits;
    private Integer displayOrder; // For ordering tiers in UI
    private Boolean isActive; // To allow disabling tiers without deleting

    public SubscriptionTier() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("TierId")
    public String getTierId() {
        return tierId;
    }

    public void setTierId(String tierId) {
        this.tierId = tierId;
    }

    @DynamoDbAttribute("Name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @DynamoDbAttribute("Description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @DynamoDbAttribute("MonthlyPriceCents")
    public Long getMonthlyPriceCents() {
        return monthlyPriceCents;
    }

    public void setMonthlyPriceCents(Long monthlyPriceCents) {
        this.monthlyPriceCents = monthlyPriceCents;
    }

    @DynamoDbAttribute("Currency")
    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    // If benefits are complex and stored as a map or nested object in DynamoDB
    // you might need a custom attribute converter if not using @DynamoDbBean for SubscriptionBenefits.
    // Since SubscriptionBenefits is also a @DynamoDbBean, it should be handled automatically.
    @DynamoDbAttribute("Benefits")
    public SubscriptionBenefits getBenefits() {
        return benefits;
    }

    public void setBenefits(SubscriptionBenefits benefits) {
        this.benefits = benefits;
    }

    @DynamoDbAttribute("DisplayOrder")
    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    @DynamoDbAttribute("IsActive")
    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubscriptionTier that = (SubscriptionTier) o;
        return Objects.equals(tierId, that.tierId) &&
               Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Objects.equals(monthlyPriceCents, that.monthlyPriceCents) &&
               Objects.equals(currency, that.currency) &&
               Objects.equals(benefits, that.benefits) &&
               Objects.equals(displayOrder, that.displayOrder) &&
               Objects.equals(isActive, that.isActive);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tierId, name, description, monthlyPriceCents, currency, benefits, displayOrder, isActive);
    }

    @Override
    public String toString() {
        return "SubscriptionTier{" +
               "tierId='" + tierId + '\'' +
               ", name='" + name + '\'' +
               ", description='" + description + '\'' +
               ", monthlyPriceCents=" + monthlyPriceCents +
               ", currency='" + currency + '\'' +
               ", benefits=" + benefits +
               ", displayOrder=" + displayOrder +
               ", isActive=" + isActive +
               '}';
    }
} 