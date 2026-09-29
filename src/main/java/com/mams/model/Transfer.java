package com.mams.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfers")
@Getter @Setter @NoArgsConstructor
public class Transfer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "from_base_id")
    private Base fromBase;

    @ManyToOne(optional = false)
    @JoinColumn(name = "to_base_id")
    private Base toBase;

    @ManyToOne(optional = false)
    @JoinColumn(name = "equipment_type_id")
    private EquipmentType equipmentType;

    @Column(nullable = false)
    private int quantity;

    private String notes;

    @Column(name = "transferred_at")
    private LocalDateTime transferredAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    void onCreate() { if (transferredAt == null) transferredAt = LocalDateTime.now(); }
}