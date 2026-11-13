package com.example.library.controller;

import com.example.library.service.CirculationService;
import com.example.library.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * Transaction history with search, status/date filters and sorting.
 */
@Controller
public class TransactionController {

    private static final int PAGE_SIZE = 12;

    private final CirculationService circulationService;
    private final ReportService reportService;

    public TransactionController(CirculationService circulationService, ReportService reportService) {
        this.circulationService = circulationService;
        this.reportService = reportService;
    }

    @GetMapping("/transactions")
    public String history(@RequestParam(defaultValue = "") String q,
                          @RequestParam(defaultValue = "") String status,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(defaultValue = "newest") String sort,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        model.addAttribute("activePage", "transactions");
        model.addAttribute("transactions",
                circulationService.searchTransactions(q, status, from, to, sort, page, PAGE_SIZE));
        model.addAttribute("summary", reportService.getSummary());
        model.addAttribute("q", q);
        model.addAttribute("status", status.toUpperCase());
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("sort", "oldest".equalsIgnoreCase(sort) ? "oldest" : "newest");
        return "transactions/list";
    }
}
