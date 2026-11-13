package com.example.library.controller;

import com.example.library.exception.BusinessException;
import com.example.library.model.Admin;
import com.example.library.service.AdminService;
import com.example.library.dto.BookStatus;
import com.example.library.service.CirculationService;
import com.example.library.service.FineService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Administrator settings: profile name, password change and the library rules in force.
 */
@Controller
public class SettingsController {

    private final AdminService adminService;

    public SettingsController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/settings")
    public String settings(Authentication authentication, Model model) {
        Admin admin = adminService.getByEmail(authentication.getName());
        model.addAttribute("activePage", "settings");
        model.addAttribute("admin", admin);
        model.addAttribute("maxLoans", CirculationService.MAX_ACTIVE_LOANS);
        model.addAttribute("loanDays", CirculationService.DEFAULT_LOAN_PERIOD_DAYS);
        model.addAttribute("finePerDay", FineService.FINE_PER_DAY);
        model.addAttribute("lowStockThreshold", BookStatus.LOW_STOCK_THRESHOLD);
        model.addAttribute("minPasswordLength", AdminService.MIN_PASSWORD_LENGTH);
        return "settings/index";
    }

    @PostMapping("/settings/profile")
    public String updateProfile(Authentication authentication,
                                @RequestParam(defaultValue = "") String fullName,
                                RedirectAttributes redirect) {
        try {
            adminService.updateProfileName(authentication.getName(), fullName);
            redirect.addFlashAttribute(WebSupport.TOAST_SUCCESS, "Profile name updated");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute(WebSupport.TOAST_ERROR, ex.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/password")
    public String changePassword(Authentication authentication,
                                 @RequestParam(defaultValue = "") String currentPassword,
                                 @RequestParam(defaultValue = "") String newPassword,
                                 @RequestParam(defaultValue = "") String confirmPassword,
                                 RedirectAttributes redirect) {
        try {
            adminService.changePassword(authentication.getName(), currentPassword, newPassword, confirmPassword);
            redirect.addFlashAttribute(WebSupport.TOAST_SUCCESS, "Password changed. Use the new password next time you sign in.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute(WebSupport.TOAST_ERROR, ex.getMessage());
            redirect.addFlashAttribute("passwordError", ex.getMessage());
        }
        return "redirect:/settings";
    }
}
