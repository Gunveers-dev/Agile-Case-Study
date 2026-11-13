package com.example.library.dto;

import java.math.BigDecimal;

/**
 * Headline numbers for the Reports & Analytics page.
 */
public record ReportSummary(
        long totalBooks,
        long totalTitles,
        long totalMembers,
        long totalTransactions,
        long activeLoans,
        long returnedLoans,
        long overdueBooks,
        BigDecimal totalFinesCollected,
        BigDecimal pendingFines
) {
}
