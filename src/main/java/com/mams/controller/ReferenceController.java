package com.mams.controller;

import com.mams.model.Base;
import com.mams.model.EquipmentType;
import com.mams.repository.BaseRepository;
import com.mams.repository.EquipmentTypeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReferenceController {
    private final BaseRepository bases;
    private final EquipmentTypeRepository types;

    public ReferenceController(BaseRepository bases, EquipmentTypeRepository types) {
        this.bases = bases;
        this.types = types;
    }

    @GetMapping("/api/bases")
    public List<Base> bases() {
        return bases.findAll();
    }

    @GetMapping("/api/equipment-types")
    public List<EquipmentType> equipmentTypes() {
        return types.findAll();
    }
}