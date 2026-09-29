package com.mams.controller;

import com.mams.model.Base;
import com.mams.model.EquipmentType;
import com.mams.model.Expenditure;
import com.mams.repository.BaseRepository;
import com.mams.repository.EquipmentTypeRepository;
import com.mams.repository.ExpenditureRepository;
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
@RequestMapping("/api/expenditures")
@PreAuthorize("hasAnyRole('ADMIN','BASE_COMMANDER')")
public class ExpenditureController {
    private final ExpenditureRepository expenditures;
    private final BaseRepository bases;
    private final EquipmentTypeRepository types;
    private final UserRepository users;
    private final BalanceService balances;
    private final AccessService access;

    public ExpenditureController(ExpenditureRepository expenditures, BaseRepository bases,
                                 EquipmentTypeRepository types, UserRepository users,
                                 BalanceService balances, AccessService access) {
        this.expenditures = expenditures;
        this.bases = bases;
        this.types = types;
        this.users = users;
        this.balances = balances;
        this.access = access;
    }

    public record ExpenditureRequest(Long baseId, Long equipmentTypeId, Integer quantity,
                                     String reason, LocalDate expendedDate) {}

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(required = false) Long baseId,
            @RequestParam(required = false) Long equipmentTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return balances.expenditures(access.readBase(baseId),
                equipmentTypeId == null ? 0L : equipmentTypeId, from, to);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody ExpenditureRequest r, HttpServletRequest req) {
        AuthUser me = access.user();
        long baseId = access.writeBase(r.baseId());
        if (r.equipmentTypeId() == null) throw bad("equipmentTypeId is required");
        if (r.quantity() == null || r.quantity() <= 0) throw bad("Quantity must be greater than 0");
        Base base = bases.findById(baseId).orElseThrow(() -> bad("Unknown base"));
        EquipmentType type = types.findById(r.equipmentTypeId()).orElseThrow(() -> bad("Unknown equipment type"));

        long available = balances.available(baseId, type.getId());
        if (r.quantity() > available) {
            throw bad("Insufficient stock at " + base.getName() + ". Available: " + available);
        }

        Expenditure x = new Expenditure();
        x.setBase(base);
        x.setEquipmentType(type);
        x.setQuantity(r.quantity());
        x.setReason(r.reason());
        x.setExpendedDate(r.expendedDate() == null ? LocalDate.now() : r.expendedDate());
        x.setCreatedBy(users.getReferenceById(me.id()));
        expenditures.save(x);

        AuditFilter.note(req, "EXPENDITURE_CREATED", "Expenditure", x.getId(),
                "base=" + base.getName() + ", type=" + type.getName() + ", qty=" + r.quantity());
        return ResponseEntity.status(201).body(Map.of("id", x.getId()));
    }
}