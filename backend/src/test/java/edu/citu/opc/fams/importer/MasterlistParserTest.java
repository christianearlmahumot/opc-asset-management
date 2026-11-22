package edu.citu.opc.fams.importer;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC-107: reading the masterlist layout and the set-aside / reject rules, without a database. */
class MasterlistParserTest {

    private static final String[] HEADINGS = {"ISSUE ORDER #", "ISSUED BY", "DEPARTMENT", "ACCOUNTABLE PERSON",
            "DESIGNATION", "INVOICE DATE", "PURCHASED from", "INVOICE #", "QTY", "UNIT", "ITEMS/DESCRIPTION",
            "ACQUISITION UNIT COST", "ACQUISITION TOTAL COST", "Remarks"};

    @Test
    void readsTheOpcLayoutAndAppliesTheRules() throws Exception {
        Path file = Files.createTempFile("ml-test-", ".xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("Administrators");
            Sheet s = wb.createSheet("Masterlist");
            s.createRow(0).createCell(0).setCellValue("CIT-U (ISSUE ORDER) INVENTORY RECORD MONITORING");
            Row h = s.createRow(2);
            for (int i = 0; i < HEADINGS.length; i++) h.createCell(i).setCellValue(HEADINGS[i]);
            CellStyle date = wb.createCellStyle();
            date.setDataFormat(wb.createDataFormat().getFormat("mm/dd/yy;@"));

            row(s, 3, 12774d, "TINA", "ME ", "Engr. A", "Chair", LocalDate.of(2017, 8, 1), date, "CEBU", 18347d, 2d, "PC", "Monitor", 3750d, 7500d, null);
            row(s, 4, 1804001d, "cancelled", "ME", "Engr. A", null, null, null, null, null, 1d, "pc", "Mouse", 100d, 100d, null);
            row(s, 5, "N/A", "TINA", "ME", null, null, null, null, null, null, 1d, "pc", "Chair", null, null, null);
            row(s, 6, "1803681A", "TINA", "ME", null, null, null, null, null, null, 1d, "pc", "Chair", null, null, null);
            row(s, 7, 1804002d, "TINA", "ME", null, null, "10/3/2023 - 9/19/2023", null, null, "X1", 0d, "pc", "Desk", "4,185.00", null, null);
            row(s, 8, 1804003d, "TINA", null, null, null, "POdate 7/13/2019", null, null, null, 1d, "pc", "Desk", null, "#VALUE!", null);
            row(s, 9, 1804004d, "TINA", "CE", null, null, "POdate 7/13/2019", null, null, null, 3d, "copies", "Book", null, 300d, "LIB - books");
            try (OutputStream out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }
        try {
            assertEquals(List.of("Administrators", "Masterlist"), SheetReader.sheetNames(file));
            MasterlistParser.Result r = MasterlistParser.parse(SheetReader.read(file, "Masterlist", 40));
            assertEquals(3, r.headerRow());
            assertTrue(r.missing().isEmpty());
            List<MasterlistParser.Line> l = r.lines();
            assertEquals(7, l.size());

            MasterlistParser.Line a = l.get(0);
            assertTrue(a.importable());
            assertEquals("0012774", a.ioNo);
            assertEquals("ME", a.department);
            assertEquals(LocalDate.of(2017, 8, 1), a.invoiceDate);
            assertEquals("18347", a.invoiceNo);
            assertEquals(2, a.quantity);
            assertEquals(new BigDecimal("3750.00"), a.unitCost);

            assertEquals("Marked cancelled, deleted or superseded", l.get(1).reasonGroup);
            assertEquals("Not an issue order", l.get(2).reasonGroup);
            assertEquals("Issue order number with letters", l.get(3).reasonGroup);
            assertEquals(MasterlistParser.Outcome.REJECTED, l.get(4).outcome);
            assertEquals("Quantity is 0", l.get(4).reasonGroup);
            assertEquals(LocalDate.of(2023, 10, 3), l.get(4).invoiceDate);
            assertEquals("No department", l.get(5).reasonGroup);
            assertNull(l.get(5).unitCost);

            MasterlistParser.Line b = l.get(6);
            assertTrue(b.importable());
            assertEquals(new BigDecimal("100.00"), b.unitCost);   // from the total, 300 / 3
            assertEquals(LocalDate.of(2019, 7, 13), b.invoiceDate);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void row(Sheet s, int r, Object io, Object by, Object dept, Object person, Object desig,
                            Object invDate, CellStyle dateStyle, Object from, Object inv, Object qty, Object unit,
                            Object desc, Object unitCost, Object total, Object remarks) {
        Row x = s.createRow(r);
        Object[] v = {io, by, dept, person, desig, invDate, from, inv, qty, unit, desc, unitCost, total, remarks};
        for (int c = 0; c < v.length; c++) {
            if (v[c] == null) continue;
            var cell = x.createCell(c);
            if (v[c] instanceof Double d) cell.setCellValue(d);
            else if (v[c] instanceof LocalDate d) {
                cell.setCellValue(d);
                cell.setCellStyle(dateStyle);
            } else if ("#VALUE!".equals(v[c])) cell.setCellErrorValue(org.apache.poi.ss.usermodel.FormulaError.VALUE.getCode());
            else cell.setCellValue(String.valueOf(v[c]));
        }
    }

    @Test
    void fillsMissingIssueDatesFromTheNeighbours() {
        Map<String, LocalDate> d = new TreeMap<>();
        d.put("0000001", null);
        d.put("0000002", LocalDate.of(2020, 1, 5));
        d.put("0000003", null);
        ImportJob.fillGaps(d);
        assertEquals(LocalDate.of(2020, 1, 5), d.get("0000001"));
        assertEquals(LocalDate.of(2020, 1, 5), d.get("0000003"));
    }

    @Test
    void suggestsDepartmentCodes() {
        assertEquals("LIBRARY-COLLEGE", ImportService.suggestCode("LIBRARY - College"));
        assertEquals("CMBA-DHTM", ImportService.suggestCode("CMBA-DHTM"));
    }

    /** Run with -Dmasterlist.file=C:\path\file.xlsx to check the real OPC file. Skipped otherwise. */
    @Test
    void summarizesTheRealMasterlist() throws Exception {
        String path = System.getProperty("masterlist.file");
        Assumptions.assumeTrue(path != null && !path.isBlank(), "masterlist.file not set");
        long start = System.currentTimeMillis();
        MasterlistParser.Result r = MasterlistParser.parse(SheetReader.read(Path.of(path), "Masterlist", 40));
        Map<String, Integer> groups = new TreeMap<>();
        int ok = 0;
        for (MasterlistParser.Line l : r.lines()) {
            if (l.importable()) ok++;
            else groups.merge(l.outcome + " · " + l.reasonGroup, 1, Integer::sum);
        }
        System.out.println("header row " + r.headerRow() + ", missing " + r.missing() + ", lines " + r.lines().size()
                + ", importable " + ok + ", " + (System.currentTimeMillis() - start) + " ms");
        groups.forEach((k, v) -> System.out.println("  " + k + ": " + v));
        long noDate = r.lines().stream().filter(MasterlistParser.Line::importable).filter(l -> l.invoiceDate == null).count();
        long noCost = r.lines().stream().filter(MasterlistParser.Line::importable).filter(l -> l.unitCost == null).count();
        System.out.println("  importable without invoice date: " + noDate + ", without unit cost: " + noCost);
        assertTrue(ok > 0);
    }
}
