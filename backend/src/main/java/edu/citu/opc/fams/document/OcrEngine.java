package edu.citu.opc.fams.document;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs Tesseract OCR 5 through Tess4J (SRS 3.1.2, SDD OcrService). PDF pages are rendered at 300 DPI in
 * grey; photos are scaled to a similar size. Page segmentation mode 4 ("a single column of text of
 * variable sizes") keeps each table row on one line, which suits invoices, POs and PRs.
 */
@Component
class OcrEngine {

    static final int MAX_PAGES = 10;
    private static final int DPI = 300;

    private final String configuredDataPath;
    private final String language;

    OcrEngine(@Value("${fams.ocr.tessdata:}") String dataPath, @Value("${fams.ocr.language:eng}") String language) {
        this.configuredDataPath = dataPath;
        this.language = language;
    }

    /** Reads every page (up to {@link #MAX_PAGES}). Throws when Tesseract is not installed or the file is unreadable. */
    List<OcrText.Page> read(byte[] file, String contentType) throws IOException {
        List<BufferedImage> images = images(file, contentType);
        Tesseract t = new Tesseract();
        t.setDatapath(dataPath());
        t.setLanguage(language);
        t.setPageSegMode(ITessAPI.TessPageSegMode.PSM_SINGLE_COLUMN);
        t.setVariable("user_defined_dpi", String.valueOf(DPI));
        List<OcrText.Page> pages = new ArrayList<>();
        try {
            for (int i = 0; i < images.size(); i++) {
                BufferedImage img = images.get(i);
                List<Word> words = t.getWords(img, ITessAPI.TessPageIteratorLevel.RIL_WORD);
                pages.add(new OcrText.Page(i + 1, img.getWidth(), img.getHeight(), lines(words)));
                images.set(i, null);   // free the page image before the next one
            }
        } catch (Error e) {   // JNA reports a missing native library as an Error
            throw new IOException("Tesseract OCR is not available on the server: " + e.getMessage(), e);
        }
        return pages;
    }

    /** Words come in reading order; a new line starts when the next word moves back to the left or down. */
    static List<OcrText.Line> lines(List<Word> words) {
        List<OcrText.Line> lines = new ArrayList<>();
        List<OcrText.Word> current = new ArrayList<>();
        OcrText.Word prev = null;
        for (Word w : words) {
            String text = w.getText() == null ? "" : w.getText().strip();
            if (text.isEmpty()) continue;
            var b = w.getBoundingBox();
            OcrText.Word ow = new OcrText.Word(text, b.x, b.y, b.width, b.height, Math.round(w.getConfidence()));
            if (prev != null && (ow.x() < prev.x() - 5 || Math.abs(ow.centerY() - prev.centerY()) > Math.max(ow.h(), prev.h()))) {
                lines.add(new OcrText.Line(current));
                current = new ArrayList<>();
            }
            current.add(ow);
            prev = ow;
        }
        if (!current.isEmpty()) lines.add(new OcrText.Line(current));
        return lines;
    }

    private static List<BufferedImage> images(byte[] file, String contentType) throws IOException {
        List<BufferedImage> out = new ArrayList<>();
        if ("application/pdf".equals(contentType)) {
            try (PDDocument doc = Loader.loadPDF(file)) {
                PDFRenderer r = new PDFRenderer(doc);
                int n = Math.min(doc.getNumberOfPages(), MAX_PAGES);
                for (int i = 0; i < n; i++) out.add(r.renderImageWithDPI(i, DPI, ImageType.GRAY));
            }
        } else {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(file));
            if (img == null) throw new IOException("The image could not be read.");
            out.add(normalize(img));
        }
        return out;
    }

    /** Grey, and about the size of a 300-DPI letter page: phone photos are larger, small scans smaller. */
    private static BufferedImage normalize(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = 1.0;
        int longest = Math.max(w, h);
        if (longest > 3600) scale = 3300.0 / longest;
        else if (longest < 1600) scale = 2.0;
        int nw = (int) Math.round(w * scale);
        int nh = (int) Math.round(h * scale);
        BufferedImage grey = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = grey.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return grey;
    }

    /**
     * Where the language data is: the configured folder, else the usual places — the Docker image's
     * Tesseract package, then a local ./tessdata folder for development.
     */
    String dataPath() {
        List<String> candidates = new ArrayList<>();
        if (configuredDataPath != null && !configuredDataPath.isBlank()) candidates.add(configuredDataPath.trim());
        String env = System.getenv("TESSDATA_PREFIX");
        if (env != null && !env.isBlank()) candidates.add(env.trim());
        candidates.add("/usr/share/tesseract-ocr/5/tessdata");
        candidates.add("/usr/share/tesseract-ocr/4.00/tessdata");
        candidates.add("tessdata");
        candidates.add("backend/tessdata");
        for (String c : candidates) {
            if (Files.exists(Path.of(c, language + ".traineddata"))) return Path.of(c).toAbsolutePath().toString();
        }
        return candidates.get(0);
    }

    /** For the status endpoint and start-up log: is the language file there? */
    boolean available() {
        return Files.exists(Path.of(dataPath(), language + ".traineddata"));
    }
}
