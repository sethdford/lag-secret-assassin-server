package com.assassin.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the benefits associated with a subscription tier.
 * This class can be embedded within SubscriptionTier or stored separately if benefits become very complex.
 */
@DynamoDbBean
public class SubscriptionBenefits {

    private Integer monthlyCurrencyBonus;
    private List<String> freeItemGrants; // List of Item IDs or descriptive names
    private Boolean priorityAccess; // e.g., to new game modes or support
    private Double discountPercentage; // e.g., on in-game store items
    private Map<String, String> customBenefits; // For other specific benefits

    public SubscriptionBenefits() {
    }

    @DynamoDbAttribute("MonthlyCurrencyBonus")
    public Integer getMonthlyCurrencyBonus() {
        return monthlyCurrencyBonus;
    }

    public void setMonthlyCurrencyBonus(Integer monthlyCurrencyBonus) {
        this.monthlyCurrencyBonus = monthlyCurrencyBonus;
    }

    @DynamoDbAttribute("FreeItemGrants")
    public List<String> getFreeItemGrants() {
        return freeItemGrants;
    }

    public void setFreeItemGrants(List<String> freeItemGrants) {
        this.freeItemGrants = freeItemGrants;
    }

    @DynamoDbAttribute("PriorityAccess")
    public Boolean getPriorityAccess() {
        return priorityAccess;
    }

    public void setPriorityAccess(Boolean priorityAccess) {
        this.priorityAccess = priorityAccess;
    }

    @DynamoDbAttribute("DiscountPercentage")
    public Double getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(Double discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    @DynamoDbAttribute("CustomBenefits")
    public Map<String, String> getCustomBenefits() {
        return customBenefits;
    }

    public void setCustomBenefits(Map<String, String> customBenefits) {
        this.customBenefits = customBenefits;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubscriptionBenefits that = (SubscriptionBenefits) o;
        return Objects.equals(monthlyCurrencyBonus, that.monthlyCurrencyBonus) &&
               Objects.equals(freeItemGrants, that.freeItemGrants) &&
               Objects.equals(priorityAccess, that.priorityAccess) &&
               Objects.equals(discountPercentage, that.discountPercentage) &&
               Objects.equals(customBenefits, that.customBenefits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(monthlyCurrencyBonus, freeItemGrants, priorityAccess, discountPercentage, customBenefits);
    }

    @Override
    public String toString() {
        return "SubscriptionBenefits{" +
               "monthlyCurrencyBonus=" + monthlyCurrencyBonus +
               ", freeItemGrants=" + freeItemGrants +
               ", priorityAccess=" + priorityAccess +
               ", discountPercentage=" + discountPercentage +
               ", customBenefits=" + customBenefits +
               '}';
    }
} 