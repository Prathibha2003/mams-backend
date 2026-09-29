package com.mams.controller;

import com.mams.model.AuditLog;
import com.mams.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private final AuditLogRepository repo;

    public AuditController(AuditLogRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<AuditLog> list(@RequestParam(defaultValue = "100") int size) {
        int s = Math.min(Math.max(size, 1), 500);
        return repo.findAll(PageRequest.of(0, s, Sort.by(Sort.Direction.DESC, "id"))).getContent();
    }
}