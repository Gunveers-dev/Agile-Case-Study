package com.example.library.controller;

import com.example.library.dto.ActiveLoanView;
import com.example.library.model.Admin;
import com.example.library.service.AdminService;
import com.example.library.service.CirculationService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds data used by the shared header to every page: signed-in administrator and overdue alerts.
 */
@ControllerAdvice(basePackages = "com.example.library.controller")
public class GlobalModelAdvice {

    private static final int MAX_ALERTS = 5;

    private final AdminService adminService;
    private final CirculationService circulationService;

    public GlobalModelAdvice(AdminService adminService, CirculationService circulationService) {
        this.adminService = adminService;
        this.circulationService = circulationService;
    }

    @ModelAttribute
    public void addHeaderData(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return;
        }
        String email = auth.getName();
        String name = adminService.findByEmail(email)
                .map(Admin::getFullName)
                .filter(n -> n != null && !n.isBlank())
                .orElse("Administrator");
        model.addAttribute("currentAdminName", name);
        model.addAttribute("currentAdminEmail", email);
        model.addAttribute("currentAdminInitials", initials(name));

        List<ActiveLoanView> overdue = circulationService.findOverdueLoans();
        model.addAttribute("overdueAlertCount", overdue.size());
        model.addAttribute("overdueAlerts", overdue.size() > MAX_ALERTS ? new ArrayList<>(overdue.subList(0, MAX_ALERTS)) : overdue);
    }

    private static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }
}
