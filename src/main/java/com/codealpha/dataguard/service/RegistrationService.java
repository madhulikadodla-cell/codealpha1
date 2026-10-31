package com.codealpha.dataguard.service;

import com.codealpha.dataguard.entity.AppUser;
import com.codealpha.dataguard.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser register(String username, String password) {
        String normalizedUsername = username.trim();
        if (users.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new IllegalArgumentException("That username is already registered.");
        }
        AppUser user = new AppUser();
        user.setUsername(normalizedUsername);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("USER");
        return users.save(user);
    }
}
