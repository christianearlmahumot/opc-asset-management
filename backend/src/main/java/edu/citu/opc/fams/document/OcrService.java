package edu.citu.opc.fams.document;

import edu.citu.opc.fams.common.storage.FileStorage;
import edu.citu.opc.fams.reference.ValueMappingService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UC-101 step 5 "starts OCR" and UC-102 step 1 (SDD OcrService): reads queued documents one at a time in
 * the background, so an upload returns at once and the small server never runs two OCR jobs together.
 * The result is saved as the document's header fields and its extracted lines.
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final SourceDocumentRepository documents;
    private final ExtractedLineRepository lines;
    private final FileStorage storage;
    private final OcrEngine engine;
    private final ValueMappingService mappings;
    private final TransactionTemplate tx;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ocr");
        t.setDaemon(true);
        return t;
    });

    public OcrService(SourceDocumentRepository documents, ExtractedLineRepository lines, FileStorage storage,
                      OcrEngine engine, ValueMappingService mappings, TransactionTemplate tx) {
        this.documents = documents;
        this.lines = lines;
        this.storage = storage;
        this.engine = engine;
        this.mappings = mappings;
        this.tx = tx;
    }

    /** Documents still waiting when the server stopped are read again after a restart. */
    @EventListener(ApplicationReadyEvent.class)
    public void resume() {
        if (!engine.available()) {
            log.warn("Tesseract language data not found at {}; uploaded documents will need manual encoding", engine.dataPath());
        }
        List<SourceDocument> waiting = documents.findByStatusIn(List.of(SourceDocument.Status.QUEUED, SourceDocument.Status.PROCESSING));
        for (SourceDocument d : waiting) queue(d.getId());
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }

    boolean available() {
        return engine.available();
    }

    public void queue(Long documentId) {
        worker.submit(() -> read(documentId));
    }

    void read(Long id) {
        SourceDocument doc = tx.execute(s -> {
            SourceDocument d = documents.findById(id).orElse(null);
            if (d == null || d.getStatus() == SourceDocument.Status.ENCODED) return null;
            d.processing();
            return d;
        });
        if (doc == null) return;
        long start = System.currentTimeMillis();
        int pages = 0;
        try {
            byte[] file = storage.load(doc.getFilePath());
            List<OcrText.Page> read = engine.read(file, doc.getContentType());
            pages = read.size();
            Map<String, String> unitLookup = mappings.unitLookup();
            Set<String> spellings = DocumentParser.unitSpellings(unitLookup.keySet());
            DocumentParser.Result r = DocumentParser.parse(doc.getType(), read, spellings);
            String text = OcrText.plain(read);
            int seconds = (int) ((System.currentTimeMillis() - start) / 1000);
            int pageCount = pages;
            tx.executeWithoutResult(s -> {
                SourceDocument d = documents.findById(id).orElseThrow();
                if (d.getStatus() == SourceDocument.Status.ENCODED) return;
                d.read(r, pageCount, text, seconds);
                lines.deleteUnconfirmed(id);
                int n = 1;
                for (DocumentParser.Item item : r.items()) {
                    // The unit as the OPC stores it ("pcs" → "pc"), when the spelling is known.
                    String clean = item.unit() == null ? null : unitLookup.get(ValueMappingService.key(item.unit()));
                    DocumentParser.Item normalized = clean == null ? item : new DocumentParser.Item(item.quantity(), clean,
                            item.description(), item.unitCost(), item.totalCost(), item.category(), item.confidence());
                    lines.save(new ExtractedLine(id, n++, normalized));
                }
            });
            log.info("OCR read document {} ({} pages, {} lines) in {} s", id, pages, r.items().size(), seconds);
        } catch (Exception | Error e) {
            log.warn("OCR failed for document {}", id, e);
            int seconds = (int) ((System.currentTimeMillis() - start) / 1000);
            int pageCount = pages;
            String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            tx.executeWithoutResult(s -> documents.findById(id).ifPresent(d -> {
                if (d.getStatus() != SourceDocument.Status.ENCODED) d.failed(why, pageCount, seconds);
            }));
        }
    }
}
