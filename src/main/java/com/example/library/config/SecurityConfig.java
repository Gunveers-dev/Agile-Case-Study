package com.example.library.config;

import com.example.library.service.AdminService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * Server-side authentication with Spring Security.
 * <ul>
 *   <li>Every page except the login page and static assets requires a signed-in administrator.</li>
 *   <li>Credentials are checked against the ADMINS table; passwords are BCrypt hashes.</li>
 *   <li>CSRF protection is on for all forms (Thymeleaf adds the token automatically).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final int REMEMBER_ME_SECONDS = 7 * 24 * 60 * 60;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AdminService adminService) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                antMatcher("/login"),
                                antMatcher("/css/**"),
                                antMatcher("/js/**"),
                                antMatcher("/images/**"),
                                antMatcher("/vendor/**"),
                                antMatcher("/favicon.ico"),
                                antMatcher("/error"))
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error"))
                .rememberMe(remember -> remember
                        .key("libratrack-remember-me-key")
                        .rememberMeParameter("remember-me")
                        .tokenValiditySeconds(REMEMBER_ME_SECONDS)
                        .userDetailsService(adminService))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "remember-me"))
                // The H2 console (development tool) posts its own forms and renders in frames.
                .csrf(csrf -> csrf.ignoringRequestMatchers(antMatcher("/h2-console/**")))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
