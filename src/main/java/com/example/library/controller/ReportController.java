package com.example.library.controller;

import com.example.library.dto.ChartData;
import com.example.library.dto.PopularBook;
import com.example.library.service.CirculationService;
import com.example.library.service.DashboardService;
import com.example.library.service.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reports &amp; Analytics page (also printable).
 */
@Controller
public class ReportController {

    private final ReportService reportService;
    private final DashboardService dashboardService;
    private final CirculationService circulationService;

    public ReportController(ReportService reportService,
                            DashboardService dashboardService,
                            CirculationService circulationService) {
        this.reportService = reportService;
        this.dashboardService = dashboardService;
        this.circulationService = circulationService;
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("activePage", "reports");
        model.addAttribute("summary", reportService.getSummary());
        model.addAttribute("monthlyIssues", dashboardService.getMonthlyIssueTrend(12));
        model.addAttribute("monthlyFines", reportService.getMonthlyFinesCollected(12));
        model.addAttribute("categoryChart", reportService.getCategoryDistribution());
        List<PopularBook> mostBorrowed = dashboardService.getPopularBooks(10);
        model.addAttribute("mostBorrowed", mostBorrowed);
        model.addAttribute("mostBorrowedChart", new ChartData(
                mostBorrowed.stream().map(p -> p.book().getTitle()).toList(),
                mostBorrowed.stream().map(PopularBook::issueCount).toList()));
        model.addAttribute("overdueLoans", circulationService.findOverdueLoans());
        model.addAttribute("generatedAt", LocalDateTime.now());
        return "reports/index";
    }
}
