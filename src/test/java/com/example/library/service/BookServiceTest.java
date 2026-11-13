package com.example.library.service;

import com.example.library.dto.BookRow;
import com.example.library.dto.BookStatus;
import com.example.library.exception.BusinessException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.model.Book;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookService - catalogue rules")
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private LoanRepository loanRepository;

    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookService(bookRepository, loanRepository);
    }

    @Test
    @DisplayName("Adds a new book: generates the book ID, cleans the ISBN and puts every copy on the shelf")
    void addsNewBook() {
        Book input = new Book("", "Clean Code", "Robert C. Martin", "978-0-13-235088-4",
                "Programming", "Prentice Hall", 5);
        input.setAvailableQuantity(null);
        when(bookRepository.existsByIsbn("9780132350884")).thenReturn(false);
        when(bookRepository.count()).thenReturn(20L);
        when(bookRepository.existsByBookCodeIgnoreCase("BK021")).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            book.setId(21L);
            return book;
        });

        Book saved = bookService.addBook(input);

        assertEquals(21L, saved.getId());
        assertEquals("BK021", saved.getBookCode());
        assertEquals("9780132350884", saved.getIsbn());
        assertEquals(5, saved.getTotalQuantity());
        assertEquals(5, saved.getAvailableQuantity());
        verify(bookRepository).save(input);
    }

    @Test
    @DisplayName("Rejects a book whose ISBN already exists")
    void rejectsDuplicateIsbn() {
        Book input = book(null, "BK050", "Clean Code (copy)", "9780132350884", 2, 2);
        when(bookRepository.existsByIsbn("9780132350884")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> bookService.addBook(input));

        assertEquals("isbn", ex.getField());
        assertTrue(ex.getMessage().contains("already exists"));
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    @DisplayName("Rejects an empty title")
    void rejectsEmptyTitle() {
        Book input = book(null, "BK050", "   ", "9780132350884", 2, 2);

        BusinessException ex = assertThrows(BusinessException.class, () -> bookService.addBook(input));

        assertEquals("title", ex.getField());
        assertEquals("Title cannot be empty", ex.getMessage());
        verifyNoInteractions(bookRepository);
    }

    @Test
    @DisplayName("Rejects a quantity that is not positive")
    void rejectsNonPositiveQuantity() {
        Book input = book(null, "BK050", "Some Title", "9780132350884", 0, 0);

        BusinessException ex = assertThrows(BusinessException.class, () -> bookService.addBook(input));

        assertEquals("Quantity must be positive", ex.getMessage());
        verifyNoInteractions(bookRepository);
    }

    @Test
    @DisplayName("Rejects available copies greater than the total quantity")
    void rejectsAvailableAboveTotal() {
        Book input = book(null, "BK050", "Some Title", "9780132350884", 3, 5);

        BusinessException ex = assertThrows(BusinessException.class, () -> bookService.addBook(input));

        assertEquals("Available copies cannot exceed total quantity", ex.getMessage());
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    @DisplayName("Searches books by title")
    void searchesBookByTitle() {
        Book cleanCode = book(1L, "BK001", "Clean Code", "9780132350884", 8, 5);
        when(bookRepository.findByTitleContainingIgnoreCase("clean")).thenReturn(List.of(cleanCode));

        List<Book> results = bookService.searchByTitle("  clean ");

        assertEquals(1, results.size());
        assertEquals("Clean Code", results.get(0).getTitle());
    }

    @Test
    @DisplayName("Filters the inventory by availability badge")
    void filtersBooksByAvailability() {
        Book plenty = book(1L, "BK001", "Clean Code", "9780132350884", 8, 5);
        Book low = book(2L, "BK002", "Algorithms", "9780321573513", 3, 1);
        Book allIssued = book(3L, "BK003", "AI: A Modern Approach", "9780136042594", 3, 0);
        when(bookRepository.search("", "")).thenReturn(List.of(plenty, low, allIssued));
        List<Object[]> activeLoans = new ArrayList<>();
        activeLoans.add(new Object[]{1L, 3L});
        activeLoans.add(new Object[]{2L, 2L});
        activeLoans.add(new Object[]{3L, 3L});
        when(loanRepository.countActiveLoansPerBook()).thenReturn(activeLoans);

        Page<BookRow> lowStock = bookService.findBooks("", "", "LOW_STOCK", 0, 10);

        assertEquals(1, lowStock.getTotalElements());
        assertEquals("Algorithms", lowStock.getContent().get(0).book().getTitle());
        assertEquals(BookStatus.LOW_STOCK, lowStock.getContent().get(0).status());
    }

    @Test
    @DisplayName("Works out the correct badge for each stock level")
    void computesBookStatusBadges() {
        assertEquals(BookStatus.AVAILABLE, BookStatus.of(5, 8, 3));
        assertEquals(BookStatus.AVAILABLE, BookStatus.of(2, 2, 0));
        assertEquals(BookStatus.LOW_STOCK, BookStatus.of(2, 4, 2));
        assertEquals(BookStatus.ISSUED, BookStatus.of(0, 3, 3));
        assertEquals(BookStatus.OUT_OF_STOCK, BookStatus.of(0, 2, 0));
    }

    @Test
    @DisplayName("Updates book details and adjusts shelf copies by the change in quantity")
    void updatesBookDetails() {
        Book existing = book(1L, "BK001", "Clean Code", "9780132350884", 5, 3);
        Book changes = book(null, "BK001", "Clean Code (2nd Edition)", "9780132350884", 8, null);
        changes.setPublisher("Pearson");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookRepository.existsByIsbnAndIdNot("9780132350884", 1L)).thenReturn(false);
        when(bookRepository.existsByBookCodeIgnoreCaseAndIdNot("BK001", 1L)).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book updated = bookService.updateBook(1L, changes);

        assertSame(existing, updated);
        assertEquals("Clean Code (2nd Edition)", updated.getTitle());
        assertEquals("Pearson", updated.getPublisher());
        assertEquals(8, updated.getTotalQuantity());
        assertEquals(6, updated.getAvailableQuantity(), "2 copies were out, so 8 - 2 = 6 stay on the shelf");
    }

    @Test
    @DisplayName("Refuses to lower the total quantity below the copies currently out")
    void rejectsTotalBelowCopiesOut() {
        Book existing = book(1L, "BK001", "Clean Code", "9780132350884", 5, 1);
        Book changes = book(null, "BK001", "Clean Code", "9780132350884", 3, null);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookRepository.existsByIsbnAndIdNot("9780132350884", 1L)).thenReturn(false);
        when(bookRepository.existsByBookCodeIgnoreCaseAndIdNot("BK001", 1L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> bookService.updateBook(1L, changes));

        assertEquals("totalQuantity", ex.getField());
        assertEquals(5, existing.getTotalQuantity());
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    @DisplayName("Deletes a book that has no active loans")
    void deletesBookWithoutActiveLoans() {
        Book existing = book(1L, "BK001", "Clean Code", "9780132350884", 5, 5);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(loanRepository.countByBookIdAndReturnDateIsNull(1L)).thenReturn(0L);

        bookService.deleteBook(1L);

        verify(loanRepository).deleteHistoryForBook(1L);
        verify(bookRepository).delete(existing);
    }

    @Test
    @DisplayName("Prevents deleting a book that still has copies on loan")
    void preventsDeletionOfBookWithActiveLoans() {
        Book existing = book(1L, "BK001", "Clean Code", "9780132350884", 5, 3);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(loanRepository.countByBookIdAndReturnDateIsNull(1L)).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> bookService.deleteBook(1L));

        assertTrue(ex.getMessage().contains("still on loan"));
        verify(bookRepository, never()).delete(any(Book.class));
        verify(loanRepository, never()).deleteHistoryForBook(anyLong());
    }

    @Test
    @DisplayName("Reports a missing book as not found")
    void throwsWhenBookDoesNotExist() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookService.getBook(99L));
    }

    private static Book book(Long id, String code, String title, String isbn, int total, Integer available) {
        Book book = new Book(code, title, "Test Author", isbn, "Programming", "Test Publisher", total);
        book.setId(id);
        book.setAvailableQuantity(available);
        return book;
    }
}
