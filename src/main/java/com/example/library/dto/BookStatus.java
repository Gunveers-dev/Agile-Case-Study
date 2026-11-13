package com.example.library.dto;

/**
 * Availability badge shown for a book.
 * <ul>
 *   <li>AVAILABLE    - 3 or more copies on the shelf, or every copy on the shelf.</li>
 *   <li>LOW_STOCK    - only 1 or 2 copies left on the shelf.</li>
 *   <li>ISSUED       - no copies on the shelf because they are all out on loan.</li>
 *   <li>OUT_OF_STOCK - no copies on the shelf and none are on loan (e.g. withdrawn/damaged).</li>
 * </ul>
 */
public enum BookStatus {
    AVAILABLE("Available", "badge-available"),
    LOW_STOCK("Low Stock", "badge-low"),
    ISSUED("Issued", "badge-issued"),
    OUT_OF_STOCK("Out of Stock", "badge-out");

    /** At or below this many shelf copies a book is "Low Stock". */
    public static final int LOW_STOCK_THRESHOLD = 2;

    private final String label;
    private final String cssClass;

    BookStatus(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }

    public String getLabel() {
        return label;
    }

    public String getCssClass() {
        return cssClass;
    }

    /**
     * Works out the badge for a book.
     *
     * @param available   copies on the shelf
     * @param total       total copies owned
     * @param activeLoans copies currently on loan
     */
    public static BookStatus of(int available, int total, long activeLoans) {
        if (available <= 0) {
            return activeLoans > 0 ? ISSUED : OUT_OF_STOCK;
        }
        if (available <= LOW_STOCK_THRESHOLD && available < total) {
            return LOW_STOCK;
        }
        return AVAILABLE;
    }
}
