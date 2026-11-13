package com.example.library.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Result of returning a book: shown to the administrator in a receipt dialog.
 */
public record ReturnBookResponse(
        Long loanId,
        String transactionCode,
        String memberName,
        String memberCode,
        String bookTitle,
        LocalDate issueDate,
        LocalDate dueDate,
        LocalDate returnDate,
        long overdueDays,
        BigDecimal fineAmount
) implements Serializable {

    public boolean hasFine() {
        return fineAmount != null && fineAmount.signum() > 0;
    }
}
