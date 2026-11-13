package com.example.library.dto;

import com.example.library.model.Book;

/**
 * A book together with how many times it has been issued.
 */
public record PopularBook(Book book, long issueCount) {
}
