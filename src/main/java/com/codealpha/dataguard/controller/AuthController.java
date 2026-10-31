package com.codealpha.dataguard.controller;

import com.codealpha.dataguard.entity.AppUser;
import com.codealpha.dataguard.repository.AppUserRepository;
import com.codealpha.dataguard.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final RegistrationService registrationService;
    private final AppUserRepository users;

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
            RegistrationService registrationService, AppUserRepository users) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.registrationService = registrationService;
        this.users = users;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        token.getToken();
        return token;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest request) {
        try {
            AppUser user = registrationService.register(request.username(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(user.getUsername(), user.getRole()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(exception.getMessage()));
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            if (httpRequest.getSession(false) != null) {
                httpRequest.changeSessionId();
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, httpRequest, httpResponse);
            AppUser user = users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
            return ResponseEntity.ok(new UserResponse(user.getUsername(), user.getRole()));
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            throw new BadCredentialsException("Username or password is incorrect.");
        }
    }

    @GetMapping("/auth/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.noContent().build();
        }
        AppUser user = users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(new UserResponse(user.getUsername(), user.getRole()));
    }

    public record RegistrationRequest(
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$", message = "Use 3-50 letters, numbers, dots, underscores or hyphens.") String username,
            @NotBlank @Size(min = 10, max = 72, message = "Password must be 10-72 characters.") String password) { }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }
    public record UserResponse(String username, String role) { }
    public record ErrorResponse(String message) { }
}
