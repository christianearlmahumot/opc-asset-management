package edu.citu.opc.fams.importer;

import org.apache.poi.ss.usermodel.DateUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UC-107 step 2 and 5: finds the 14 masterlist columns by their headings, reads each line, and sets
 * aside or rejects the lines that cannot become an issue order line, each with its reason. Pure
 * logic, no database: department and unit spellings are resolved later, against the reference lists.
 */
final class MasterlistParser {

    /** The 14 columns of the OPC masterlist (SRS UC-107 step 2). */
    enum Field {
        IO_NO("Issue Order #", true),
        ISSUED_BY("Issued By", false),
        DEPARTMENT("Department", true),
        PERSON("Accountable Person", false),
        DESIGNATION("Designation", false),
        INVOICE_DATE("Invoice Date", false),
        PURCHASED_FROM("Purchased From", false),
        INVOICE_NO("Invoice #", false),
        QTY("Qty", true),
        UNIT("Unit", true),
        DESCRIPTION("Items/Description", true),
        UNIT_COST("Acquisition Unit Cost", false),
        TOTAL_COST("Acquisition Total Cost", false),
        REMARKS("Remarks", false);

        final String heading;
        final boolean required;

        Field(String heading, boolean required) {
            this.heading = heading;
            this.required = required;
        }

        String key() {
            return squash(heading);
        }
    }

    enum Outcome { SET_ASIDE, REJECTED }

    /** One masterlist line. {@code outcome} is null while the line can still be imported. */
    static final class Line {
        final int rowNo;
        final Map<String, String> cells = new LinkedHashMap<>();   // as written, for the review list
        String ioNo;            // 7 digits
        String issuedBy;
        String department;      // as written; resolved later
        String person;
        String designation;
        LocalDate invoiceDate;
        String purchasedFrom;
        String invoiceNo;
        Integer quantity;
        String unit;            // as written; resolved later
        String description;
        BigDecimal unitCost;
        String remarks;
        Outcome outcome;
        String reasonGroup;     // e.g. "Marked cancelled, deleted or superseded"
        String reason;          // e.g. "Issued By says 'cancelled'"
        Long departmentId;      // set once the spelling is mapped
        String cleanUnit;       // set once the spelling is mapped

        Line(int rowNo) {
            this.rowNo = rowNo;
        }

        boolean importable() {
            return outcome == null;
        }

        void setAside(String group, String why) {
            mark(Outcome.SET_ASIDE, group, why);
        }

        void reject(String group, String why) {
            mark(Outcome.REJECTED, group, why);
        }

        private void mark(Outcome o, String group, String why) {
            if (outcome != null) return;   // the first reason wins
            outcome = o;
            reasonGroup = group;
            reason = why.length() > 255 ? why.substring(0, 254) + "…" : why;
        }
    }

    record Result(int headerRow, Map<Field, Integer> columns, List<Field> missing, List<Line> lines) {
    }

    /** SRS UC-107 step 5: rows marked cancelled, deleted or superseded in the Issued By column. */
    private static final Pattern WITHDRAWN = Pattern.compile(
            "cancel|delet|supersed|superseed|changed to|replaced|returned to supplier|\\bvoid", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("(\\d{1,2})\\s*[/-]\\s*(\\d{1,2})\\s*[/-]\\s*'?(\\d{4}|\\d{2})(?!\\d)");
    private static final BigDecimal MAX_COST = new BigDecimal("9999999999.99");   // NUMERIC(12,2)

    private MasterlistParser() {
    }

    static Result parse(List<SheetReader.Row> rows) {
        // The heading row: the first of the top rows that has most of the 14 headings.
        int headerIdx = -1;
        Map<Field, Integer> cols = new EnumMap<>(Field.class);
        for (int i = 0; i < Math.min(rows.size(), 25) && headerIdx < 0; i++) {
            Map<Field, Integer> found = headings(rows.get(i).cells());
            if (found.size() >= 8) {
                headerIdx = i;
                cols = found;
            }
        }
        List<Field> missing = new ArrayList<>();
        for (Field f : Field.values()) if (!cols.containsKey(f)) missing.add(f);
        if (headerIdx < 0) return new Result(0, cols, missing, List.of());

        List<Line> lines = new ArrayList<>();
        for (int i = headerIdx + 1; i < rows.size(); i++) {
            Line l = line(rows.get(i), cols);
            if (l != null) lines.add(l);
        }
        return new Result(rows.get(headerIdx).rowNo(), cols, missing, lines);
    }

    private static Map<Field, Integer> headings(Object[] cells) {
        Map<String, Field> byKey = new LinkedHashMap<>();
        for (Field f : Field.values()) byKey.put(f.key(), f);
        Map<Field, Integer> found = new EnumMap<>(Field.class);
        for (int c = 0; c < cells.length; c++) {
            if (!(cells[c] instanceof String s)) continue;
            Field f = byKey.get(squash(s));
            if (f == null && squash(s).equals("ITEMDESCRIPTION")) f = Field.DESCRIPTION;
            if (f != null && !found.containsKey(f)) found.put(f, c);
        }
        return found;
    }

    private static Line line(SheetReader.Row row, Map<Field, Integer> cols) {
        Object[] c = row.cells();
        boolean any = false;
        for (Integer idx : cols.values()) if (!blank(c[idx])) any = true;
        if (!any) return null;   // a blank line between blocks is not a masterlist line

        Line l = new Line(row.rowNo());
        for (Field f : Field.values()) {
            Integer idx = cols.get(f);
            l.cells.put(f.heading, idx == null ? "" : display(c[idx]));
        }

        // Issue order number.
        Object io = get(c, cols, Field.IO_NO);
        String ioText = text(io);
        if (ioText == null) {
            l.setAside("No issue order number", "Issue Order # is empty");
        } else if (ioText.matches("\\d{1,7}")) {
            l.ioNo = "0".repeat(7 - ioText.length()) + ioText;
        } else if (ioText.matches("\\d{7}.+")) {
            l.setAside("Issue order number with letters", "Issue Order # '" + ioText + "' is not a plain 7-digit number");
        } else {
            l.setAside("Not an issue order", "Issue Order # says '" + ioText + "'");
        }

        l.issuedBy = text(get(c, cols, Field.ISSUED_BY));
        if (l.issuedBy != null && WITHDRAWN.matcher(l.issuedBy).find()) {
            l.setAside("Marked cancelled, deleted or superseded", "Issued By says '" + l.issuedBy + "'");
        }
        if (l.issuedBy != null && l.issuedBy.matches("(?i)n/?a|no entry|-+|\\.")) l.issuedBy = null;

        l.department = text(get(c, cols, Field.DEPARTMENT));
        l.person = text(get(c, cols, Field.PERSON));
        if (l.person != null && l.person.matches("(?i)n/?a|-+|\\.")) l.person = null;
        l.designation = text(get(c, cols, Field.DESIGNATION));
        l.invoiceDate = date(get(c, cols, Field.INVOICE_DATE));
        l.purchasedFrom = text(get(c, cols, Field.PURCHASED_FROM));
        l.invoiceNo = text(get(c, cols, Field.INVOICE_NO));
        l.unit = text(get(c, cols, Field.UNIT));
        l.description = text(get(c, cols, Field.DESCRIPTION));
        l.remarks = text(get(c, cols, Field.REMARKS));

        if (l.department == null) l.reject("No department", "Department is empty");

        Object q = get(c, cols, Field.QTY);
        BigDecimal qty = number(q);
        if (qty == null) {
            l.reject("No quantity", "Qty is empty or not a number");
        } else if (qty.signum() <= 0) {
            l.reject("Quantity is 0", "Qty is " + qty.stripTrailingZeros().toPlainString());
        } else if (qty.stripTrailingZeros().scale() > 0 || qty.compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
            l.reject("Quantity not a whole number", "Qty " + qty.stripTrailingZeros().toPlainString() + " is not a whole number of items");
        } else {
            l.quantity = qty.intValueExact();
        }

        if (l.description == null) l.reject("No description", "Items/Description is empty");

        BigDecimal unitCost = number(get(c, cols, Field.UNIT_COST));
        BigDecimal total = number(get(c, cols, Field.TOTAL_COST));
        if (unitCost == null && total != null && l.quantity != null) {
            unitCost = total.divide(BigDecimal.valueOf(l.quantity), 2, RoundingMode.HALF_UP);
        }
        if (unitCost != null) {
            unitCost = unitCost.setScale(2, RoundingMode.HALF_UP);
            if (unitCost.signum() < 0) unitCost = null;
            else if (unitCost.compareTo(MAX_COST) > 0) l.reject("Cost too large", "Acquisition Unit Cost " + unitCost.toPlainString() + " is too large");
        }
        l.unitCost = unitCost;
        return l;
    }

    // ------------------------------------------------------------------ cell helpers

    private static Object get(Object[] c, Map<Field, Integer> cols, Field f) {
        Integer i = cols.get(f);
        return i == null ? null : c[i];
    }

    private static boolean blank(Object o) {
        return o == null || (o instanceof String s && s.isBlank());
    }

    /** A cell as text: numbers without ".0", dates as ISO, trimmed, single spaces; null when empty. */
    static String text(Object o) {
        if (o == null || SheetReader.ERROR.equals(o)) return null;
        String s;
        if (o instanceof Double d) {
            s = d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf(d.longValue()) : BigDecimal.valueOf(d).toPlainString();
        } else {
            s = String.valueOf(o);
        }
        s = s.replace(' ', ' ').trim().replaceAll("\\s+", " ");
        return s.isEmpty() ? null : s;
    }

    private static String display(Object o) {
        if (SheetReader.ERROR.equals(o)) return "#VALUE!";
        String s = text(o);
        return s == null ? "" : s;
    }

    /** A number cell, or text such as "4,185.00" or "₱ 316.20". Null for empty, errors and other text. */
    static BigDecimal number(Object o) {
        if (o instanceof Double d) return BigDecimal.valueOf(d);
        if (o instanceof String s) {
            String t = s.replaceAll("[₱,\\s]|^[Pp]hp|^P", "");
            if (t.matches("-?\\d+(\\.\\d+)?")) return new BigDecimal(t);
        }
        return null;
    }

    /**
     * A date cell, an Excel date number, or the first date written in text such as "10/3/2023 - 9/19/2023"
     * or "POdate 7/13/2019". Null when there is none.
     */
    static LocalDate date(Object o) {
        if (o instanceof LocalDate d) return d;
        if (o instanceof Double d && d > 20000 && d < 80000) return DateUtil.getLocalDateTime(d).toLocalDate();
        if (o instanceof String s) {
            Matcher m = DATE.matcher(s);
            while (m.find()) {
                int month = Integer.parseInt(m.group(1));
                int day = Integer.parseInt(m.group(2));
                int year = Integer.parseInt(m.group(3));
                if (year < 100) year += 2000;
                try {
                    LocalDate d = LocalDate.of(year, month, day);
                    if (d.getYear() >= 1990 && d.getYear() <= 2100) return d;
                } catch (DateTimeException ignored) {
                    // not a real date, try the next one
                }
            }
        }
        return null;
    }

    /** "ISSUE ORDER #" → "ISSUEORDER", "PURCHASED from" → "PURCHASEDFROM". */
    static String squash(String s) {
        return s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
