package edu.citu.opc.fams.document;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Local check against a real scanned purchase (not committed: the OPC's documents stay out of the repo).
 * Run with -Dsample.pdf=C:\path\file.pdf and, optionally, -Dsample.pages=SALES_INVOICE:1,PURCHASE_ORDER:3-6
 * to parse page ranges as separate documents. Prints what was read.
 */
class RealDocumentOcrTest {

    @Test
    void readsTheSample() throws Exception {
        String pdf = System.getProperty("sample.pdf");
        Assumptions.assumeTrue(pdf != null && !pdf.isBlank(), "sample.pdf not set");
        OcrEngine engine = new OcrEngine("tessdata", "eng");
        long t0 = System.currentTimeMillis();
        List<OcrText.Page> pages = engine.read(Files.readAllBytes(Path.of(pdf)), "application/pdf");
        System.out.println("OCR " + pages.size() + " pages in " + (System.currentTimeMillis() - t0) + " ms");
        Set<String> units = DocumentParser.unitSpellings(List.of());
        String spec = System.getProperty("sample.pages", "SALES_INVOICE:1-" + pages.size());
        for (String part : spec.split(",")) {
            String[] kv = part.split(":");
            String[] range = kv[1].split("-");
            int from = Integer.parseInt(range[0]);
            int to = range.length > 1 ? Integer.parseInt(range[1]) : from;
            List<OcrText.Page> slice = pages.subList(from - 1, Math.min(to, pages.size()));
            DocumentParser.Result r = DocumentParser.parse(SourceDocument.Type.valueOf(kv[0]), slice, units);
            System.out.println("==== " + kv[0] + " pages " + kv[1]);
            System.out.println("supplier=" + r.supplier() + " | po=" + r.poNo() + " | pr=" + r.prNo() + " | dept="
                    + r.departmentCode() + " | date=" + r.docDate() + " | conf=" + r.confidence());
            int n = 1;
            for (DocumentParser.Item i : r.items()) {
                String d = i.description() == null ? "" : i.description();
                System.out.println(n++ + ". qty=" + i.quantity() + " unit=" + i.unit() + " cost=" + i.unitCost()
                        + " total=" + i.totalCost() + " cat=" + i.category() + " conf=" + i.confidence());
                System.out.println("   " + (d.length() > 150 ? d.substring(0, 150) + "…" : d));
            }
        }
    }
}
