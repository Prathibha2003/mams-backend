package com.mams.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccessService {

    public static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    public AuthUser user() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof AuthUser u)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
        return u;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(user().role());
    }

    /** For reads. Returns 0 when an admin wants all bases. Others are limited to their own base. */
    public long readBase(Long requested) {
        AuthUser u = user();
        if (isAdmin()) {
            return requested == null ? 0L : requested;
        }
        if (u.baseId() == null) {
            throw forbidden("No base assigned to this user");
        }
        if (requested != null && !requested.equals(u.baseId())) {
            throw forbidden("You can only access your own base");
        }
        return u.baseId();
    }

    /** For writes. Admin must name a base. Others always act on their own base. */
    public long writeBase(Long requested) {
        AuthUser u = user();
        if (isAdmin()) {
            if (requested == null) {
                throw bad("baseId is required");
            }
            return requested;
        }
        if (u.baseId() == null) {
            throw forbidden("No base assigned to this user");
        }
        if (requested != null && !requested.equals(u.baseId())) {
            throw forbidden("You can only act on your own base");
        }
        return u.baseId();
    }
}