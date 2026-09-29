package com.mams.controller;

import com.mams.model.Base;
import com.mams.model.EquipmentType;
import com.mams.model.Purchase;
import com.mams.repository.BaseRepository;
import com.mams.repository.EquipmentTypeRepository;
import com.mams.repository.PurchaseRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.mams.security.AccessService.bad;

@RestController
@RequestMapping("/api/purchases")
@PreAuthorize("hasAnyRole('ADMIN','BASE_COMMANDER','LOGISTICS_OFFICER')")
public class PurchaseController {
    private final PurchaseRepository purchases;
    private final BaseRepository bases;
    private final EquipmentTypeRepository types;
    private final UserRepository users;
    private final BalanceService balances;
    private final AccessService access;

    public PurchaseController(PurchaseRepository purchases, BaseRepository bases, EquipmentTypeRepository types,
                              UserRepository users, BalanceService balances, AccessService access) {
        this.purchases = purchases;
        this.bases = bases;
        this.types = types;
        this.users = users;
        this.balances = balances;
        this.access = access;
    }

    public record PurchaseRequest(Long baseId, Long equipmentTypeId, Integer quantity,
                                  BigDecimal unitCost, String supplier, LocalDate purchaseDate) {}

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(required = false) Long baseId,
            @RequestParam(required = false) Long equipmentTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return balances.purchases(access.readBase(baseId),
                equipmentTypeId == null ? 0L : equipmentTypeId, from, to);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody PurchaseRequest r, HttpServletRequest req) {
        AuthUser me = access.user();
        long baseId = access.writeBase(r.baseId());
        if (r.equipmentTypeId() == null) throw bad("equipmentTypeId is required");
        if (r.quantity() == null || r.quantity() <= 0) throw bad("Quantity must be greater than 0");
        if (r.unitCost() != null && r.unitCost().signum() < 0) throw bad("Unit cost cannot be negative");
        Base base = bases.findById(baseId).orElseThrow(() -> bad("Unknown base"));
        EquipmentType type = types.findById(r.equipmentTypeId()).orElseThrow(() -> bad("Unknown equipment type"));

        Purchase p = new Purchase();
        p.setBase(base);
        p.setEquipmentType(type);
        p.setQuantity(r.quantity());
        p.setUnitCost(r.unitCost());
        p.setSupplier(r.supplier());
        p.setPurchaseDate(r.purchaseDate() == null ? LocalDate.now() : r.purchaseDate());
        p.setCreatedBy(users.getReferenceById(me.id()));
        purchases.save(p);

        AuditFilter.note(req, "PURCHASE_CREATED", "Purchase", p.getId(),
                "base=" + base.getName() + ", type=" + type.getName() + ", qty=" + r.quantity());
        return ResponseEntity.status(201).body(Map.of("id", p.getId()));
    }
}