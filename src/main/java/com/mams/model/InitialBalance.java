package com.mams.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "initial_balances")
@IdClass(InitialBalance.Key.class)
@Getter @Setter @NoArgsConstructor
public class InitialBalance {
    @Id @Column(name = "base_id")
    private Long baseId;

    @Id @Column(name = "equipment_type_id")
    private Long equipmentTypeId;

    @Column(nullable = false)
    private int quantity;

    @Getter @Setter @NoArgsConstructor
    public static class Key implements java.io.Serializable {
        private Long baseId;
        private Long equipmentTypeId;

        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return java.util.Objects.equals(baseId, k.baseId)
                && java.util.Objects.equals(equipmentTypeId, k.equipmentTypeId);
        }
        @Override public int hashCode() { return java.util.Objects.hash(baseId, equipmentTypeId); }
    }
}