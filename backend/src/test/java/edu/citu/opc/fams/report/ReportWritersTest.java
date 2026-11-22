package edu.citu.opc.fams.report;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC-305: the Excel and PDF writers, without a database. */
class ReportWritersTest {

    private static ReportTable masterlist() {
        ReportTable t = new ReportTable("Masterlist of Issue Orders");
        t.excelHeadingRows = false;
        t.column("Issue Order #", ReportTable.Kind.TEXT, 11)
                .column("Invoice Date", ReportTable.Kind.DATE, 11)
                .column("Qty", ReportTable.Kind.INT, 6)
                .column("Acquisition Unit Cost", ReportTable.Kind.MONEY, 14);
        return t;
    }

    @Test
    void excelKeepsRealNumbersAndDates() throws Exception {
        ReportTable t = masterlist();
        t.item("1804333", LocalDate.of(2026, 3, 5), 49, new BigDecimal("1250.50"));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(ReportXlsx.write(t)))) {
            Sheet s = wb.getSheetAt(0);
            assertEquals("Issue Order #", s.getRow(0).getCell(0).getStringCellValue());
            assertEquals("1804333", s.getRow(1).getCell(0).getStringCellValue());
            assertEquals(LocalDate.of(2026, 3, 5), s.getRow(1).getCell(1).getLocalDateTimeCellValue().toLocalDate());
            assertEquals(49, s.getRow(1).getCell(2).getNumericCellValue());
            assertEquals(1250.50, s.getRow(1).getCell(3).getNumericCellValue(), 0.001);
        }
    }

    @Test
    void emptyReportHasHeadersOnly() throws Exception {
        ReportTable t = masterlist();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(ReportXlsx.write(t)))) {
            Sheet s = wb.getSheetAt(0);
            assertEquals(0, s.getLastRowNum());
            assertEquals(4, s.getRow(0).getLastCellNum());
        }
        assertEquals(0, t.itemRows());
        byte[] pdf = ReportPdf.write(t, "CEBU INSTITUTE OF TECHNOLOGY - UNIVERSITY", "Generated now");
        assertTrue(new String(pdf, 0, 5).startsWith("%PDF"));
    }

    @Test
    void groupedReportWritesHeadingRowsAndTotals() throws Exception {
        ReportTable t = new ReportTable("Department Inventory Report");
        t.landscape = false;
        t.subtitle.add("CCS · College of Computer Studies");
        t.column("QTY", ReportTable.Kind.INT, 6).column("DESCRIPTION", ReportTable.Kind.TEXT, 50)
                .column("TOTAL COST", ReportTable.Kind.MONEY, 14);
        t.group("Room 101");
        t.item(2, "Monitor\nTAG-1 · Acer", new BigDecimal("10000.00"));
        t.subtotal(2, "Subtotal · Room 101", new BigDecimal("10000.00"));
        t.total(2, "GRAND TOTAL · 1 item", new BigDecimal("10000.00"));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(ReportXlsx.write(t)))) {
            Sheet s = wb.getSheetAt(0);
            assertEquals("Department Inventory Report", s.getRow(0).getCell(0).getStringCellValue());
            assertEquals("QTY", s.getRow(3).getCell(0).getStringCellValue());
            assertEquals("Room 101", s.getRow(4).getCell(0).getStringCellValue());
            assertEquals(10000.0, s.getRow(7).getCell(2).getNumericCellValue(), 0.001);
        }
        assertEquals(1, t.itemRows());
        assertTrue(ReportPdf.write(t, "CIT-U", "Generated now").length > 500);
    }
}
