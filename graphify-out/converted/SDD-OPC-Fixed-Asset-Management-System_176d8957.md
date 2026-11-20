<!-- converted from SDD-OPC-Fixed-Asset-Management-System.docx -->

CEBU INSTITUTE OF TECHNOLOGY
UNIVERSITY


COLLEGE OF COMPUTER STUDIES





Software Design Description
for
OPC FIXED-ASSET MANAGEMENT SYSTEM


















Change History Signature
| Version | Date | Description of change | Prepared by |
| --- | --- | --- | --- |
| 1.0 | 16 November 2026 | Release of the Software Design Description for adviser review, updated with the OPC's actual forms and records. | C. E. V. Mahumot, E. J. L. Saranza, S. B. V. Sanico, J. E. L. Selma, J. P. D. Dela Cruz |





Preface




This Software Design Description was prepared by Team 2627-sem1-it332-09 of the College of Computer Studies, Cebu Institute of Technology – University, for the OPC Fixed-Asset Management System. It describes how the requirements in the Software Requirements Specification will be built, and it is kept consistent with the Requirements Inventory and the Feature Completion Matrix.



Table of Contents
1. Introduction	6
1.1. Purpose	6
1.2. Scope	6
1.3. Definitions and Acronyms	6
1.4. References	7
2. Architectural Design	8
3. Detailed Design	12
Module 1: Document Capture	12
1.1 Upload Purchasing Document	12
1.2 Review and Confirm Extracted Fields	15
Module 2: Asset Register	19
2.1 Add Asset	19
2.2 Edit Asset	23
2.3 Import Excel Masterlist	27
2.4 Search Asset and View History	30
Module 3: Issue Orders	35
3.1 Record Department Request	35
3.2 Select Assets and Generate Issue Order	38
3.3 View and Reprint Issue Order	41
Module 4: Movements and Turnovers	45
4.1 Transfer Asset	45
4.2 Record Condition Change or Turnover	49
4.3 Process Resignation Turnover	53
Module 5: Summaries and Reports	58
5.1 View Department Inventory Report	58
5.2 Export Reports	61
Module 6: Access Control and Audit	63
6.1 Log In	63
6.2 Manage User Accounts and Roles	67
6.3 View Audit Log	71





# Introduction
## Purpose
This document describes the design of the OPC Fixed-Asset Management System: its architecture, the front-end and back-end components of each transaction, its classes, sequences and data. It implements the requirements in the Software Requirements Specification (SRS) and uses the same module and transaction numbers.
## Scope
The design covers the six modules of the minimum viable product: Document Capture, Asset Register, Issue Orders, Movements and Turnovers, Summaries and Reports, and Access Control and Audit. It covers fixed assets only, not consumables. Field names and the issue order and report layouts follow the OPC's current forms: the issue order, the Property Inventory Slip, the Masterlist of Issue Orders, the department inventory report and the Inventory of Turned-over Items.
## Definitions and Acronyms
| Term | Definition |
| --- | --- |
| Asset | A fixed asset of the university, such as laboratory equipment, a machine, a computer or a book, recorded individually. |
| Audit log | A record of every create, edit and delete, with the user, time and changed values. |
| BCrypt | A one-way password hashing function. |
| Department code | The short code of a department or office used in OPC records and CIT-U purchasing documents, for example MEDTECH, ME or OPC. |
| Issue order (IO) | The OPC document, numbered with 7 digits (for example 1804327), that releases items to a department and names the accountable person. It is signed by the Property Custodian, the dean and the department head. |
| JWT | JSON Web Token; a signed token that identifies a logged-in user and their role. |
| Masterlist | The complete list of assets, currently kept in Excel and replaced by the system's asset register. |
| Movement | A recorded change in an asset's department, holder or condition: issue, transfer, defective or damaged turnover, or resignation turnover. |
| MVP | Minimum viable product. |
| NAS | Non-Academic Scholar; a student assistant working in the section. |
| OCR | Optical character recognition; software that reads text from an image or scanned document. |
| OPC | Office of the Property Custodian of CIT-U. |
| PO / PR / SI | Purchase order, purchase request and sales invoice, the documents forwarded by Purchasing. The PO and PR are printed from CIT-U's own systems; the sales invoice comes from the supplier. |
| Property Inventory Slip | The sticker the OPC attaches to an asset, showing its property tag number, serial number, description, date acquired, acquisition cost, issue order number, department or location and accountable person. |
| Property tag number | The asset identifier on the Property Inventory Slip, in the form year-office-item type-number, for example 2018-PCO-PRNT-01. |
| RBAC | Role-based access control; permissions are given to roles, and users are assigned roles. |
| SRS / SDD | Software Requirements Specification / Software Design Description. |
| DTO | Data transfer object; the JSON shape exchanged between front end and back end. |
| JPA | Java Persistence API; maps Java entity classes to database tables. |

## References
1.  Mahumot, C. E. V., Saranza, E. J. L., Sanico, S. B. V., Selma, J. E. L., & Dela Cruz, J. P. D. (2026). A centralized fixed-asset management system with OCR-assisted document encoding and transaction-based movement tracking for the CIT-U Office of the Property Custodian Inventory Section [Capstone project proposal]. College of Computer Studies, Cebu Institute of Technology – University.
2.  Requirements Inventory – OPC Fixed-Asset Management System (2026). Team 2627-sem1-it332-09, College of Computer Studies, CIT-U.
3.  Software Requirements Specification – OPC Fixed-Asset Management System (2026). Team 2627-sem1-it332-09, College of Computer Studies, CIT-U.
4.  IEEE. (1998). IEEE recommended practice for software requirements specifications (IEEE Std 830-1998). Institute of Electrical and Electronics Engineers.
5.  Data Privacy Act of 2012, Rep. Act No. 10173 (2012). https://www.privacy.gov.ph/data-privacy-act/
# Architectural Design
The system has three tiers. The React single-page application runs in the user's browser and calls the Spring Boot REST API over HTTPS with a JWT. Inside the API, Spring Security checks the token and role on every request, controllers receive the requests, services hold the business rules, and Spring Data JPA repositories read and write Supabase PostgreSQL over SSL. Services also call Tesseract OCR through Tess4J, Apache POI and OpenPDF for files, and Supabase Storage for uploaded documents, photos and generated PDFs. The React app is served as a Render static site and the API runs on Render from a Docker image that includes Tesseract. Supabase is in the Singapore region, and the browser never connects to it directly, so role checks and the audit log cannot be bypassed. A GitHub Actions workflow backs up the database daily with pg_dump.

Figure 1. System architecture block diagram
| Layer | Frameworks, libraries and technologies |
| --- | --- |
| Front end | React 18 with Vite, React Router, Axios and Material UI (MUI), deployed as a Render static site |
| Back end | Java 17, Spring Boot 3 (Spring Web, Spring Data JPA, Spring Security), deployed as a Render web service from a Docker image |
| Database | Supabase PostgreSQL, Singapore region (ap-southeast-1), reached only from the Spring Boot back end over JDBC with SSL |
| File storage | Supabase Storage private buckets for scanned documents, asset photos and generated PDFs, accessed only by the back end |
| OCR | Tesseract OCR 5 through the Tess4J Java library, installed in the back-end Docker image |
| Documents | Apache POI (Excel import and export), OpenPDF (issue orders and reports) |
| Authentication | Spring Security with JSON Web Tokens (JWT) and BCrypt password hashing |
| Backup | Scheduled GitHub Actions workflow running pg_dump daily; encrypted dump kept as a private artifact |
| Version control | Git and GitHub |

Database overview. Every module uses the same PostgreSQL schema, shown below. The detailed design of each transaction shows only the tables it uses.


Figure 2. Entity relationship diagram of the full database (rotated)
# Detailed Design
### Module 1: Document Capture
1.1 Upload Purchasing Document
Implements REQ-101; specified in SRS use case UC-101.
User Interface Design

Figure 3. UI-101 Upload Purchasing Document screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| UploadDocumentPage | Page with the document type selector, document number field, file picker and upload button. | React page component (.jsx) |
| FileDropzone | Drag-and-drop or browse control that checks the file type before upload. | React component (.jsx) |
| documentApi | Sends the multipart upload request to the back end. | Axios service module (.js) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| DocumentController | REST endpoint POST /api/documents that receives the upload. | Spring @RestController |
| DocumentService | Validates the file, stores it and creates the SourceDocument record. | Spring @Service |
| FileStorageService | Uploads files to and downloads files from the private Supabase Storage bucket through its REST API, using a key held only on the server. | Spring @Service |
| SourceDocumentRepository | Reads and writes source_document rows. | Spring Data JPA repository |

Object-Oriented Components
Class Diagram

Figure 4. CD-101 Upload Purchasing Document class diagram
Sequence Diagram

Figure 5. SD-101 Upload Purchasing Document sequence diagram
Data Design

Figure 6. DB-101 Upload Purchasing Document entity relationship diagram
1.2 Review and Confirm Extracted Fields
Implements REQ-102, REQ-103, REQ-108, REQ-109; specified in SRS use case UC-102.
User Interface Design

Figure 7. UI-102 Review and Confirm Extracted Fields screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| ReviewFieldsPage | Split screen with the document preview on the left and editable item lines on the right. | React page component (.jsx) |
| DocumentViewer | Shows the uploaded image or PDF with zoom. | React component (.jsx) |
| ExtractedLineTable | Editable table of OCR lines; low-confidence cells are highlighted. | React component (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| DocumentController | GET /api/documents/{id}/lines returns OCR lines; POST /api/documents/{id}/confirm saves them. | Spring @RestController |
| OcrService | Runs Tesseract through Tess4J and parses the text into item lines with confidence scores. | Spring @Service |
| AssetService | Creates assets from confirmed lines inside one database transaction. | Spring @Service |
| DuplicateCheckService | Compares new lines with existing assets by serial number, tag number and item description. | Spring @Service |
| ExtractedLineRepository | Reads and writes extracted_line rows. | Spring Data JPA repository |

Object-Oriented Components
Class Diagram

Figure 8. CD-102 Review and Confirm Extracted Fields class diagram
Sequence Diagram

Figure 9. SD-102 Review and Confirm Extracted Fields sequence diagram
Data Design

Figure 10. DB-102 Review and Confirm Extracted Fields entity relationship diagram
### Module 2: Asset Register
2.1 Add Asset
Implements REQ-104, REQ-105; specified in SRS use case UC-104.
User Interface Design

Figure 11. UI-104 Add Asset screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| AssetFormPage | Form for all asset fields with a photo upload; reused by Edit Asset. | React page component (.jsx) |
| DuplicateWarningDialog | Dialog listing the matching asset with Cancel and Save Anyway buttons. | React component (.jsx) |
| assetApi | Calls the asset endpoints. | Axios service module (.js) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| AssetController | REST endpoint POST /api/assets. | Spring @RestController |
| AssetService | Validates the asset, assigns the asset code and saves it. | Spring @Service |
| DuplicateCheckService | Finds assets with the same serial or tag number, or the same category, item, brand and model. | Spring @Service |
| AssetRepository | Reads and writes asset rows. | Spring Data JPA repository |

Object-Oriented Components
Class Diagram

Figure 12. CD-104 Add Asset class diagram
Sequence Diagram

Figure 13. SD-104 Add Asset sequence diagram
Data Design

Figure 14. DB-104 Add Asset entity relationship diagram
2.2 Edit Asset
Implements REQ-106; specified in SRS use case UC-106.
User Interface Design

Figure 15. UI-106 Edit Asset screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| AssetFormPage | Same form as Add Asset, opened in edit mode; department and accountable person are read-only. | React page component (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| AssetController | REST endpoint PUT /api/assets/{id}. | Spring @RestController |
| AssetService | Applies the changes and passes old and new values to the audit service. | Spring @Service |
| AuditService | Writes an audit_log entry for the change. | Spring @Service |
| AssetRepository | Reads and writes asset rows. | Spring Data JPA repository |

Object-Oriented Components
Class Diagram

Figure 16. CD-106 Edit Asset class diagram
Sequence Diagram

Figure 17. SD-106 Edit Asset sequence diagram
Data Design

Figure 18. DB-106 Edit Asset entity relationship diagram
2.3 Import Excel Masterlist
Implements REQ-107; specified in SRS use case UC-107.
User Interface Design

Figure 19. UI-107 Import Excel Masterlist screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| ImportPage | Upload, column mapping and result summary in three steps. | React page component (.jsx) |
| ColumnMapper | Maps Excel columns to asset fields. | React component (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| ImportController | REST endpoints POST /api/import/preview and POST /api/import. | Spring @RestController |
| ImportService | Reads the workbook with Apache POI, validates rows and saves assets. | Spring @Service |
| ImportBatchRepository | Reads and writes import_batch rows. | Spring Data JPA repository |

Object-Oriented Components
Class Diagram

Figure 20. CD-107 Import Excel Masterlist class diagram
Sequence Diagram

Figure 21. SD-107 Import Excel Masterlist sequence diagram
Data Design

Figure 22. DB-107 Import Excel Masterlist entity relationship diagram
2.4 Search Asset and View History
Implements REQ-205; specified in SRS use case UC-205.
User Interface Design

Figure 23. UI-205 Search Asset and View History screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| AssetSearchPage | Search bar, filters and results table. | React page component (.jsx) |
| AssetDetailPage | Asset details, photo and movement history timeline. | React page component (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| AssetController | GET /api/assets?q= and GET /api/assets/{id}/history. | Spring @RestController |
| AssetService | Builds the search query and loads history. | Spring @Service |
| MovementRepository | Reads movement rows for an asset. | Spring Data JPA repository |

Object-Oriented Components
Class Diagram

Figure 24. CD-205 Search Asset and View History class diagram
Sequence Diagram

Figure 25. SD-205 Search Asset and View History sequence diagram
Data Design

Figure 26. DB-205 Search Asset and View History entity relationship diagram
### Module 3: Issue Orders
3.1 Record Department Request
Implements REQ-201; specified in SRS use case UC-201.
User Interface Design

Figure 27. UI-201 Record Department Request screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| RequestFormPage | Form for department, requester and item lines. | React page component (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| RequestController | REST endpoint POST /api/requests. | Spring @RestController |
| RequestService | Validates and saves the request. | Spring @Service |
| DeptRequestRepository | Reads and writes dept_request rows. | Spring Data JPA repository |

Object-Oriented Components
Class Diagram

Figure 28. CD-201 Record Department Request class diagram
Sequence Diagram

Figure 29. SD-201 Record Department Request sequence diagram
Data Design

Figure 30. DB-201 Record Department Request entity relationship diagram
3.2 Select Assets and Generate Issue Order
Implements REQ-202, REQ-203, REQ-206; specified in SRS use case UC-202.
User Interface Design

Figure 31. UI-202 Select Assets and Generate Issue Order screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| PrepareIssueOrderPage | Request summary, available-asset picker and Confirm button. | React page component (.jsx) |
| AssetPicker | Searchable, selectable list of available assets with photos. | React component (.jsx) |
| IssueOrderPreview | Shows the generated PDF with Print and Download buttons. | React component (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| IssueOrderController | REST endpoint POST /api/issue-orders and GET /api/issue-orders/{id}/pdf. | Spring @RestController |
| IssueOrderService | Creates the issue order, assigns assets and records movements in one @Transactional method. | Spring @Service |
| PdfService | Builds the issue order PDF with OpenPDF, including item photos. | Spring @Service |
| IssueOrderRepository | Reads and writes issue_order and issue_order_item rows. | Spring Data JPA repository |

Object-Oriented Components
Class Diagram

Figure 32. CD-202 Select Assets and Generate Issue Order class diagram
Sequence Diagram

Figure 33. SD-202 Select Assets and Generate Issue Order sequence diagram
Data Design

Figure 34. DB-202 Select Assets and Generate Issue Order entity relationship diagram
3.3 View and Reprint Issue Order
Implements REQ-204; specified in SRS use case UC-204.
User Interface Design

Figure 35. UI-204 View and Reprint Issue Order screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| IssueOrderHistoryPage | Filterable table of issue orders with View and Print actions. | React page component (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| IssueOrderController | GET /api/issue-orders with filters. | Spring @RestController |
| IssueOrderService | Builds the filtered query. | Spring @Service |
| IssueOrderRepository | Reads issue_order rows. | Spring Data JPA repository |

Object-Oriented Components
Class Diagram

Figure 36. CD-204 View and Reprint Issue Order class diagram
Sequence Diagram

Figure 37. SD-204 View and Reprint Issue Order sequence diagram
Data Design

Figure 38. DB-204 View and Reprint Issue Order entity relationship diagram
### Module 4: Movements and Turnovers
4.1 Transfer Asset
Implements REQ-301, REQ-306; specified in SRS use case UC-301.
User Interface Design

Figure 39. UI-301 Transfer Asset screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| TransferDialog | Dialog showing the source and fields for destination department, accountable person and reason. | React component (.jsx) |
| movementApi | Calls the movement endpoints. | Axios service module (.js) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| MovementController | REST endpoint POST /api/movements/transfer. | Spring @RestController |
| MovementService | Updates the asset and saves the movement inside one @Transactional method. | Spring @Service |
| MovementRepository | Reads and writes movement rows. | Spring Data JPA repository |

Object-Oriented Components
Class Diagram

Figure 40. CD-301 Transfer Asset class diagram
Sequence Diagram

Figure 41. SD-301 Transfer Asset sequence diagram
Data Design

Figure 42. DB-301 Transfer Asset entity relationship diagram
4.2 Record Condition Change or Turnover
Implements REQ-302; specified in SRS use case UC-302.
User Interface Design

Figure 43. UI-302 Record Condition Change or Turnover screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| TurnoverDialog | Dialog with turnover type, reason and optional photo. | React component (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| MovementController | REST endpoint POST /api/movements/turnover. | Spring @RestController |
| MovementService | Updates condition and custody and saves the movement in one transaction. | Spring @Service |

Object-Oriented Components
Class Diagram

Figure 44. CD-302 Record Condition Change or Turnover class diagram
Sequence Diagram

Figure 45. SD-302 Record Condition Change or Turnover sequence diagram
Data Design

Figure 46. DB-302 Record Condition Change or Turnover entity relationship diagram
4.3 Process Resignation Turnover
Implements REQ-303; specified in SRS use case UC-303.
User Interface Design

Figure 47. UI-303 Process Resignation Turnover screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| ResignationTurnoverPage | Employee search and table of held assets with a destination per row. | React page component (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| MovementController | GET /api/employees/{id}/assets and POST /api/movements/resignation. | Spring @RestController |
| MovementService | Records one transaction per asset. | Spring @Service |
| EmployeeRepository | Reads employee rows. | Spring Data JPA repository |

Object-Oriented Components
Class Diagram

Figure 48. CD-303 Process Resignation Turnover class diagram
Sequence Diagram

Figure 49. SD-303 Process Resignation Turnover sequence diagram
Data Design

Figure 50. DB-303 Process Resignation Turnover entity relationship diagram
### Module 5: Summaries and Reports
5.1 View Department Inventory Report
Implements REQ-304; specified in SRS use case UC-304.
User Interface Design

Figure 51. UI-304 View Department Inventory Report screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| DepartmentSummaryPage | Department selector, total cards and grouped table. | React page component (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| ReportController | GET /api/reports/departments/{id}/summary. | Spring @RestController |
| SummaryService | Runs the grouped query over current assets. | Spring @Service |

Object-Oriented Components
Class Diagram

Figure 52. CD-304 View Department Inventory Report class diagram
Sequence Diagram

Figure 53. SD-304 View Department Inventory Report sequence diagram
Data Design

Figure 54. DB-304 View Department Inventory Report entity relationship diagram
5.2 Export Reports
Implements REQ-305; specified in SRS use case UC-305.
User Interface Design

Figure 55. UI-305 Export Reports screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| ReportsPage | Report type, filters, format and Export button. | React page component (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| ReportController | GET /api/reports/export?type=&format=. | Spring @RestController |
| ExportService | Builds Excel files with Apache POI and PDF files with OpenPDF. | Spring @Service |

Object-Oriented Components
Class Diagram

Figure 56. CD-305 Export Reports class diagram
Sequence Diagram

Figure 57. SD-305 Export Reports sequence diagram
Data Design

Figure 58. DB-305 Export Reports entity relationship diagram
### Module 6: Access Control and Audit
6.1 Log In
Implements REQ-401, REQ-403; specified in SRS use case UC-401.
User Interface Design

Figure 59. UI-401 Log In screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| LoginPage | Username and password form. | React page component (.jsx) |
| AuthContext | Stores the token and role and hides menus the role cannot use. | React context (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| AuthController | REST endpoint POST /api/auth/login. | Spring @RestController |
| AuthService | Verifies credentials with BCrypt and issues the JWT. | Spring @Service |
| JwtFilter | Checks the token on every request and loads the user's role. | Spring Security filter |
| SecurityConfig | Declares which roles may call each endpoint. | Spring @Configuration |

Object-Oriented Components
Class Diagram

Figure 60. CD-401 Log In class diagram
Sequence Diagram

Figure 61. SD-401 Log In sequence diagram
Data Design

Figure 62. DB-401 Log In entity relationship diagram
6.2 Manage User Accounts and Roles
Implements REQ-402, REQ-403, REQ-406; specified in SRS use case UC-402.
User Interface Design

Figure 63. UI-402 Manage User Accounts and Roles screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| UserManagementPage | Table of users with role, status and Add, Edit, Deactivate actions. | React page component (.jsx) |
| UserFormDialog | Form for name, username, temporary password and role. | React component (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| UserController | REST endpoints /api/users (Administrator only). | Spring @RestController |
| UserService | Creates, updates and deactivates accounts; hashes passwords with BCrypt. | Spring @Service |
| UserRepository | Reads and writes user rows. | Spring Data JPA repository |

Object-Oriented Components
Class Diagram

Figure 64. CD-402 Manage User Accounts and Roles class diagram
Sequence Diagram

Figure 65. SD-402 Manage User Accounts and Roles sequence diagram
Data Design

Figure 66. DB-402 Manage User Accounts and Roles entity relationship diagram
6.3 View Audit Log
Implements REQ-404; specified in SRS use case UC-404.
User Interface Design

Figure 67. UI-404 View Audit Log screen
Front-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| AuditLogPage | Filterable table of audit entries with a detail drawer. | React page component (.jsx) |

Back-end component(s)
| Component name | Description and purpose | Component type or format |
| --- | --- | --- |
| AuditLogController | GET /api/audit-logs (Administrator only). | Spring @RestController |
| AuditService | Writes entries for every create, edit and delete and queries them. | Spring @Service |
| AuditLogRepository | Reads and writes audit_log rows; no update or delete methods are exposed. | Spring Data JPA repository |

Object-Oriented Components
Class Diagram

Figure 68. CD-404 View Audit Log class diagram
Sequence Diagram

Figure 69. SD-404 View Audit Log sequence diagram
Data Design

Figure 70. DB-404 View Audit Log entity relationship diagram