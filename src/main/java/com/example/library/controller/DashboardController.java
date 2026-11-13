package com.example.library.controller;

import com.example.library.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("activePage", "dashboard");
        model.addAttribute("stats", dashboardService.getStats());
        model.addAttribute("issueTrend", dashboardService.getMonthlyIssueTrend(6));
        model.addAttribute("activities", dashboardService.getRecentActivities(6));
        model.addAttribute("popularBooks", dashboardService.getPopularBooks(5));
        return "dashboard";
    }
}
