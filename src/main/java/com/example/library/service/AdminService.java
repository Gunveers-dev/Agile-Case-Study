package com.example.library.service;

import com.example.library.exception.BusinessException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.model.Admin;
import com.example.library.repository.AdminRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator accounts. Also tells Spring Security how to load an admin by email at login.
 * Passwords are always stored as BCrypt hashes.
 */
@Service
@Transactional(readOnly = true)
public class AdminService implements UserDetailsService {

    public static final int MIN_PASSWORD_LENGTH = 6;

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Admin admin = adminRepository.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("No administrator with email " + email));
        return User.withUsername(admin.getEmail())
                .password(admin.getPassword())
                .roles(admin.getRole())
                .build();
    }

    public java.util.Optional<Admin> findByEmail(String email) {
        return email == null ? java.util.Optional.empty() : adminRepository.findByEmailIgnoreCase(email);
    }

    public Admin getByEmail(String email) {
        return adminRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("No administrator with email " + email));
    }

    /** Creates the administrator account if it does not exist yet. Returns true if it was created. */
    @Transactional
    public boolean createAdminIfMissing(String email, String rawPassword, String fullName) {
        if (adminRepository.existsByEmailIgnoreCase(email)) {
            return false;
        }
        adminRepository.save(new Admin(email.toLowerCase(), passwordEncoder.encode(rawPassword), "ADMIN", fullName));
        return true;
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword, String confirmPassword) {
        Admin admin = getByEmail(email);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, admin.getPassword())) {
            throw new BusinessException("currentPassword", "Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("newPassword",
                    "New password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException("confirmPassword", "New password and confirmation do not match");
        }
        if (passwordEncoder.matches(newPassword, admin.getPassword())) {
            throw new BusinessException("newPassword", "New password must be different from the current one");
        }
        admin.setPassword(passwordEncoder.encode(newPassword));
        adminRepository.save(admin);
    }

    @Transactional
    public void updateProfileName(String email, String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessException("fullName", "Name cannot be empty");
        }
        Admin admin = getByEmail(email);
        admin.setFullName(fullName.trim());
        adminRepository.save(admin);
    }
}
