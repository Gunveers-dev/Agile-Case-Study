package com.example.library.service;

import com.example.library.dto.ChartData;
import com.example.library.dto.DashboardStats;
import com.example.library.model.Book;
import com.example.library.model.Loan;
import com.example.library.model.Member;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService - live statistics")
class DashboardServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    @Mock
    private BookRepository bookRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private LoanRepository loanRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        dashboardService = new DashboardService(bookRepository, memberRepository, loanRepository,
                new FineService(), clock);
    }

    @Test
    @DisplayName("Total books is the number of copies owned")
    void calculatesTotalBooks() {
        when(bookRepository.sumTotalQuantity()).thenReturn(87L);

        assertEquals(87L, dashboardService.countTotalBooks());
    }

    @Test
    @DisplayName("An empty catalogue counts as zero books")
    void emptyCatalogueCountsAsZero() {
        when(bookRepository.sumTotalQuantity()).thenReturn(null);

        assertEquals(0L, dashboardService.countTotalBooks());
    }

    @Test
    @DisplayName("Total members comes from the member table")
    void calculatesTotalMembers() {
        when(memberRepository.count()).thenReturn(12L);

        assertEquals(12L, dashboardService.countTotalMembers());
    }

    @Test
    @DisplayName("Active loans are loans without a return date")
    void calculatesActiveLoans() {
        when(loanRepository.countByReturnDateIsNull()).thenReturn(14L);

        assertEquals(14L, dashboardService.countActiveLoans());
    }

    @Test
    @DisplayName("Overdue loans are unreturned loans due before today")
    void calculatesOverdueLoans() {
        when(loanRepository.countByReturnDateIsNullAndDueDateBefore(TODAY)).thenReturn(3L);

        assertEquals(3L, dashboardService.countOverdueLoans());
    }

    @Test
    @DisplayName("Pending fines add up the fine accruing on every overdue loan")
    void calculatesPendingFines() {
        Loan threeDaysLate = loan(TODAY.minusDays(17), TODAY.minusDays(3));
        Loan tenDaysLate = loan(TODAY.minusDays(24), TODAY.minusDays(10));
        when(loanRepository.findByReturnDateIsNullAndDueDateBeforeOrderByDueDateAsc(TODAY))
                .thenReturn(List.of(tenDaysLate, threeDaysLate));

        BigDecimal pending = dashboardService.calculatePendingFines();

        assertEquals(0, new BigDecimal("65").compareTo(pending), "3 x 5 + 10 x 5 = 65");
    }

    @Test
    @DisplayName("Dashboard statistics combine all live figures")
    void buildsDashboardStats() {
        when(bookRepository.sumTotalQuantity()).thenReturn(87L);
        when(bookRepository.count()).thenReturn(20L);
        when(memberRepository.count()).thenReturn(12L);
        when(loanRepository.countByReturnDateIsNull()).thenReturn(14L);
        when(bookRepository.sumAvailableQuantity()).thenReturn(71L);
        when(loanRepository.countByReturnDateIsNullAndDueDateBefore(TODAY)).thenReturn(1L);
        when(loanRepository.findByReturnDateIsNullAndDueDateBeforeOrderByDueDateAsc(TODAY))
                .thenReturn(List.of(loan(TODAY.minusDays(20), TODAY.minusDays(6))));

        DashboardStats stats = dashboardService.getStats();

        assertEquals(87L, stats.totalBooks());
        assertEquals(20L, stats.totalTitles());
        assertEquals(12L, stats.totalMembers());
        assertEquals(14L, stats.booksIssued());
        assertEquals(71L, stats.availableBooks());
        assertEquals(1L, stats.overdueBooks());
        assertEquals(0, new BigDecimal("30").compareTo(stats.pendingFines()));
    }

    @Test
    @DisplayName("Monthly issue trend counts loans per month for the last 6 months")
    void buildsMonthlyIssueTrend() {
        when(loanRepository.findIssueDatesSince(LocalDate.of(2026, 6, 1))).thenReturn(List.of(
                LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 28),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 11, 2)));

        ChartData trend = dashboardService.getMonthlyIssueTrend(6);

        assertEquals(List.of("Jun", "Jul", "Aug", "Sep", "Oct", "Nov"), trend.labels());
        assertEquals(List.of(2L, 0L, 1L, 0L, 0L, 1L), trend.values());
    }

    private static Loan loan(LocalDate issueDate, LocalDate dueDate) {
        Member member = new Member("STU101", "Ananya Verma", "ananya@college.edu", "9876543210", "CSE");
        Book book = new Book("BK001", "Clean Code", "Robert C. Martin", "9780132350884", "Programming", "Prentice Hall", 5);
        return new Loan(member, book, issueDate, dueDate);
    }
}
