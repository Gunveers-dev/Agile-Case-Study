package com.example.library.service;

import com.example.library.dto.ChartData;
import com.example.library.dto.ReportSummary;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Figures for the Reports &amp; Analytics page.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final DashboardService dashboardService;
    private final Clock clock;

    public ReportService(BookRepository bookRepository,
                         LoanRepository loanRepository,
                         DashboardService dashboardService,
                         Clock clock) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.dashboardService = dashboardService;
        this.clock = clock;
    }

    public ReportSummary getSummary() {
        BigDecimal collected = loanRepository.sumCollectedFines();
        return new ReportSummary(
                dashboardService.countTotalBooks(),
                bookRepository.count(),
                dashboardService.countTotalMembers(),
                loanRepository.count(),
                dashboardService.countActiveLoans(),
                loanRepository.countByReturnDateIsNotNull(),
                dashboardService.countOverdueLoans(),
                collected == null ? BigDecimal.ZERO : collected,
                dashboardService.calculatePendingFines());
    }

    /** Number of titles in each category. */
    public ChartData getCategoryDistribution() {
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        for (Object[] row : bookRepository.countTitlesByCategory()) {
            labels.add(String.valueOf(row[0]));
            values.add(((Number) row[1]).longValue());
        }
        return new ChartData(labels, values);
    }

    /** Fines collected per month (by return date) for the last {@code months} months. */
    public ChartData getMonthlyFinesCollected(int months) {
        int span = Math.max(1, months);
        YearMonth current = YearMonth.from(LocalDate.now(clock));
        YearMonth first = current.minusMonths(span - 1L);

        Map<YearMonth, BigDecimal> totals = new LinkedHashMap<>();
        for (int i = 0; i < span; i++) {
            totals.put(first.plusMonths(i), BigDecimal.ZERO);
        }
        for (Object[] row : loanRepository.findFinesCollectedSince(first.atDay(1))) {
            YearMonth month = YearMonth.from((LocalDate) row[0]);
            if (totals.containsKey(month)) {
                totals.put(month, totals.get(month).add((BigDecimal) row[1]));
            }
        }
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        totals.forEach((month, amount) -> {
            labels.add(month.format(MONTH_LABEL));
            values.add(amount);
        });
        return new ChartData(labels, values);
    }
}
