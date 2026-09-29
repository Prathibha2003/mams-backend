package com.mams.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-side queries. A baseId or typeId of 0 means "all". */
@Service
public class BalanceService {
    private static final LocalDate MIN = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX = LocalDate.of(9999, 12, 31);

    private final NamedParameterJdbcTemplate jdbc;

    public BalanceService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private MapSqlParameterSource params(long baseId, long typeId, LocalDate from, LocalDate to) {
        return new MapSqlParameterSource()
                .addValue("baseId", baseId)
                .addValue("typeId", typeId)
                .addValue("from", Date.valueOf(from == null ? MIN : from))
                .addValue("to", Date.valueOf(to == null ? MAX : to));
    }

    private long one(String sql, MapSqlParameterSource p) {
        Long v = jdbc.queryForObject(sql, p, Long.class);
        return v == null ? 0L : v;
    }

    private long initial(MapSqlParameterSource p) {
        return one("SELECT COALESCE(SUM(quantity),0) FROM initial_balances"
                + " WHERE (:baseId=0 OR base_id=:baseId) AND (:typeId=0 OR equipment_type_id=:typeId)", p);
    }

    private long total(String table, String baseCol, String dateExpr, boolean before, MapSqlParameterSource p) {
        String sql = "SELECT COALESCE(SUM(quantity),0) FROM " + table
                + " WHERE (:baseId=0 OR " + baseCol + "=:baseId)"
                + " AND (:typeId=0 OR equipment_type_id=:typeId) AND "
                + (before ? dateExpr + " < :from" : dateExpr + " BETWEEN :from AND :to");
        return one(sql, p);
    }

    public Map<String, Object> dashboard(long baseId, long typeId, LocalDate from, LocalDate to) {
        MapSqlParameterSource p = params(baseId, typeId, from, to);
        long opening = initial(p)
                + total("purchases", "base_id", "purchase_date", true, p)
                + total("transfers", "to_base_id", "DATE(transferred_at)", true, p)
                - total("transfers", "from_base_id", "DATE(transferred_at)", true, p)
                - total("expenditures", "base_id", "expended_date", true, p);
        long purchased = total("purchases", "base_id", "purchase_date", false, p);
        long transferIn = total("transfers", "to_base_id", "DATE(transferred_at)", false, p);
        long transferOut = total("transfers", "from_base_id", "DATE(transferred_at)", false, p);
        long assigned = total("assignments", "base_id", "assigned_date", false, p);
        long expended = total("expenditures", "base_id", "expended_date", false, p);
        long net = purchased + transferIn - transferOut;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("openingBalance", opening);
        m.put("purchases", purchased);
        m.put("transferIn", transferIn);
        m.put("transferOut", transferOut);
        m.put("netMovement", net);
        m.put("assigned", assigned);
        m.put("expended", expended);
        m.put("closingBalance", opening + net - expended);
        return m;
    }

    /** Stock at a base that is neither expended nor assigned (all time). */
    public long available(long baseId, long typeId) {
        MapSqlParameterSource p = params(baseId, typeId, null, null);
        return initial(p)
                + total("purchases", "base_id", "purchase_date", false, p)
                + total("transfers", "to_base_id", "DATE(transferred_at)", false, p)
                - total("transfers", "from_base_id", "DATE(transferred_at)", false, p)
                - total("expenditures", "base_id", "expended_date", false, p)
                - total("assignments", "base_id", "assigned_date", false, p);
    }

    public List<Map<String, Object>> purchases(long baseId, long typeId, LocalDate from, LocalDate to) {
        String sql = "SELECT p.id, p.base_id AS baseId, b.name AS baseName,"
                + " p.equipment_type_id AS equipmentTypeId, e.name AS equipmentType,"
                + " p.quantity, p.unit_cost AS unitCost, p.supplier, p.purchase_date AS purchaseDate,"
                + " u.username AS createdBy, p.created_at AS createdAt"
                + " FROM purchases p JOIN bases b ON b.id=p.base_id"
                + " JOIN equipment_types e ON e.id=p.equipment_type_id"
                + " JOIN users u ON u.id=p.created_by"
                + " WHERE (:baseId=0 OR p.base_id=:baseId) AND (:typeId=0 OR p.equipment_type_id=:typeId)"
                + " AND p.purchase_date BETWEEN :from AND :to ORDER BY p.purchase_date DESC, p.id DESC";
        return jdbc.queryForList(sql, params(baseId, typeId, from, to));
    }

    /** direction: "in", "out" or "any". */
    public List<Map<String, Object>> transfers(long baseId, long typeId, String direction,
                                               LocalDate from, LocalDate to) {
        String baseCond;
        if ("in".equals(direction)) {
            baseCond = "(:baseId=0 OR t.to_base_id=:baseId)";
        } else if ("out".equals(direction)) {
            baseCond = "(:baseId=0 OR t.from_base_id=:baseId)";
        } else {
            baseCond = "(:baseId=0 OR t.from_base_id=:baseId OR t.to_base_id=:baseId)";
        }
        String sql = "SELECT t.id, t.from_base_id AS fromBaseId, fb.name AS fromBase,"
                + " t.to_base_id AS toBaseId, tb.name AS toBase,"
                + " t.equipment_type_id AS equipmentTypeId, e.name AS equipmentType,"
                + " t.quantity, t.notes, t.transferred_at AS transferredAt, u.username AS createdBy"
                + " FROM transfers t JOIN bases fb ON fb.id=t.from_base_id"
                + " JOIN bases tb ON tb.id=t.to_base_id"
                + " JOIN equipment_types e ON e.id=t.equipment_type_id"
                + " JOIN users u ON u.id=t.created_by"
                + " WHERE " + baseCond + " AND (:typeId=0 OR t.equipment_type_id=:typeId)"
                + " AND DATE(t.transferred_at) BETWEEN :from AND :to"
                + " ORDER BY t.transferred_at DESC, t.id DESC";
        return jdbc.queryForList(sql, params(baseId, typeId, from, to));
    }

    public List<Map<String, Object>> assignments(long baseId, long typeId, LocalDate from, LocalDate to) {
        String sql = "SELECT a.id, a.base_id AS baseId, b.name AS baseName,"
                + " a.equipment_type_id AS equipmentTypeId, e.name AS equipmentType,"
                + " a.quantity, a.assigned_to AS assignedTo, a.assigned_date AS assignedDate,"
                + " a.notes, u.username AS createdBy, a.created_at AS createdAt"
                + " FROM assignments a JOIN bases b ON b.id=a.base_id"
                + " JOIN equipment_types e ON e.id=a.equipment_type_id"
                + " JOIN users u ON u.id=a.created_by"
                + " WHERE (:baseId=0 OR a.base_id=:baseId) AND (:typeId=0 OR a.equipment_type_id=:typeId)"
                + " AND a.assigned_date BETWEEN :from AND :to ORDER BY a.assigned_date DESC, a.id DESC";
        return jdbc.queryForList(sql, params(baseId, typeId, from, to));
    }

    public List<Map<String, Object>> expenditures(long baseId, long typeId, LocalDate from, LocalDate to) {
        String sql = "SELECT x.id, x.base_id AS baseId, b.name AS baseName,"
                + " x.equipment_type_id AS equipmentTypeId, e.name AS equipmentType,"
                + " x.quantity, x.reason, x.expended_date AS expendedDate,"
                + " u.username AS createdBy, x.created_at AS createdAt"
                + " FROM expenditures x JOIN bases b ON b.id=x.base_id"
                + " JOIN equipment_types e ON e.id=x.equipment_type_id"
                + " JOIN users u ON u.id=x.created_by"
                + " WHERE (:baseId=0 OR x.base_id=:baseId) AND (:typeId=0 OR x.equipment_type_id=:typeId)"
                + " AND x.expended_date BETWEEN :from AND :to ORDER BY x.expended_date DESC, x.id DESC";
        return jdbc.queryForList(sql, params(baseId, typeId, from, to));
    }

    public Map<String, Object> netMovementDetails(long baseId, long typeId, LocalDate from, LocalDate to) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("purchases", purchases(baseId, typeId, from, to));
        m.put("transferIn", transfers(baseId, typeId, "in", from, to));
        m.put("transferOut", transfers(baseId, typeId, "out", from, to));
        return m;
    }
}