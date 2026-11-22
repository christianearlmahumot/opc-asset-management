package edu.citu.opc.fams.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Writes a {@link ReportTable} as a PDF with OpenPDF (SDD 5.2 ExportService): the institution and report
 * heading, then the table with its header repeated on every page, and page numbers in the footer.
 */
final class ReportPdf {

    private static final Font INSTITUTION = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font TITLE = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font SUB = new Font(Font.HELVETICA, 9);
    private static final Font HEAD = new Font(Font.HELVETICA, 7, Font.BOLD);
    private static final Font CELL = new Font(Font.HELVETICA, 7);
    private static final Font CELL_B = new Font(Font.HELVETICA, 7, Font.BOLD);
    private static final Font NOTE = new Font(Font.HELVETICA, 8, Font.ITALIC);
    private static final Font FOOT = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.GRAY);
    private static final Color SHADE = new Color(0xE6, 0xE6, 0xE6);

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final DecimalFormat WHOLE = new DecimalFormat("#,##0");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH);

    private ReportPdf() {
    }

    static byte[] write(ReportTable t, String institution, String footer) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Rectangle size = t.landscape ? PageSize.LEGAL.rotate() : PageSize.LETTER;
        Document doc = new Document(size, 28, 28, 30, 34);
        try {
            PdfWriter w = PdfWriter.getInstance(doc, out);
            w.setPageEvent(new Footer(footer));
            doc.addTitle(t.title);
            doc.open();

            center(doc, institution, INSTITUTION, 0);
            center(doc, t.title.toUpperCase(Locale.ROOT), TITLE, 2);
            for (String s : t.subtitle) center(doc, s, SUB, 0);

            int n = t.columns.size();
            float[] widths = new float[n];
            for (int i = 0; i < n; i++) widths[i] = t.columns.get(i).width();
            PdfPTable table = new PdfPTable(widths);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            table.setHeaderRows(1);
            for (ReportTable.Column c : t.columns) {
                PdfPCell h = new PdfPCell(new Phrase(c.header(), HEAD));
                h.setBackgroundColor(SHADE);
                h.setHorizontalAlignment(Element.ALIGN_CENTER);
                h.setVerticalAlignment(Element.ALIGN_MIDDLE);
                h.setPadding(3);
                table.addCell(h);
            }

            if (t.rows.isEmpty()) {
                PdfPCell e = new PdfPCell(new Phrase(t.emptyText, CELL));
                e.setColspan(n);
                e.setHorizontalAlignment(Element.ALIGN_CENTER);
                e.setPadding(8);
                table.addCell(e);
            }
            for (ReportTable.Row row : t.rows) {
                if (row.style() == ReportTable.Style.GROUP) {
                    PdfPCell g = new PdfPCell(new Phrase(String.valueOf(row.cells().get(0)), CELL_B));
                    g.setColspan(n);
                    g.setBackgroundColor(SHADE);
                    g.setPadding(3);
                    table.addCell(g);
                    continue;
                }
                boolean bold = row.style() != ReportTable.Style.ITEM;
                for (int i = 0; i < n; i++) {
                    ReportTable.Kind k = t.columns.get(i).kind();
                    PdfPCell c = new PdfPCell(new Phrase(format(row.cells().get(i), k), bold ? CELL_B : CELL));
                    c.setPadding(2.5f);
                    c.setHorizontalAlignment(k == ReportTable.Kind.MONEY || k == ReportTable.Kind.INT
                            ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                    table.addCell(c);
                }
            }
            doc.add(table);

            for (String note : t.notes) {
                Paragraph p = new Paragraph(note, NOTE);
                p.setSpacingBefore(6);
                doc.add(p);
            }
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not build the PDF file", e);
        }
        return out.toByteArray();
    }

    private static void center(Document doc, String text, Font f, float before) throws DocumentException {
        Paragraph p = new Paragraph(text, f);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(before);
        doc.add(p);
    }

    private static String format(Object v, ReportTable.Kind k) {
        if (v == null) return "";
        if (k == ReportTable.Kind.MONEY && v instanceof BigDecimal b) return MONEY.format(b);
        if (k == ReportTable.Kind.INT && v instanceof Number x) return WHOLE.format(x);
        if (v instanceof LocalDate d) return d.format(DATE);
        return String.valueOf(v);
    }

    /** "Generated … · Page n" at the bottom of every page. */
    private static final class Footer extends PdfPageEventHelper {
        private final String text;

        Footer(String text) {
            this.text = text;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase(text + " · Page " + writer.getPageNumber(), FOOT),
                    page.getRight(document.rightMargin()), page.getBottom(18), 0);
        }
    }
}
