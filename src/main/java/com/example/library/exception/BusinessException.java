package com.example.library.exception;

/**
 * Thrown when an operation breaks a library business rule
 * (duplicate ISBN, borrowing limit reached, book not available, ...).
 * The message is safe to show to the administrator.
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Optional form field the error relates to (e.g. "isbn"), so the form can highlight it. */
    private final String field;

    public BusinessException(String message) {
        this(null, message);
    }

    public BusinessException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
