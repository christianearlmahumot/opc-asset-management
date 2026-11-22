package edu.citu.opc.fams.document;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UC-102 step 1: maps the OCR text to the header fields (supplier, PO and PR numbers, department code,
 * date) and the item lines (quantity, unit, description, unit cost, total, category from the PR).
 *
 * <p>The item table is found by its heading row (QTY · UNIT · DESCRIPTION · UNIT COST · AMOUNT, or the
 * PO's PR NO · DEPARTMENT · QTY · DESCRIPTION · PRICE · TOTAL, or the PR's Item No · Qty · Unit ·
 * Description · Category · Unit Price · Total Price). Each word is placed in a column by its type and
 * position. A row with prices or a unit starts an item; the rows below it continue its description.
 * Pages without a heading row continue the previous page's table.
 *
 * <p>Every field gets a confidence (0-100). The review page highlights fields below 70, so
 * staff check those first. Pure logic: no OCR, no database.
 */
final class DocumentParser {

    record Item(Integer quantity, String unit, String description, BigDecimal unitCost, BigDecimal totalCost,
                String category, Map<String, Integer> confidence) {
    }

    record Result(String supplier, String poNo, String prNo, String departmentCode, LocalDate docDate,
                  Map<String, Integer> confidence, List<Item> items) {
    }

    private enum Col { ITEM_NO, PR_NO, DEPT, QTY, UNIT, DESC, CATEGORY, PRICE, TOTAL }

    private static final Pattern MONEY = Pattern.compile("^\\d{1,3}(,\\d{3})+\\.\\d{2}$|^\\d+\\.\\d{2}$");
    private static final Pattern INT = Pattern.compile("^\\d{1,5}$");
    private static final Pattern DEPT_CODE = Pattern.compile("\\b(\\d{4}[A-Z]{3,12})\\b");
    private static final Pattern PO_NO = Pattern.compile(
            "(?i)(?:\\bp\\s*\\.?\\s*o\\s*\\.?\\s*(?:n[o0]?\\s*\\.?|#)|order\\s*no\\s*\\.?)\\s*[:.,]?\\s*(\\d{6,10})\\b");
    private static final Pattern PR_NO = Pattern.compile("(?i)\\bPR\\s*-\\s*(\\d{4}\\s*[A-Z]{3,12})\\s*-\\s*(\\d{2})\\s*-\\s*(\\d{5,6})\\b");
    private static final Pattern MONTH_DATE = Pattern.compile(
            "(?i)(?<![a-z])(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s+(\\d{1,2})\\s*,?\\s*(\\d{4})\\b");
    private static final Pattern NUM_DATE = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4}|\\d{2})\\b");
    private static final Pattern COMPANY = Pattern.compile(
            "(?i)^(.*\\b(?:center|centre|inc\\.?|incorporated|corporation|corp\\.?|trading|enterprises?|company|co\\.|"
                    + "merchandising|marketing|suppl(?:y|ies)|industries|store|services))\\b");
    /** The item table ends here. */
    private static final List<String> END = List.of("sub-total", "subtotal", "sub total", "total amount", "grand total",
            "nothing follows", "total sales", "amount due", "less: discount", "less discount", "discount amount");
    /** This page's part of the table ends here; the table may go on at the top of the next page. */
    private static final List<String> PAGE_END = List.of("this is a system", "pay to", "prepared by", "remarks :",
            "remarks:", "approved by");
    /** Browser headers and footers on printed web forms: dates, URLs, page numbers. */
    private static final Pattern FURNITURE = Pattern.compile(
            "(?i)(https?://|www\\.|\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b|\\bpage \\d+ of \\d+\\b|"
                    + "^\\W*\\d{1,2}/\\d{1,2}/\\d{2,4},? \\d{1,2}:\\d{2})");
    private static final List<String> MONTHS = List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep",
            "oct", "nov", "dec");

    private DocumentParser() {
    }

    /** {@code units}: unit spellings the OPC uses (lower case), to tell a unit from a word of the description. */
    static Result parse(SourceDocument.Type type, List<OcrText.Page> pages, Set<String> units) {
        Map<String, Integer> conf = new LinkedHashMap<>();
        List<OcrText.Line> all = new ArrayList<>();
        for (OcrText.Page p : pages) all.addAll(p.lines());

        Found supplier = type == SourceDocument.Type.PURCHASE_REQUEST ? null : supplier(type, pages);
        Found po = first(all, PO_NO, m -> m.group(1));
        Found pr = first(all, PR_NO, m -> "PR-" + m.group(1).replace(" ", "").toUpperCase(Locale.ROOT) + "-" + m.group(2) + "-" + m.group(3));
        Found dept = departmentCode(all);
        FoundDate date = date(pages);
        conf.put("supplier", supplier == null ? 0 : supplier.conf);
        conf.put("poNo", po == null ? 0 : po.conf);
        conf.put("prNo", pr == null ? 0 : pr.conf);
        conf.put("departmentCode", dept == null ? 0 : dept.conf);
        conf.put("docDate", date == null ? 0 : date.conf);

        List<Item> items = items(pages, units);
        return new Result(supplier == null ? null : supplier.value, po == null ? null : po.value,
                pr == null ? null : pr.value, dept == null ? null : dept.value, date == null ? null : date.value,
                conf, items);
    }

    // ------------------------------------------------------------------ header fields

    private record Found(String value, int conf) {
    }

    private record FoundDate(LocalDate value, int conf) {
    }

    private static Found first(List<OcrText.Line> lines, Pattern p, java.util.function.Function<Matcher, String> value) {
        for (OcrText.Line l : lines) {
            Matcher m = p.matcher(l.text());
            if (m.find()) return new Found(value.apply(m), l.conf());
        }
        return null;
    }

    private static Found supplier(SourceDocument.Type type, List<OcrText.Page> pages) {
        if (pages.isEmpty()) return null;
        // A purchase order names it: "Supplier : Mars Laboratory Instruments Center".
        for (OcrText.Page p : pages) {
            for (OcrText.Line l : p.lines()) {
                Matcher m = Pattern.compile("(?i)^\\W*supplier\\s*[:;.]?\\s*(.{3,})$").matcher(l.text());
                if (m.find()) {
                    String v = clean(m.group(1));
                    if (v.length() >= 3) return new Found(v, l.conf());
                }
            }
        }
        if (type != SourceDocument.Type.SALES_INVOICE) return null;
        // A sales invoice prints the seller's name at the top of the first page.
        OcrText.Page p = pages.get(0);
        Found best = null;
        double bestHeight = 0;
        for (OcrText.Line l : p.lines()) {
            if (l.y() > p.height() * 0.2) continue;
            String t = l.text();
            String low = t.toLowerCase(Locale.ROOT);
            if (low.contains("institute of technology") || low.contains("sold to") || low.matches(".*\\b(unit|floor|street|st\\.|rd\\.|road|avenue|ave\\.|bldg|building|tower)\\b.*")) continue;
            Matcher m = COMPANY.matcher(clean(t));
            if (!m.find() || m.group(1).length() < 6) continue;
            double height = l.words().stream().mapToInt(OcrText.Word::h).average().orElse(0);
            if (height > bestHeight) {
                bestHeight = height;
                best = new Found(clean(m.group(1)), l.conf());
            }
        }
        return best;
    }

    /** The code that appears most often, e.g. 1004MEDTEC (read once per table row on a PO). */
    private static Found departmentCode(List<OcrText.Line> lines) {
        Map<String, int[]> seen = new LinkedHashMap<>();
        for (OcrText.Line l : lines) {
            Matcher m = DEPT_CODE.matcher(l.text());
            while (m.find()) {
                int[] c = seen.computeIfAbsent(m.group(1), k -> new int[2]);
                c[0]++;
                c[1] = Math.max(c[1], l.conf());
            }
        }
        return seen.entrySet().stream().max((a, b) -> Integer.compare(a.getValue()[0], b.getValue()[0]))
                .map(e -> new Found(e.getKey(), seen.size() > 1 ? Math.min(e.getValue()[1], 60) : e.getValue()[1]))
                .orElse(null);
    }

    /** The date on a line that says "Date" (not "Date Issued", "Valid Until"…), else the first date near the top. */
    private static FoundDate date(List<OcrText.Page> pages) {
        FoundDate fallback = null;
        for (OcrText.Page p : pages) {
            for (OcrText.Line l : p.lines()) {
                String t = l.text();
                String low = t.toLowerCase(Locale.ROOT);
                LocalDate d = anyDate(t);
                if (d == null) continue;
                boolean labelled = low.contains("date") && !low.contains("issued") && !low.contains("accreditation")
                        && !low.contains("valid") && !low.contains("generated") && !low.contains("printed");
                if (labelled) return new FoundDate(d, l.conf());
                if (fallback == null && p.number() == 1 && l.y() < p.height() * 0.4) fallback = new FoundDate(d, Math.min(l.conf(), 60));
            }
        }
        return fallback;
    }

    static LocalDate anyDate(String t) {
        Matcher m = MONTH_DATE.matcher(t);
        while (m.find()) {
            LocalDate d = date(Integer.parseInt(m.group(3)), MONTHS.indexOf(m.group(1).toLowerCase(Locale.ROOT)) + 1,
                    Integer.parseInt(m.group(2)));
            if (d != null) return d;
        }
        m = NUM_DATE.matcher(t);
        while (m.find()) {
            int y = Integer.parseInt(m.group(3));
            LocalDate d = date(y < 100 ? 2000 + y : y, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            if (d != null) return d;
        }
        return null;
    }

    private static LocalDate date(int y, int m, int d) {
        if (y < 2000 || y > 2100) return null;
        try {
            return LocalDate.of(y, m, d);
        } catch (DateTimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ item table

    /** A heading word's column and horizontal extent. */
    private record Anchor(Col col, int left, int right) {
    }

    private static final class Draft {
        Integer qty;
        int qtyConf;
        String unit;
        int unitConf;
        final StringBuilder desc = new StringBuilder();
        int descConfSum;
        int descWords;
        int descLeft = Integer.MAX_VALUE;
        final StringBuilder category = new StringBuilder();
        int categoryConf = 100;
        BigDecimal unitCost;
        int unitCostConf;
        BigDecimal total;
        int totalConf;
    }

    private static List<Item> items(List<OcrText.Page> pages, Set<String> units) {
        List<Item> items = new ArrayList<>();
        List<Anchor> anchors = null;
        boolean tableOpen = false;
        Draft current = null;
        List<Row> pending = new ArrayList<>();
        for (OcrText.Page page : pages) {
            int start = 0;
            // Top to bottom: Tesseract sometimes reads a row's left cells after the text below it.
            List<OcrText.Line> lines = new ArrayList<>(page.lines());
            lines.sort(java.util.Comparator.comparingDouble(DocumentParser::middle));
            for (int i = 0; i < lines.size(); i++) {
                List<Anchor> h = heading(lines.get(i));
                if (h != null) {
                    anchors = h;
                    start = i + 1;
                    tableOpen = true;
                    break;
                }
            }
            if (!tableOpen || anchors == null) continue;
            for (int i = start; i < lines.size(); i++) {
                OcrText.Line line = lines.get(i);
                String low = line.text().toLowerCase(Locale.ROOT);
                if (END.stream().anyMatch(low::contains)) {
                    tableOpen = false;
                    break;
                }
                if (PAGE_END.stream().anyMatch(low::contains)) break;
                if (FURNITURE.matcher(line.text()).find()) continue;
                Row row = row(line, anchors, units);
                if (row.moneyOnly()) {
                    if (current == null || !row.fillMissingPrices(current)) pending.add(row);
                } else if (row.starts()) {
                    if (current != null) items.add(finish(current));
                    current = new Draft();
                    row.fill(current);
                    for (Row p : pending) p.fillMissingPrices(current);   // prices read just above their row
                    pending.clear();
                } else if (current != null) {
                    row.continueInto(current);
                }
            }
        }
        if (current != null) items.add(finish(current));
        items.removeIf(it -> (it.description() == null || it.description().isBlank()) && it.unitCost() == null && it.totalCost() == null);
        return items;
    }

    /** The heading row: has a DESCRIPTION word and at least one other column heading. */
    private static List<Anchor> heading(OcrText.Line line) {
        List<OcrText.Word> w = line.words();
        int descIdx = -1;
        for (int i = 0; i < w.size(); i++) {
            String s = squash(w.get(i).text());
            if (s.startsWith("DESCRIPTI") || s.equals("PARTICULARS") || s.equals("ARTICLES")) {
                descIdx = i;
                break;
            }
        }
        if (descIdx < 0) return null;
        List<Anchor> out = new ArrayList<>();
        Map<Col, Boolean> has = new EnumMap<>(Col.class);
        for (int i = 0; i < w.size(); i++) {
            OcrText.Word word = w.get(i);
            String s = squash(word.text());
            String next = i + 1 < w.size() ? squash(w.get(i + 1).text()) : "";
            Col c = null;
            if (i == descIdx) c = Col.DESC;
            else if (s.equals("QTY") || s.equals("QUANTITY")) c = Col.QTY;
            else if (s.equals("UNIT") && (next.equals("COST") || next.equals("PRICE"))) c = Col.PRICE;
            else if (s.equals("UNIT") || s.equals("UOM")) c = Col.UNIT;
            else if ((s.equals("PRICE") || s.equals("COST")) && i > 0 && squash(w.get(i - 1).text()).matches("UNIT|TOTAL")) c = null;
            else if (s.equals("PRICE") || s.equals("COST")) c = Col.PRICE;
            else if (s.equals("AMOUNT") || s.equals("TOTAL")) c = Col.TOTAL;
            else if (s.startsWith("CATEG")) c = Col.CATEGORY;
            else if (s.startsWith("DEPART")) c = Col.DEPT;
            else if (s.equals("PRNO") || (s.equals("PR") && next.startsWith("NO"))) c = Col.PR_NO;
            else if (s.equals("ITEM") && i < descIdx - 1) c = Col.ITEM_NO;
            if (c != null && !has.containsKey(c)) {
                has.put(c, true);
                out.add(new Anchor(c, word.x(), word.right()));
            }
        }
        // An unreadable heading just left of DESCRIPTION is the quantity ("arty" for QTY).
        if (!has.containsKey(Col.QTY) && descIdx > 0) {
            OcrText.Word before = w.get(descIdx - 1);
            boolean known = out.stream().anyMatch(a -> a.left() == before.x());
            if (!known && !squash(before.text()).equals("ITEM")) out.add(new Anchor(Col.QTY, before.x(), before.right()));
        }
        long others = out.stream().filter(a -> a.col() != Col.DESC).count();
        return others >= 2 ? out : null;
    }

    private static Anchor anchor(List<Anchor> anchors, Col c) {
        for (Anchor a : anchors) if (a.col() == c) return a;
        return null;
    }

    /** One OCR line, its words sorted into columns. */
    private static final class Row {
        Integer qty;
        int qtyConf;
        String unit;
        int unitConf;
        final List<OcrText.Word> desc = new ArrayList<>();
        final List<OcrText.Word> category = new ArrayList<>();
        final List<OcrText.Word> money = new ArrayList<>();
        boolean deptCode;
        boolean purchaseColumns;   // the PO's PR NO · DEPARTMENT columns: each item row starts with them
        Anchor price;
        Anchor total;

        boolean starts() {
            return (!money.isEmpty() && !moneyOnly()) || unit != null || (deptCode && (qty != null || purchaseColumns));
        }

        /** A row with nothing but prices: Tesseract read the price cells apart from their row. */
        boolean moneyOnly() {
            return !money.isEmpty() && qty == null && unit == null && !deptCode && desc.isEmpty();
        }

        /** Fills the prices the item is missing; false when it already has them. */
        boolean fillMissingPrices(Draft d) {
            Draft probe = new Draft();
            money(probe);
            boolean used = false;
            if (probe.unitCost != null && d.unitCost == null) {
                d.unitCost = probe.unitCost;
                d.unitCostConf = probe.unitCostConf;
                used = true;
            }
            if (probe.total != null && d.total == null) {
                d.total = probe.total;
                d.totalConf = probe.totalConf;
                used = true;
            }
            return used;
        }

        void fill(Draft d) {
            d.qty = qty;
            d.qtyConf = qtyConf;
            d.unit = unit;
            d.unitConf = unitConf;
            addDesc(d, desc);
            addCategory(d, category);
            money(d);
        }

        void continueInto(Draft d) {
            List<OcrText.Word> words = new ArrayList<>();
            for (OcrText.Word w : desc) {
                // Handwritten ticks in the margin: short marks well left of the description column.
                if (d.descLeft != Integer.MAX_VALUE && w.right() < d.descLeft - 20 && w.text().length() <= 4) continue;
                words.add(w);
            }
            addDesc(d, words);
            addCategory(d, category);
            if (d.unitCost == null && d.total == null) money(d);
        }

        private void money(Draft d) {
            if (money.isEmpty()) return;
            if (money.size() >= 2) {
                OcrText.Word u = money.get(money.size() - 2);
                OcrText.Word t = money.get(money.size() - 1);
                d.unitCost = amount(u.text());
                d.unitCostConf = u.conf();
                d.total = amount(t.text());
                d.totalConf = t.conf();
            } else {
                OcrText.Word m = money.get(0);
                boolean isTotal = total != null && (price == null
                        || Math.abs(m.right() - total.right()) < Math.abs(m.right() - price.right()));
                if (isTotal) {
                    d.total = amount(m.text());
                    d.totalConf = m.conf();
                } else {
                    d.unitCost = amount(m.text());
                    d.unitCostConf = m.conf();
                }
            }
        }

        private static void addDesc(Draft d, List<OcrText.Word> words) {
            for (OcrText.Word w : words) {
                String t = w.text();
                if (t.matches("[-—–]+")) t = "-";
                if (t.matches("[|_~=*°«»‘’“”•.,:;]+") || (t.equals("-") && d.desc.length() == 0)) continue;   // rules and specks
                if (d.desc.length() > 0) d.desc.append(' ');
                d.desc.append(t);
                d.descConfSum += w.conf();
                d.descWords++;
                d.descLeft = Math.min(d.descLeft, w.x());
            }
        }

        private static void addCategory(Draft d, List<OcrText.Word> words) {
            for (OcrText.Word w : words) {
                String t = w.text();
                if (!t.matches(".*[A-Za-z].*")) continue;
                if (d.category.length() > 0 && !d.category.toString().endsWith("-")) d.category.append(' ');
                d.category.append(t);
                d.categoryConf = Math.min(d.categoryConf, w.conf());
            }
        }
    }

    private static Row row(OcrText.Line line, List<Anchor> anchors, Set<String> units) {
        Row r = new Row();
        r.price = anchor(anchors, Col.PRICE);
        r.purchaseColumns = anchor(anchors, Col.DEPT) != null || anchor(anchors, Col.PR_NO) != null;
        r.total = anchor(anchors, Col.TOTAL);
        Anchor descA = anchor(anchors, Col.DESC);
        Anchor catA = anchor(anchors, Col.CATEGORY);
        int leftEdge = 0;   // right edge of the left-hand heading words (qty, unit, PR no, department)
        for (Anchor a : anchors) {
            if (a.col() == Col.QTY || a.col() == Col.UNIT || a.col() == Col.PR_NO || a.col() == Col.DEPT || a.col() == Col.ITEM_NO) {
                leftEdge = Math.max(leftEdge, a.right());
            }
        }
        int catLeft = catA == null ? Integer.MAX_VALUE : catA.left() - 60;
        int priceLeft = Integer.MAX_VALUE;
        for (Anchor a : anchors) if (a.col() == Col.PRICE || a.col() == Col.TOTAL) priceLeft = Math.min(priceLeft, a.left() - 80);
        Anchor qtyA = anchor(anchors, Col.QTY);
        Anchor unitA = anchor(anchors, Col.UNIT);

        boolean inDescription = false;
        for (OcrText.Word w : line.words()) {
            String raw = w.text();
            String money = raw.replaceAll("^[^0-9]+|[^0-9]+$", "");
            if (MONEY.matcher(money).matches() && w.x() > (descA == null ? 0 : descA.left() - 200)) {
                r.money.add(new OcrText.Word(money, w.x(), w.y(), w.w(), w.h(), w.conf()));
                continue;
            }
            if (w.x() >= priceLeft && w.x() >= catLeft) {
                if (catA != null && w.x() < priceLeft + 80) r.category.add(w);
                continue;   // specks and marks in the price columns
            }
            if (catA != null && w.x() >= catLeft) {
                r.category.add(w);
                continue;
            }
            if (!inDescription) {
                String t = raw.replaceAll("[^A-Za-z0-9/,]", "");
                String key = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z/]", "");
                if (DEPT_CODE.matcher(raw).find()) {
                    r.deptCode = true;
                    continue;
                }
                if (t.matches("\\d{1,2},\\d{3}") && anchor(anchors, Col.PR_NO) != null && r.qty == null) continue;   // PR no. 9,021
                if (INT.matcher(t).matches() && r.unit == null && w.right() < leftEdge + 120) {
                    boolean nearQty = qtyA != null && Math.abs(w.centerX() - (qtyA.left() + qtyA.right()) / 2) < 150;
                    if (nearQty || (qtyA == null && r.qty == null)) {
                        r.qty = Integer.parseInt(t);   // a later integer (Qty after Item No.) replaces an earlier one
                        r.qtyConf = w.conf();
                        continue;
                    }
                }
                if (unitA != null && isUnit(key, units) && Math.abs(w.centerX() - (unitA.left() + unitA.right()) / 2) < 160) {
                    r.unit = key.replace("/s", "").replace("/", "");
                    r.unitConf = w.conf();
                    continue;
                }
                // Marks in the qty / unit columns before the description: handwritten ticks, rules.
                if (w.right() < leftEdge + 60 && raw.replaceAll("[^A-Za-z0-9]", "").length() <= 3) continue;
                inDescription = true;
            }
            r.desc.add(w);
        }
        return r;
    }

    private static double middle(OcrText.Line l) {
        return l.words().stream().mapToInt(OcrText.Word::centerY).average().orElse(0);
    }

    private static boolean isUnit(String key, Set<String> units) {
        if (key.isEmpty()) return false;
        String k = key.replace("/s", "").replace("/", "");
        return units.contains(k) || units.contains(k.replaceAll("s$", "")) || units.contains(key);
    }

    private static Item finish(Draft d) {
        Map<String, Integer> c = new LinkedHashMap<>();
        Integer qty = d.qty;
        int qtyConf = qty == null ? 0 : d.qtyConf;
        BigDecimal unitCost = d.unitCost;
        BigDecimal total = d.total;
        int unitCostConf = unitCost == null ? 0 : d.unitCostConf;
        int totalConf = total == null ? 0 : d.totalConf;

        // Cross-check quantity × unit cost = total, and fill in what the OCR missed.
        if (unitCost != null && total != null && unitCost.signum() > 0) {
            BigDecimal q = total.divide(unitCost, 4, RoundingMode.HALF_UP);
            boolean whole = q.remainder(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.001")) < 0;
            if (qty == null && whole) {
                qty = q.intValue();
                qtyConf = 60;   // worked out, not read
            } else if (qty != null && unitCost.multiply(BigDecimal.valueOf(qty)).subtract(total).abs().compareTo(BigDecimal.ONE) > 0) {
                if (whole) {
                    qty = q.intValue();
                    qtyConf = Math.min(qtyConf, 50);
                } else {
                    unitCostConf = Math.min(unitCostConf, 40);
                    totalConf = Math.min(totalConf, 40);
                }
            }
        } else if (unitCost == null && total != null && qty != null && qty > 0) {
            unitCost = total.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP);
            unitCostConf = 55;
        } else if (total == null && unitCost != null && qty != null) {
            total = unitCost.multiply(BigDecimal.valueOf(qty));
            totalConf = 55;
        }

        String desc = d.desc.toString().replaceAll("\\s+", " ").replaceAll("^[\\W_]+", "").trim();
        String category = d.category.toString().replaceAll("[~=_—|]+", " ").replaceAll("\\s+", " ")
                .replaceAll("\\s*-\\s*", "-").replaceAll("^[\\W]+|[\\W]+$", "").trim();
        c.put("quantity", qtyConf);
        c.put("unit", d.unit == null ? 0 : d.unitConf);
        c.put("description", d.descWords == 0 ? 0 : d.descConfSum / d.descWords);
        c.put("unitCost", unitCostConf);
        c.put("totalCost", totalConf);
        if (!category.isEmpty()) c.put("category", d.categoryConf);
        return new Item(qty, d.unit, desc.isEmpty() ? null : desc, unitCost, total, category.isEmpty() ? null : category, c);
    }

    // ------------------------------------------------------------------ helpers

    private static BigDecimal amount(String s) {
        try {
            return new BigDecimal(s.replace(",", "")).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String clean(String s) {
        return s.replaceAll("[_|~=<>*°«»‘’“”]+", " ").replaceAll("\\s+", " ").replaceAll("^[\\W]+|[\\W&&[^.)]]+$", "").trim();
    }

    private static String squash(String s) {
        return s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
    }

    /** Lower-case unit spellings for {@link #parse}, from the saved unit mappings plus common ones. */
    static Set<String> unitSpellings(java.util.Collection<String> known) {
        Set<String> s = new java.util.HashSet<>();
        for (String k : known) s.add(k.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""));
        s.addAll(List.of("unit", "units", "pc", "pcs", "piece", "pieces", "set", "sets", "lot", "lots", "box", "boxes",
                "pair", "pairs", "pack", "packs", "roll", "rolls", "copy", "copies", "ream", "reams", "bottle", "bottles",
                "kg", "meter", "meters", "gallon", "gallons", "tube", "tubes", "sheet", "sheets", "license", "licenses"));
        s.remove("");
        return s;
    }
}
