package com.example.library.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One circulation transaction: a book issued to a member, and (later) its return.
 * Each loan belongs to exactly one member and one book.
 */
@Entity
@Table(name = "loans", indexes = {
        @Index(name = "idx_loan_return_date", columnList = "return_date"),
        @Index(name = "idx_loan_due_date", columnList = "due_date")
})
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Column(name = "fine_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal fineAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status = LoanStatus.ISSUED;

    public Loan() {
    }

    public Loan(Member member, Book book, LocalDate issueDate, LocalDate dueDate) {
        this.member = member;
        this.book = book;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.status = LoanStatus.ISSUED;
        this.fineAmount = BigDecimal.ZERO;
    }

    /** True while the book has not been returned. */
    @Transient
    public boolean isActive() {
        return returnDate == null;
    }

    /** True if the book is still out and the due date is before the given day. */
    public boolean isOverdueOn(LocalDate today) {
        return returnDate == null && dueDate != null && dueDate.isBefore(today);
    }

    /**
     * Status as of today, independent of when the stored status was last refreshed.
     * Used by the views so the badge is always correct.
     */
    @Transient
    public LoanStatus getCurrentStatus() {
        if (returnDate != null) {
            return LoanStatus.RETURNED;
        }
        return isOverdueOn(LocalDate.now()) ? LoanStatus.OVERDUE : LoanStatus.ISSUED;
    }

    /** Transaction ID shown in the UI, e.g. TXN-0007. */
    @Transient
    public String getTransactionCode() {
        return id == null ? "TXN-NEW" : String.format("TXN-%04d", id);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public BigDecimal getFineAmount() {
        return fineAmount;
    }

    public void setFineAmount(BigDecimal fineAmount) {
        this.fineAmount = fineAmount;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Loan{" + id + ", status=" + status + "}";
    }
}
