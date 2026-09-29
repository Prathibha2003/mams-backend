package com.mams.controller;

import com.mams.model.Base;
import com.mams.model.EquipmentType;
import com.mams.model.Transfer;
import com.mams.repository.BaseRepository;
import com.mams.repository.EquipmentTypeRepository;
import com.mams.repository.TransferRepository;
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
@RequestMapping("/api/transfers")
@PreAuthorize("hasAnyRole('ADMIN','BASE_COMMANDER','LOGISTICS_OFFICER')")
public class TransferController {
    private final TransferRepository transfers;
    private final BaseRepository bases;
    private final EquipmentTypeRepository types;
    private final UserRepository users;
    private final BalanceService balances;
    private final AccessService access;

    public TransferController(TransferRepository transfers, BaseRepository bases, EquipmentTypeRepository types,
                              UserRepository users, BalanceService balances, AccessService access) {
        this.transfers = transfers;
        this.bases = bases;
        this.types = types;
        this.users = users;
        this.balances = balances;
        this.access = access;
    }

    public record TransferRequest(Long fromBaseId, Long toBaseId, Long equipmentTypeId,
                                  Integer quantity, String notes) {}

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(required = false) Long baseId,
            @RequestParam(required = false) Long equipmentTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return balances.transfers(access.readBase(baseId),
                equipmentTypeId == null ? 0L : equipmentTypeId, "any", from, to);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody TransferRequest r, HttpServletRequest req) {
        AuthUser me = access.user();
        long fromId = access.writeBase(r.fromBaseId());
        if (r.toBaseId() == null) throw bad("toBaseId is required");
        if (r.toBaseId() == fromId) throw bad("Source and destination base must be different");
        if (r.equipmentTypeId() == null) throw bad("equipmentTypeId is required");
        if (r.quantity() == null || r.quantity() <= 0) throw bad("Quantity must be greater than 0");
        Base from = bases.findById(fromId).orElseThrow(() -> bad("Unknown source base"));
        Base to = bases.findById(r.toBaseId()).orElseThrow(() -> bad("Unknown destination base"));
        EquipmentType type = types.findById(r.equipmentTypeId()).orElseThrow(() -> bad("Unknown equipment type"));

        long available = balances.available(fromId, type.getId());
        if (r.quantity() > available) {
            throw bad("Insufficient stock at " + from.getName() + ". Available: " + available);
        }

        Transfer t = new Transfer();
        t.setFromBase(from);
        t.setToBase(to);
        t.setEquipmentType(type);
        t.setQuantity(r.quantity());
        t.setNotes(r.notes());
        t.setCreatedBy(users.getReferenceById(me.id()));
        transfers.save(t);

        AuditFilter.note(req, "TRANSFER_CREATED", "Transfer", t.getId(),
                "from=" + from.getName() + ", to=" + to.getName() + ", type=" + type.getName()
                        + ", qty=" + r.quantity());
        return ResponseEntity.status(201).body(Map.of("id", t.getId()));
    }
}