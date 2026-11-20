# Graph Report - asset-management-system  (2026-11-20)

## Corpus Check
- Corpus is ~23,725 words - fits in a single context window. You may not need a graph.

## Summary
- 371 nodes · 600 edges · 14 communities (13 shown, 1 thin omitted)
- Extraction: 84% EXTRACTED · 16% INFERRED · 0% AMBIGUOUS · INFERRED: 93 edges (avg confidence: 0.86)
- Token cost: 231,093 input · 0 output

## Community Hubs (Navigation)
- Proposal Objectives & Modules
- Issue Orders & Records
- Import, Export & Reports
- Asset Register & Audit
- OCR Capture & Asset Tagging
- Standards, Security & Stack
- Literature & Research Gap
- Excel Migration & Schema
- Auth & Architecture
- Legacy Design (MySQL/Docker)
- OCR Verification & Accuracy
- Document Capture Components
- Movements & Turnovers
- PDF Export

## God Nodes (most connected - your core abstractions)
1. `Software Design Description - OPC Fixed-Asset Management System` - 25 edges
2. `Fixed-Asset Management System Project Proposal` - 19 edges
3. `Software Requirements Specification (SRS)` - 18 edges
4. `SRS for OPC Fixed-Asset Management System` - 18 edges
5. `Software Design Document: Centralized FAMS` - 15 edges
6. `Three-tier Architecture` - 14 edges
7. `MovementService.post()` - 12 edges
8. `Problem statement: manual encoding into scattered Excel files` - 11 edges
9. `Before-and-after validation design` - 10 edges
10. `Minimum viable product (Dec 2026)` - 10 edges

## Surprising Connections (you probably didn't know these)
- `Research gap (proposal)` --semantically_similar_to--> `Research gap: no OCR capture + transaction tracking in a school property office`  [INFERRED] [semantically similar]
  graphify-out/converted/Fixed-Asset-Management-System-Project-Proposal-Revised_c92759a0.md → graphify-out/converted/Fixed-Asset-Management-System-Review-of-Related-Literature (1)_d4d83ca4.md
- `Movements are ledger entries (append-only)` --semantically_similar_to--> `Paired movement transaction (source + destination)`  [INFERRED] [semantically similar]
  graphify-out/converted/Software-Design-Document-Centralized-Fixed-Asset-Management-System_65d127bf.md → graphify-out/converted/Fixed-Asset-Management-System-Project-Proposal-Revised_c92759a0.md
- `DuplicateChecker` --conceptually_related_to--> `Data quality dimensions (completeness, consistency)`  [INFERRED]
  graphify-out/converted/Software-Design-Document-Centralized-Fixed-Asset-Management-System_65d127bf.md → graphify-out/converted/Fixed-Asset-Management-System-Review-of-Related-Literature (1)_d4d83ca4.md
- `OCR-assisted encoding with staff verification` --semantically_similar_to--> `Double entry verification`  [INFERRED] [semantically similar]
  graphify-out/converted/Fixed-Asset-Management-System-Project-Proposal-Revised_c92759a0.md → graphify-out/converted/Fixed-Asset-Management-System-Review-of-Related-Literature (1)_d4d83ca4.md
- `movement table` --conceptually_related_to--> `Asset life cycle management`  [INFERRED]
  graphify-out/converted/Software-Design-Document-Centralized-Fixed-Asset-Management-System_65d127bf.md → graphify-out/converted/Fixed-Asset-Management-System-Review-of-Related-Literature (1)_d4d83ca4.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Atomic movement ledger posting flow** — graphify_out_converted_software_design_document_centralized_fixed_asset_management_system_65d127bf_movementservice_post, graphify_out_converted_software_design_document_centralized_fixed_asset_management_system_65d127bf_entity_asset, graphify_out_converted_software_design_document_centralized_fixed_asset_management_system_65d127bf_entity_movement, graphify_out_converted_software_design_document_centralized_fixed_asset_management_system_65d127bf_entity_movement_item, graphify_out_converted_software_design_document_centralized_fixed_asset_management_system_65d127bf_r1_ledger_rule, graphify_out_converted_software_design_document_centralized_fixed_asset_management_system_65d127bf_auditservice [EXTRACTED 1.00]
- **OCR document capture and staff confirmation flow** — graphify_out_converted_software_design_document_centralized_fixed_asset_management_system_65d127bf_documentservice, graphify_out_converted_software_design_document_centralized_fixed_asset_management_system_65d127bf_ocrjobqueue, graphify_out_converted_software_design_document_centralized_fixed_asset_management_system_65d127bf_ocrworker, graphify_out_converted_software_design_document_centralized_fixed_asset_management_system_65d127bf_fieldextractor, graphify_out_converted_software_design_document_centralized_fixed_asset_management_system_65d127bf_entity_ocr_field_result, graphify_out_converted_software_design_document_centralized_fixed_asset_management_system_65d127bf_uc02_encode_document_with_ocr [EXTRACTED 1.00]
- **Literature evidence that the OPC problem is real** — graphify_out_converted_fixed_asset_management_system_review_of_related_literature__1__d4d83ca4_barchard_pace_2011, graphify_out_converted_fixed_asset_management_system_review_of_related_literature__1__d4d83ca4_caulkins_2007, graphify_out_converted_fixed_asset_management_system_review_of_related_literature__1__d4d83ca4_dehoratius_raman_2008, graphify_out_converted_fixed_asset_management_system_review_of_related_literature__1__d4d83ca4_massingham_2018, graphify_out_converted_fixed_asset_management_system_review_of_related_literature__1__d4d83ca4_ayochok_perez_2024 [EXTRACTED 1.00]
- **OCR-assisted document capture flow** — graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_documentcontroller, graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_documentservice, graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_filestorageservice, graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_ocrservice, graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_duplicatecheckservice, graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_assetservice [INFERRED 0.85]
- **JWT authentication and RBAC flow** — graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_authcontroller, graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_authservice, graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_jwtfilter, graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_securityconfig, graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_authcontext [EXTRACTED 1.00]
- **Movement and turnover transactions** — graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_tx_4_1_transfer_asset, graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_tx_4_2_record_condition_change_or_turnover, graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_tx_4_3_process_resignation_turnover, graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_movementservice, graphify_out_converted_sdd_opc_fixed_asset_management_system_176d8957_movement [EXTRACTED 1.00]
- **Transaction-based movement tracking** — graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_uc_202, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_uc_301, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_uc_302, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_uc_303, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_movement, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_req_306 [EXTRACTED 1.00]
- **OCR-assisted document capture flow** — graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_uc_101, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_uc_102, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_ocr, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_tesseract_ocr, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_purchasing_documents, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_draft_confirmation [EXTRACTED 1.00]
- **Security and privacy controls** — graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_req_401, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_req_403, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_req_404, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_req_406, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_bcrypt, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_jwt, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_rbac, graphify_out_converted_srs_opc_fixed_asset_management_system_fdbf8f95_audit_log [INFERRED 0.85]

## Communities (14 total, 1 thin omitted)

### Community 0 - "Proposal Objectives & Modules"
Cohesion: 0.05
Nodes (47): Access control and audit log module, Document capture module, Issue orders module, Limitations (typewritten/handwritten OCR, no integration, small sample), Minimum viable product (Dec 2026), Movements and turnovers module, Objective: accurate records across departments, Objective: faster, more accurate encoding (OCR >=85%, -40% time) (+39 more)

### Community 1 - "Issue Orders & Records"
Cohesion: 0.07
Nodes (37): Apache POI, Department Inventory Report, asset entity, dept_request / issue_order / issue_order_line entities, movement entity, Excel Masterlist of Issue Orders, Inventory of Turned-over Items, Issue Order (7-digit IO) (+29 more)

### Community 2 - "Import, Export & Reports"
Cohesion: 0.09
Nodes (34): Apache POI, AssetPicker, ColumnMapper, DepartmentSummaryPage, DeptRequestRepository, ExportService, ImportBatchRepository, ImportController (+26 more)

### Community 3 - "Asset Register & Audit"
Cohesion: 0.09
Nodes (33): Fixed Asset, assetApi, AssetController, AssetDetailPage, AssetFormPage, AssetRepository, AssetSearchPage, AssetService (+25 more)

### Community 4 - "OCR Capture & Asset Tagging"
Cohesion: 0.08
Nodes (29): Asset Categories (CAPEX-Equipment, CAPEX-Furniture and Fixtures, Inventoriable Items), Audit Log, Department Code, Duplicate Check, department / employee / category entities, source_document / extracted_line entities, user / audit_log / import_batch / value_mapping entities, Module 1: Document Capture (+21 more)

### Community 5 - "Standards, Security & Stack"
Cohesion: 0.09
Nodes (28): BCrypt Password Hashing, Capstone Project Proposal (Mahumot et al., 2026), Cebu Institute of Technology - University, Data Privacy Act of 2012 (RA 10173), Feature Completion Matrix, GitHub Actions pg_dump Daily Backup, IEEE Std 830-1998, JSON Web Token (JWT) (+20 more)

### Community 6 - "Literature & Research Gap"
Cohesion: 0.11
Nodes (27): Previous unfinished student-built system, Problem statement: manual encoding into scattered Excel files, Research gap (proposal), Ayochok & Perez (2024) - SUC inventory management, Mountain Province, Bandara et al. (2024) - Low-cost RFID inventory system, Caulkins et al. (2007) - Spreadsheet errors field interviews, Daghfous et al. (2013) - Managing knowledge loss, DeHoratius & Raman (2008) - Inventory record inaccuracy (+19 more)

### Community 7 - "Excel Migration & Schema"
Cohesion: 0.10
Nodes (22): Excel masterlist (~15,000 issue orders), Asset life cycle management, Schuman & Brent (2005) - Asset life cycle management, Apache POI, ColumnMapper, asset table, department table, issue_order table (+14 more)

### Community 8 - "Auth & Architecture"
Cohesion: 0.12
Nodes (24): AuthContext, AuthController, AuthService, BCrypt, Shared PostgreSQL Schema (ERD), GitHub Actions pg_dump daily backup, JSON Web Token (JWT), JwtFilter (+16 more)

### Community 9 - "Legacy Design (MySQL/Docker)"
Cohesion: 0.12
Nodes (22): AuditListener (JPA entity listener), BackupScheduler, BackupService, BCrypt password hashing, Republic Act No. 10173 Data Privacy Act of 2012, Docker Compose deployment on CIT-U server, audit_log table, employee table (+14 more)

### Community 10 - "OCR Verification & Accuracy"
Cohesion: 0.14
Nodes (16): Asset register module, RQ2 OCR field accuracy and encoding time saved, Barchard & Pace (2011) - Data entry methods and accuracy, Double entry verification, AssetService, DocumentService, DuplicateChecker, document_line_item table (+8 more)

### Community 11 - "Document Capture Components"
Cohesion: 0.16
Nodes (18): documentApi, DocumentController, DocumentService, DocumentViewer, ExtractedLineRepository, ExtractedLineTable, FileDropzone, FileStorageService (+10 more)

### Community 12 - "Movements & Turnovers"
Cohesion: 0.27
Nodes (13): EmployeeRepository, Module 4: Movements and Turnovers, Movement, movementApi, MovementController, MovementRepository, MovementService, ResignationTurnoverPage (+5 more)

## Knowledge Gaps
- **91 isolated node(s):** `OCRMiner`, `Perceived usefulness / ease of use (TAM)`, `DeLone & McLean IS success model`, `Team 2627-sem1-it332-09`, `RQ3 Issue-order and retrieval time reduction` (+86 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 94 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **1 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Fixed-Asset Management System Project Proposal` connect `Proposal Objectives & Modules` to `Legacy Design (MySQL/Docker)`, `OCR Verification & Accuracy`, `Literature & Research Gap`?**
  _High betweenness centrality (0.065) - this node is a cross-community bridge._
- **What connects `OCRMiner`, `Perceived usefulness / ease of use (TAM)`, `DeLone & McLean IS success model` to the rest of the system?**
  _91 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Proposal Objectives & Modules` be split into smaller, more focused modules?**
  _Cohesion score 0.05380852550663871 - nodes in this community are weakly interconnected._
- **Why does `Software Requirements Specification (SRS)` connect `Import, Export & Reports` to `Document Capture Components`, `Auth & Architecture`, `Asset Register & Audit`, `Movements & Turnovers`?**
  _High betweenness centrality (0.058) - this node is a cross-community bridge._
- **Should `Issue Orders & Records` be split into smaller, more focused modules?**
  _Cohesion score 0.07207207207207207 - nodes in this community are weakly interconnected._
- **Why does `Software Design Document: Centralized FAMS` connect `Legacy Design (MySQL/Docker)` to `Proposal Objectives & Modules`, `OCR Verification & Accuracy`, `Literature & Research Gap`, `Excel Migration & Schema`?**
  _High betweenness centrality (0.050) - this node is a cross-community bridge._
- **Should `Import, Export & Reports` be split into smaller, more focused modules?**
  _Cohesion score 0.09269162210338681 - nodes in this community are weakly interconnected._