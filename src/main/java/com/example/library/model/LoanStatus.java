package com.example.library.model;

/**
 * Lifecycle of a loan.
 * ISSUED   - book is with the member and not yet due.
 * OVERDUE  - book is with the member and the due date has passed.
 * RETURNED - book has been returned (fine, if any, recorded on the loan).
 */
public enum LoanStatus {
    ISSUED("Issued"),
    OVERDUE("Overdue"),
    RETURNED("Returned");

    private final String label;

    LoanStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
