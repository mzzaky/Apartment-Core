package com.aithor.apartmentcore.model;

/**
 * Represents the overall tax status of an apartment based on unpaid invoices and time elapsed.
 */
public enum TaxStatus {
    ACTIVE,       // Normal, can generate income
    OVERDUE,      // 3 days without paying (income stops)
    INACTIVE,     // 5 days without paying (cannot use apartment)
    REPOSSESSION  // 7 days without paying (ownership removed)
}