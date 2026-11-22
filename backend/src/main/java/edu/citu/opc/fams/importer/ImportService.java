package edu.citu.opc.fams.importer;

import edu.citu.opc.fams.access.CurrentUser;
import edu.citu.opc.fams.common.ApiException;
import edu.citu.opc.fams.reference.Department;
import edu.citu.opc.fams.reference.DepartmentService;
import edu.citu.opc.fams.reference.UnitService;
import edu.citu.opc.fams.reference.ValueMappingService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UC-107 Import Excel Masterlist (SDD 2.3 ImportService). Three steps, as on the Import page:
 * <ol>
 *   <li>{@link #preview}: read the sheet, find the 14 columns, list the department and unit spellings
 *       that do not match the reference lists, and count the rows that will be set aside or rejected.</li>
 *   <li>{@link #start}: save the administrator's mappings (once, for every later import), then run the
 *       import in the background ({@link ImportJob}), as one transaction.</li>
 *   <li>{@link #batch} / {@link #rows}: follow the progress and review the rows that were not imported.</li>
 * </ol>
 * Nothing is saved by the preview. The file is never stored: it is uploaded again to start the import.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    static final String DEFAULT_SHEET = "Masterlist";
    static final String BLANK_UNIT = "(blank)";

    private final ImportBatchRepository batches;
    private final ImportJob job;
    private final ValueMappingService mappings;
    private final DepartmentService departments;
    private final UnitService units;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "masterlist-import");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<Long, ImportJob.Progress> progress = new ConcurrentHashMap<>();

    public ImportService(ImportBatchRepository batches, ImportJob job, ValueMappingService mappings,
                         DepartmentService departments, UnitService units, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.batches = batches;
        this.job = job;
        this.mappings = mappings;
        this.departments = departments;
        this.units = units;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedImports() {
        Integer n = tx.execute(s -> batches.failInterrupted());
        if (n != null && n > 0) log.warn("{} masterlist import(s) were cut off by a restart and marked FAILED", n);
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }

    // ------------------------------------------------------------------ step 1: preview

    public record ColumnInfo(String heading, String column, boolean found, boolean required) {
    }

    public record Problem(String outcome, String group, int rows) {
    }

    /** A department spelling with no match. {@code suggestedCode} is offered for a new department. */
    public record DepartmentLabel(String label, int rows, Long suggestedDepartmentId, String suggestedCode) {
    }

    /** A unit spelling with no match. {@code suggestedUnit} is an existing unit or a new one. */
    public record UnitLabel(String label, int rows, String suggestedUnit, boolean suggestedIsNew) {
    }

    public record Preview(String fileName, List<String> sheets, String sheet, int headerRow, List<ColumnInfo> columns,
                          int totalRows, int readyRows, int issueOrders, int issueOrdersAlreadyInSystem,
                          List<Problem> problems, int matchedDepartmentLabels, List<DepartmentLabel> departments,
                          int matchedUnitLabels, List<UnitLabel> units, boolean running) {
    }

    public Preview preview(MultipartFile file, String sheet) {
        Parsed p = parse(file, sheet);
        Map<String, Department> deptLookup = mappings.departmentLookup();
        Map<String, String> unitLookup = mappings.unitLookup();

        Map<String, Integer> deptRows = new LinkedHashMap<>();
        Map<String, Integer> unitRows = new LinkedHashMap<>();
        Map<String, String> deptSpelling = new HashMap<>();
        Map<String, String> unitSpelling = new HashMap<>();
        Set<String> ioNos = new HashSet<>();
        int ready = 0;
        for (MasterlistParser.Line l : p.result.lines()) {
            if (!l.importable()) continue;
            ready++;
            ioNos.add(l.ioNo);
            String dk = ValueMappingService.key(l.department);
            deptRows.merge(dk, 1, Integer::sum);
            deptSpelling.putIfAbsent(dk, l.department);
            String uk = unitKey(l.unit);
            unitRows.merge(uk, 1, Integer::sum);
            unitSpelling.putIfAbsent(uk, l.unit == null ? BLANK_UNIT : l.unit);
        }

        List<DepartmentLabel> deptOut = new ArrayList<>();
        int deptMatched = 0;
        for (var e : deptRows.entrySet()) {
            if (deptLookup.containsKey(e.getKey())) {
                deptMatched++;
                continue;
            }
            String label = deptSpelling.get(e.getKey());
            deptOut.add(new DepartmentLabel(label, e.getValue(), null, suggestCode(label)));
        }
        deptOut.sort(Comparator.comparing(DepartmentLabel::rows).reversed().thenComparing(DepartmentLabel::label));

        List<UnitLabel> unitOut = new ArrayList<>();
        int unitMatched = 0;
        Set<String> clean = new HashSet<>(unitLookup.values());
        for (var e : unitRows.entrySet()) {
            if (resolveUnit(e.getKey(), unitLookup) != null) {
                unitMatched++;
                continue;
            }
            String label = unitSpelling.get(e.getKey());
            String guess = guessUnit(e.getKey(), clean);
            unitOut.add(new UnitLabel(label, e.getValue(), guess, guess != null && !clean.contains(guess)));
        }
        unitOut.sort(Comparator.comparing(UnitLabel::rows).reversed().thenComparing(UnitLabel::label));

        Map<String, Problem> problems = new LinkedHashMap<>();
        for (MasterlistParser.Line l : p.result.lines()) {
            if (l.importable()) continue;
            problems.merge(l.outcome + "|" + l.reasonGroup, new Problem(l.outcome.name(), l.reasonGroup, 1),
                    (a, b) -> new Problem(a.outcome(), a.group(), a.rows() + 1));
        }
        List<Problem> problemList = new ArrayList<>(problems.values());
        problemList.sort(Comparator.comparing(Problem::rows).reversed());

        return new Preview(p.fileName, p.sheets, p.sheet, p.result.headerRow(), columns(p.result),
                p.result.lines().size(), ready, ioNos.size(), existingIssueOrders(ioNos).size(), problemList,
                deptMatched, deptOut, unitMatched, unitOut, running.get());
    }

    // ------------------------------------------------------------------ step 2: map and start

    /** {@code action}: MAP to an existing department, NEW to create one, SET_ASIDE to leave its rows out. */
    public record DepartmentChoice(String label, String action, Long departmentId, String code, String name) {
    }

    /** {@code action}: MAP to an existing unit, NEW to add one, SET_ASIDE to leave its rows out. */
    public record UnitChoice(String label, String action, String unit) {
    }

    public record StartRequest(String sheet, List<DepartmentChoice> departments, List<UnitChoice> units) {
    }

    public ImportBatch start(MultipartFile file, StartRequest r) {
        CurrentUser me = CurrentUser.get();
        if (batches.existsByStatus(ImportBatch.Status.RUNNING) || running.get()) {
            throw ApiException.conflict("An import is already running. Wait for it to finish.");
        }
        Parsed p = parse(file, r == null ? null : r.sheet());
        List<DepartmentChoice> deptChoices = r == null || r.departments() == null ? List.of() : r.departments();
        List<UnitChoice> unitChoices = r == null || r.units() == null ? List.of() : r.units();

        ImportBatch batch = tx.execute(s -> {
            Map<String, Department> deptLookup = mappings.departmentLookup();
            Map<String, String> unitLookup = mappings.unitLookup();
            Map<String, DepartmentChoice> dc = new HashMap<>();
            for (DepartmentChoice c : deptChoices) if (c.label() != null) dc.put(ValueMappingService.key(c.label()), c);
            Map<String, UnitChoice> uc = new HashMap<>();
            for (UnitChoice c : unitChoices) if (c.label() != null) uc.put(unitKey(BLANK_UNIT.equals(c.label()) ? null : c.label()), c);

            // Apply the administrator's choices. Mappings are saved so the next import needs none.
            Set<String> unmappedDepts = new java.util.TreeSet<>();
            Set<String> unmappedUnits = new java.util.TreeSet<>();
            Map<String, Department> createdDepts = new HashMap<>();
            for (MasterlistParser.Line l : p.result.lines()) {
                if (!l.importable()) continue;
                String dk = ValueMappingService.key(l.department);
                Department d = deptLookup.get(dk);
                if (d == null) {
                    DepartmentChoice c = dc.get(dk);
                    if (c == null || c.action() == null) {
                        unmappedDepts.add(l.department);
                    } else if ("SET_ASIDE".equals(c.action())) {
                        l.setAside("Department set aside when mapping", "Department '" + l.department + "' was set aside when mapping");
                    } else {
                        d = applyDepartment(c, l.department, createdDepts);
                        deptLookup.put(dk, d);
                    }
                }
                l.departmentId = d == null ? null : d.getId();

                String uk = unitKey(l.unit);
                String u = resolveUnit(uk, unitLookup);
                if (u == null && l.importable()) {
                    UnitChoice c = uc.get(uk);
                    if (c == null || c.action() == null) {
                        unmappedUnits.add(l.unit == null ? BLANK_UNIT : l.unit);
                    } else if ("SET_ASIDE".equals(c.action())) {
                        l.setAside("Unit set aside when mapping", "Unit '" + (l.unit == null ? BLANK_UNIT : l.unit) + "' was set aside when mapping");
                    } else {
                        u = applyUnit(c, l.unit, unitLookup);
                        unitLookup.put(uk, u);
                    }
                }
                l.cleanUnit = u;
            }
            if (!unmappedDepts.isEmpty() || !unmappedUnits.isEmpty()) {
                List<String> parts = new ArrayList<>();
                if (!unmappedDepts.isEmpty()) parts.add(unmappedDepts.size() + " department spelling(s)");
                if (!unmappedUnits.isEmpty()) parts.add(unmappedUnits.size() + " unit spelling(s)");
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Map every spelling first: " + String.join(" and ", parts) + " still have no match.",
                        Map.of(), Map.of("departments", unmappedDepts, "units", unmappedUnits));
            }
            long importable = p.result.lines().stream().filter(MasterlistParser.Line::importable).count();
            if (importable == 0) {
                // UC-107 4a: every row fails validation, so nothing is saved.
                s.setRollbackOnly();
                return null;
            }
            return batches.save(new ImportBatch(p.fileName, p.sheet, p.result.lines().size(), me.id()));
        });
        if (batch == null) {
            throw ApiException.badRequest("No row can be imported, so nothing was saved. Check the reasons in the preview.");
        }

        if (!running.compareAndSet(false, true)) {
            throw ApiException.conflict("An import is already running. Wait for it to finish.");
        }
        ImportJob.Progress pr = new ImportJob.Progress();
        progress.put(batch.getId(), pr);
        Long batchId = batch.getId();
        List<MasterlistParser.Line> lines = p.result.lines();
        worker.submit(() -> {
            try {
                job.run(batchId, me.id(), lines, pr);
            } catch (Throwable t) {   // the job's transaction was rolled back; record why
                log.error("Masterlist import {} failed", batchId, t);
                String why = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                jdbc.update("update import_batch set status = 'FAILED', error = ?, finished_at = now() where id = ?",
                        "Nothing was saved. " + (why.length() > 450 ? why.substring(0, 450) : why), batchId);
            } finally {
                running.set(false);
                pr.stage = "Finished";
            }
        });
        return batch;
    }

    private Department applyDepartment(DepartmentChoice c, String label, Map<String, Department> created) {
        Department d;
        if ("MAP".equals(c.action())) {
            if (c.departmentId() == null) throw ApiException.field("departments", "Choose the department for '" + label + "'.");
            d = departments.get(c.departmentId());
        } else if ("NEW".equals(c.action())) {
            String code = c.code() == null ? "" : c.code().trim().toUpperCase(Locale.ROOT);
            d = created.get(code);
            if (d == null) {
                d = departments.findByCode(code).orElse(null);
                if (d == null) {
                    String name = c.name() == null || c.name().isBlank() ? label : c.name().trim();
                    try {
                        d = departments.create(code, name.length() > 120 ? name.substring(0, 120) : name, null, null);
                    } catch (ApiException e) {
                        throw ApiException.field("departments", "'" + label + "': " + e.getFields().getOrDefault("code", e.getMessage()));
                    }
                }
                created.put(code, d);
            }
        } else {
            throw ApiException.field("departments", "Choose what to do with '" + label + "'.");
        }
        mappings.mapDepartment(label, d);
        return d;
    }

    private String applyUnit(UnitChoice c, String label, Map<String, String> lookup) {
        String u = c.unit() == null ? "" : c.unit().trim().toLowerCase(Locale.ROOT);
        if (u.isEmpty()) throw ApiException.field("units", "Choose the unit for '" + (label == null ? BLANK_UNIT : label) + "'.");
        boolean exists = lookup.containsValue(u);
        if ("NEW".equals(c.action()) && !exists) {
            try {
                units.add(u);
            } catch (ApiException e) {
                throw ApiException.field("units", "'" + u + "': " + e.getFields().getOrDefault("unit", e.getMessage()));
            }
            lookup.put(ValueMappingService.key(u), u);
        } else if (!exists) {
            throw ApiException.field("units", "The unit '" + u + "' is not in the list.");
        }
        if (label != null) mappings.mapUnit(label, u);
        return u;
    }

    // ------------------------------------------------------------------ step 3: progress and review

    public record BatchView(ImportBatch batch, String stage, int done, int total) {
    }

    public BatchView batch(Long id) {
        ImportBatch b = batches.findById(id).orElseThrow(() -> ApiException.notFound("Import"));
        ImportJob.Progress p = progress.get(id);
        if (b.getStatus() == ImportBatch.Status.RUNNING && p != null) return new BatchView(b, p.stage, p.done, p.total);
        return new BatchView(b, null, 0, 0);
    }

    public List<ImportBatch> recent() {
        return batches.findTop20ByOrderByImportedAtDescIdDesc();
    }

    public record RowView(int rowNo, String outcome, String reason, String cells) {
    }

    public record RowPage(List<RowView> items, int page, int size, long total, int totalPages) {
    }

    public RowPage rows(Long batchId, String outcome, int page, int size) {
        batches.findById(batchId).orElseThrow(() -> ApiException.notFound("Import"));
        String o = outcome == null || outcome.isBlank() ? null : outcome.trim().toUpperCase(Locale.ROOT);
        int sz = Math.min(Math.max(size, 1), 200);
        int pg = Math.max(page, 0);
        Long total = jdbc.queryForObject("select count(*) from import_row where batch_id = ? and (?::text is null or outcome = ?)",
                Long.class, batchId, o, o);
        List<RowView> items = jdbc.query("""
                        select row_no, outcome, reason, cells::text from import_row
                        where batch_id = ? and (?::text is null or outcome = ?)
                        order by row_no limit ? offset ?""",
                (rs, i) -> new RowView(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                batchId, o, o, sz, (long) pg * sz);
        long t = total == null ? 0 : total;
        return new RowPage(items, pg, sz, t, (int) ((t + sz - 1) / sz));
    }

    /** Every row that was not imported, in file order, for the Excel download. */
    List<RowView> allRows(Long batchId) {
        batches.findById(batchId).orElseThrow(() -> ApiException.notFound("Import"));
        return jdbc.query("select row_no, outcome, reason, cells::text from import_row where batch_id = ? order by row_no",
                (rs, i) -> new RowView(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)), batchId);
    }

    // ------------------------------------------------------------------ helpers

    private record Parsed(String fileName, List<String> sheets, String sheet, MasterlistParser.Result result) {
    }

    private Parsed parse(MultipartFile file, String sheet) {
        if (file == null || file.isEmpty()) throw ApiException.field("file", "Choose the masterlist .xlsx file.");
        String name = file.getOriginalFilename() == null ? "masterlist.xlsx" : file.getOriginalFilename();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw ApiException.field("file", "The file must be an Excel .xlsx file. Save it as .xlsx in Excel first.");   // UC-107 1a
        }
        Path tmp = null;
        try {
            tmp = Files.createTempFile("masterlist-", ".xlsx");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            List<String> sheets = SheetReader.sheetNames(tmp);
            String chosen = sheet != null && !sheet.isBlank() ? sheet
                    : sheets.contains(DEFAULT_SHEET) ? DEFAULT_SHEET
                    : sheets.stream().filter(s -> s.toLowerCase(Locale.ROOT).contains("master")).findFirst()
                    .orElse(sheets.isEmpty() ? "" : sheets.get(0));
            if (!sheets.contains(chosen)) throw ApiException.field("sheet", "The workbook has no sheet named '" + chosen + "'.");
            MasterlistParser.Result result = MasterlistParser.parse(SheetReader.read(tmp, chosen, 40));
            List<String> missingRequired = result.missing().stream().filter(f -> f.required).map(f -> f.heading).toList();
            if (result.headerRow() == 0 || !missingRequired.isEmpty()) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        result.headerRow() == 0
                                ? "The sheet '" + chosen + "' has no masterlist headings (Issue Order #, Department, Qty…). Choose the Masterlist sheet."
                                : "The sheet is missing these columns: " + String.join(", ", missingRequired) + ".",
                        Map.of("sheet", "Choose the Masterlist sheet."), Map.of("sheets", sheets));
            }
            return new Parsed(name, sheets, chosen, result);
        } catch (IOException e) {
            throw ApiException.field("file", e.getMessage() == null ? "The file could not be read." : e.getMessage());
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // the OS cleans its temp folder
                }
            }
        }
    }

    private static List<ColumnInfo> columns(MasterlistParser.Result r) {
        List<ColumnInfo> out = new ArrayList<>();
        for (MasterlistParser.Field f : MasterlistParser.Field.values()) {
            Integer c = r.columns().get(f);
            out.add(new ColumnInfo(f.heading, c == null ? null : letter(c), c != null, f.required));
        }
        return out;
    }

    private static String letter(int c) {
        StringBuilder s = new StringBuilder();
        for (int n = c + 1; n > 0; n = (n - 1) / 26) s.insert(0, (char) ('A' + (n - 1) % 26));
        return s.toString();
    }

    private Set<String> existingIssueOrders(Set<String> ioNos) {
        Set<String> found = new HashSet<>();
        if (ioNos.isEmpty()) return found;
        List<String> all = ioNos.stream().toList();
        for (int i = 0; i < all.size(); i += 1000) {
            List<String> part = all.subList(i, Math.min(i + 1000, all.size()));
            found.addAll(jdbc.queryForList("select trim(io_no) from issue_order where io_no::text = any (cast(? as text[]))", String.class,
                    (Object) part.toArray(String[]::new)));
        }
        return found;
    }

    static String unitKey(String unit) {
        return unit == null ? "" : ValueMappingService.key(unit);
    }

    /** A saved mapping, or the same spelling without a trailing dot ("pc." → "pc"). */
    static String resolveUnit(String key, Map<String, String> lookup) {
        if (key.isEmpty()) return null;
        String u = lookup.get(key);
        if (u == null && key.endsWith(".")) u = lookup.get(key.substring(0, key.length() - 1).trim());
        return u;
    }

    /** "pairs" → "pair" (existing or new), "bxs" → "box", "mtrs" → "meter"; otherwise the spelling itself. */
    private static String guessUnit(String key, Set<String> clean) {
        if (key.isEmpty()) return null;
        Map<String, String> known = Map.of("bxs", "box", "pcks", "pack", "pck", "pack", "mtrs", "meter",
                "sht", "sheet", "shts", "sheet", "cyls", "cylinder", "cyl", "cylinder", "kgs", "kg", "gals", "gallon");
        String k = key.replaceAll("\\.$", "");
        if (known.containsKey(k)) return known.get(k);
        if (k.endsWith("es") && clean.contains(k.substring(0, k.length() - 2))) return k.substring(0, k.length() - 2);
        if (k.endsWith("s") && k.length() > 2) return k.substring(0, k.length() - 1);
        return k.matches("[a-z][a-z .-]{0,19}") ? k : null;
    }

    /** "LIBRARY - College" → "LIBRARY-COLLEGE", within the 20-character code limit. */
    static String suggestCode(String label) {
        String c = label.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (c.length() > 20) c = c.substring(0, 20).replaceAll("-+$", "");
        return c.isEmpty() ? null : c;
    }
}
