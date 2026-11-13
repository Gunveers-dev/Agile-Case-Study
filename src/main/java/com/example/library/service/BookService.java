package com.example.library.service;

import com.example.library.dto.BookRow;
import com.example.library.dto.BookStatus;
import com.example.library.exception.BusinessException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.model.Book;
import com.example.library.model.Loan;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Book inventory: add, edit, delete, search and availability status.
 */
@Service
@Transactional(readOnly = true)
public class BookService {

    public static final List<String> DEFAULT_CATEGORIES = List.of(
            "Programming",
            "AI & Machine Learning",
            "Data Structures",
            "Database Management",
            "Mathematics",
            "Computer Networks");

    /** ISBN-10 (last character may be X) or ISBN-13, after removing hyphens and spaces. */
    private static final Pattern ISBN_PATTERN = Pattern.compile("^(\\d{9}[\\dX]|\\d{13})$");

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;

    public BookService(BookRepository bookRepository, LoanRepository loanRepository) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
    }

    // ------------------------------------------------------------------ queries

    /**
     * Inventory table: search text, category and availability filters, paginated.
     *
     * @param status one of AVAILABLE, LOW_STOCK, ISSUED, OUT_OF_STOCK, or blank for all
     */
    public Page<BookRow> findBooks(String query, String category, String status, int page, int size) {
        List<Book> books = bookRepository.search(Pagination.clean(query), Pagination.clean(category));
        Map<Long, Long> activeLoans = activeLoansPerBook();
        BookStatus statusFilter = parseStatus(status);

        List<BookRow> rows = new ArrayList<>();
        for (Book book : books) {
            BookRow row = toRow(book, activeLoans.getOrDefault(book.getId(), 0L));
            if (statusFilter == null || row.status() == statusFilter) {
                rows.add(row);
            }
        }
        return Pagination.paginate(rows, page, size);
    }

    public List<Book> searchByTitle(String title) {
        return bookRepository.findByTitleContainingIgnoreCase(Pagination.clean(title));
    }

    public Book getBook(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Book", id));
    }

    public BookRow getBookRow(Long id) {
        Book book = getBook(id);
        return toRow(book, loanRepository.countByBookIdAndReturnDateIsNull(id));
    }

    /** Books with at least one copy on the shelf (for the Issue Book dropdown). */
    public List<Book> findAvailableBooks() {
        return bookRepository.findByAvailableQuantityGreaterThanOrderByTitleAsc(0);
    }

    public List<Loan> getLoanHistory(Long bookId) {
        return loanRepository.findByBookIdOrderByIssueDateDescIdDesc(bookId);
    }

    /** Standard categories plus any custom ones already used in the catalogue. */
    public List<String> getCategories() {
        Set<String> categories = new LinkedHashSet<>(DEFAULT_CATEGORIES);
        categories.addAll(bookRepository.findDistinctCategories());
        return new ArrayList<>(categories);
    }

    /** Suggests the next free book ID, e.g. BK019. */
    public String nextBookCode() {
        long number = bookRepository.count() + 1;
        String code = String.format("BK%03d", number);
        while (bookRepository.existsByBookCodeIgnoreCase(code)) {
            number++;
            code = String.format("BK%03d", number);
        }
        return code;
    }

    // ------------------------------------------------------------------ commands

    /**
     * Adds a new title to the catalogue.
     * Rules: title/author/category required, valid and unique ISBN, quantity &gt; 0,
     * available copies between 0 and total quantity (defaults to total quantity).
     */
    @Transactional
    public Book addBook(Book book) {
        if (book == null) {
            throw new BusinessException("Book details are required");
        }
        normalise(book);
        validateCommonFields(book);

        if (book.getAvailableQuantity() == null) {
            book.setAvailableQuantity(book.getTotalQuantity());
        }
        if (book.getAvailableQuantity() < 0) {
            throw new BusinessException("availableQuantity", "Available copies cannot be negative");
        }
        if (book.getAvailableQuantity() > book.getTotalQuantity()) {
            throw new BusinessException("availableQuantity", "Available copies cannot exceed total quantity");
        }
        if (bookRepository.existsByIsbn(book.getIsbn())) {
            throw new BusinessException("isbn", "A book with ISBN " + book.getIsbn() + " already exists");
        }
        if (book.getBookCode() == null || book.getBookCode().isBlank()) {
            book.setBookCode(nextBookCode());
        } else if (bookRepository.existsByBookCodeIgnoreCase(book.getBookCode())) {
            throw new BusinessException("bookCode", "Book ID " + book.getBookCode() + " is already in use");
        }

        book.setId(null);
        return bookRepository.save(book);
    }

    /**
     * Updates a book's details. Shelf copies are adjusted by the change in total quantity,
     * and the total cannot drop below the number of copies currently out.
     */
    @Transactional
    public Book updateBook(Long id, Book changes) {
        Book existing = getBook(id);
        if (changes == null) {
            throw new BusinessException("Book details are required");
        }
        normalise(changes);
        validateCommonFields(changes);

        if (bookRepository.existsByIsbnAndIdNot(changes.getIsbn(), id)) {
            throw new BusinessException("isbn", "Another book already uses ISBN " + changes.getIsbn());
        }
        String code = changes.getBookCode();
        if (code != null && !code.isBlank()) {
            if (bookRepository.existsByBookCodeIgnoreCaseAndIdNot(code, id)) {
                throw new BusinessException("bookCode", "Book ID " + code + " is already in use");
            }
            existing.setBookCode(code);
        }

        int copiesOut = existing.getCopiesOut();
        int newAvailable = changes.getTotalQuantity() - copiesOut;
        if (newAvailable < 0) {
            throw new BusinessException("totalQuantity",
                    "Total quantity cannot be less than the " + copiesOut + " copies currently issued");
        }

        existing.setTitle(changes.getTitle());
        existing.setAuthor(changes.getAuthor());
        existing.setIsbn(changes.getIsbn());
        existing.setCategory(changes.getCategory());
        existing.setPublisher(changes.getPublisher());
        existing.setTotalQuantity(changes.getTotalQuantity());
        existing.setAvailableQuantity(newAvailable);
        return bookRepository.save(existing);
    }

    /**
     * Deletes a book. Refused while any copy is on loan; returned-loan history of the book is removed with it.
     */
    @Transactional
    public void deleteBook(Long id) {
        Book book = getBook(id);
        long activeLoans = loanRepository.countByBookIdAndReturnDateIsNull(id);
        if (activeLoans > 0) {
            throw new BusinessException("Cannot delete '" + book.getTitle() + "': " + activeLoans
                    + (activeLoans == 1 ? " copy is" : " copies are") + " still on loan. Return them first.");
        }
        loanRepository.deleteHistoryForBook(id);
        bookRepository.delete(book);
    }

    // ------------------------------------------------------------------ helpers

    public static BookRow toRow(Book book, long activeLoans) {
        int available = book.getAvailableQuantity() == null ? 0 : book.getAvailableQuantity();
        int total = book.getTotalQuantity() == null ? 0 : book.getTotalQuantity();
        return new BookRow(book, activeLoans, BookStatus.of(available, total, activeLoans));
    }

    private Map<Long, Long> activeLoansPerBook() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : loanRepository.countActiveLoansPerBook()) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static BookStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BookStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static void normalise(Book book) {
        book.setTitle(trim(book.getTitle()));
        book.setAuthor(trim(book.getAuthor()));
        book.setCategory(trim(book.getCategory()));
        book.setPublisher(trim(book.getPublisher()));
        String code = trim(book.getBookCode());
        book.setBookCode(code == null ? null : code.toUpperCase());
        String isbn = book.getIsbn();
        book.setIsbn(isbn == null ? null : isbn.replaceAll("[\\s-]", "").toUpperCase());
    }

    private static void validateCommonFields(Book book) {
        if (isBlank(book.getTitle())) {
            throw new BusinessException("title", "Title cannot be empty");
        }
        if (isBlank(book.getAuthor())) {
            throw new BusinessException("author", "Author is required");
        }
        if (isBlank(book.getIsbn())) {
            throw new BusinessException("isbn", "ISBN is required");
        }
        if (!ISBN_PATTERN.matcher(book.getIsbn()).matches()) {
            throw new BusinessException("isbn", "ISBN must have 10 or 13 digits");
        }
        if (isBlank(book.getCategory())) {
            throw new BusinessException("category", "Category is required");
        }
        if (book.getTotalQuantity() == null || book.getTotalQuantity() <= 0) {
            throw new BusinessException("totalQuantity", "Quantity must be positive");
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
