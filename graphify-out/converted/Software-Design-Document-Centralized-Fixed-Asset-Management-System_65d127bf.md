<!-- converted from Software-Design-Document-Centralized-Fixed-Asset-Management-System.docx -->

# Software Design Document: Centralized Fixed-Asset Management System
Team 2627-sem1-it332-09
## 1. Introduction
### 1.1 Purpose
This Software Design Document (SDD) describes how the Centralized Fixed-Asset Management System (FAMS) will be built to meet the requirements in the companion Software Requirements Specification (SRS). It covers the architecture, data model, modules, API, key process flows, interface, security, deployment and testing approach. It guides the team through the three MVP sprints (November to December 2026) and is the reference for the IT office and future maintainers after handover in May 2027.
### 1.2 Scope
The design covers the MVP modules: document capture with OCR, asset register, issue orders, movements and turnovers, summaries and reports, search and history, access control and audit, and the one-time masterlist import. Optional extensions (QR labels, replacement flags, department-head access) are noted where the design leaves room for them.
### 1.3 Design goals
- Single source of truth. One database replaces every Excel copy; summaries are computed, never stored by hand.
- Movements are ledger entries. An asset's location changes only through an append-only movement transaction, so the register and the history cannot disagree.
- Human-confirmed OCR. OCR suggests; staff decide. Nothing extracted is saved unconfirmed.
- Least privilege and full traceability. Every action is checked against the user's role and every change is logged.
- Familiar outputs. Issue orders and reports print in the office's existing formats.
- Maintainable after handover. Mainstream, free technologies and a documented container deployment on a CIT-U server.
### 1.4 References
- FAMS Software Requirements Specification, v1.0 (2026).
- FAMS Project Proposal 2.0, Team 2627-sem1-it332-09 (2026).
- IEEE Std 1016-2009, Software Design Descriptions.
- Republic Act No. 10173, Data Privacy Act of 2012.
## 2. System Architecture
### 2.1 Architectural style
FAMS uses a three-tier, client–server architecture: a single-page web client, a layered REST backend, and a relational database with file storage. OCR runs as a separate worker inside the server so that slow scans never block the web requests of other users.
| Tier | Component | Responsibility |
| --- | --- | --- |
| Presentation | React single-page app | Screens, forms, document review, print previews; calls the REST API |
| Application | Spring Boot REST API | Business rules, validation, RBAC checks, transactions, audit logging, report generation |
| Application | OCR worker (Tesseract 5 via Tess4J) | Pre-processes images and extracts text and fields; results stored for review |
| Application | Scheduler | Nightly backup job; cleanup of expired sessions and temp files |
| Data | MySQL 8 database | Assets, documents, issue orders, movements, users, audit log |
| Data | File storage (server volume) | Uploaded documents, asset photos, generated PDFs |
| Data | Backup storage | Daily database dump and file archive, kept 30 days off the app server |
Request path: Browser → HTTPS (Nginx reverse proxy) → Spring Boot API → MySQL / file storage. Upload path: API saves the file → queues an OCR job → OCR worker writes the extraction → the client polls the job status and opens the review screen.
### 2.2 Backend layers
- Controller layer — REST endpoints, request validation, mapping to DTOs.
- Security layer — Spring Security filter chain: session authentication, role checks with method-level @PreAuthorize rules.
- Service layer — business rules per module (e.g., MovementService.post() runs inside one @Transactional boundary).
- Repository layer — Spring Data JPA repositories.
- Cross-cutting — an audit aspect/entity listener writes every create, update and delete to the audit log; a global exception handler returns consistent error responses.
### 2.3 Technology stack
| Concern | Choice | Reason |
| --- | --- | --- |
| Frontend | React 18 + Vite, React Router, Tailwind CSS, TanStack Query | Team familiarity; fast forms and tables |
| Backend | Java 21, Spring Boot 3 (Web, Security, Data JPA, Validation) | Mature transactions and security; team familiarity |
| Database | MySQL 8 (InnoDB) | ACID transactions, row locking, free |
| OCR | Tesseract 5 (Tess4J) with OpenCV pre-processing | Free, on-premise, cited in proposal (Smith, 2007) |
| PDF generation | JasperReports or OpenPDF with HTML templates | Matches office layouts; fast for 500 items |
| Excel import/export | Apache POI | Reads the current masterlist; writes .xlsx |
| Password hashing | BCrypt (Spring Security) | Salted, adaptive |
| Deployment | Docker Compose (nginx, app, mysql) on a CIT-U Linux server | Repeatable, easy to hand over |
| Version control & CI | GitHub, GitHub Actions (build + tests) | Code review and automated tests per sprint |
Note: the stack is the team's working assumption and can be swapped (e.g., PostgreSQL, Laravel) without changing the rest of this design.
## 3. Data Design
### 3.1 Entity overview
| Entity | Purpose | Key relationships |
| --- | --- | --- |
| user | System login account with one role | 1 user → many audit entries, documents, movements |
| department | Department or office that holds assets (the OPC is one row) | 1 department → many assets, employees |
| employee | Accountable person (personal data under the DPA) | belongs to 1 department; 1 → many assets |
| category | Fixed list of asset categories | 1 → many assets |
| location | Building/room | 1 → many assets |
| source_document | Uploaded sales invoice, PO or PR | 1 → many line items, assets |
| document_line_item | One extracted and confirmed line of a document | belongs to 1 document; 1 → many assets |
| ocr_field_result | OCR value vs. confirmed value per field (for RQ2) | belongs to 1 document |
| asset | One physical fixed asset (current state) | refs category, department, employee, location, line item |
| issue_order | Header of an issue order | 1 → many issue_order_items; refs department, employee |
| issue_order_item | One asset on an issue order | refs issue_order, asset |
| movement | Ledger header: type, source, destination, date, reason | 1 → many movement_items; may reverse another movement |
| movement_item | One asset moved by a movement, with its before/after state | refs movement, asset |
| audit_log | Append-only record of every change | refs user |
| backup_record | Result of each backup and restore run | — |
### 3.2 Table definitions
asset
| Column | Type | Constraints |
| --- | --- | --- |
| asset_id | BIGINT | PK, auto-increment |
| property_tag_no | VARCHAR(50) | UNIQUE, nullable |
| serial_no | VARCHAR(100) | UNIQUE, nullable; CHECK tag or serial not both null |
| category_id | INT | FK → category, NOT NULL |
| item_name | VARCHAR(200) | NOT NULL |
| brand, model_no, color, dimension | VARCHAR | nullable |
| unit_cost | DECIMAL(12,2) | ≥ 0 |
| acquisition_date | DATE |  |
| line_item_id | BIGINT | FK → document_line_item, nullable for migrated assets |
| photo_path | VARCHAR(255) |  |
| current_department_id | INT | FK → department, NOT NULL (updated only by MovementService) |
| current_employee_id | INT | FK → employee, nullable |
| current_location_id | INT | FK → location, nullable |
| condition | ENUM('GOOD','FAIR','DEFECTIVE','DAMAGED','FOR_DISPOSAL') | NOT NULL |
| status | ENUM('DRAFT','ACTIVE','ARCHIVED') | NOT NULL |
| version | INT | optimistic lock |
| created_at, updated_at, created_by, updated_by |  | audit columns |
movement
| Column | Type | Constraints |
| --- | --- | --- |
| movement_id | BIGINT | PK |
| movement_no | VARCHAR(30) | UNIQUE (e.g., MV-2026-000123) |
| type | ENUM('ISSUE','TRANSFER','DEFECTIVE_TURNOVER','DAMAGE_REPORT','RESIGNATION_TURNOVER','RETURN','REVERSAL','MIGRATION') | NOT NULL |
| source_department_id / source_employee_id / source_location_id | FK | source side |
| dest_department_id / dest_employee_id / dest_location_id | FK | destination side; dest_department NOT NULL |
| movement_date | DATE | NOT NULL |
| reason, remarks | TEXT |  |
| issue_order_id | BIGINT | FK, nullable (set for ISSUE) |
| reverses_movement_id | BIGINT | FK → movement, nullable |
| status | ENUM('DRAFT','POSTED') | NOT NULL |
| posted_by, posted_at |  | set when posted |
movement_item: movement_item_id (PK), movement_id (FK), asset_id (FK), condition_before, condition_after. UNIQUE (movement_id, asset_id).
issue_order: io_id (PK), io_no (UNIQUE, sequential), department_id, employee_id, request_ref, status ENUM('DRAFT','CONFIRMED','CANCELLED'), pdf_path, confirmed_by, confirmed_at. issue_order_item: io_id, asset_id, line_no; UNIQUE (io_id, asset_id).
source_document: doc_id, doc_type ENUM('SALES_INVOICE','PO','PR'), doc_no, supplier, doc_date, file_path, ocr_status ENUM('QUEUED','PROCESSING','DONE','FAILED'), print_type ENUM('PRINTED','TYPEWRITTEN','HANDWRITTEN','MIXED'), linked_po_id, linked_pr_id, uploaded_by, confirmed_by.
document_line_item: line_item_id, doc_id, line_no, description, quantity, unit, unit_cost.
ocr_field_result: id, doc_id, line_no (nullable for header fields), field_name ENUM('ITEM','QUANTITY','UNIT_COST','SUPPLIER','DATE','DOC_NO'), ocr_value, confidence, confirmed_value, is_correct (generated: ocr_value = confirmed_value).
user: user_id, username (UNIQUE), password_hash, full_name, role ENUM('ADMIN','STAFF','NAS','VIEW_ONLY'), is_active, failed_attempts, locked_until, last_login_at.
employee: employee_id, employee_no (UNIQUE), full_name, department_id, status ENUM('ACTIVE','RESIGNED').
audit_log: log_id, user_id, action ENUM('CREATE','UPDATE','DELETE','LOGIN','LOGIN_FAILED','EXPORT','BACKUP','RESTORE','ACCESS_DENIED'), entity_type, entity_id, old_value JSON, new_value JSON, ip_address, created_at. The application database user has INSERT and SELECT only on this table.
### 3.3 Integrity rules
- R1 Ledger rule. asset.current_* columns change only inside MovementService.post(), which, in one transaction, locks the asset rows (SELECT … FOR UPDATE), checks the source matches, inserts movement + movement_item rows and updates the asset. If any step fails, everything rolls back.
- R2 Immutability. Posted movements and audit entries are never updated or deleted; corrections are REVERSAL movements referencing the original.
- R3 Replayability. Replaying all posted movements in order from each asset's first movement must reproduce its current department. The reconciliation report (SRS FR-5.4) runs this check; its expected result is zero mismatches.
- R4 Migration. Each imported asset gets one MIGRATION movement into its current department, so every asset has a complete trail from day one.
- R5 Summaries are views. Department summaries are SQL views/queries grouped over asset (current state); nothing is stored by hand.
### 3.4 Indexes
Unique indexes on property_tag_no, serial_no, io_no, movement_no, username, employee_no; non-unique indexes on asset(current_department_id), asset(current_employee_id), movement_item(asset_id), audit_log(entity_type, entity_id); a FULLTEXT index on asset(item_name, brand, model_no) for keyword search.
## 4. Component Design
Each backend module is a package with its own controller, service, repository and DTOs. Modules talk to each other only through service interfaces.
| Module | Main classes | Responsibilities | SRS |
| --- | --- | --- | --- |
| document | DocumentController, DocumentService, OcrJobQueue, OcrWorker, FieldExtractor | Upload, store file, queue OCR, extract fields, save review results and OCR-vs-confirmed values | FR-1 |
| asset | AssetController, AssetService, DuplicateChecker | Create/edit assets, enforce identifier rules, duplicate warnings, photos | FR-2.1–2.7 |
| issueorder | IssueOrderController, IssueOrderService, IssueOrderPdfRenderer | Draft, confirm, cancel, number and render issue orders; post ISSUE movements | FR-3 |
| movement | MovementController, MovementService, ReconciliationService | Draft and post movements, resignation batch, reversals, reconciliation check | FR-4, FR-5.4 |
| report | ReportController, SummaryQueryService, ExcelExporter, PdfExporter | Department summaries, filtered reports, .xlsx/PDF export | FR-5 |
| search | SearchController, SearchService | Keyword/tag/serial search, asset history timeline | FR-6 |
| security | AuthController, UserService, SecurityConfig, PermissionEvaluator | Login, lockout, sessions, users, roles | FR-7.1–7.4 |
| audit | AuditListener, AuditService, AuditController | Write and view the audit log | FR-7.3 |
| backup | BackupScheduler, BackupService | Nightly dump + file archive, restore to a target, record results | FR-7.5 |
| migration | MasterlistImporter, ColumnMapper, ImportValidator | Parse the Excel masterlist, validate, preview, import with MIGRATION movements | FR-2.8 |
| reference | ReferenceController | Departments, employees, categories, locations | FR-2.2 |
### 4.1 OCR pipeline (document module)
- Pre-process (OpenCV): convert PDF pages to 300-dpi images, grayscale, deskew, denoise, adaptive threshold.
- Recognize (Tesseract 5, eng model, page segmentation mode 6 for tables): words with bounding boxes and confidence.
- Extract header fields with keyword anchors and regular expressions per document type, e.g., the value right of "Supplier", "P.O. No.", "Date"; dates normalized to ISO format, amounts to decimals.
- Extract line items by detecting the item table's column headers (Qty, Unit, Description, Unit Cost, Amount) and grouping words into rows by y-coordinate.
- Score each field: Tesseract confidence plus checks (quantity × unit cost = amount; date parses; supplier matches a known supplier). Fields under 80 are flagged for review.
- Save the extraction as ocr_field_result rows (ocr_value, confidence); the review screen writes confirmed_value.
OCR runs asynchronously on a bounded thread pool (2 workers), so a 3-page document does not block other users. Templates per document layout are kept in configuration so new Purchasing formats can be added without code changes.
### 4.2 Duplicate checking (asset module)
Before saving, DuplicateChecker returns matches on: exact serial number; exact property tag; or same normalized item name + brand + model on the same source document beyond its quantity. Serial and tag matches block the save (unique constraint); name matches show a warning the user can override with a reason, which is logged.
### 4.3 Issue-order rendering
IssueOrderPdfRenderer loads the order and its assets in one query (join fetch), loads photo thumbnails pre-generated at upload (300 px), and fills a template matching the office's current layout. Thumbnails, a single query and streaming output keep a 500-item order within the 10-second target (SRS NFR-P2).
## 5. API Design
REST over HTTPS, JSON bodies, base path /api/v1. Authentication by server session cookie (HttpOnly, Secure, SameSite=Strict) with CSRF token. Lists are paginated (?page=&size=&sort=). Errors return { "code", "message", "details" } with HTTP 400 (validation), 401, 403 (role), 404, 409 (conflict/duplicate/stale source).
| Method & path | Purpose | Roles |
| --- | --- | --- |
| POST /auth/login, POST /auth/logout, GET /auth/me | Session login/logout, current user | All |
| POST /documents (multipart) | Upload a document; queues OCR; returns doc_id | Admin, Staff, NAS |
| GET /documents/{id}/ocr | OCR status and extracted fields with confidence | Admin, Staff, NAS |
| PUT /documents/{id}/confirm | Save confirmed fields and line items; create assets | Admin, Staff, NAS |
| GET /assets?q=&tag=&serial=&dept=&category=&condition= | Search/list assets | All |
| POST /assets, PUT /assets/{id} | Create or edit descriptive fields | Admin, Staff, NAS (draft) |
| POST /assets/duplicate-check | Return likely duplicates for a draft asset | Admin, Staff, NAS |
| POST /assets/{id}/photo | Upload asset photo | Admin, Staff, NAS |
| GET /assets/{id}/history | Movement timeline for one asset | All |
| POST /issue-orders, PUT /issue-orders/{id} | Create/edit a draft IO | Admin, Staff |
| POST /issue-orders/{id}/confirm | Confirm, number, post ISSUE movements, render PDF | Admin, Staff |
| POST /issue-orders/{id}/cancel | Cancel a draft | Admin, Staff |
| GET /issue-orders/{id}/pdf | Download/print the IO | All |
| POST /movements | Create a draft movement | Admin, Staff, NAS |
| POST /movements/{id}/post | Post a movement (atomic) | Admin, Staff |
| POST /movements/{id}/reverse | Post a reversal of a movement | Admin, Staff |
| GET /employees/{id}/assets | Assets held by an employee (resignation list) | Admin, Staff |
| POST /movements/resignation | Batch turnover for a resigning employee | Admin, Staff |
| GET /reports/department-summary?dept= | Summary by category, count, cost, condition | All |
| GET /reports/reconciliation | Assets whose state disagrees with their ledger | Admin, Staff |
| GET /reports/{type}/export?format=xlsx\|pdf | Export masterlist, summaries, movement history | All |
| GET/POST/PUT /users | Manage users and roles | Admin |
| GET/POST/PUT /reference/{departments\|employees\|categories\|locations} | Reference lists | Admin (write), All (read) |
| GET /audit-log?user=&entity=&from=&to= | View the audit log | Admin |
| POST /backups/run, GET /backups, POST /backups/{id}/restore | Backups | Admin |
| POST /migration/preview, POST /migration/commit | Masterlist import | Admin |
Example — post a transfer
POST /api/v1/movements
{
  "type": "TRANSFER",
  "assetIds": [10452, 10453],
  "source": { "departmentId": 7, "employeeId": 112 },
  "destination": { "departmentId": 12, "employeeId": 230, "locationId": 54 },
  "movementDate": "2026-11-20",
  "reason": "Lab reassignment"
}
→ 201 { "movementId": 881, "movementNo": "MV-2026-000881", "status": "DRAFT" }

POST /api/v1/movements/881/post
→ 200 { "status": "POSTED", "postedAt": "2026-11-20T09:14:03+08:00" }
→ 409 { "code": "SOURCE_MISMATCH", "message": "Asset 10453 is now held by Dept 9" }
## 6. Key Process Designs
### 6.1 Encode a document with OCR (UC-02)
- Client → POST /documents with file and type.
- DocumentService saves the file, creates source_document (ocr_status = QUEUED), enqueues the job, returns doc_id.
- OcrWorker pre-processes, recognizes and extracts; writes ocr_field_result rows; sets status DONE (or FAILED).
- Client polls GET /documents/{id}/ocr every 2 s and opens the review screen when DONE.
- User corrects fields, adds tags/serials/category/photos per unit; client calls POST /assets/duplicate-check as each identifier is entered.
- Client → PUT /documents/{id}/confirm. In one transaction: save line items and confirmed values, create one asset per unit (department = OPC, status ACTIVE), and audit each insert.
- Encoding time per document is captured from upload to confirm for the evaluation (RQ2).
### 6.2 Confirm an issue order (UC-04)
- Client → POST /issue-orders/{id}/confirm.
- IssueOrderService locks the selected assets and checks each is still held by the OPC and ACTIVE; any that is not → 409 with the list.
- Assign the next io_no from a database sequence table (row-locked).
- Call MovementService.post() with one ISSUE movement (OPC → requesting department/employee) covering all assets.
- Commit; then render the PDF from the committed data and store pdf_path.
- Return the PDF URL; generation time (Confirm → PDF ready) is logged for NFR-P2.
### 6.3 Post a movement (core ledger operation)
MovementService.post(movementId, user):
  BEGIN TRANSACTION
    m = load movement (status must be DRAFT)
    require user.role in (ADMIN, STAFF)
    assets = SELECT * FROM asset WHERE asset_id IN m.items FOR UPDATE
    for each a in assets:
        if a.current_department_id != m.source_department_id
           or a.current_employee_id != m.source_employee_id:
            ROLLBACK; raise SOURCE_MISMATCH(a)
        record movement_item(condition_before = a.condition)
        a.current_department_id = m.dest_department_id
        a.current_employee_id   = m.dest_employee_id
        a.current_location_id   = m.dest_location_id
        if m.type in (DEFECTIVE_TURNOVER, DAMAGE_REPORT):
            a.condition = DEFECTIVE or DAMAGED
        movement_item.condition_after = a.condition
    m.status = POSTED; m.posted_by = user; m.posted_at = now()
    audit(UPDATE, asset, old, new) for each asset; audit(CREATE, movement)
  COMMIT
Because the asset update and the ledger entry share one transaction, a deduction from one department without an addition to another is impossible (SRS NFR-D1, RQ4).
### 6.4 Resignation turnover (UC-07)
GET /employees/{id}/assets lists every asset held by the employee. The user assigns destinations (one for all, or per asset) and submits; the service groups assets by destination into RESIGNATION_TURNOVER movements and posts them all in one transaction. On success the employee's status can be set to RESIGNED, which is blocked while any asset remains under their name.
### 6.5 Reversal
A wrong posted movement is never edited. POST /movements/{id}/reverse creates a REVERSAL movement with source and destination swapped and reverses_movement_id set, and posts it through the same post() path.
### 6.6 Masterlist migration
- Admin uploads the Excel workbook; MasterlistImporter reads each sheet with Apache POI.
- ColumnMapper maps columns to asset fields (saved mapping, editable).
- ImportValidator reports missing required fields, invalid departments, duplicate serials/tags and similar names, for staff to fix in the preview.
- On commit, each valid row becomes an asset plus one MIGRATION movement into its department; rejected rows are exported to a fix-up sheet.
- Before/after counts of duplicates and missing fields are recorded for the data-quality measure.
## 7. Interface Design
### 7.1 Screen inventory
| Screen | Main elements | Roles |
| --- | --- | --- |
| Login | Username, password, lockout message | All |
| Dashboard | Counts by department and condition, recent movements, documents awaiting review, reconciliation status (should read 0) | All |
| Documents › Upload | Drag-and-drop or scan, document type, print type | Admin, Staff, NAS |
| Documents › Review | Split view: image with highlighted regions left; header and line-item fields right; low-confidence fields in amber; per-unit tag/serial entry | Admin, Staff, NAS |
| Assets | Filterable table (tag, serial, item, department, holder, condition); asset form with duplicate banner | All (edit by role) |
| Asset detail | Photo, fields, current holder, movement timeline, source document link, actions: Transfer, Turn over, Report damage | All (actions by role) |
| Issue Orders | List; builder with asset picker (search, multi-select, count), preview with photos, Confirm, Print | Admin, Staff |
| Movements | List and form: type, assets, source (auto), destination, reason; Post; Reverse | Admin, Staff (NAS: draft) |
| Resignation turnover | Employee search, asset list, destination per asset, Post all | Admin, Staff |
| Reports | Summary per department, masterlist, movement history, reconciliation; filters; Export xlsx/PDF | All |
| Admin | Users, reference lists, audit log viewer, backups, masterlist import | Admin |
### 7.2 Interface guidelines
- Use the office's own words (masterlist, issue order, turnover, accountable person).
- The global search box accepts scanner input and jumps straight to the asset when a tag or serial matches exactly.
- Posting, confirming and restoring ask for confirmation and show what will change ("2 assets will move from CCS to CEA").
- 14 px minimum body text, WCAG AA contrast, keyboard-navigable forms; tested with older staff in each sprint demo.
## 8. Security Design
| Threat / requirement | Control |
| --- | --- |
| Unauthorized actions (NFR-S1) | Role checks on every endpoint (@PreAuthorize) plus hidden UI actions; deny by default; denied attempts logged as ACCESS_DENIED |
| Untraceable changes (NFR-S2) | JPA entity listener writes old/new values for every create/update/delete; DB user cannot UPDATE/DELETE audit_log |
| Password theft (NFR-S3) | BCrypt cost 12; min. 10 characters; admin resets force change at next login |
| Brute force / session hijack (NFR-S4) | Lockout after 5 failures for 15 min; 30-min idle timeout; HttpOnly, Secure, SameSite cookies; CSRF tokens |
| Injection, XSS, bad uploads (NFR-S6) | JPA parameterized queries; Bean Validation; React output escaping; uploads limited to PDF/JPG/PNG ≤ 20 MB, content-type sniffed, stored outside web root with random names |
| Data privacy (DPA, NFR-S5) | Only name, employee number and department stored for employees; access only after login; exports logged; HTTPS only; privacy notice on login page |
| Data loss (NFR-R1, R2) | Nightly mysqldump + file archive at 01:00, encrypted, copied to IT-office backup storage, 30-day retention; monthly restore drill on test server |
## 9. Deployment Design
- Server: one CIT-U Linux VM (suggested 4 vCPU, 8 GB RAM, 200 GB disk), approved by the IT office.
- Containers (Docker Compose): nginx (TLS, serves React build, proxies /api), app (Spring Boot + Tesseract), mysql (data volume), backup (cron job).
- Environments: local dev (each member), staging (team test server, sample data with sensitive details removed), production (CIT-U server).
- Pipeline: GitHub Actions builds and runs tests on each pull request; tagged releases produce images deployed with a documented script.
- Handover: repository, .env template, deployment and restore runbooks, user and admin manuals, training (May 2027).
## 10. Testing and Evaluation Design
| Level | What | How |
| --- | --- | --- |
| Unit | Services: movement rules, duplicate checks, OCR field parsers, permission evaluator | JUnit 5, Mockito; run on every PR |
| Integration | API + MySQL: atomic posting, rollback on mismatch, audit entries | Spring Boot Test + Testcontainers |
| Security | Every role × every forbidden endpoint; audit coverage; backup restore | Scripted test suite; 100% pass required (RQ6) |
| Performance | IO generation at 1, 50, 500 assets; search with 15,000+ IOs; 10 concurrent users | Timed tests, JMeter |
| OCR accuracy | 30 documents (printed, typewritten, handwritten); accuracy per field and document type | ocr_field_result.is_correct aggregated (RQ2) |
| Reconciliation | After each test movement, replay check returns 0 mismatches | ReconciliationService report (RQ4) |
| Acceptance & usability | Staff tasks per sprint demo; SUS after UAT | SUS ≥ 65 (RQ5) |
| Before-and-after | Encoding, IO preparation and retrieval times vs. Excel baseline | Time-and-motion study; paired t-test or Wilcoxon (RQ1–RQ3) |
## 11. Sprint Mapping
| Sprint | Dates | Design elements built |
| --- | --- | --- |
| 1 | 1–15 Nov 2026 | Project setup, auth and roles, reference lists, asset module, search, trial masterlist import |
| 2 | 16–30 Nov 2026 | Issue orders and PDF, movement ledger, resignation turnover, department summaries, reconciliation |
| 3 | 1–15 Dec 2026 | OCR pipeline and review screen, Excel/PDF export, audit log viewer, backups; MVP demo |
| Sem 2 | Jan–May 2027 | Full migration, refinements, evaluation (by March), deployment, manuals, training, handover |
