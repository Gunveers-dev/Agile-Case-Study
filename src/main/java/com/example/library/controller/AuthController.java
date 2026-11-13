package com.example.library.controller;

import com.example.library.config.DataInitializer;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Login page. The credential check itself is done by Spring Security (see SecurityConfig).
 */
@Controller
public class AuthController {

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String login(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "redirect:/dashboard";
        }
        model.addAttribute("demoEmail", DataInitializer.DEMO_ADMIN_EMAIL);
        model.addAttribute("demoPassword", DataInitializer.DEMO_ADMIN_PASSWORD);
        return "auth/login";
    }
}
