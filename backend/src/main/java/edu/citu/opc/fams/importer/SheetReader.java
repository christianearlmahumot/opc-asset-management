package edu.citu.opc.fams.importer;

import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Reads an .xlsx sheet row by row with the SAX (event) API instead of loading the whole workbook, so
 * the 19,000-row masterlist fits in the small server's memory. Cell values come back as String,
 * Double, LocalDate (numbers in a date format), Boolean, or {@link #ERROR} for #VALUE! and similar.
 */
final class SheetReader {

    static final String ERROR = "#ERROR";

    /** One row: its 1-based row number and its first {@code width} cells (null when empty). */
    record Row(int rowNo, Object[] cells) {
    }

    private SheetReader() {
    }

    static List<String> sheetNames(Path file) throws IOException {
        try (OPCPackage pkg = OPCPackage.open(file.toFile(), PackageAccess.READ)) {
            XSSFReader r = new XSSFReader(pkg);
            List<String> names = new ArrayList<>();
            XSSFReader.SheetIterator it = (XSSFReader.SheetIterator) r.getSheetsData();
            while (it.hasNext()) {
                try (InputStream ignored = it.next()) {
                    names.add(it.getSheetName());
                }
            }
            return names;
        } catch (OpenXML4JException | RuntimeException e) {
            throw new IOException("The file is not a readable .xlsx workbook.", e);
        }
    }

    /** Every non-empty row of the named sheet. */
    static List<Row> read(Path file, String sheet, int width) throws IOException {
        try (OPCPackage pkg = OPCPackage.open(file.toFile(), PackageAccess.READ)) {
            XSSFReader r = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = r.getStylesTable();
            Iterator<InputStream> it = r.getSheetsData();
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) it;
            while (sheets.hasNext()) {
                try (InputStream in = sheets.next()) {
                    if (!sheets.getSheetName().equals(sheet)) continue;
                    Handler h = new Handler(strings, styles, width);
                    XMLReader x = XMLHelper.newXMLReader();
                    x.setContentHandler(h);
                    x.parse(new InputSource(in));
                    return h.rows;
                }
            }
            throw new IOException("The workbook has no sheet named '" + sheet + "'.");
        } catch (OpenXML4JException | SAXException | ParserConfigurationException | RuntimeException e) {
            throw new IOException("The file is not a readable .xlsx workbook.", e);
        }
    }

    private static final class Handler extends DefaultHandler {
        private final ReadOnlySharedStringsTable strings;
        private final StylesTable styles;
        private final int width;
        final List<Row> rows = new ArrayList<>();

        private Object[] current;
        private int rowNo;
        private int col;
        private String type;
        private int style;
        private final StringBuilder text = new StringBuilder();
        private boolean inValue;

        Handler(ReadOnlySharedStringsTable strings, StylesTable styles, int width) {
            this.strings = strings;
            this.styles = styles;
            this.width = width;
        }

        @Override
        public void startElement(String uri, String local, String qName, Attributes a) {
            switch (local) {
                case "row" -> {
                    current = new Object[width];
                    String r = a.getValue("r");
                    rowNo = r == null ? rowNo + 1 : Integer.parseInt(r);
                }
                case "c" -> {
                    col = column(a.getValue("r"));
                    type = a.getValue("t");
                    String s = a.getValue("s");
                    style = s == null ? 0 : Integer.parseInt(s);
                    text.setLength(0);
                }
                case "v", "t" -> {
                    inValue = true;
                    if ("v".equals(local)) text.setLength(0);
                }
                default -> { }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inValue) text.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String local, String qName) {
            switch (local) {
                case "v", "t" -> inValue = false;
                case "c" -> {
                    if (current != null && col >= 0 && col < width && !text.isEmpty()) current[col] = value(text.toString());
                }
                case "row" -> {
                    boolean any = false;
                    for (Object o : current) {
                        if (o != null && !(o instanceof String s && s.isBlank())) {
                            any = true;
                            break;
                        }
                    }
                    if (any) rows.add(new Row(rowNo, current));
                    current = null;
                }
                default -> { }
            }
        }

        private Object value(String raw) {
            if (type == null || "n".equals(type)) {
                double d;
                try {
                    d = Double.parseDouble(raw);
                } catch (NumberFormatException e) {
                    return raw;
                }
                if (isDateStyle() && DateUtil.isValidExcelDate(d)) {
                    return DateUtil.getLocalDateTime(d).toLocalDate();
                }
                return d;
            }
            return switch (type) {
                case "s" -> strings.getItemAt(Integer.parseInt(raw.trim())).getString();
                case "b" -> "1".equals(raw);
                case "e" -> ERROR;
                default -> raw;   // "str" (formula text) and "inlineStr"
            };
        }

        private boolean isDateStyle() {
            if (style <= 0 || style >= styles.getNumCellStyles()) return false;
            XSSFCellStyle s = styles.getStyleAt(style);
            return s != null && DateUtil.isADateFormat(s.getDataFormat(), s.getDataFormatString());
        }

        /** "AB12" → 27. */
        private static int column(String ref) {
            if (ref == null) return -1;
            int c = 0;
            for (int i = 0; i < ref.length(); i++) {
                char ch = ref.charAt(i);
                if (ch < 'A' || ch > 'Z') break;
                c = c * 26 + (ch - 'A' + 1);
            }
            return c - 1;
        }
    }
}
