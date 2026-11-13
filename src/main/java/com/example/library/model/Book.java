package com.example.library.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A book title held by the library. One title can have several physical copies
 * (totalQuantity); availableQuantity is the number currently on the shelf.
 */
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-friendly book ID shown in the UI, e.g. BK001. */
    @Size(max = 20, message = "Book ID must be at most 20 characters")
    @Column(name = "book_code", nullable = false, unique = true, length = 20)
    private String bookCode;

    @NotBlank(message = "Title cannot be empty")
    @Size(max = 200, message = "Title must be at most 200 characters")
    @Column(nullable = false, length = 200)
    private String title;

    @NotBlank(message = "Author is required")
    @Size(max = 150, message = "Author must be at most 150 characters")
    @Column(nullable = false, length = 150)
    private String author;

    @NotBlank(message = "ISBN is required")
    @Size(max = 20, message = "ISBN must be at most 20 characters")
    @Column(nullable = false, unique = true, length = 20)
    private String isbn;

    @NotBlank(message = "Category is required")
    @Size(max = 60)
    @Column(nullable = false, length = 60)
    private String category;

    @Size(max = 120, message = "Publisher must be at most 120 characters")
    @Column(length = 120)
    private String publisher;

    @NotNull(message = "Total quantity is required")
    @Min(value = 1, message = "Quantity must be positive")
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Min(value = 0, message = "Available copies cannot be negative")
    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** All loans (past and present) of this book. Not used for cascading. */
    @OneToMany(mappedBy = "book")
    private List<Loan> loans = new ArrayList<>();

    public Book() {
    }

    public Book(String bookCode, String title, String author, String isbn, String category,
                String publisher, int totalQuantity) {
        this.bookCode = bookCode;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.category = category;
        this.publisher = publisher;
        this.totalQuantity = totalQuantity;
        this.availableQuantity = totalQuantity;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** Number of copies currently not on the shelf. */
    public int getCopiesOut() {
        int total = totalQuantity == null ? 0 : totalQuantity;
        int available = availableQuantity == null ? 0 : availableQuantity;
        return Math.max(0, total - available);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBookCode() {
        return bookCode;
    }

    public void setBookCode(String bookCode) {
        this.bookCode = bookCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<Loan> getLoans() {
        return loans;
    }

    public void setLoans(List<Loan> loans) {
        this.loans = loans;
    }

    @Override
    public String toString() {
        return "Book{" + bookCode + " '" + title + "'}";
    }
}
