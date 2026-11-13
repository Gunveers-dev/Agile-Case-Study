package com.example.library.controller;

import com.example.library.dto.BookRow;
import com.example.library.dto.BookStatus;
import com.example.library.exception.BusinessException;
import com.example.library.model.Book;
import com.example.library.service.BookService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Book inventory pages: list/search/filter, add, edit, view details, delete.
 */
@Controller
@RequestMapping("/books")
public class BookController {

    private static final int PAGE_SIZE = 10;

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @InitBinder("book")
    void restrictBinding(WebDataBinder binder) {
        binder.setDisallowedFields("id", "createdAt", "loans*");
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "") String category,
                       @RequestParam(defaultValue = "") String status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("activePage", "books");
        model.addAttribute("books", bookService.findBooks(q, category, status, page, PAGE_SIZE));
        model.addAttribute("categories", bookService.getCategories());
        model.addAttribute("statuses", BookStatus.values());
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("status", status);
        return "books/list";
    }

    @GetMapping("/new")
    public String newBook(Model model) {
        Book book = new Book();
        book.setBookCode(bookService.nextBookCode());
        book.setTotalQuantity(1);
        book.setAvailableQuantity(1);
        return showForm(model, book, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("book") Book book, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return showForm(model, book, null);
        }
        try {
            Book saved = bookService.addBook(book);
            redirect.addFlashAttribute(WebSupport.TOAST_SUCCESS, "'" + saved.getTitle() + "' was added to the catalogue");
            return "redirect:/books/" + saved.getId();
        } catch (BusinessException ex) {
            WebSupport.reject(result, ex);
            return showForm(model, book, null);
        }
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model) {
        BookRow row = bookService.getBookRow(id);
        model.addAttribute("activePage", "books");
        model.addAttribute("row", row);
        model.addAttribute("book", row.book());
        model.addAttribute("history", bookService.getLoanHistory(id));
        return "books/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        return showForm(model, bookService.getBook(id), id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("book") Book book, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return showForm(model, book, id);
        }
        try {
            Book saved = bookService.updateBook(id, book);
            redirect.addFlashAttribute(WebSupport.TOAST_SUCCESS, "Changes to '" + saved.getTitle() + "' were saved");
            return "redirect:/books/" + id;
        } catch (BusinessException ex) {
            WebSupport.reject(result, ex);
            return showForm(model, book, id);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            String title = bookService.getBook(id).getTitle();
            bookService.deleteBook(id);
            redirect.addFlashAttribute(WebSupport.TOAST_SUCCESS, "'" + title + "' was deleted");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute(WebSupport.TOAST_ERROR, ex.getMessage());
        }
        return "redirect:/books";
    }

    private String showForm(Model model, Book book, Long editingId) {
        model.addAttribute("activePage", "books");
        model.addAttribute("book", book);
        model.addAttribute("editing", editingId != null);
        model.addAttribute("bookId", editingId);
        model.addAttribute("pageTitle", editingId != null ? "Edit Book" : "Add Book");
        model.addAttribute("pageSubtitle", editingId != null ? "Update the details of this title" : "Add a new title to the catalogue");
        model.addAttribute("categories", bookService.getCategories());
        if (editingId != null) {
            model.addAttribute("copiesOut", bookService.getBook(editingId).getCopiesOut());
        }
        return "books/form";
    }
}
