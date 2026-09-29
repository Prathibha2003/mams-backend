package com.mams.controller;

import com.mams.security.AccessService;
import com.mams.service.BalanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN','BASE_COMMANDER')")
public class DashboardController {
    private final BalanceService balances;
    private final AccessService access;

    public DashboardController(BalanceService balances, AccessService access) {
        this.balances = balances;
        this.access = access;
    }

    @GetMapping
    public Map<String, Object> summary(
            @RequestParam(required = false) Long baseId,
            @RequestParam(required = false) Long equipmentTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return balances.dashboard(access.readBase(baseId),
                equipmentTypeId == null ? 0L : equipmentTypeId, from, to);
    }

    @GetMapping("/net-movement")
    public Map<String, Object> netMovement(
            @RequestParam(required = false) Long baseId,
            @RequestParam(required = false) Long equipmentTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return balances.netMovementDetails(access.readBase(baseId),
                equipmentTypeId == null ? 0L : equipmentTypeId, from, to);
    }
}