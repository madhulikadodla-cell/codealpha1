package com.codealpha.dataguard;

import com.codealpha.dataguard.entity.AppUser;
import com.codealpha.dataguard.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@SpringBootApplication
public class DataGuardApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataGuardApplication.class, args);
    }

    @Bean
    CommandLineRunner bootstrapAdmin(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.password:}") String password) {
        return args -> {
            if (!StringUtils.hasText(username) && !StringUtils.hasText(password)) {
                return;
            }
            if (!StringUtils.hasText(username) || password.length() < 12) {
                throw new IllegalStateException("Set both bootstrap admin values and use a password of at least 12 characters.");
            }
            if (!users.existsByUsernameIgnoreCase(username)) {
                AppUser admin = new AppUser();
                admin.setUsername(username.trim());
                admin.setPasswordHash(passwordEncoder.encode(password));
                admin.setRole("ADMIN");
                users.save(admin);
            }
        };
    }
}
