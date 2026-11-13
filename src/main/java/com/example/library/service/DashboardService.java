package com.example.library.service;

import com.example.library.dto.ActivityItem;
import com.example.library.dto.ChartData;
import com.example.library.dto.DashboardStats;
import com.example.library.dto.PopularBook;
import com.example.library.model.Book;
import com.example.library.model.Loan;
import com.example.library.model.Member;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.MemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Live figures for the dashboard. Nothing here is hard-coded: every number is read from the database.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final FineService fineService;
    private final Clock clock;

    public DashboardService(BookRepository bookRepository,
                            MemberRepository memberRepository,
                            LoanRepository loanRepository,
                            FineService fineService,
                            Clock clock) {
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.fineService = fineService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ statistic cards

    /** Total physical copies owned by the library. */
    public long countTotalBooks() {
        return nullToZero(bookRepository.sumTotalQuantity());
    }

    /** Copies currently on the shelf. */
    public long countAvailableBooks() {
        return nullToZero(bookRepository.sumAvailableQuantity());
    }

    public long countTotalMembers() {
        return memberRepository.count();
    }

    /** Loans not yet returned (books issued). */
    public long countActiveLoans() {
        return loanRepository.countByReturnDateIsNull();
    }

    /** Loans not returned whose due date has passed. */
    public long countOverdueLoans() {
        return loanRepository.countByReturnDateIsNullAndDueDateBefore(LocalDate.now(clock));
    }

    /** Fines accruing on overdue books, as if they were all returned today. */
    public BigDecimal calculatePendingFines() {
        LocalDate today = LocalDate.now(clock);
        BigDecimal total = BigDecimal.ZERO;
        for (Loan loan : loanRepository.findByReturnDateIsNullAndDueDateBeforeOrderByDueDateAsc(today)) {
            total = total.add(fineService.calculateFine(loan.getDueDate(), today));
        }
        return total;
    }

    public DashboardStats getStats() {
        return new DashboardStats(
                countTotalBooks(),
                bookRepository.count(),
                countTotalMembers(),
                countActiveLoans(),
                countAvailableBooks(),
                countOverdueLoans(),
                calculatePendingFines());
    }

    // ------------------------------------------------------------------ charts & feeds

    /** Number of books issued per month for the last {@code months} months (oldest first). */
    public ChartData getMonthlyIssueTrend(int months) {
        int span = Math.max(1, months);
        YearMonth current = YearMonth.from(LocalDate.now(clock));
        YearMonth first = current.minusMonths(span - 1L);

        Map<YearMonth, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < span; i++) {
            counts.put(first.plusMonths(i), 0L);
        }
        for (LocalDate issueDate : loanRepository.findIssueDatesSince(first.atDay(1))) {
            YearMonth month = YearMonth.from(issueDate);
            if (counts.containsKey(month)) {
                counts.put(month, counts.get(month) + 1);
            }
        }

        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        counts.forEach((month, count) -> {
            labels.add(month.format(MONTH_LABEL));
            values.add(count);
        });
        return new ChartData(labels, values);
    }

    /** Most frequently issued books, most popular first. */
    public List<PopularBook> getPopularBooks(int limit) {
        List<Object[]> rows = loanRepository.countLoansPerBook(PageRequest.of(0, Math.max(1, limit)));
        List<Long> ids = new ArrayList<>();
        for (Object[] row : rows) {
            ids.add((Long) row[0]);
        }
        Map<Long, Book> booksById = new HashMap<>();
        for (Book book : bookRepository.findAllById(ids)) {
            booksById.put(book.getId(), book);
        }
        List<PopularBook> popular = new ArrayList<>();
        for (Object[] row : rows) {
            Book book = booksById.get((Long) row[0]);
            if (book != null) {
                popular.add(new PopularBook(book, ((Number) row[1]).longValue()));
            }
        }
        return popular;
    }

    /** Latest issues, returns and member registrations, newest first. */
    public List<ActivityItem> getRecentActivities(int limit) {
        List<ActivityItem> items = new ArrayList<>();
        for (Loan loan : loanRepository.findTop5ByOrderByIssueDateDescIdDesc()) {
            items.add(new ActivityItem("ISSUED", "Book Issued",
                    "'" + loan.getBook().getTitle() + "' to " + loan.getMember().getFullName(),
                    loan.getIssueDate(), "/members/" + loan.getMember().getId()));
        }
        for (Loan loan : loanRepository.findTop5ByReturnDateIsNotNullOrderByReturnDateDescIdDesc()) {
            items.add(new ActivityItem("RETURNED", "Book Returned",
                    "'" + loan.getBook().getTitle() + "' by " + loan.getMember().getFullName(),
                    loan.getReturnDate(), "/members/" + loan.getMember().getId()));
        }
        for (Member member : memberRepository.findTop5ByOrderByRegistrationDateDescIdDesc()) {
            items.add(new ActivityItem("MEMBER", "New Member Added",
                    member.getFullName() + " (" + member.getDepartment() + ")",
                    member.getRegistrationDate(), "/members/" + member.getId()));
        }
        items.sort(Comparator.comparing(ActivityItem::date, Comparator.nullsLast(Comparator.reverseOrder())));
        return items.size() > limit ? new ArrayList<>(items.subList(0, limit)) : items;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
