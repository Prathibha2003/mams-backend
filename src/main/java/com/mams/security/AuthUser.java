package com.mams.security;

public record AuthUser(Long id, String username, String role, Long baseId) {}