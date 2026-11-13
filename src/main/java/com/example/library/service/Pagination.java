package com.example.library.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

/**
 * Splits an in-memory list into a Spring Data {@link Page}.
 * Used where rows are filtered on a computed value (e.g. book availability badge).
 */
final class Pagination {

    private Pagination() {
    }

    static <T> Page<T> paginate(List<T> rows, int page, int size) {
        int pageSize = size <= 0 ? 10 : size;
        int totalPages = Math.max(1, (int) Math.ceil(rows.size() / (double) pageSize));
        int pageIndex = Math.min(Math.max(page, 0), totalPages - 1);
        int from = Math.min(pageIndex * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return new PageImpl<>(rows.subList(from, to), PageRequest.of(pageIndex, pageSize), rows.size());
    }

    static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
