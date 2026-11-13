package com.example.library.dto;

import com.example.library.model.Loan;

import java.math.BigDecimal;

/**
 * A book currently out on loan, with how late it is and the fine that would be charged if returned today.
 */
public record ActiveLoanView(Loan loan, long overdueDays, BigDecimal fineIfReturnedToday) {

    public boolean isOverdue() {
        return overdueDays > 0;
    }
}
