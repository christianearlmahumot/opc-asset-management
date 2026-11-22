package edu.citu.opc.fams.importer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SDD 2.3: POST /api/import/preview and POST /api/import. Administrators only (SRS 2.2). */
@RestController
@RequestMapping("/api/import")
@PreAuthorize("hasRole('ADMIN')")
public class ImportController {

    private final ImportService service;
    private final ObjectMapper json;

    public ImportController(ImportService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @PostMapping("/preview")
    public ImportService.Preview preview(@RequestPart("file") MultipartFile file,
                                         @RequestParam(required = false) String sheet) {
        return service.preview(file, sheet);
    }

    @PostMapping
    public ImportService.BatchView start(@RequestPart("file") MultipartFile file,
                                         @RequestPart("request") ImportService.StartRequest request) {
        return service.batch(service.start(file, request).getId());
    }

    @GetMapping("/batches")
    public List<ImportBatch> recent() {
        return service.recent();
    }

    @GetMapping("/batches/{id}")
    public ImportService.BatchView batch(@PathVariable Long id) {
        return service.batch(id);
    }

    @GetMapping("/batches/{id}/rows")
    public ImportService.RowPage rows(@PathVariable Long id,
                                      @RequestParam(required = false) String outcome,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return service.rows(id, outcome, page, size);
    }

    /** The rows that were not imported, as Excel, with the reason and the original 14 columns. */
    @GetMapping("/batches/{id}/rows.xlsx")
    public ResponseEntity<byte[]> rowsXlsx(@PathVariable Long id) throws IOException {
        List<ImportService.RowView> rows = service.allRows(id);
        List<String> headings = new ArrayList<>(List.of("Row", "Outcome", "Reason"));
        for (MasterlistParser.Field f : MasterlistParser.Field.values()) headings.add(f.heading);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            Sheet sh = wb.createSheet("Not imported");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            Row h = sh.createRow(0);
            for (int c = 0; c < headings.size(); c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(headings.get(c));
                cell.setCellStyle(head);
                sh.setColumnWidth(c, (c == 2 ? 50 : c == 13 ? 50 : 16) * 256);
            }
            sh.createFreezePane(0, 1);
            int r = 1;
            for (ImportService.RowView v : rows) {
                Row x = sh.createRow(r++);
                x.createCell(0).setCellValue(v.rowNo());
                x.createCell(1).setCellValue("SET_ASIDE".equals(v.outcome()) ? "Set aside" : "Rejected");
                x.createCell(2).setCellValue(v.reason());
                Map<String, String> cells = parse(v.cells());
                int c = 3;
                for (MasterlistParser.Field f : MasterlistParser.Field.values()) {
                    x.createCell(c++).setCellValue(cells.getOrDefault(f.heading, ""));
                }
            }
            wb.write(out);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Masterlist-import-" + id + "-not-imported.xlsx").build().toString())
                .body(out.toByteArray());
    }

    private Map<String, String> parse(String cells) {
        try {
            return json.readValue(cells, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (IOException e) {
            return Map.of();
        }
    }
}
