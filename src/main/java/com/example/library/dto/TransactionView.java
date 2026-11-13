package com.example.library.dto;

import com.example.library.model.Loan;
import com.example.library.model.LoanStatus;

import java.math.BigDecimal;

/**
 * A row of the transaction history.
 *
 * @param loan     the loan
 * @param status   status as of today (ISSUED, OVERDUE or RETURNED)
 * @param fine     fine charged (returned loans) or accruing so far (overdue loans)
 * @param accruing true when the fine is still growing because the book is not back yet
 */
public record TransactionView(Loan loan, LoanStatus status, BigDecimal fine, boolean accruing) {
}
