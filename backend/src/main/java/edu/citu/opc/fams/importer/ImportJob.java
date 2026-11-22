package edu.citu.opc.fams.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import edu.citu.opc.fams.reference.ValueMappingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * UC-107 step 6: saves the valid lines as issue orders, issue order lines, asset items (issued to the
 * department and accountable person) and their ISSUE movements, and keeps every other line with its
 * reason.
 *
 * <p>ONE transaction for the whole file: it is saved completely or not at all. This is a bulk loader,
 * so it writes with JDBC batches and ids taken from the sequences in one call each, instead of one
 * JPA save per row: about 50,000 rows go in a few dozen round trips instead of 50,000. The rows it
 * writes are the same rows the issue order, asset and movement services write.
 */
@Component
public class ImportJob {

    static final class Progress {
        volatile String stage = "Starting";
        volatile int done;
        volatile int total;
    }

    private static final int CHUNK = 1000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AuditService audit;
    private final ObjectMapper json;

    public ImportJob(JdbcTemplate jdbc, TransactionTemplate tx, AuditService audit, ObjectMapper json) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.audit = audit;
        this.json = json;
    }

    void run(Long batchId, Long userId, List<MasterlistParser.Line> lines, Progress p) {
        tx.executeWithoutResult(s -> load(batchId, userId, lines, p));
    }

    private void load(Long batchId, Long userId, List<MasterlistParser.Line> lines, Progress p) {
        p.stage = "Checking issue orders";

        // Group the valid lines by issue order number; a number may appear in several places in the file.
        Map<String, List<MasterlistParser.Line>> byIo = new TreeMap<>();
        for (MasterlistParser.Line l : lines) if (l.importable()) byIo.computeIfAbsent(l.ioNo, k -> new ArrayList<>()).add(l);

        Set<String> existing = existing(byIo.keySet());
        for (var e : byIo.entrySet()) {
            List<MasterlistParser.Line> group = e.getValue();
            if (existing.contains(e.getKey())) {
                for (MasterlistParser.Line l : group) {
                    l.setAside("Issue order already in the system", "Issue order " + e.getKey() + " is already in the system");
                }
                group.clear();
                continue;
            }
            // An issue order belongs to one department: lines that name another are set aside for review.
            MasterlistParser.Line first = group.get(0);
            for (MasterlistParser.Line l : group) {
                if (!Objects.equals(l.departmentId, first.departmentId)) {
                    l.setAside("Department differs within the issue order", "Department '" + l.department
                            + "' differs from '" + first.department + "' on the other lines of issue order " + e.getKey());
                }
            }
            group.removeIf(l -> !l.importable());
        }
        byIo.values().removeIf(List::isEmpty);

        // The masterlist has no "date issued": use the earliest invoice date on the issue order, or,
        // when it has none, the date of the issue order numbered just before it.
        Map<String, LocalDate> issuedOn = new LinkedHashMap<>();
        for (var e : byIo.entrySet()) {
            issuedOn.put(e.getKey(), e.getValue().stream().map(l -> l.invoiceDate).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null));
        }
        fillGaps(issuedOn);

        // Accountable persons: match by name within the department, create the missing ones.
        p.stage = "Matching accountable persons";
        Map<String, Long> persons = new HashMap<>();
        jdbc.query("select id, department_id, full_name from employee", rs -> {
            persons.putIfAbsent(personKey(rs.getObject(2, Long.class), rs.getString(3)), rs.getLong(1));
        });
        Map<String, String> newPersons = new LinkedHashMap<>();   // key → name
        for (List<MasterlistParser.Line> group : byIo.values()) {
            for (MasterlistParser.Line l : group) {
                if (l.person == null) continue;
                String k = personKey(l.departmentId, l.person);
                if (!persons.containsKey(k)) newPersons.putIfAbsent(k, cut(l.person, 150));
            }
        }
        List<Long> personIds = ids("employee", newPersons.size());
        List<Object[]> personRows = new ArrayList<>();
        Iterator<Long> pid = personIds.iterator();
        for (var e : newPersons.entrySet()) {
            long id = pid.next();
            persons.put(e.getKey(), id);
            Long dept = Long.valueOf(e.getKey().substring(0, e.getKey().indexOf('|')));
            personRows.add(new Object[]{id, e.getValue(), dept});
        }
        batch("insert into employee (id, full_name, department_id, status) values (?, ?, ?, 'ACTIVE')",
                personRows, new int[]{Types.BIGINT, Types.VARCHAR, Types.BIGINT}, p);

        // Issue orders.
        p.stage = "Saving issue orders";
        List<Long> orderIds = ids("issue_order", byIo.size());
        Map<String, Long> orderId = new HashMap<>();
        List<Object[]> orderRows = new ArrayList<>();
        Iterator<Long> oid = orderIds.iterator();
        for (var e : byIo.entrySet()) {
            long id = oid.next();
            orderId.put(e.getKey(), id);
            List<MasterlistParser.Line> g = e.getValue();
            MasterlistParser.Line first = g.get(0);
            orderRows.add(new Object[]{
                    id, e.getKey(), first.departmentId,
                    first.person == null ? null : persons.get(personKey(first.departmentId, first.person)),
                    joined(g.stream().map(l -> l.purchasedFrom).toList(), 120),
                    joined(g.stream().map(l -> l.invoiceNo).toList(), 100),
                    g.stream().map(l -> l.invoiceDate).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null),
                    issuedOn.get(e.getKey()),
                    joined(g.stream().map(l -> l.remarks).toList(), 255),
                    cut(g.stream().map(l -> l.issuedBy).filter(Objects::nonNull).findFirst().orElse(null), 150),
                    cut(first.person, 150), cut(first.designation, 120), batchId});
        }
        batch("""
                insert into issue_order (id, io_no, department_id, employee_id, purchased_from, invoice_no, invoice_date,
                    issued_on, remarks, issued_by_name, received_by_name, received_by_title, import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", orderRows,
                new int[]{Types.BIGINT, Types.CHAR, Types.BIGINT, Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.DATE,
                        Types.DATE, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BIGINT}, p);

        // Issue order lines, asset items and their ISSUE movements.
        p.stage = "Saving items";
        List<MasterlistParser.Line> all = byIo.values().stream().flatMap(List::stream).toList();
        List<Long> lineIds = ids("issue_order_line", all.size());
        List<Long> assetIds = ids("asset", all.size());
        List<Object[]> lineRows = new ArrayList<>(all.size());
        List<Object[]> assetRows = new ArrayList<>(all.size());
        List<Object[]> moveRows = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            MasterlistParser.Line l = all.get(i);
            long lineId = lineIds.get(i);
            long assetId = assetIds.get(i);
            Long order = orderId.get(l.ioNo);
            Long person = l.person == null ? null : persons.get(personKey(l.departmentId, l.person));
            String description = cut(l.description, 2500);
            BigDecimal total = l.unitCost == null ? null : l.unitCost.multiply(BigDecimal.valueOf(l.quantity));
            lineRows.add(new Object[]{lineId, order, l.quantity, l.cleanUnit, description, l.unitCost, total});
            assetRows.add(new Object[]{assetId, description, l.quantity, l.cleanUnit, l.invoiceDate, l.unitCost,
                    cut(l.purchasedFrom, 120), cut(l.invoiceNo, 100), l.departmentId, person, lineId, userId, batchId});
            moveRows.add(new Object[]{assetId, l.quantity, l.departmentId, person, order,
                    "Imported from the masterlist, row " + l.rowNo, userId});
        }
        p.total = lineRows.size() * 3;
        p.done = 0;
        batch("""
                insert into issue_order_line (id, issue_order_id, quantity, unit, description, unit_cost, total_cost)
                values (?, ?, ?, ?, ?, ?, ?)""", lineRows,
                new int[]{Types.BIGINT, Types.BIGINT, Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC}, p);
        batch("""
                insert into asset (id, description, quantity, unit, acquisition_date, unit_cost, supplier, invoice_no,
                    department_id, employee_id, asset_condition, status, issue_order_line_id, created_by, import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'GOOD', 'ISSUED', ?, ?, ?)""", assetRows,
                new int[]{Types.BIGINT, Types.VARCHAR, Types.INTEGER, Types.VARCHAR, Types.DATE, Types.NUMERIC, Types.VARCHAR,
                        Types.VARCHAR, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT}, p);
        batch("""
                insert into movement (asset_id, type, quantity, to_department_id, to_employee_id, issue_order_id, note, recorded_by)
                values (?, 'ISSUE', ?, ?, ?, ?, ?, ?)""", moveRows,
                new int[]{Types.BIGINT, Types.INTEGER, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.VARCHAR, Types.BIGINT}, p);

        // Every line that was not imported, with its reason (UC-107 step 6).
        p.stage = "Saving the review list";
        List<Object[]> skipped = new ArrayList<>();
        int setAside = 0;
        int rejected = 0;
        for (MasterlistParser.Line l : lines) {
            if (l.importable()) continue;
            if (l.outcome == MasterlistParser.Outcome.SET_ASIDE) setAside++;
            else rejected++;
            skipped.add(new Object[]{batchId, l.rowNo, l.outcome.name(), l.reason, toJson(l.cells)});
        }
        batch("insert into import_row (batch_id, row_no, outcome, reason, cells) values (?, ?, ?, ?, cast(? as jsonb))",
                skipped, new int[]{Types.BIGINT, Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR}, p);

        jdbc.update("""
                update import_batch set status = 'DONE', imported_rows = ?, set_aside_rows = ?, rejected_rows = ?,
                    issue_orders = ?, created_persons = ?, finished_at = now() where id = ?""",
                all.size(), setAside, rejected, byIo.size(), newPersons.size(), batchId);

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("rows", lines.size());
        v.put("imported", all.size());
        v.put("setAside", setAside);
        v.put("rejected", rejected);
        v.put("issueOrders", byIo.size());
        v.put("createdPersons", newPersons.size());
        audit.recordAs(userId, AuditAction.IMPORT, "import_batch", batchId, null, v);
        p.stage = "Finished";
    }

    // ------------------------------------------------------------------ helpers

    private Set<String> existing(Set<String> ioNos) {
        Set<String> found = new HashSet<>();
        List<String> all = List.copyOf(ioNos);
        for (int i = 0; i < all.size(); i += CHUNK) {
            List<String> part = all.subList(i, Math.min(i + CHUNK, all.size()));
            found.addAll(jdbc.queryForList("select trim(io_no) from issue_order where io_no::text = any (cast(? as text[]))", String.class,
                    (Object) part.toArray(String[]::new)));
        }
        return found;
    }

    /** n new ids from the table's own sequence, in one round trip, so the rows can be batch-inserted. */
    private List<Long> ids(String table, int n) {
        if (n == 0) return List.of();
        return jdbc.queryForList("select nextval(pg_get_serial_sequence(?, 'id')) from generate_series(1, ?)",
                Long.class, table, n);
    }

    private void batch(String sql, List<Object[]> rows, int[] types, Progress p) {
        for (int i = 0; i < rows.size(); i += CHUNK) {
            List<Object[]> part = rows.subList(i, Math.min(i + CHUNK, rows.size()));
            jdbc.batchUpdate(sql, part, types);
            p.done += part.size();
        }
    }

    /** Issue orders without any invoice date take the date of the nearest earlier one (or later one). */
    static void fillGaps(Map<String, LocalDate> dates) {
        LocalDate last = null;
        List<String> leading = new ArrayList<>();
        for (var e : dates.entrySet()) {
            if (e.getValue() != null) {
                last = e.getValue();
                for (String k : leading) dates.put(k, last);
                leading.clear();
            } else if (last != null) {
                e.setValue(last);
            } else {
                leading.add(e.getKey());
            }
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
        for (String k : leading) dates.put(k, today);
    }

    private static String personKey(Long departmentId, String name) {
        return departmentId + "|" + ValueMappingService.key(name).replaceAll("[.,]", "");
    }

    private static String joined(List<String> values, int max) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String v : values) if (v != null && !v.isBlank()) distinct.add(v.trim());
        if (distinct.isEmpty()) return null;
        return cut(String.join(" / ", distinct), max);
    }

    static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private String toJson(Map<String, String> cells) {
        try {
            return json.writeValueAsString(cells);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
