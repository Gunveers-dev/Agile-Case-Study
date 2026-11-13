package com.example.library.service;

import com.example.library.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Overdue fine rules.
 * <pre>
 *   Fine = Overdue Days x Rs 5
 *   Returned on or before the due date  ->  Fine = Rs 0
 * </pre>
 */
@Service
public class FineService {

    /** Fine charged for each day a book is returned late (in rupees). */
    public static final BigDecimal FINE_PER_DAY = BigDecimal.valueOf(5);

    /**
     * Number of days a book is late. Returns 0 when returned on or before the due date.
     */
    public long calculateOverdueDays(LocalDate dueDate, LocalDate returnDate) {
        requireDate(dueDate, "Due date");
        requireDate(returnDate, "Return date");
        long daysLate = ChronoUnit.DAYS.between(dueDate, returnDate);
        return Math.max(0, daysLate);
    }

    /**
     * Fine for a book with the given due date returned on the given date.
     */
    public BigDecimal calculateFine(LocalDate dueDate, LocalDate returnDate) {
        long overdueDays = calculateOverdueDays(dueDate, returnDate);
        return FINE_PER_DAY.multiply(BigDecimal.valueOf(overdueDays));
    }

    /**
     * Fine for a complete loan, also checking that the dates are consistent with each other.
     *
     * @throws BusinessException if a date is missing, the due date is before the issue date,
     *                           or the return date is before the issue date
     */
    public BigDecimal calculateFine(LocalDate issueDate, LocalDate dueDate, LocalDate returnDate) {
        requireDate(issueDate, "Issue date");
        requireDate(dueDate, "Due date");
        requireDate(returnDate, "Return date");
        if (dueDate.isBefore(issueDate)) {
            throw new BusinessException("Due date cannot be before the issue date");
        }
        if (returnDate.isBefore(issueDate)) {
            throw new BusinessException("Return date cannot be before the issue date");
        }
        return calculateFine(dueDate, returnDate);
    }

    private static void requireDate(LocalDate date, String name) {
        if (date == null) {
            throw new BusinessException(name + " is required to calculate the fine");
        }
    }
}
