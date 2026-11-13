package com.example.library.dto;

import com.example.library.model.Book;

/**
 * A book as displayed in the inventory table, with its live loan count and badge.
 */
public record BookRow(Book book, long activeLoans, BookStatus status) {
}
