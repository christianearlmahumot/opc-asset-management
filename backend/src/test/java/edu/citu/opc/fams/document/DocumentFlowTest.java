package edu.citu.opc.fams.document;

import edu.citu.opc.fams.access.CurrentUser;
import edu.citu.opc.fams.access.Role;
import edu.citu.opc.fams.asset.AssetService;
import edu.citu.opc.fams.common.ApiException;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-101 and UC-102 end to end on an embedded PostgreSQL: upload a (drawn) sales invoice, let the
 * background OCR read it, check what it found, confirm the lines as assets, and see the purchase linked.
 * Skipped when the Tesseract language data is not on this machine.
 */
@SpringBootTest
class DocumentFlowTest {

    private static EmbeddedPostgres pg;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) throws IOException {
        if (pg == null) pg = EmbeddedPostgres.builder().start();
        r.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "postgres");
        r.add("fams.storage.type", () -> "local");
        r.add("fams.storage.local-dir", () -> {
            try {
                return Files.createTempDirectory("fams-docs").toString();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @AfterAll
    static void stop() throws IOException {
        if (pg != null) pg.close();
    }

    @Autowired DocumentService documents;
    @Autowired OcrService ocr;
    @Autowired PurchaseLookup purchases;
    @Autowired AssetService assets;
    @Autowired JdbcTemplate jdbc;

    private long categoryId;

    @BeforeEach
    void signIn() {
        Long userId = jdbc.queryForList("select id from app_user where username = 'staff1'", Long.class).stream().findFirst()
                .orElseGet(() -> jdbc.queryForObject("insert into app_user (username, password_hash, full_name, role) "
                        + "values ('staff1', 'x', 'Staff One', 'STAFF') returning id", Long.class));
        categoryId = jdbc.queryForList("select id from category where name = 'Furniture'", Long.class).stream().findFirst()
                .orElseGet(() -> jdbc.queryForObject("insert into category (name) values ('Furniture') returning id", Long.class));
        CurrentUser me = new CurrentUser(userId, "staff1", "Staff One", Role.STAFF, null, false);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(me, null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadReadReviewAndConfirm() throws Exception {
        Assumptions.assumeTrue(ocr.available(), "Tesseract language data not installed here");

        // UC-101 4a: not an image or PDF.
        ApiException bad = assertThrows(ApiException.class, () -> documents.upload(
                new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()), "SALES_INVOICE", "9001", false));
        assertTrue(bad.getFields().containsKey("file"));

        // UC-101: upload, then the background OCR reads it.
        SourceDocument d = documents.upload(new MockMultipartFile("file", "si-9001.png", "image/png", invoice()),
                "SALES_INVOICE", "9001", false);
        DocumentService.Detail read = waitUntilRead(d.getId());
        assertEquals("READ", read.status(), "OCR error: " + read.ocrError());
        assertEquals("000123456", read.poNo());
        assertEquals(LocalDate.of(2026, 9, 2), read.docDate());
        assertEquals("Acme Laboratory Supply Center", read.supplier());
        assertEquals(2, read.lines().size(), "lines: " + read.lines());
        DocumentService.LineView stools = read.lines().get(1);
        assertEquals(49, stools.quantity());
        assertEquals("unit", stools.unit());
        assertEquals(0, new BigDecimal("2834.00").compareTo(stools.unitCost()));
        assertTrue(stools.description().startsWith("Stool"), stools.description());

        // UC-101 4b: the same invoice number again.
        ApiException dup = assertThrows(ApiException.class, () -> documents.upload(
                new MockMultipartFile("file", "again.png", "image/png", invoice()), "SALES_INVOICE", "9001", false));
        assertEquals(409, dup.getStatus().value());

        // UC-102 6a: a required field missing (no category) is refused with the line's field named.
        List<DocumentService.ConfirmLine> lines = new ArrayList<>();
        for (DocumentService.LineView l : read.lines()) {
            lines.add(new DocumentService.ConfirmLine(l.id(), l.quantity(), l.unit(), l.description(), l.unitCost(),
                    null, null, null, null, null, null, null));
        }
        ApiException missing = assertThrows(ApiException.class, () -> documents.confirm(d.getId(),
                new DocumentService.ConfirmRequest(null, lines, false)));
        assertTrue(missing.getFields().containsKey("lines.0.categoryId"), missing.getFields().toString());

        // UC-102 step 7: confirmed lines become assets in OPC storage, linked to the document.
        List<DocumentService.ConfirmLine> ok = lines.stream().map(l -> new DocumentService.ConfirmLine(l.lineId(),
                l.quantity(), l.unit(), l.description(), l.unitCost(), categoryId, null, null, null, null, null, null)).toList();
        DocumentService.Confirmed c = documents.confirm(d.getId(), new DocumentService.ConfirmRequest(null, ok, false));
        assertEquals(2, c.saved());
        DocumentService.Detail done = documents.detail(d.getId());
        assertEquals("ENCODED", done.status());
        assertNotNull(done.encodingSeconds());
        assertEquals(2, done.assets().size());
        assertEquals("In OPC storage", done.assets().get(0).status());
        assertEquals(2, jdbc.queryForObject("select count(*) from movement m join asset a on a.id = m.asset_id "
                + "where a.source_document_id = ? and m.type = 'ENCODE'", Integer.class, d.getId()));

        // UC-101 step 5 / UC-202 step 2: the invoice belongs to PO 000123456's purchase.
        SourceDocument po = documents.upload(new MockMultipartFile("file", "po.png", "image/png", invoice()),
                "PURCHASE_ORDER", "000123456", false);
        assertTrue(purchases.linked("000123456", null).stream().anyMatch(l -> l.id().equals(d.getId())));
        waitUntilRead(po.getId());
    }

    private DocumentService.Detail waitUntilRead(Long id) throws InterruptedException {
        for (int i = 0; i < 240; i++) {
            DocumentService.Detail x = documents.detail(id);
            if (!x.status().equals("QUEUED") && !x.status().equals("PROCESSING")) return x;
            Thread.sleep(500);
        }
        throw new AssertionError("OCR did not finish in 2 minutes");
    }

    /** A plain printed sales invoice, drawn at about 300 DPI. */
    private static byte[] invoice() throws IOException {
        BufferedImage img = new BufferedImage(2550, 1500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SERIF, Font.BOLD, 72));
        g.drawString("Acme Laboratory Supply Center", 400, 140);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 40));
        g.drawString("SALES INVOICE", 1900, 140);
        g.drawString("SOLD TO: CEBU INSTITUTE OF TECHNOLOGY - UNIVERSITY", 100, 300);
        g.drawString("Date: September 2, 2026", 1700, 300);
        g.drawString("P.O. No. 000123456", 1700, 370);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
        g.drawString("Quantity", 100, 520);
        g.drawString("Unit", 330, 520);
        g.drawString("DESCRIPTION", 900, 520);
        g.drawString("Unit Cost", 1750, 520);
        g.drawString("Amount", 2200, 520);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 40));
        g.drawString("1", 150, 620);
        g.drawString("unit", 330, 620);
        g.drawString("Table - Laboratory side table w/2 sinks", 520, 620);
        g.drawString("596,565.00", 1750, 620);
        g.drawString("596,565.00", 2150, 620);
        g.drawString("Dimensions: L7350xW600xH850mm", 520, 680);
        g.drawString("49", 140, 780);
        g.drawString("unit", 330, 780);
        g.drawString("Stool - Laboratory stool 300mm dia x H550mm", 520, 780);
        g.drawString("2,834.00", 1790, 780);
        g.drawString("138,866.00", 2150, 780);
        g.drawString("Sub-total", 1750, 950);
        g.drawString("735,431.00", 2150, 950);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
