package com.example.library.service;

import com.example.library.dto.IssueBookRequest;
import com.example.library.dto.ReturnBookResponse;
import com.example.library.exception.BusinessException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.model.Book;
import com.example.library.model.Loan;
import com.example.library.model.LoanStatus;
import com.example.library.model.Member;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CirculationService - issuing and returning books")
class CirculationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private MemberRepository memberRepository;

    private CirculationService circulationService;

    private Member ananya;
    private Book dbmsBook;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        circulationService = new CirculationService(loanRepository, bookRepository, memberRepository,
                new FineService(), clock);

        ananya = new Member("STU101", "Ananya Verma", "ananya@college.edu", "9876543210", "CSE");
        ananya.setId(1L);
        dbmsBook = new Book("BK013", "Database System Concepts", "Abraham Silberschatz", "9780073523323",
                "Database Management", "McGraw-Hill", 5);
        dbmsBook.setId(10L);
        dbmsBook.setAvailableQuantity(2);
    }

    // ------------------------------------------------------------------ issue

    @Test
    @DisplayName("Issues an available book with the default 14-day loan period")
    void issuesAvailableBook() {
        stubMemberAndBook();
        when(loanRepository.countByMemberIdAndReturnDateIsNull(1L)).thenReturn(1L);
        when(loanRepository.existsByMemberIdAndBookIdAndReturnDateIsNull(1L, 10L)).thenReturn(false);
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Loan loan = circulationService.issueBook(new IssueBookRequest(1L, 10L, null, null));

        assertSame(ananya, loan.getMember());
        assertSame(dbmsBook, loan.getBook());
        assertEquals(TODAY, loan.getIssueDate());
        assertEquals(TODAY.plusDays(14), loan.getDueDate());
        assertEquals(LoanStatus.ISSUED, loan.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(loan.getFineAmount()));
    }

    @Test
    @DisplayName("Reduces the available quantity after issuing and records a transaction")
    void reducesAvailableQuantityAfterIssue() {
        stubMemberAndBook();
        when(loanRepository.countByMemberIdAndReturnDateIsNull(1L)).thenReturn(0L);
        when(loanRepository.existsByMemberIdAndBookIdAndReturnDateIsNull(1L, 10L)).thenReturn(false);
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        circulationService.issueBook(new IssueBookRequest(1L, 10L, TODAY, TODAY.plusDays(14)));

        assertEquals(1, dbmsBook.getAvailableQuantity());
        verify(bookRepository).save(dbmsBook);
        ArgumentCaptor<Loan> saved = ArgumentCaptor.forClass(Loan.class);
        verify(loanRepository).save(saved.capture());
        assertEquals("Database System Concepts", saved.getValue().getBook().getTitle());
    }

    @Test
    @DisplayName("Rejects issuing a book with no copies on the shelf")
    void rejectsIssuingUnavailableBook() {
        dbmsBook.setAvailableQuantity(0);
        stubMemberAndBook();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> circulationService.issueBook(new IssueBookRequest(1L, 10L, TODAY, TODAY.plusDays(14))));

        assertTrue(ex.getMessage().contains("not available"));
        assertEquals(0, dbmsBook.getAvailableQuantity());
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    @DisplayName("Rejects issuing to a member who is not registered")
    void rejectsUnregisteredMember() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> circulationService.issueBook(new IssueBookRequest(99L, 10L, TODAY, TODAY.plusDays(14))));

        assertTrue(ex.getMessage().contains("Only registered members can borrow"));
        verifyNoInteractions(bookRepository, loanRepository);
    }

    @Test
    @DisplayName("Rejects issuing when the member already has 3 books")
    void rejectsWhenMemberHasThreeActiveLoans() {
        stubMemberAndBook();
        when(loanRepository.countByMemberIdAndReturnDateIsNull(1L)).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> circulationService.issueBook(new IssueBookRequest(1L, 10L, TODAY, TODAY.plusDays(14))));

        assertTrue(ex.getMessage().contains("cannot borrow more than 3 books"));
        assertEquals(2, dbmsBook.getAvailableQuantity());
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    @DisplayName("Rejects a due date that is not after the issue date")
    void rejectsDueDateNotAfterIssueDate() {
        stubMemberAndBook();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> circulationService.issueBook(new IssueBookRequest(1L, 10L, TODAY, TODAY)));

        assertEquals("dueDate", ex.getField());
        verify(loanRepository, never()).save(any(Loan.class));
    }

    // ------------------------------------------------------------------ return

    @Test
    @DisplayName("Returns a book on time: status RETURNED, return date saved, no fine")
    void returnsBookSuccessfully() {
        Loan loan = loan(5L, TODAY.minusDays(10), TODAY.plusDays(4));
        dbmsBook.setAvailableQuantity(1);
        when(loanRepository.findById(5L)).thenReturn(Optional.of(loan));

        ReturnBookResponse response = circulationService.returnBook(5L);

        assertEquals(LoanStatus.RETURNED, loan.getStatus());
        assertEquals(TODAY, loan.getReturnDate());
        assertEquals(0, response.overdueDays());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.fineAmount()));
        verify(loanRepository).save(loan);
    }

    @Test
    @DisplayName("Increases the available quantity after a return")
    void increasesAvailableQuantityAfterReturn() {
        Loan loan = loan(5L, TODAY.minusDays(3), TODAY.plusDays(11));
        dbmsBook.setAvailableQuantity(1);
        when(loanRepository.findById(5L)).thenReturn(Optional.of(loan));

        circulationService.returnBook(5L);

        assertEquals(2, dbmsBook.getAvailableQuantity());
        verify(bookRepository).save(dbmsBook);
    }

    @Test
    @DisplayName("Calculates the overdue fine on return: 6 days late = Rs 30")
    void calculatesOverdueFineOnReturn() {
        Loan loan = loan(5L, TODAY.minusDays(20), TODAY.minusDays(6));
        when(loanRepository.findById(5L)).thenReturn(Optional.of(loan));

        ReturnBookResponse response = circulationService.returnBook(5L);

        assertEquals(6, response.overdueDays());
        assertEquals(0, new BigDecimal("30").compareTo(response.fineAmount()));
        assertEquals(0, new BigDecimal("30").compareTo(loan.getFineAmount()));
        assertTrue(response.hasFine());
    }

    @Test
    @DisplayName("Prevents returning a book that was already returned")
    void preventsDuplicateReturn() {
        Loan loan = loan(5L, TODAY.minusDays(10), TODAY.plusDays(4));
        loan.setReturnDate(TODAY.minusDays(1));
        loan.setStatus(LoanStatus.RETURNED);
        dbmsBook.setAvailableQuantity(3);
        when(loanRepository.findById(5L)).thenReturn(Optional.of(loan));

        BusinessException ex = assertThrows(BusinessException.class, () -> circulationService.returnBook(5L));

        assertTrue(ex.getMessage().contains("already returned"));
        assertEquals(3, dbmsBook.getAvailableQuantity());
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    @DisplayName("Reports an unknown loan as not found")
    void throwsWhenLoanDoesNotExist() {
        when(loanRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> circulationService.returnBook(404L));
    }

    // ------------------------------------------------------------------ helpers

    private void stubMemberAndBook() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(ananya));
        when(bookRepository.findById(10L)).thenReturn(Optional.of(dbmsBook));
    }

    private Loan loan(Long id, LocalDate issueDate, LocalDate dueDate) {
        Loan loan = new Loan(ananya, dbmsBook, issueDate, dueDate);
        loan.setId(id);
        return loan;
    }
}
