package com.example.library.integration;

import com.example.library.dto.BookRow;
import com.example.library.dto.IssueBookRequest;
import com.example.library.dto.ReturnBookResponse;
import com.example.library.dto.TransactionView;
import com.example.library.exception.BusinessException;
import com.example.library.model.Book;
import com.example.library.model.Loan;
import com.example.library.model.LoanStatus;
import com.example.library.model.Member;
import com.example.library.repository.AdminRepository;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.MemberRepository;
import com.example.library.service.BookService;
import com.example.library.service.CirculationService;
import com.example.library.service.DashboardService;
import com.example.library.service.MemberService;
import com.example.library.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests of the business workflows against a real (in-memory) H2 database.
 * Every test runs in a transaction that is rolled back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Integration - circulation workflow with H2")
class CirculationIntegrationTest {

    @Autowired private CirculationService circulationService;
    @Autowired private BookService bookService;
    @Autowired private MemberService memberService;
    @Autowired private DashboardService dashboardService;
    @Autowired private ReportService reportService;
    @Autowired private BookRepository bookRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private LoanRepository loanRepository;
    @Autowired private AdminRepository adminRepository;

    @Test
    @DisplayName("Sample data is inserted on start-up and the admin password is hashed")
    void sampleDataIsSeeded() {
        assertTrue(bookRepository.count() >= 15, "at least 15 books");
        assertTrue(memberRepository.count() >= 10, "at least 10 members");
        assertTrue(loanRepository.count() >= 5, "at least 5 loans");
        String storedPassword = adminRepository.findByEmailIgnoreCase("admin@libratrack.com").orElseThrow().getPassword();
        assertNotEquals("admin123", storedPassword);
        assertTrue(storedPassword.startsWith("$2"), "BCrypt hash expected");
    }

    @Test
    @DisplayName("Issue then late return: stock goes down and back up, fine is Rs 5 per day")
    void issueAndReturnWithFine() {
        Book book = bookService.addBook(new Book(null, "Integration Testing Patterns", "Test Author",
                "9781111111111", "Programming", "Test Press", 2));
        Member member = memberService.registerMember(new Member("ITM001", "Integration Student",
                "integration.student@college.edu", "9000000001", "CSE"));
        LocalDate today = circulationService.today();

        Loan loan = circulationService.issueBook(new IssueBookRequest(member.getId(), book.getId(),
                today.minusDays(20), today.minusDays(6)));
        assertEquals(1, bookRepository.findById(book.getId()).orElseThrow().getAvailableQuantity());
        assertEquals(LoanStatus.OVERDUE, loan.getStatus());

        ReturnBookResponse response = circulationService.returnBook(loan.getId());

        assertEquals(6, response.overdueDays());
        assertEquals(0, new BigDecimal("30").compareTo(response.fineAmount()));
        assertEquals(2, bookRepository.findById(book.getId()).orElseThrow().getAvailableQuantity());
        Loan stored = loanRepository.findById(loan.getId()).orElseThrow();
        assertEquals(LoanStatus.RETURNED, stored.getStatus());
        assertEquals(today, stored.getReturnDate());
        assertThrows(BusinessException.class, () -> circulationService.returnBook(loan.getId()));
    }

    @Test
    @DisplayName("A fourth simultaneous loan is refused")
    void borrowingLimitIsEnforced() {
        Member member = memberService.registerMember(new Member("ITM002", "Limit Tester",
                "limit.tester@college.edu", "9000000002", "IT"));
        for (int i = 1; i <= 3; i++) {
            Book book = bookService.addBook(new Book(null, "Limit Book " + i, "Author", "978222222222" + i,
                    "Mathematics", "Press", 1));
            circulationService.issueBook(new IssueBookRequest(member.getId(), book.getId(), null, null));
        }
        Book fourth = bookService.addBook(new Book(null, "Limit Book 4", "Author", "9782222222224",
                "Mathematics", "Press", 1));

        BusinessException ex = assertThrows(BusinessException.class, () -> circulationService.issueBook(
                new IssueBookRequest(member.getId(), fourth.getId(), null, null)));

        assertTrue(ex.getMessage().contains("more than 3"));
        assertEquals(3, memberService.countActiveLoans(member.getId()));
    }

    @Test
    @DisplayName("A book or member with active loans cannot be deleted")
    void deletionBlockedWhileOnLoan() {
        Book book = bookService.addBook(new Book(null, "Delete Guard", "Author", "9783333333333",
                "Programming", "Press", 1));
        Member member = memberService.registerMember(new Member("ITM003", "Delete Guard Student",
                "delete.guard@college.edu", "9000000003", "ECE"));
        circulationService.issueBook(new IssueBookRequest(member.getId(), book.getId(), null, null));

        assertThrows(BusinessException.class, () -> bookService.deleteBook(book.getId()));
        assertThrows(BusinessException.class, () -> memberService.deleteMember(member.getId()));
        assertTrue(bookRepository.existsById(book.getId()));
    }

    @Test
    @DisplayName("Search, filter and report queries run against H2")
    void queriesRunAgainstDatabase() {
        Page<BookRow> programming = bookService.findBooks("", "Programming", "", 0, 50);
        assertFalse(programming.getContent().isEmpty());
        assertTrue(programming.getContent().stream().allMatch(r -> "Programming".equals(r.book().getCategory())));

        Page<TransactionView> overdue = circulationService.searchTransactions("", "OVERDUE", null, null, "newest", 0, 50);
        assertTrue(overdue.getContent().stream().allMatch(t -> t.status() == LoanStatus.OVERDUE));
        assertEquals(dashboardService.countOverdueLoans(), overdue.getTotalElements());

        Page<TransactionView> returned = circulationService.searchTransactions("", "RETURNED", null, null, "oldest", 0, 50);
        assertTrue(returned.getContent().stream().allMatch(t -> t.loan().getReturnDate() != null));

        assertFalse(memberService.findMembers("STU101", "", 0, 10).getContent().isEmpty());
        assertEquals(6, dashboardService.getMonthlyIssueTrend(6).labels().size());
        assertFalse(dashboardService.getPopularBooks(5).isEmpty());
        assertFalse(dashboardService.getRecentActivities(6).isEmpty());
        assertEquals(12, reportService.getMonthlyFinesCollected(12).values().size());
        assertFalse(reportService.getCategoryDistribution().labels().isEmpty());
        assertTrue(reportService.getSummary().totalFinesCollected().signum() >= 0);
        circulationService.refreshOverdueStatuses();
        assertFalse(circulationService.findActiveLoans("").isEmpty());
    }
}
