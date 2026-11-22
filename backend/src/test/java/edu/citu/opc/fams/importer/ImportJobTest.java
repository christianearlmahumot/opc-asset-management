package edu.citu.opc.fams.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * UC-107 step 6 against a real PostgreSQL with all Flyway migrations (an embedded server, no Docker):
 * the bulk SQL, the issue-order rules, and the all-or-nothing transaction.
 */
class ImportJobTest {

    private static EmbeddedPostgres pg;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static final AuditService audit = mock(AuditService.class);
    private static ImportJob job;
    private static long admin;

    @BeforeAll
    static void startDatabase() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        javax.sql.DataSource ds = pg.getPostgresDatabase();   // one DataSource, so the transaction and the queries share it
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        job = new ImportJob(jdbc, tx, audit, new ObjectMapper());
        admin = jdbc.queryForObject("insert into app_user (username, password_hash, full_name, role) "
                + "values ('admin', 'x', 'Admin', 'ADMIN') returning id", Long.class);
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (pg != null) pg.close();
    }

    @BeforeEach
    void clean() {
        jdbc.execute("truncate movement, asset, issue_order_line, issue_order, import_row, import_batch, employee restart identity cascade");
        jdbc.execute("delete from department where code <> 'OPC'");
        reset(audit);
    }

    private static long dept(String code) {
        List<Long> found = jdbc.queryForList("select id from department where code = ?", Long.class, code);
        if (!found.isEmpty()) return found.get(0);
        return jdbc.queryForObject("insert into department (code, name) values (?, ?) returning id", Long.class, code, code);
    }

    private static long batch() {
        return jdbc.queryForObject("insert into import_batch (file_name, status, total_rows) values ('t.xlsx', 'RUNNING', 0) returning id", Long.class);
    }

    private static MasterlistParser.Line line(int row, String io, Long deptId, String dept, String person, int qty, String cost) {
        MasterlistParser.Line l = new MasterlistParser.Line(row);
        l.ioNo = io;
        l.departmentId = deptId;
        l.department = dept;
        l.person = person;
        l.quantity = qty;
        l.cleanUnit = "pc";
        l.description = "Item on row " + row;
        l.unitCost = cost == null ? null : new BigDecimal(cost);
        l.invoiceDate = row == 4 ? null : LocalDate.of(2020, 1, row);
        l.issuedBy = "TINA";
        l.cells.put("Issue Order #", io);
        return l;
    }

    @Test
    void savesIssueOrdersItemsAndMovementsAndSetsTheRestAside() {
        long me = dept("ME");
        long ce = dept("CE");
        jdbc.update("insert into employee (full_name, department_id) values ('Engr. Roberto P. Base Jr.', ?)", me);
        jdbc.update("insert into issue_order (io_no, department_id, issued_on) values ('1804332', ?, current_date)", me);
        long b = batch();

        List<MasterlistParser.Line> lines = new ArrayList<>();
        lines.add(line(4, "0012774", me, "ME", "engr. roberto p. base jr", 2, "3750.00"));   // matches the existing person
        lines.add(line(5, "0012774", me, "ME ", "Mrs. New Person", 1, null));
        lines.add(line(6, "0012774", ce, "CE", null, 1, "10.00"));            // other department: set aside
        lines.add(line(7, "1804332", me, "ME", null, 1, "10.00"));            // already in the system
        lines.add(line(8, "1804400", ce, "CE", null, 5, "1.50"));
        MasterlistParser.Line rejected = line(9, "1804401", ce, "CE", null, 1, null);
        rejected.reject("Quantity is 0", "Qty is 0");
        lines.add(rejected);

        job.run(b, admin, lines, new ImportJob.Progress());

        assertEquals(2, count("issue_order where import_batch_id = " + b));
        assertEquals(3, count("asset where import_batch_id = " + b + " and status = 'ISSUED'"));
        assertEquals(3, count("issue_order_line"));
        assertEquals(3, count("movement where type = 'ISSUE'"));
        assertEquals(2, count("employee"));   // one existing, one created
        assertEquals(3, count("import_row where batch_id = " + b));
        assertEquals(1, count("import_row where outcome = 'REJECTED'"));

        Map<String, Object> io = jdbc.queryForMap("select * from issue_order where io_no = '0012774'");
        assertEquals(me, ((Number) io.get("department_id")).longValue());
        assertEquals(LocalDate.of(2020, 1, 4).plusDays(1), ((java.sql.Date) io.get("issued_on")).toLocalDate());
        assertEquals("TINA", io.get("issued_by_name"));
        Map<String, Object> io2 = jdbc.queryForMap("select issued_on from issue_order where io_no = '1804400'");
        assertEquals(LocalDate.of(2020, 1, 8), ((java.sql.Date) io2.get("issued_on")).toLocalDate());

        BigDecimal total = jdbc.queryForObject("select total_cost from issue_order_line where quantity = 2", BigDecimal.class);
        assertEquals(0, new BigDecimal("7500.00").compareTo(total));
        Long holder = jdbc.queryForObject("select a.employee_id from asset a join issue_order_line l on l.id = a.issue_order_line_id where l.quantity = 2", Long.class);
        assertEquals(1L, holder);   // the existing employee, matched by name

        Map<String, Object> bt = jdbc.queryForMap("select * from import_batch where id = ?", b);
        assertEquals("DONE", bt.get("status"));
        assertEquals(3, bt.get("imported_rows"));
        assertEquals(2, bt.get("set_aside_rows"));
        assertEquals(1, bt.get("rejected_rows"));
        assertEquals(1, bt.get("created_persons"));
        verify(audit).recordAs(eq(admin), eq(AuditAction.IMPORT), eq("import_batch"), eq(b), any(), anyMap());

        // A new issue order after the import continues after the highest number.
        assertEquals("1804400", jdbc.queryForObject("select max(io_no) from issue_order", String.class));
    }

    @Test
    void savesNothingWhenAnythingFails() {
        long me = dept("ME");
        long b = batch();
        doThrow(new IllegalStateException("audit down")).when(audit).recordAs(any(), any(), any(), any(), any(), anyMap());
        List<MasterlistParser.Line> lines = List.of(line(4, "1804500", me, "ME", "Someone", 1, "5.00"));
        assertThrows(IllegalStateException.class, () -> job.run(b, admin, lines, new ImportJob.Progress()));
        assertEquals(0, count("issue_order"));
        assertEquals(0, count("asset"));
        assertEquals(0, count("employee"));
        assertEquals("RUNNING", jdbc.queryForObject("select status from import_batch where id = ?", String.class, b));
    }

    /** Run with -Dmasterlist.file=C:\path\file.xlsx: the whole real file, every spelling given its own department. */
    @Test
    void importsTheRealMasterlist() throws Exception {
        String path = System.getProperty("masterlist.file");
        Assumptions.assumeTrue(path != null && !path.isBlank(), "masterlist.file not set");
        MasterlistParser.Result r = MasterlistParser.parse(SheetReader.read(Path.of(path), "Masterlist", 40));
        Map<String, Long> depts = new HashMap<>();
        Map<String, Long> codes = new HashMap<>();
        for (MasterlistParser.Line l : r.lines()) {
            if (!l.importable()) continue;
            String key = l.department.trim().toLowerCase();
            String code = ImportService.suggestCode(l.department);
            l.departmentId = depts.computeIfAbsent(key, k -> codes.computeIfAbsent(code, ImportJobTest::dept));
            l.cleanUnit = l.unit == null ? "pc" : l.unit.trim().toLowerCase().replaceAll("[^a-z]", "");
            if (l.cleanUnit.isEmpty()) l.cleanUnit = "pc";
        }
        long b = batch();
        long start = System.currentTimeMillis();
        job.run(b, admin, r.lines(), new ImportJob.Progress());
        Map<String, Object> bt = jdbc.queryForMap("select * from import_batch where id = ?", b);
        System.out.println("real masterlist: " + bt + " in " + (System.currentTimeMillis() - start) + " ms");
        assertEquals("DONE", bt.get("status"));
        assertTrue((Integer) bt.get("imported_rows") > 14000);
        assertEquals(count("asset"), count("movement"));
        assertEquals(r.lines().size(), (Integer) bt.get("imported_rows") + (Integer) bt.get("set_aside_rows") + (Integer) bt.get("rejected_rows"));
    }

    private static long count(String fromWhere) {
        Long n = jdbc.queryForObject("select count(*) from " + fromWhere, Long.class);
        return n == null ? 0 : n;
    }
}
