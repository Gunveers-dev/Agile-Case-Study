package com.example.library.service;

import com.example.library.dto.ActiveLoanView;
import com.example.library.dto.IssueBookRequest;
import com.example.library.dto.ReturnBookResponse;
import com.example.library.dto.TransactionView;
import com.example.library.exception.BusinessException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.model.Book;
import com.example.library.model.Loan;
import com.example.library.model.LoanStatus;
import com.example.library.model.Member;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Circulation: issuing books, returning books (with fines) and transaction history.
 */
@Service
@Transactional
public class CirculationService {

    /** A member cannot hold more than this many books at the same time. */
    public static final int MAX_ACTIVE_LOANS = 3;

    /** Default borrowing period in days. */
    public static final int DEFAULT_LOAN_PERIOD_DAYS = 14;

    private static final Set<String> STATUS_FILTERS = Set.of("ISSUED", "OVERDUE", "RETURNED");
    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final FineService fineService;
    private final Clock clock;

    public CirculationService(LoanRepository loanRepository,
                              BookRepository bookRepository,
                              MemberRepository memberRepository,
                              FineService fineService,
                              Clock clock) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.fineService = fineService;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDate defaultDueDate(LocalDate issueDate) {
        return issueDate.plusDays(DEFAULT_LOAN_PERIOD_DAYS);
    }

    // ------------------------------------------------------------------ issue

    /**
     * Issues a book to a member.
     * <ul>
     *   <li>Only registered members can borrow.</li>
     *   <li>Only books with a copy on the shelf can be issued.</li>
     *   <li>A member can hold at most {@value #MAX_ACTIVE_LOANS} books.</li>
     *   <li>Due date defaults to {@value #DEFAULT_LOAN_PERIOD_DAYS} days after the issue date.</li>
     *   <li>The available quantity of the book goes down by one and a transaction is recorded.</li>
     * </ul>
     */
    public Loan issueBook(IssueBookRequest request) {
        if (request == null || request.getMemberId() == null) {
            throw new BusinessException("memberId", "Please select a member");
        }
        if (request.getBookId() == null) {
            throw new BusinessException("bookId", "Please select a book");
        }

        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new BusinessException("memberId",
                        "Only registered members can borrow books. No member found for the selected ID."));
        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new BusinessException("bookId", "The selected book does not exist"));

        LocalDate today = today();
        LocalDate issueDate = request.getIssueDate() != null ? request.getIssueDate() : today;
        LocalDate dueDate = request.getDueDate() != null ? request.getDueDate() : defaultDueDate(issueDate);
        if (issueDate.isAfter(today)) {
            throw new BusinessException("issueDate", "Issue date cannot be in the future");
        }
        if (!dueDate.isAfter(issueDate)) {
            throw new BusinessException("dueDate", "Due date must be after the issue date");
        }

        int available = book.getAvailableQuantity() == null ? 0 : book.getAvailableQuantity();
        if (available <= 0) {
            throw new BusinessException("bookId",
                    "'" + book.getTitle() + "' is not available right now - no copies are on the shelf");
        }

        long activeLoans = loanRepository.countByMemberIdAndReturnDateIsNull(member.getId());
        if (activeLoans >= MAX_ACTIVE_LOANS) {
            throw new BusinessException("memberId", member.getFullName() + " already has " + activeLoans
                    + " books issued. A member cannot borrow more than " + MAX_ACTIVE_LOANS + " books at a time.");
        }
        if (loanRepository.existsByMemberIdAndBookIdAndReturnDateIsNull(member.getId(), book.getId())) {
            throw new BusinessException("bookId",
                    member.getFullName() + " already has a copy of '" + book.getTitle() + "'");
        }

        book.setAvailableQuantity(available - 1);
        bookRepository.save(book);

        Loan loan = new Loan(member, book, issueDate, dueDate);
        if (dueDate.isBefore(today)) {
            loan.setStatus(LoanStatus.OVERDUE);
        }
        return loanRepository.save(loan);
    }

    // ------------------------------------------------------------------ return

    /** Returns a book today. */
    public ReturnBookResponse returnBook(Long loanId) {
        return returnBook(loanId, null);
    }

    /**
     * Returns a book: records the return date, marks the loan RETURNED, puts the copy back on the shelf
     * and calculates the overdue fine (Rs 5 per day late). A loan can only be returned once.
     */
    public ReturnBookResponse returnBook(Long loanId, LocalDate returnDate) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> ResourceNotFoundException.of("Loan", loanId));
        if (loan.getReturnDate() != null || loan.getStatus() == LoanStatus.RETURNED) {
            throw new BusinessException("This book was already returned on " + loan.getReturnDate()
                    + ". A loan cannot be returned twice.");
        }

        LocalDate today = today();
        LocalDate actualReturnDate = returnDate != null ? returnDate : today;
        if (actualReturnDate.isAfter(today)) {
            throw new BusinessException("Return date cannot be in the future");
        }

        BigDecimal fine = fineService.calculateFine(loan.getIssueDate(), loan.getDueDate(), actualReturnDate);
        long overdueDays = fineService.calculateOverdueDays(loan.getDueDate(), actualReturnDate);

        loan.setReturnDate(actualReturnDate);
        loan.setFineAmount(fine);
        loan.setStatus(LoanStatus.RETURNED);

        Book book = loan.getBook();
        int available = book.getAvailableQuantity() == null ? 0 : book.getAvailableQuantity();
        int total = book.getTotalQuantity() == null ? available + 1 : book.getTotalQuantity();
        book.setAvailableQuantity(Math.min(total, available + 1));
        bookRepository.save(book);
        loanRepository.save(loan);

        Member member = loan.getMember();
        return new ReturnBookResponse(
                loan.getId(),
                loan.getTransactionCode(),
                member.getFullName(),
                member.getMemberCode(),
                book.getTitle(),
                loan.getIssueDate(),
                loan.getDueDate(),
                actualReturnDate,
                overdueDays,
                fine);
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Loan getLoan(Long id) {
        return loanRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Loan", id));
    }

    /** Books currently out, soonest due first, with overdue days and the fine if returned today. */
    @Transactional(readOnly = true)
    public List<ActiveLoanView> findActiveLoans(String query) {
        LocalDate today = today();
        List<ActiveLoanView> views = new ArrayList<>();
        for (Loan loan : loanRepository.searchActiveLoans(Pagination.clean(query))) {
            views.add(toActiveView(loan, today));
        }
        return views;
    }

    /** Overdue loans, most overdue first. */
    @Transactional(readOnly = true)
    public List<ActiveLoanView> findOverdueLoans() {
        LocalDate today = today();
        List<ActiveLoanView> views = new ArrayList<>();
        for (Loan loan : loanRepository.findByReturnDateIsNullAndDueDateBeforeOrderByDueDateAsc(today)) {
            views.add(toActiveView(loan, today));
        }
        return views;
    }

    /**
     * Transaction history.
     *
     * @param status ISSUED, OVERDUE, RETURNED or blank for all
     * @param from   earliest issue date (inclusive), or null
     * @param to     latest issue date (inclusive), or null
     * @param sort   "oldest" for oldest first, anything else for newest first
     */
    @Transactional(readOnly = true)
    public Page<TransactionView> searchTransactions(String query, String status, LocalDate from, LocalDate to,
                                                    String sort, int page, int size) {
        String statusFilter = status == null ? "" : status.trim().toUpperCase();
        if (!STATUS_FILTERS.contains(statusFilter)) {
            statusFilter = "";
        }
        LocalDate fromDate = from != null ? from : EARLIEST;
        LocalDate toDate = to != null ? to : LATEST;
        if (fromDate.isAfter(toDate)) {
            LocalDate swap = fromDate;
            fromDate = toDate;
            toDate = swap;
        }
        Sort order = "oldest".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.ASC, "issueDate").and(Sort.by(Sort.Direction.ASC, "id"))
                : Sort.by(Sort.Direction.DESC, "issueDate").and(Sort.by(Sort.Direction.DESC, "id"));

        LocalDate today = today();
        Page<Loan> loans = loanRepository.searchTransactions(Pagination.clean(query), statusFilter,
                fromDate, toDate, today, PageRequest.of(Math.max(page, 0), size <= 0 ? 12 : size, order));
        return loans.map(loan -> toTransactionView(loan, today));
    }

    /**
     * Brings stored statuses up to date: ISSUED loans past their due date become OVERDUE.
     * Runs at start-up and every night (see OverdueStatusScheduler).
     */
    public int refreshOverdueStatuses() {
        return loanRepository.markOverdue(today(), LoanStatus.ISSUED, LoanStatus.OVERDUE);
    }

    // ------------------------------------------------------------------ helpers

    private ActiveLoanView toActiveView(Loan loan, LocalDate today) {
        long overdueDays = fineService.calculateOverdueDays(loan.getDueDate(), today);
        return new ActiveLoanView(loan, overdueDays, fineService.calculateFine(loan.getDueDate(), today));
    }

    private TransactionView toTransactionView(Loan loan, LocalDate today) {
        if (loan.getReturnDate() != null) {
            BigDecimal fine = loan.getFineAmount() == null ? BigDecimal.ZERO : loan.getFineAmount();
            return new TransactionView(loan, LoanStatus.RETURNED, fine, false);
        }
        if (loan.isOverdueOn(today)) {
            return new TransactionView(loan, LoanStatus.OVERDUE,
                    fineService.calculateFine(loan.getDueDate(), today), true);
        }
        return new TransactionView(loan, LoanStatus.ISSUED, BigDecimal.ZERO, false);
    }
}
