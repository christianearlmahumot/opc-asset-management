package edu.citu.opc.fams.issueorder;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds the issue order PDF in the OPC's printed layout (SRS UC-202 step 7, SDD PdfService): header,
 * number and date, department, issued by / purchased from / invoice, the QTY · ARTICLES · Unit Cost ·
 * Total Cost table, the acknowledgement lines, grand total, and the Approved / Noted / Received
 * signature blocks. Item photos, where attached, go on a second page.
 */
@Component
public class IssueOrderPdf {

    private static final Font TITLE = new Font(Font.HELVETICA, 14, Font.BOLD | Font.UNDERLINE);
    private static final Font SUB = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font IO_TITLE = new Font(Font.TIMES_ROMAN, 15, Font.BOLD | Font.UNDERLINE);
    private static final Font NUMBER = new Font(Font.HELVETICA, 15, Font.BOLD);
    private static final Font LABEL = new Font(Font.HELVETICA, 8);
    private static final Font LABEL_I = new Font(Font.HELVETICA, 9, Font.ITALIC);
    private static final Font TEXT = new Font(Font.HELVETICA, 9);
    private static final Font TEXT_B = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font HEAD = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font SIGN = new Font(Font.HELVETICA, 9, Font.BOLD);

    private static final DateTimeFormatter ISSUED = DateTimeFormatter.ofPattern("MMM. d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter INVOICE = DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH);

    private final String institution;

    public IssueOrderPdf(@Value("${fams.issue-order.institution}") String institution) {
        this.institution = institution;
    }

    public record Photo(String caption, byte[] bytes) {
    }

    public record Data(IssueOrder io, String departmentCode, List<IssueOrderLine> lines, List<Photo> photos) {
    }

    public byte[] render(Data d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 40, 40, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Issue Order " + d.io().getIoNo());
            doc.open();
            header(doc, d);
            meta(doc, d);
            items(doc, d);
            signatures(doc, d.io());
            if (!d.photos().isEmpty()) photos(doc, d);
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not build issue order PDF", e);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ layout parts

    private void header(Document doc, Data d) throws DocumentException {
        String[] parts = institution.split("\\s+-\\s+", 2);
        Paragraph p1 = new Paragraph(parts[0], TITLE);
        p1.setAlignment(Element.ALIGN_CENTER);
        doc.add(p1);
        if (parts.length > 1) {
            Paragraph p2 = new Paragraph(spaced(parts[1]), SUB);
            p2.setAlignment(Element.ALIGN_CENTER);
            doc.add(p2);
        }
        Paragraph p3 = new Paragraph("ISSUE ORDER", IO_TITLE);
        p3.setAlignment(Element.ALIGN_CENTER);
        p3.setSpacingBefore(6);
        doc.add(p3);
        Paragraph no = new Paragraph();
        no.add(new Chunk("No.  ", new Font(Font.HELVETICA, 13)));
        no.add(new Chunk(d.io().getIoNo(), NUMBER));
        no.setAlignment(Element.ALIGN_RIGHT);
        doc.add(no);
    }

    private void meta(Document doc, Data d) throws DocumentException {
        IssueOrder io = d.io();
        PdfPTable t = new PdfPTable(new float[]{14, 52, 34});
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        t.addCell(plain(new Phrase("STOREKEEPER:", LABEL), Element.ALIGN_LEFT));
        t.addCell(plain(new Phrase("Please issue to the bearer the\nmaterials and/or supplies\nlisted below:", TEXT), Element.ALIGN_LEFT));
        PdfPCell right = plain(new Phrase(), Element.ALIGN_RIGHT);
        right.addElement(underlinedRight(io.getIssuedOn().format(ISSUED)));
        right.addElement(underlinedRight(d.departmentCode()));
        Paragraph dl = new Paragraph("Department", LABEL);
        dl.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(dl);
        t.addCell(right);
        doc.add(t);

        PdfPTable m = new PdfPTable(new float[]{12, 15, 30, 18, 25});
        m.setWidthPercentage(100);
        m.setSpacingBefore(6);
        m.addCell(plain(new Phrase("Issued by:", LABEL_I), Element.ALIGN_LEFT));
        m.addCell(plain(new Phrase("Purchased from:", LABEL_I), Element.ALIGN_RIGHT));
        m.addCell(plain(new Phrase(nz(io.getPurchasedFrom()), TEXT_B), Element.ALIGN_CENTER));
        Phrase date = new Phrase();
        date.add(new Chunk("Date  ", LABEL_I));
        date.add(new Chunk(io.getInvoiceDate() == null ? "" : io.getInvoiceDate().format(INVOICE), TEXT_B));
        m.addCell(plain(date, Element.ALIGN_LEFT));
        Phrase inv = new Phrase();
        inv.add(new Chunk("Inv. No.:  ", LABEL_I));
        inv.add(new Chunk(nz(io.getInvoiceNo()), TEXT_B));
        m.addCell(plain(inv, Element.ALIGN_RIGHT));
        PdfPCell by = plain(new Phrase(nz(firstName(io.getIssuedByName())).toUpperCase(Locale.ROOT), TEXT), Element.ALIGN_CENTER);
        by.setColspan(5);
        by.setHorizontalAlignment(Element.ALIGN_LEFT);
        by.setPaddingLeft(10);
        m.addCell(by);
        doc.add(m);
    }

    private void items(Document doc, Data d) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{11, 55, 17, 17});
        t.setWidthPercentage(100);
        t.setSpacingBefore(2);
        for (String h : new String[]{"QTY.", "ARTICLES / DESCRIPTION", "Unit Cost", "Total Cost"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, HEAD));
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setPadding(4);
            c.setBackgroundColor(new Color(245, 245, 245));
            t.addCell(c);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (IssueOrderLine l : d.lines()) {
            t.addCell(cell(l.getQuantity() + " " + l.getUnit(), TEXT, Element.ALIGN_CENTER));
            t.addCell(cell(l.getDescription(), TEXT, Element.ALIGN_LEFT));
            t.addCell(cell(money(l.getUnitCost()), TEXT, Element.ALIGN_RIGHT));
            t.addCell(cell(money(l.getTotalCost()), TEXT, Element.ALIGN_RIGHT));
            if (l.getTotalCost() != null) total = total.add(l.getTotalCost());
        }
        // Acknowledgement, as printed on the OPC form, with the grand total on the right.
        String purpose = d.io().getRoom() != null ? d.departmentCode() + " · " + d.io().getRoom() : d.departmentCode();
        PdfPCell blank1 = cell("", TEXT, Element.ALIGN_CENTER);
        blank1.setRowspan(3);
        t.addCell(blank1);
        PdfPCell ack = cell("I / We acknowledge to have received from the Property Custodian's Office the above listed "
                + "property/ies which will be used for the  " + purpose + "  and for which I / We am / are accountable.",
                new Font(Font.HELVETICA, 9, Font.ITALIC), Element.ALIGN_LEFT);
        ack.setColspan(2);
        ack.setRowspan(3);
        t.addCell(ack);
        t.addCell(cell("", TEXT, Element.ALIGN_RIGHT));
        t.addCell(cell(money(total), new Font(Font.HELVETICA, 10, Font.BOLD), Element.ALIGN_RIGHT));
        t.addCell(cell("", TEXT, Element.ALIGN_RIGHT));
        doc.add(t);

        if (d.io().getRemarks() != null) {
            Paragraph r = new Paragraph("Remarks: " + d.io().getRemarks(), TEXT);
            r.setSpacingBefore(4);
            doc.add(r);
        }
    }

    private void signatures(Document doc, IssueOrder io) throws DocumentException {
        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setSpacingBefore(10);
        t.addCell(plain(new Phrase("APPROVED BY:", LABEL), Element.ALIGN_LEFT));
        t.addCell(plain(new Phrase("Noted by:", LABEL), Element.ALIGN_LEFT));
        t.addCell(plain(new Phrase("Received by:", LABEL), Element.ALIGN_LEFT));
        t.addCell(signature(io.getApprovedByName(), "Property Custodian"));
        t.addCell(signature(io.getNotedByName(), io.getNotedByTitle() == null ? "Dean" : io.getNotedByTitle()));
        t.addCell(signature(io.getReceivedByName(), io.getReceivedByTitle()));
        doc.add(t);
    }

    private PdfPCell signature(String name, String title) {
        PdfPCell c = plain(new Phrase(), Element.ALIGN_CENTER);
        c.setPaddingTop(26);
        Paragraph n = new Paragraph(name == null ? " " : name, SIGN);
        n.setAlignment(Element.ALIGN_CENTER);
        Paragraph line = new Paragraph("______________________________", TEXT);
        line.setAlignment(Element.ALIGN_CENTER);
        line.setSpacingBefore(-10);
        Paragraph ti = new Paragraph(title == null ? "" : title, LABEL_I);
        ti.setAlignment(Element.ALIGN_CENTER);
        c.addElement(n);
        c.addElement(line);
        c.addElement(ti);
        return c;
    }

    private void photos(Document doc, Data d) throws DocumentException {
        doc.newPage();
        Paragraph h = new Paragraph("Item photos · Issue Order No. " + d.io().getIoNo(), new Font(Font.HELVETICA, 12, Font.BOLD));
        h.setSpacingAfter(10);
        doc.add(h);
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        for (Photo p : d.photos()) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.BOX);
            c.setBorderColor(new Color(210, 210, 210));
            c.setPadding(6);
            try {
                Image img = Image.getInstance(p.bytes());
                img.scaleToFit(240, 180);
                img.setAlignment(Element.ALIGN_CENTER);
                c.addElement(img);
            } catch (Exception e) {
                c.addElement(new Paragraph("(photo could not be read)", LABEL_I));
            }
            Paragraph cap = new Paragraph(p.caption(), TEXT);
            cap.setAlignment(Element.ALIGN_CENTER);
            c.addElement(cap);
            t.addCell(c);
        }
        if (d.photos().size() % 2 == 1) t.addCell(plain(new Phrase(""), Element.ALIGN_LEFT));
        doc.add(t);
    }

    // ------------------------------------------------------------------ helpers

    private static PdfPCell plain(Phrase p, int align) {
        PdfPCell c = new PdfPCell(p);
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(align);
        c.setPadding(2);
        return c;
    }

    private static PdfPCell cell(String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4);
        return c;
    }

    private static Paragraph underlinedRight(String text) {
        Paragraph p = new Paragraph(new Chunk(text == null ? "" : text, new Font(Font.HELVETICA, 10, Font.UNDERLINE)));
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static String money(BigDecimal v) {
        return v == null ? "" : new DecimalFormat("#,##0.00").format(v);
    }

    private static String spaced(String s) {
        return String.join(" ", s.split(""));
    }

    private static String firstName(String full) {
        if (full == null) return null;
        return full.trim().split("\\s+")[0];
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    static String date(LocalDate d) {
        return d == null ? "" : d.format(ISSUED);
    }
}
