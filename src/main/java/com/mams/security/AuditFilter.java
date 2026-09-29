package com.mams.security;

import com.mams.model.AuditLog;
import com.mams.repository.AuditLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuditFilter extends OncePerRequestFilter {
    private final AuditLogRepository repo;

    public AuditFilter(AuditLogRepository repo) {
        this.repo = repo;
    }

    /** Controllers call this to describe what a request did. */
    public static void note(HttpServletRequest req, String action, String entityType, Long entityId, String details) {
        req.setAttribute("audit.action", action);
        req.setAttribute("audit.entityType", entityType);
        req.setAttribute("audit.entityId", entityId);
        req.setAttribute("audit.details", details);
    }

    public static void user(HttpServletRequest req, String username) {
        req.setAttribute("audit.username", username);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Look at every /api/ request; decide after the response what is worth logging.
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        AuthUser user = null;
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AuthUser u) {
            user = u;
        }
        try {
            chain.doFilter(req, res);
        } finally {
            writeLog(req, res, user);
        }
    }

    private void writeLog(HttpServletRequest req, HttpServletResponse res, AuthUser user) {
        String m = req.getMethod();
        boolean write = m.equals("POST") || m.equals("PUT") || m.equals("DELETE") || m.equals("PATCH");
        int status = res.getStatus();
        boolean denied = status == 401 || status == 403;

        // Ordinary successful reads are not logged; writes and denials always are.
        if (!write && !denied) {
            return;
        }

        try {
            AuditLog log = new AuditLog();
            if (user != null) {
                log.setUserId(user.id());
                log.setUsername(user.username());
            } else {
                log.setUsername((String) req.getAttribute("audit.username"));
            }

            String action = (String) req.getAttribute("audit.action");
            if (action == null) {
                action = denied
                        ? "ACCESS_DENIED " + m + " " + req.getRequestURI()
                        : m + " " + req.getRequestURI();
            }
            log.setAction(action);

            log.setEntityType((String) req.getAttribute("audit.entityType"));
            log.setEntityId((Long) req.getAttribute("audit.entityId"));
            log.setHttpMethod(m);
            log.setEndpoint(req.getRequestURI());

            String details = (String) req.getAttribute("audit.details");
            if (details != null && details.length() > 1000) {
                details = details.substring(0, 1000);
            }
            log.setDetails(details);
            log.setStatusCode(status);
            log.setIpAddress(req.getRemoteAddr());
            repo.save(log);
        } catch (Exception ignored) {
            // logging must never break the request
        }
    }
}