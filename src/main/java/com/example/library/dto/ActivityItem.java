package com.example.library.dto;

import java.time.LocalDate;

/**
 * One line of the dashboard "Recent Activities" feed.
 *
 * @param type   ISSUED, RETURNED or MEMBER
 * @param title  e.g. "Book Issued"
 * @param detail e.g. "'Clean Code' to Rohit Sharma"
 * @param date   when it happened
 * @param link   page to open when clicked
 */
public record ActivityItem(String type, String title, String detail, LocalDate date, String link) {
}
