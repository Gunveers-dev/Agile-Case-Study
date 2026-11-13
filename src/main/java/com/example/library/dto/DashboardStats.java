package com.example.library.dto;

import java.math.BigDecimal;

/**
 * Figures shown on the dashboard statistic cards. All values are read from the database.
 *
 * @param totalBooks     total physical copies owned
 * @param totalTitles    number of distinct titles
 * @param totalMembers   registered members
 * @param booksIssued    copies currently out on loan
 * @param availableBooks copies currently on the shelf
 * @param overdueBooks   loans past their due date and not returned
 * @param pendingFines   fines accruing on overdue loans if they were returned today
 */
public record DashboardStats(
        long totalBooks,
        long totalTitles,
        long totalMembers,
        long booksIssued,
        long availableBooks,
        long overdueBooks,
        BigDecimal pendingFines
) {
}
