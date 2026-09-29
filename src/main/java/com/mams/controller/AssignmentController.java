package com.mams.controller;

import com.mams.model.Assignment;
import com.mams.model.Base;
import com.mams.model.EquipmentType;
import com.mams.repository.AssignmentRepository;
import com.mams.repository.BaseRepository;
import com.mams.repository.EquipmentTypeRepository;
import com.mams.repository.UserRepository;
import com.mams.security.AccessService;
import com.mams.security.AuditFilter;
import com.mams.security.AuthUser;
import com.mams.service.BalanceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.mams.security.AccessService.bad;

@RestController
@RequestMapping("/api/assignments")
@PreAuthorize("hasAnyRole('ADMIN','BASE_COMMANDER')")
public class AssignmentController {
    private final AssignmentRepository assignments;
    private final BaseRepository bases;
    private final EquipmentTypeRepository types;
    private final UserRepository users;
    private final BalanceService balances;
    private final AccessService access;

    public AssignmentController(AssignmentRepository assignments, BaseRepository bases,
                                EquipmentTypeRepository types, UserRepository users,
                                BalanceService balances, AccessService access) {
        this.assignments = assignments;
        this.bases = bases;
        this.types = types;
        this.users = users;
        this.balances = balances;
        this.access = access;
    }

    public record AssignmentRequest(Long baseId, Long equipmentTypeId, Integer quantity,
                                    String assignedTo, LocalDate assignedDate, String notes) {}

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(required = false) Long baseId,
            @RequestParam(required = false) Long equipmentTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return balances.assignments(access.readBase(baseId),
                equipmentTypeId == null ? 0L : equipmentTypeId, from, to);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody AssignmentRequest r, HttpServletRequest req) {
        AuthUser me = access.user();
        long baseId = access.writeBase(r.baseId());
        if (r.equipmentTypeId() == null) throw bad("equipmentTypeId is required");
        if (r.quantity() == null || r.quantity() <= 0) throw bad("Quantity must be greater than 0");
        if (r.assignedTo() == null || r.assignedTo().isBlank()) throw bad("assignedTo is required");
        Base base = bases.findById(baseId).orElseThrow(() -> bad("Unknown base"));
        EquipmentType type = types.findById(r.equipmentTypeId()).orElseThrow(() -> bad("Unknown equipment type"));

        long available = balances.available(baseId, type.getId());
        if (r.quantity() > available) {
            throw bad("Insufficient stock at " + base.getName() + ". Available: " + available);
        }

        Assignment a = new Assignment();
        a.setBase(base);
        a.setEquipmentType(type);
        a.setQuantity(r.quantity());
        a.setAssignedTo(r.assignedTo().trim());
        a.setAssignedDate(r.assignedDate() == null ? LocalDate.now() : r.assignedDate());
        a.setNotes(r.notes());
        a.setCreatedBy(users.getReferenceById(me.id()));
        assignments.save(a);

        AuditFilter.note(req, "ASSIGNMENT_CREATED", "Assignment", a.getId(),
                "base=" + base.getName() + ", type=" + type.getName() + ", qty=" + r.quantity()
                        + ", to=" + a.getAssignedTo());
        return ResponseEntity.status(201).body(Map.of("id", a.getId()));
    }
}