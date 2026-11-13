package com.example.library.service;

import com.example.library.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FineService - overdue fine rules (Rs 5 per day)")
class FineServiceTest {

    private static final LocalDate ISSUE_DATE = LocalDate.of(2026, 11, 1);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 11, 15);

    private final FineService fineService = new FineService();

    @Test
    @DisplayName("No fine when the book is returned before the due date")
    void noFineWhenReturnedBeforeDueDate() {
        LocalDate returned = DUE_DATE.minusDays(3);

        assertMoney("0", fineService.calculateFine(DUE_DATE, returned));
        assertEquals(0, fineService.calculateOverdueDays(DUE_DATE, returned));
    }

    @Test
    @DisplayName("No fine when the book is returned exactly on the due date")
    void noFineWhenReturnedOnDueDate() {
        assertMoney("0", fineService.calculateFine(DUE_DATE, DUE_DATE));
        assertEquals(0, fineService.calculateOverdueDays(DUE_DATE, DUE_DATE));
    }

    @Test
    @DisplayName("Rs 5 fine for 1 overdue day")
    void fineForOneOverdueDay() {
        LocalDate returned = DUE_DATE.plusDays(1);

        assertEquals(1, fineService.calculateOverdueDays(DUE_DATE, returned));
        assertMoney("5", fineService.calculateFine(DUE_DATE, returned));
    }

    @Test
    @DisplayName("Rs 25 fine for 5 overdue days")
    void fineForFiveOverdueDays() {
        LocalDate returned = DUE_DATE.plusDays(5);

        assertEquals(5, fineService.calculateOverdueDays(DUE_DATE, returned));
        assertMoney("25", fineService.calculateFine(DUE_DATE, returned));
    }

    @Test
    @DisplayName("Rs 50 fine for 10 overdue days")
    void fineForTenOverdueDays() {
        LocalDate returned = DUE_DATE.plusDays(10);

        assertEquals(10, fineService.calculateOverdueDays(DUE_DATE, returned));
        assertMoney("50", fineService.calculateFine(DUE_DATE, returned));
    }

    @Test
    @DisplayName("Fine is counted across a month boundary")
    void fineAcrossMonthBoundary() {
        LocalDate due = LocalDate.of(2026, 10, 28);
        LocalDate returned = LocalDate.of(2026, 11, 2);

        assertMoney("25", fineService.calculateFine(due, returned));
    }

    @Test
    @DisplayName("Full loan: issued 1 Nov, due 15 Nov, returned 18 Nov = Rs 15")
    void fineForCompleteLoan() {
        assertMoney("15", fineService.calculateFine(ISSUE_DATE, DUE_DATE, LocalDate.of(2026, 11, 18)));
    }

    @Test
    @DisplayName("Rejects a missing due date")
    void rejectsMissingDueDate() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fineService.calculateFine(null, DUE_DATE));
        assertTrue(ex.getMessage().contains("Due date"));
    }

    @Test
    @DisplayName("Rejects a missing return date")
    void rejectsMissingReturnDate() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fineService.calculateFine(DUE_DATE, null));
        assertTrue(ex.getMessage().contains("Return date"));
    }

    @Test
    @DisplayName("Rejects a return date before the issue date")
    void rejectsReturnDateBeforeIssueDate() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fineService.calculateFine(ISSUE_DATE, DUE_DATE, ISSUE_DATE.minusDays(1)));
        assertEquals("Return date cannot be before the issue date", ex.getMessage());
    }

    @Test
    @DisplayName("Rejects a due date before the issue date")
    void rejectsDueDateBeforeIssueDate() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fineService.calculateFine(ISSUE_DATE, ISSUE_DATE.minusDays(2), DUE_DATE));
        assertEquals("Due date cannot be before the issue date", ex.getMessage());
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "Expected fine of Rs " + expected + " but was Rs " + actual);
    }
}
