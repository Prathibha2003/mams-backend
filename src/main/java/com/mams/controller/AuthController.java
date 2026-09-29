package com.mams.controller;

import com.mams.model.User;
import com.mams.repository.UserRepository;
import com.mams.security.AuditFilter;
import com.mams.security.AuthUser;
import com.mams.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtUtil jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public record LoginRequest(String username, String password) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        if (req.username() == null || req.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required"));
        }
        AuditFilter.user(http, req.username());
        Optional<User> found = users.findByUsername(req.username());
        if (found.isEmpty() || !encoder.matches(req.password(), found.get().getPasswordHash())) {
            AuditFilter.note(http, "LOGIN_FAILED", "User", null, "Invalid credentials");
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }
        User u = found.get();
        Long baseId = u.getBase() == null ? null : u.getBase().getId();
        String token = jwt.generate(u.getId(), u.getUsername(), u.getRole().name(), baseId);
        AuditFilter.note(http, "LOGIN", "User", u.getId(), "Login successful");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("username", u.getUsername());
        body.put("fullName", u.getFullName());
        body.put("role", u.getRole().name());
        body.put("baseId", baseId);
        body.put("baseName", u.getBase() == null ? null : u.getBase().getName());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }
        return ResponseEntity.ok(user);
    }
}