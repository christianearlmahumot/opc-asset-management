package edu.citu.opc.fams.report;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Writes a {@link ReportTable} as an .xlsx file with Apache POI (SDD 5.2 ExportService). Numbers, costs
 * and dates are real Excel values, not text, so the OPC can still sort, filter and add them up.
 * Streaming workbook: the full masterlist (15,000+ lines) fits in the small server's memory.
 */
final class ReportXlsx {

    private ReportXlsx() {
    }

    static byte[] write(ReportTable t) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            Styles st = new Styles(wb);
            Sheet sh = wb.createSheet(WorkbookUtil.createSafeSheetName(t.sheetName));
            int last = t.columns.size() - 1;
            int r = 0;

            if (t.excelHeadingRows) {
                r = text(sh, r, t.title, st.title, last);
                for (String s : t.subtitle) r = text(sh, r, s, st.subtitle, last);
                r++;
            }

            int headerRow = r;
            Row h = sh.createRow(r++);
            h.setHeightInPoints(30);
            for (int c = 0; c <= last; c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(t.columns.get(c).header());
                cell.setCellStyle(st.header);
                sh.setColumnWidth(c, Math.min(255, t.columns.get(c).width()) * 256);
            }

            for (ReportTable.Row row : t.rows) {
                Row x = sh.createRow(r);
                if (row.style() == ReportTable.Style.GROUP) {
                    Cell cell = x.createCell(0);
                    cell.setCellValue(String.valueOf(row.cells().get(0)));
                    cell.setCellStyle(st.group);
                    for (int c = 1; c <= last; c++) x.createCell(c).setCellStyle(st.group);
                    if (last > 0) sh.addMergedRegion(new CellRangeAddress(r, r, 0, last));
                } else {
                    boolean bold = row.style() != ReportTable.Style.ITEM;
                    for (int c = 0; c <= last; c++) {
                        cell(x.createCell(c), row.cells().get(c), t.columns.get(c).kind(), st, bold);
                    }
                }
                r++;
            }

            sh.createFreezePane(0, headerRow + 1);
            if (!t.excelHeadingRows) sh.setAutoFilter(new CellRangeAddress(headerRow, Math.max(headerRow, r - 1), 0, last));

            if (!t.notes.isEmpty()) {
                r++;
                for (String n : t.notes) r = text(sh, r, n, st.note, last);
            }

            sh.getPrintSetup().setLandscape(t.landscape);
            sh.setFitToPage(true);
            sh.getPrintSetup().setFitWidth((short) 1);
            sh.getPrintSetup().setFitHeight((short) 0);
            sh.setRepeatingRows(new CellRangeAddress(headerRow, headerRow, -1, -1));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);

            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the Excel file", e);
        }
    }

    private static int text(Sheet sh, int r, String value, CellStyle style, int last) {
        Cell c = sh.createRow(r).createCell(0);
        c.setCellValue(value);
        c.setCellStyle(style);
        return r + 1;
    }

    private static void cell(Cell c, Object v, ReportTable.Kind kind, Styles st, boolean bold) {
        c.setCellStyle(st.of(kind, bold));
        if (v == null) return;
        if (v instanceof BigDecimal b) c.setCellValue(b.doubleValue());
        else if (v instanceof Number n) c.setCellValue(n.doubleValue());
        else if (v instanceof LocalDate d) c.setCellValue(d);
        else c.setCellValue(String.valueOf(v));
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle subtitle;
        final CellStyle header;
        final CellStyle group;
        final CellStyle note;
        private final Map<ReportTable.Kind, CellStyle> normal = new EnumMap<>(ReportTable.Kind.class);
        private final Map<ReportTable.Kind, CellStyle> strong = new EnumMap<>(ReportTable.Kind.class);

        Styles(Workbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 13);
            Font italic = wb.createFont();
            italic.setItalic(true);

            title = wb.createCellStyle();
            title.setFont(big);
            subtitle = wb.createCellStyle();
            subtitle.setFont(bold);
            note = wb.createCellStyle();
            note.setFont(italic);

            header = bordered(wb);
            header.setFont(bold);
            header.setWrapText(true);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            group = bordered(wb);
            group.setFont(bold);
            group.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            group.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            short money = wb.createDataFormat().getFormat("#,##0.00");
            short date = wb.createDataFormat().getFormat("d-mmm-yy");
            short whole = wb.createDataFormat().getFormat("#,##0");
            for (ReportTable.Kind k : ReportTable.Kind.values()) {
                for (boolean b : new boolean[]{false, true}) {
                    CellStyle s = bordered(wb);
                    s.setVerticalAlignment(VerticalAlignment.TOP);
                    switch (k) {
                        case MONEY -> s.setDataFormat(money);
                        case DATE -> {
                            s.setDataFormat(date);
                            s.setAlignment(HorizontalAlignment.LEFT);
                        }
                        case INT -> s.setDataFormat(whole);
                        case TEXT -> s.setWrapText(true);
                    }
                    if (b) s.setFont(bold);
                    (b ? strong : normal).put(k, s);
                }
            }
        }

        CellStyle of(ReportTable.Kind k, boolean bold) {
            return (bold ? strong : normal).get(k);
        }

        private static CellStyle bordered(Workbook wb) {
            CellStyle s = wb.createCellStyle();
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            return s;
        }
    }
}
