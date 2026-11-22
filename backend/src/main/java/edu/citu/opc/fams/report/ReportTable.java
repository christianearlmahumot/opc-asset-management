package edu.citu.opc.fams.report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A report as a plain table, so the Excel and PDF writers share one description of each OPC report:
 * the heading lines, the columns, and the rows (item rows, plus room headings and totals where the
 * report has them).
 */
final class ReportTable {

    enum Kind { TEXT, INT, MONEY, DATE }

    /** {@code width} is in Excel characters; the PDF uses the same numbers as relative widths. */
    record Column(String header, Kind kind, int width) {
    }

    enum Style { ITEM, GROUP, SUBTOTAL, TOTAL }

    /** For GROUP rows only the first cell is used, spread over all columns. */
    record Row(Style style, List<Object> cells) {
    }

    final String title;
    final List<String> subtitle = new ArrayList<>();
    final List<Column> columns = new ArrayList<>();
    final List<Row> rows = new ArrayList<>();
    final List<String> notes = new ArrayList<>();
    /** The OPC's masterlist is a plain data sheet: headers on row 1, nothing above them. */
    boolean excelHeadingRows = true;
    boolean landscape = true;
    String sheetName;
    String emptyText = "No records match the selected filters.";
    private int itemRows;

    ReportTable(String title) {
        this.title = title;
        this.sheetName = title;
    }

    ReportTable column(String header, Kind kind, int width) {
        columns.add(new Column(header, kind, width));
        return this;
    }

    void item(Object... cells) {
        add(Style.ITEM, cells);
        itemRows++;
    }

    void group(String text) {
        add(Style.GROUP, new Object[]{text});
    }

    void subtotal(Object... cells) {
        add(Style.SUBTOTAL, cells);
    }

    void total(Object... cells) {
        add(Style.TOTAL, cells);
    }

    private void add(Style s, Object[] cells) {
        if (s != Style.GROUP && cells.length != columns.size()) {
            throw new IllegalArgumentException(title + ": " + cells.length + " cells for " + columns.size() + " columns");
        }
        rows.add(new Row(s, Arrays.asList(cells)));
    }

    int itemRows() {
        return itemRows;
    }
}
