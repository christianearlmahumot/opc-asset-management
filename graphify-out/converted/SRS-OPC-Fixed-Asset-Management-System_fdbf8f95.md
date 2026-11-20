<!-- converted from SRS-OPC-Fixed-Asset-Management-System.docx -->


CEBU INSTITUTE OF TECHNOLOGY
UNIVERSITY


COLLEGE OF COMPUTER STUDIES





Software Requirements Specifications
for
OPC FIXED-ASSET MANAGEMENT SYSTEM
















# Change History
| Version | Date | Description of change | Prepared by |
| --- | --- | --- | --- |
| 1.0 | 16 November 2026 | Release of the Software Requirements Specification for adviser review, updated with the OPC's actual forms and records. | C. E. V. Mahumot, E. J. L. Saranza, S. B. V. Sanico, J. E. L. Selma, J. P. D. Dela Cruz |









# Table of Contents
1. Introduction	5
1.1. Purpose	5
1.2. Scope	5
1.3. Definitions, Acronyms and Abbreviations	5
1.4. References	6
2. Overall Description	7
2.1. Product perspective	7
2.2. User characteristics	7
2.3. Product functions	8
2.4. Constraints	8
2.5. Assumptions and dependencies	9
3. Specific Requirements	10
3.1. External interface requirements	10
3.1.1. Hardware interfaces	10
3.1.2. Software interfaces	10
3.1.3. Communications interfaces	10
3.2. Functional requirements	10
Module 1: Document Capture	10
1.1 Upload Purchasing Document	11
1.2 Review and Confirm Extracted Fields	13
Module 2: Asset Register	16
2.1 Add Asset	17
2.2 Edit Asset	19
2.3 Import Excel Masterlist	21
2.4 Search Asset and View History	24
Module 3: Issue Orders	26
3.1 Record Department Request	26
3.2 Select Assets and Generate Issue Order	28
3.3 View and Reprint Issue Order	31
Module 4: Movements and Turnovers	32
4.1 Transfer Asset	33
4.2 Record Condition Change or Turnover	35
4.3 Process Resignation Turnover	37
Module 5: Summaries and Reports	39
5.1 View Department Inventory Report	39
5.2 Export Reports	41
Module 6: Access Control and Audit	43
6.1 Log In	44
6.2 Manage User Accounts and Roles	47
6.3 View Audit Log	48
3.3. Logical database requirements	51
3.4. Non-functional requirements	51
Performance	51
Security	52
Reliability	52














# Introduction
## Purpose
This document specifies the requirements of the OPC Fixed-Asset Management System, a web-based system for the Inventory Section of the Office of the Property Custodian (OPC) of Cebu Institute of Technology – University (CIT-U). It is the reference for the development team, the capstone adviser and the OPC staff, and it is the basis of the Software Design Description, the Requirements Inventory and the Feature Completion Matrix.
## Scope
The system replaces the section's Excel masterlist with one central database for the university's fixed assets: laboratory equipment, machines, computers, books and other building contents assigned to departments. Consumables such as bond paper and office supplies are outside its scope. The system includes the following core functionalities:
•  Document Capture: Turns the sales invoices, purchase orders and purchase requests forwarded by Purchasing into masterlist records through OCR with staff verification.
•  Asset Register: Keeps one central record of every fixed asset, with structured fields, photos, accountable person and condition, and lets staff find any asset with its history.
•  Issue Orders: Records department requests and generates printable issue orders, with item photos, directly from the asset register.
•  Movements and Turnovers: Records every transfer, defective or damaged turnover and resignation turnover as one transaction with a source and a destination, so the masterlist never disagrees with recorded movements.
•  Summaries and Reports: Calculates department summaries from the transaction records and exports the masterlist, summaries and issue orders to Excel and PDF.
•  Access Control and Audit: Protects the records and the personal data of accountable employees through login, role-based permissions, an audit log and daily backups.
The system does not connect to the university's purchasing, finance or human resources systems; documents from Purchasing are uploaded, and employees and departments are maintained in the system.
## Definitions, Acronyms and Abbreviations
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

## References
1.  Mahumot, C. E. V., Saranza, E. J. L., Sanico, S. B. V., Selma, J. E. L., & Dela Cruz, J. P. D. (2026). A centralized fixed-asset management system with OCR-assisted document encoding and transaction-based movement tracking for the CIT-U Office of the Property Custodian Inventory Section [Capstone project proposal]. College of Computer Studies, Cebu Institute of Technology – University.
2.  Requirements Inventory – OPC Fixed-Asset Management System (2026). Team 2627-sem1-it332-09, College of Computer Studies, CIT-U.
3.  IEEE. (1998). IEEE recommended practice for software requirements specifications (IEEE Std 830-1998). Institute of Electrical and Electronics Engineers.
4.  Data Privacy Act of 2012, Rep. Act No. 10173 (2012). https://www.privacy.gov.ph/data-privacy-act/
# Overall Description
## Product perspective
The system is a new, self-contained web application. It follows a client-server architecture: a React single-page application in the browser calls a Spring Boot REST API, which stores data in a Supabase PostgreSQL database and files in Supabase Storage, both in the Singapore region, and uses Tesseract OCR to read uploaded documents. The front end and back end are hosted on Render. It mirrors the section's existing six-step workflow:
1.  Purchasing forwards the sales invoice, purchase order and purchase request.
2.  Staff encode the items into the masterlist.
3.  Staff create an issue order with a photo of each item.
4.  Staff keep a summary per department or office.
5.  Staff record turned-over items: defective, damaged or transferred.
6.  Staff print the issue order.
Excel and PDF remain the outputs, so printed issue orders and reports keep the form the office already uses.
The current records, reviewed from files supplied by the OPC, show the problems the system addresses:
•  The Masterlist of Issue Orders holds 15,277 item lines from 4,790 issue orders dated 2008 to 2026, in 14 columns.
•  The same departments are written in 203 different ways, and units in 74 spellings (for example copy, COPY and copies).
•  The Issued By column is sometimes used for notes such as cancelled, deleted due to duplication or superseded.
•  The Inventory of Turned-over Items holds 4,488 rows with free-text remarks (for example DEFECTIVE, NO LONGER USED, FOR UPGRADE), no link to the original issue order, and a disposal date on only one row.
•  Older assets carry a printed Property Inventory Slip; these stickers are not maintained consistently.
## User characteristics
| Role | Characteristics and responsibilities |
| --- | --- |
| Administrator | The Property Custodian or a senior OPC staff member. Manages user accounts and roles, the department list and categories, imports the masterlist, views the audit log and checks backups. Has every permission. |
| Property Staff | OPC Inventory Section staff, such as the storekeeper. Capture purchasing documents, maintain the asset register, prepare and print issue orders, record transfers, turnovers and resignation turnovers, and produce reports. |
| NAS Assistant | Non-Academic Scholar assisting the section. Can upload documents, review OCR fields and encode assets, which stay as drafts until Property Staff confirm them. Can search the register. Cannot issue, transfer or turn over assets, export reports or manage users. |
| Department Head | Chair, dean or office head accountable for a department's assets. View-only access to the assets, inventory report and history of their own department, so personal data of other departments is not exposed. |

Permissions by role:
| Action | Roles allowed |
| --- | --- |
| Upload document, review OCR fields, encode assets as drafts | Administrator, Property Staff, NAS Assistant |
| Confirm drafts, add or edit assets | Administrator, Property Staff |
| Import Excel masterlist, manage departments and categories | Administrator |
| Search assets and view history | Administrator, Property Staff, NAS Assistant; Department Head for own department only |
| Record request, generate and print issue order | Administrator, Property Staff |
| Record transfer, condition change, turnover, resignation turnover | Administrator, Property Staff |
| View department inventory report | Administrator, Property Staff; Department Head for own department only |
| Export reports | Administrator, Property Staff |
| Manage users and roles, view audit log | Administrator |

Older staff are used to physical property tags and find serial-based tracking harder to adopt, so screens must be simple and support both tag and serial numbers.
## Product functions
Module 1 – Document Capture. Turns the sales invoices, purchase orders and purchase requests forwarded by Purchasing into masterlist records through OCR with staff verification.
Module 2 – Asset Register. Keeps one central record of every fixed asset, with structured fields, photos, accountable person and condition, and lets staff find any asset with its history.
Module 3 – Issue Orders. Records department requests and generates printable issue orders, with item photos, directly from the asset register.
Module 4 – Movements and Turnovers. Records every transfer, defective or damaged turnover and resignation turnover as one transaction with a source and a destination, so the masterlist never disagrees with recorded movements.
Module 5 – Summaries and Reports. Calculates department summaries from the transaction records and exports the masterlist, summaries and issue orders to Excel and PDF.
Module 6 – Access Control and Audit. Protects the records and the personal data of accountable employees through login, role-based permissions, an audit log and daily backups.
## Constraints
•  Regulatory policy: the records contain personal data of accountable employees, which must be protected under the Data Privacy Act of 2012 (Republic Act No. 10173).
•  Hosting: the MVP runs on Render (front end and back end) and Supabase (database and files). On the free plans the back end sleeps after 15 minutes without traffic, and an inactive Supabase project pauses after a week, so both are opened before tests and demonstrations. Long-term hosting by CIT-U is to be agreed with the university.
•  Data residency: employee names stored as accountable persons are kept in Supabase's Singapore region; access is limited to the back end and to permitted roles, as the Data Privacy Act of 2012 requires.
•  Interfaces to other applications: none. Outputs must remain available as Excel and PDF files.
•  Audit functions: every create, edit and delete must be logged and cannot be edited through the system.
•  Confidential HR notices: resignations reach the OPC by email from HR. These notices contain sensitive information and are not stored in the system; staff start the resignation turnover manually.
•  Control functions: each action is allowed only to the roles in Section 2.2.
•  Reliability: every movement must be saved in one database transaction so the masterlist never disagrees with recorded movements.
•  Schedule: the minimum viable product must be working by the end of the first semester of AY 2026–2027.
•  OCR accuracy: printed documents are the target; partly typewritten or handwritten documents always need staff correction.
## Assumptions and dependencies
•  OPC computers have internet access to reach the Render and Supabase services.
•  The free plans of Render and Supabase are enough for the MVP: 500 MB of database and 1 GB of file storage on Supabase.
•  OPC staff use a current version of Google Chrome or Microsoft Edge.
•  OPC provides sample sales invoices, purchase orders and purchase requests, with sensitive details removed, for OCR testing.
•  The current Excel masterlist can be exported to .xlsx with one row per asset or issue-order line.
•  Serial and property tag numbers are typed in, or entered with a barcode scanner acting as a keyboard if the office uses one.
•  The department list and department heads come from the Administrators sheet of the OPC's masterlist workbook.
•  Asset categories follow the item handled; the CIT-U purchase request categories (CAPEX-Equipment, CAPEX-Furniture and Fixtures, Inventoriable Items) are used as the default list.
•  Accountable persons are maintained by OPC staff in the system; no HR data is imported.
# Specific Requirements
## External interface requirements
### 3.1.1.	Hardware interfaces
•  Client: any desktop or laptop able to run a current web browser, with a screen width of at least 1280 pixels.
•  Document input: a flatbed scanner or a phone camera producing JPG, PNG or PDF files.
•  Barcode scanner (optional): any USB scanner acting as a keyboard, for property tag and serial numbers.
•  Printer: any printer available to the browser, for issue orders and reports.
•  Server: no on-premises server for the MVP; the back end runs on Render and the data on Supabase.
### 3.1.2.	Software interfaces
| Component | Software |
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

### 3.1.3.	Communications interfaces
•  Browser to server: HTTPS on the CIT-U network; the API exchanges JSON over REST.
•  Authentication: every API call after login carries a JWT in the Authorization header.
•  Back end to database: JDBC over SSL to Supabase PostgreSQL; back end to file storage: Supabase Storage REST API with a key held only on the server. The browser never connects to Supabase directly.
## Functional requirements
### Module 1: Document Capture
Turns the sales invoices, purchase orders and purchase requests forwarded by Purchasing into masterlist records through OCR with staff verification.

Figure 1. Use case overview of Module 1: Document Capture
1.1 Upload Purchasing Document
Related requirements: REQ-101
Use Case Diagram

Figure 2. UC-101 Upload Purchasing Document use case diagram
Use Case Description
| Use case ID | UC-101 |
| --- | --- |
| Use case name | Upload Purchasing Document |
| Actor(s) | Property Staff, NAS Assistant |
| Description | Property Staff or a NAS Assistant upload the scanned sales invoice, purchase order or purchase request of a purchase so the system can read it. The three documents of one purchase are linked by the PO and PR numbers. |
| Preconditions | The user is logged in with permission to capture documents. The user has the document as an image (JPG, PNG) or PDF file. |
| Postconditions | The document is stored and linked to a new encoding session. The document is queued for OCR. |
| Main flow | 1. The user opens Document Capture and selects Upload Document. 2. The user chooses the document type (sales invoice, purchase order or purchase request) and enters its number, for example sales invoice 3135, PO 000004960 or PR-1004MEDTEC-26-009330. 3. The user selects the scanned file and clicks Upload and Read. 4. The system checks the file type. 5. The system stores the file, creates a source document record, links it to other documents with the same PO or PR number, and starts OCR. 6. The system shows the document preview and opens the review screen. |
| Alternate flows | 4a. The file is not JPG, PNG or PDF: the system rejects it and asks for another file. 4b. The document number already exists for that document type: the system warns of a possible duplicate and asks the user to confirm. |
| Related requirements | REQ-101 |

Activity Diagram

Figure 3. AD-101 Upload Purchasing Document activity diagram
Wireframe

Figure 4. WF-101 Upload Purchasing Document wireframe
1.2 Review and Confirm Extracted Fields
Related requirements: REQ-102, REQ-103, REQ-108, REQ-109
Use Case Diagram

Figure 5. UC-102 Review and Confirm Extracted Fields use case diagram
Use Case Description
| Use case ID | UC-102 |
| --- | --- |
| Use case name | Review and Confirm Extracted Fields |
| Actor(s) | Property Staff, NAS Assistant |
| Description | The system reads the uploaded document by OCR and pre-fills the header (supplier, invoice number and date, PO and PR numbers, department code) and the item lines (quantity, unit, description, unit cost, total, and category from the PR). The user confirms or corrects every field before the items are saved. |
| Preconditions | A document has been uploaded (transaction 1.1). OCR has finished for the document. |
| Postconditions | The confirmed lines are saved as asset items in OPC storage, linked to the source documents and ready for an issue order. The document is marked Encoded and the change is written to the audit log. |
| Main flow | 1. The system runs OCR on the document and maps the text to the header fields and item lines. 2. The system shows the document preview beside the pre-filled fields, with low-confidence fields highlighted. 3. The user checks each field against the document and corrects any errors, including handwritten marks on sales invoices. 4. For items tracked one by one, the user adds the serial number, brand and model. 5. The user clicks Confirm and Save. A NAS Assistant's entries are saved as drafts for Property Staff to confirm. 6. The system validates every line, normalizes the unit (for example pcs, pc and pieces become pc) and runs the duplicate check. 7. The system saves the items in OPC storage, ready to be issued, and records the encoding time. |
| Alternate flows | 1a. OCR cannot read the document: the system shows empty lines for manual encoding. 6a. A required field is missing: the system highlights it and does not save. 6b. A line matches an existing asset: the system shows the duplicate warning; the user removes the line or confirms it is a new asset. |
| Related requirements | REQ-102, REQ-103, REQ-108, REQ-109 |

Activity Diagram

Figure 6. AD-102 Review and Confirm Extracted Fields activity diagram
Wireframe

Figure 7. WF-102 Review and Confirm Extracted Fields wireframe
### Module 2: Asset Register
Keeps one central record of every fixed asset, with structured fields, photos, accountable person and condition, and lets staff find any asset with its history.

Figure 8. Use case overview of Module 2: Asset Register
2.1 Add Asset
Related requirements: REQ-104, REQ-105
Use Case Diagram

Figure 9. UC-104 Add Asset use case diagram
Use Case Description
| Use case ID | UC-104 |
| --- | --- |
| Use case name | Add Asset |
| Actor(s) | Property Staff, NAS Assistant |
| Description | Property Staff add an asset item manually, using the fields of the OPC's Property Inventory Slip and masterlist. |
| Preconditions | The user is logged in with permission to add assets. |
| Postconditions | The asset is saved with a unique asset code. The change is written to the audit log. |
| Main flow | 1. The user opens Asset Register and clicks Add Asset. 2. The user enters the description, category, quantity and unit, brand, model, serial number, color and dimension, acquisition date and unit cost, supplier and invoice number, department, room or location, accountable person and condition, and optionally attaches a photo for unfamiliar items. 3. The system suggests a property tag number in the OPC format year-office-item type-number, for example 2018-PCO-PRNT-01, which the user can accept or change. 4. The user clicks Save. 5. The system validates the required fields and checks for a likely duplicate. 6. The system saves the asset item and shows its record. |
| Alternate flows | 5a. A required field is missing or invalid: the system highlights it. 5b. The serial or property tag number already exists: the system shows the matching asset; the user cancels or confirms that it is a different item. |
| Related requirements | REQ-104, REQ-105 |

Activity Diagram

Figure 10. AD-104 Add Asset activity diagram
Wireframe

Figure 11. WF-104 Add Asset wireframe
2.2 Edit Asset
Related requirements: REQ-106
Use Case Diagram

Figure 12. UC-106 Edit Asset use case diagram
Use Case Description
| Use case ID | UC-106 |
| --- | --- |
| Use case name | Edit Asset |
| Actor(s) | Property Staff |
| Description | Staff correct an asset's details or update its condition. |
| Preconditions | The asset exists. The user has permission to edit assets. |
| Postconditions | The asset is updated. The old and new values are written to the audit log. |
| Main flow | 1. The user opens an asset and clicks Edit. 2. The system shows the asset form filled with the current values. 3. The user changes the details or condition and clicks Save. 4. The system validates the changes. 5. The system saves the asset and records the old and new values in the audit log. |
| Alternate flows | 4a. A field is invalid: the system highlights it. 3a. The user tries to change the department or accountable person: the system tells the user to record a transfer instead (transaction 4.1). |
| Related requirements | REQ-106 |

Activity Diagram

Figure 13. AD-106 Edit Asset activity diagram
Wireframe

Figure 14. WF-106 Edit Asset wireframe
2.3 Import Excel Masterlist
Related requirements: REQ-107
Use Case Diagram

Figure 15. UC-107 Import Excel Masterlist use case diagram
Use Case Description
| Use case ID | UC-107 |
| --- | --- |
| Use case name | Import Excel Masterlist |
| Actor(s) | Administrator |
| Description | The administrator imports the OPC's Excel 'Masterlist of Issue Orders' (15,277 item lines from 4,790 issue orders, 2008 to 2026) so existing records move into the system, cleaned of inconsistent spellings. |
| Preconditions | The user is an Administrator. The masterlist is saved as an .xlsx file. |
| Postconditions | Valid rows are saved as assets. Rejected rows are listed with their reasons. An import batch record is kept. |
| Main flow | 1. The administrator uploads the .xlsx file and selects the Masterlist sheet. 2. The system reads the 14 columns: Issue Order #, Issued By, Department, Accountable Person, Designation, Invoice Date, Purchased From, Invoice #, Qty, Unit, Items/Description, Acquisition Unit Cost, Acquisition Total Cost and Remarks. 3. The system lists every department label and unit spelling that does not match the reference lists, for example 74 unit spellings such as copy, COPY and copies. 4. The administrator maps each unmatched value once to the correct department code or unit. 5. The system validates every row; rows marked cancelled, deleted or superseded in the Issued By column are set aside for review. 6. The system saves the valid rows as issue orders and asset items, and lists the rejected and set-aside rows with reasons. |
| Alternate flows | 1a. The file is not .xlsx: the system rejects it. 4a. Every row fails validation: the system saves nothing and shows the errors. |
| Related requirements | REQ-107 |

Activity Diagram

Figure 16. AD-107 Import Excel Masterlist activity diagram
Wireframe

Figure 17. WF-107 Import Excel Masterlist wireframe
2.4 Search Asset and View History
Related requirements: REQ-205
Use Case Diagram

Figure 18. UC-205 Search Asset and View History use case diagram
Use Case Description
| Use case ID | UC-205 |
| --- | --- |
| Use case name | Search Asset and View History |
| Actor(s) | Property Staff, NAS Assistant, Department Head, Administrator |
| Description | A user finds an asset by keyword, property tag number, serial number or issue order number and sees its department, room, accountable person, condition and every movement. Department Heads see only their own department. |
| Preconditions | The user is logged in. |
| Postconditions | The asset's current details and full movement history are displayed. |
| Main flow | 1. The user types a keyword, tag number or serial number in the search bar. 2. The system searches the asset register. 3. The system lists matching assets with department, holder and condition. 4. The user opens an asset. 5. The system shows its details, photo and movement history, newest first. |
| Alternate flows | 3a. No asset matches: the system shows 'No assets found' and suggests checking the tag or serial number. |
| Related requirements | REQ-205 |

Activity Diagram

Figure 19. AD-205 Search Asset and View History activity diagram
Wireframe

Figure 20. WF-205 Search Asset and View History wireframe
### Module 3: Issue Orders
Records department requests and generates printable issue orders, with item photos, directly from the asset register.

Figure 21. Use case overview of Module 3: Issue Orders
3.1 Record Department Request
Related requirements: REQ-201
Use Case Diagram

Figure 22. UC-201 Record Department Request use case diagram
Use Case Description
| Use case ID | UC-201 |
| --- | --- |
| Use case name | Record Department Request |
| Actor(s) | Property Staff |
| Description | Property Staff record a department's request for items, usually backed by the department's purchase request (PR), so it can be fulfilled with an issue order once the items arrive. |
| Preconditions | The user has permission to manage issue orders. |
| Postconditions | The request is saved with status Pending. |
| Main flow | 1. The user opens Issue Orders and clicks New Request. 2. The user selects the department (for example MEDTECH) and enters the PR number, the purpose and the items needed. 3. The user clicks Save. 4. The system validates the request, links any uploaded PR with the same number and saves it as Pending. |
| Alternate flows | 4a. The department or items are missing: the system highlights the fields. |
| Related requirements | REQ-201 |

Activity Diagram

Figure 23. AD-201 Record Department Request activity diagram
Wireframe

Figure 24. WF-201 Record Department Request wireframe
3.2 Select Assets and Generate Issue Order
Related requirements: REQ-202, REQ-203, REQ-206
Use Case Diagram

Figure 25. UC-202 Select Assets and Generate Issue Order use case diagram
Use Case Description
| Use case ID | UC-202 |
| --- | --- |
| Use case name | Select Assets and Generate Issue Order |
| Actor(s) | Property Staff |
| Description | Property Staff select items in OPC storage for a pending request. On confirmation the system generates the issue order in the OPC's format, with the next 7-digit issue order number, and assigns the items to the department and accountable person. |
| Preconditions | A pending department request exists. The selected assets are available (not issued, not under turnover). |
| Postconditions | An issue order is saved and its PDF is ready to print within 10 seconds for up to 500 assets. Each asset is assigned to the requesting department and accountable person, and an ISSUE movement is recorded. The request is marked Fulfilled. |
| Main flow | 1. The user opens a pending request and clicks Prepare Issue Order. 2. The system lists items in OPC storage from the matching purchase. 3. The user selects the items. The accountable person defaults to the department head from the department list. 4. The user clicks Confirm. 5. The system checks that every selected item is still available. 6. In one transaction, the system creates the issue order with the next number, assigns the items and records an ISSUE movement for each. 7. The system generates the PDF in the OPC's layout: issue order number and date, department, issued by, purchased from, invoice date and number, quantity, unit, description, unit cost and total per line, grand total, and the Approved by (Property Custodian), Noted by (Dean) and Received by (department head) signature blocks. Item photos, where attached, are added on a second page. 8. The user prints the issue order for signing on paper. |
| Alternate flows | 5a. An asset was issued to someone else in the meantime: the system removes it from the selection and tells the user. 7a. PDF generation fails: the issue order stays saved and the user can regenerate the PDF from the issue order history. |
| Related requirements | REQ-202, REQ-203, REQ-206 |

Activity Diagram

Figure 26. AD-202 Select Assets and Generate Issue Order activity diagram
Wireframe

Figure 27. WF-202 Select Assets and Generate Issue Order wireframe
3.3 View and Reprint Issue Order
Related requirements: REQ-204
Use Case Diagram

Figure 28. UC-204 View and Reprint Issue Order use case diagram
Use Case Description
| Use case ID | UC-204 |
| --- | --- |
| Use case name | View and Reprint Issue Order |
| Actor(s) | Property Staff, Administrator |
| Description | Staff find a past issue order and print it again. |
| Preconditions | At least one issue order exists. |
| Postconditions | The selected issue order is shown ready to print. |
| Main flow | 1. The user opens Issue Order History. 2. The user filters by issue order number, department or date. 3. The system lists matching issue orders. 4. The user opens one and clicks Print. |
| Alternate flows | 3a. No issue order matches: the system shows an empty list. |
| Related requirements | REQ-204 |

Activity Diagram

Figure 29. AD-204 View and Reprint Issue Order activity diagram
Wireframe

Figure 30. WF-204 View and Reprint Issue Order wireframe
### Module 4: Movements and Turnovers
Records every transfer, defective or damaged turnover and resignation turnover as one transaction with a source and a destination, so the masterlist never disagrees with recorded movements.

Figure 31. Use case overview of Module 4: Movements and Turnovers
4.1 Transfer Asset
Related requirements: REQ-301, REQ-306
Use Case Diagram

Figure 32. UC-301 Transfer Asset use case diagram
Use Case Description
| Use case ID | UC-301 |
| --- | --- |
| Use case name | Transfer Asset |
| Actor(s) | Property Staff |
| Description | Property Staff move an asset item, or part of its quantity, from one department or accountable person to another. |
| Preconditions | The asset exists and is currently assigned. The destination department and accountable person exist. |
| Postconditions | The asset is removed from the source and added to the destination in one transaction. A TRANSFER movement and an audit entry are saved. |
| Main flow | 1. The user opens an asset item and clicks Transfer. 2. The system shows the current department, room and accountable person as the source. 3. The user selects the destination department, room and accountable person, the quantity to move and the reason. 4. The user clicks Confirm Transfer. 5. In one database transaction, the system moves the item, or splits off the transferred quantity as a new item, and saves a TRANSFER movement with the source and destination. 6. The system shows the updated item and its history. |
| Alternate flows | 5a. The database update fails: the whole transaction is rolled back, the asset stays with the source, and the user sees an error. |
| Related requirements | REQ-301, REQ-306 |

Activity Diagram

Figure 33. AD-301 Transfer Asset activity diagram
Wireframe

Figure 34. WF-301 Transfer Asset wireframe
4.2 Record Condition Change or Turnover
Related requirements: REQ-302
Use Case Diagram

Figure 35. UC-302 Record Condition Change or Turnover use case diagram
Use Case Description
| Use case ID | UC-302 |
| --- | --- |
| Use case name | Record Condition Change or Turnover |
| Actor(s) | Property Staff |
| Description | Property Staff record a change in an item's condition. A defective item can stay with the department, labelled Defective, if the department still uses it, or be turned over to OPC storage with a standard reason. A disposal date is recorded later for disposed items. |
| Preconditions | The asset exists and is assigned to a department. |
| Postconditions | The item's condition is updated; if turned over, it is held in OPC storage. A CONDITION or TURNOVER movement and an audit entry are saved. |
| Main flow | 1. The user opens an asset item and clicks Record Condition or Turnover. 2. The user selects the new condition: Good Condition, Functional, Defective or Damaged. 3. The user chooses Keep in department, or Turn over to OPC storage with a reason: Defective, Damaged, No longer used, For upgrade or Other with a note. 4. The user enters the date returned and the person who turned it over, and clicks Confirm. 5. In one transaction, the system updates the condition, moves the item to OPC storage if it was turned over, and saves the movement. 6. Later, when OPC disposes of a turned-over item, the user records the disposal date. |
| Alternate flows | 4a. The update fails: the transaction is rolled back and the asset is unchanged. |
| Related requirements | REQ-302 |

Activity Diagram

Figure 36. AD-302 Record Condition Change or Turnover activity diagram
Wireframe

Figure 37. WF-302 Record Condition Change or Turnover wireframe
4.3 Process Resignation Turnover
Related requirements: REQ-303
Use Case Diagram

Figure 38. UC-303 Process Resignation Turnover use case diagram
Use Case Description
| Use case ID | UC-303 |
| --- | --- |
| Use case name | Process Resignation Turnover |
| Actor(s) | Property Staff |
| Description | When HR's email notice lists an accountable person who has resigned, Property Staff list every asset item under that person's name and reassign or turn over each one. The HR notice itself is confidential and is not stored in the system. |
| Preconditions | OPC has received HR's email notice of resignations. The resigning person is recorded as an accountable person in the system. |
| Postconditions | Every asset held by the employee is turned over in its own transaction. The employee has no assets left assigned. |
| Main flow | 1. The user opens Resignation Turnover and searches for the resigning person. 2. The system lists every asset item assigned to that person, grouped by department and room. 3. For each item, the user selects the new accountable person, usually the department's new head, or OPC storage, and its condition. 4. The user clicks Complete Turnover. 5. The system records a RESIGNATION movement for each item, each in its own transaction. 6. The system shows that the person now holds no assets. |
| Alternate flows | 2a. The employee holds no assets: the system says so and nothing is recorded. 5a. One asset fails to update: that asset's transaction is rolled back and listed as pending; the others are saved. |
| Related requirements | REQ-303 |

Activity Diagram

Figure 39. AD-303 Process Resignation Turnover activity diagram
Wireframe

Figure 40. WF-303 Process Resignation Turnover wireframe
### Module 5: Summaries and Reports
Calculates department summaries from the transaction records and exports the masterlist, summaries and issue orders to Excel and PDF.

Figure 41. Use case overview of Module 5: Summaries and Reports
5.1 View Department Inventory Report
Related requirements: REQ-304
Use Case Diagram

Figure 42. UC-304 View Department Inventory Report use case diagram
Use Case Description
| Use case ID | UC-304 |
| --- | --- |
| Use case name | View Department Inventory Report |
| Actor(s) | Property Staff, Department Head, Administrator |
| Description | A user views a department's current asset items in the format of the OPC's department inventory report, grouped by room, with quantity, unit, description, year, unit cost, total cost and remarks. |
| Preconditions | The user is logged in. |
| Postconditions | The department's summary is displayed, consistent with the masterlist. |
| Main flow | 1. The user opens Summaries and selects a department. 2. The system groups the department's current items by room and totals the quantity and cost. 3. The system shows the report in the OPC's layout, with the Inventory conducted by, Checked by, Verified by, Received by and Noted by signature blocks for printing. |
| Alternate flows | 2a. The department has no assets: the system shows zero totals. |
| Related requirements | REQ-304 |

Activity Diagram

Figure 43. AD-304 View Department Inventory Report activity diagram
Wireframe

Figure 44. WF-304 View Department Inventory Report wireframe
5.2 Export Reports
Related requirements: REQ-305
Use Case Diagram

Figure 45. UC-305 Export Reports use case diagram
Use Case Description
| Use case ID | UC-305 |
| --- | --- |
| Use case name | Export Reports |
| Actor(s) | Property Staff, Administrator |
| Description | Property Staff export the three reports the OPC already uses: the Masterlist of Issue Orders, the Department Inventory Report and the Inventory of Turned-over Items, to Excel or PDF. |
| Preconditions | The user has permission to export. |
| Postconditions | The selected report is downloaded in the chosen format. |
| Main flow | 1. The user opens Reports and chooses the report, its filters and the format. 2. The user clicks Export. 3. The system generates the file with the same columns as the OPC's current file, and the browser downloads it. |
| Alternate flows | 3a. The report has no rows: the system generates the file with headers only and tells the user. |
| Related requirements | REQ-305 |

Activity Diagram

Figure 46. AD-305 Export Reports activity diagram
Wireframe

Figure 47. WF-305 Export Reports wireframe
### Module 6: Access Control and Audit
Protects the records and the personal data of accountable employees through login, role-based permissions, an audit log and daily backups.

Figure 48. Use case overview of Module 6: Access Control and Audit
6.1 Log In
Related requirements: REQ-401, REQ-403
Use Case Diagram

Figure 49. UC-401 Log In use case diagram
Use Case Description
| Use case ID | UC-401 |
| --- | --- |
| Use case name | Log In |
| Actor(s) | Administrator, Property Staff, NAS Assistant, Department Head |
| Description | A user signs in so the system knows who they are and which actions their role allows. |
| Preconditions | The user has an active account. |
| Postconditions | The user is signed in with a token that carries their role. The login is written to the audit log. |
| Main flow | 1. The user enters a username and password and clicks Log In. 2. The system checks the password against the stored BCrypt hash. 3. The system issues a JWT containing the user's role. 4. The system opens the dashboard with only the menus the role allows. |
| Alternate flows | 2a. The credentials are wrong: the system shows 'Invalid username or password' without saying which. |
| Related requirements | REQ-401, REQ-403 |

Activity Diagram

Figure 50. AD-401 Log In activity diagram
Wireframe

Figure 51. WF-401 Log In wireframe
6.2 Manage User Accounts and Roles
Related requirements: REQ-402, REQ-403, REQ-406
Use Case Diagram

Figure 52. UC-402 Manage User Accounts and Roles use case diagram
Use Case Description
| Use case ID | UC-402 |
| --- | --- |
| Use case name | Manage User Accounts and Roles |
| Actor(s) | Administrator |
| Description | The administrator creates and deactivates accounts and assigns each user one role: Administrator, Property Staff, NAS Assistant or Department Head. A Department Head account is linked to one department. |
| Preconditions | The user is an Administrator. |
| Postconditions | The account is created or updated. The change is written to the audit log. |
| Main flow | 1. The administrator opens User Management. 2. The administrator clicks Add User and enters the name, username, temporary password and role. 3. The administrator clicks Save. 4. The system hashes the password, saves the account and logs the change. 5. To deactivate a user, the administrator clicks Deactivate; the user can no longer sign in. |
| Alternate flows | 4a. The username already exists: the system asks for another. 5a. The administrator tries to deactivate their own account: the system refuses. |
| Related requirements | REQ-402, REQ-403, REQ-406 |

Activity Diagram

Figure 53. AD-402 Manage User Accounts and Roles activity diagram
Wireframe

Figure 54. WF-402 Manage User Accounts and Roles wireframe
6.3 View Audit Log
Related requirements: REQ-404
Use Case Diagram

Figure 55. UC-404 View Audit Log use case diagram
Use Case Description
| Use case ID | UC-404 |
| --- | --- |
| Use case name | View Audit Log |
| Actor(s) | Administrator |
| Description | The administrator reviews who changed what and when. |
| Preconditions | The user is an Administrator. |
| Postconditions | Matching audit entries are displayed with old and new values. |
| Main flow | 1. The administrator opens Audit Log. 2. The administrator filters by user, action, record type or date range. 3. The system lists matching entries, newest first. 4. The administrator opens an entry to see its old and new values. |
| Alternate flows | 3a. No entry matches: the system shows an empty list. |
| Related requirements | REQ-404 |

Activity Diagram

Figure 56. AD-404 View Audit Log activity diagram
Wireframe

Figure 57. WF-404 View Audit Log wireframe
## Logical database requirements
The system stores the following entities. Their full design is in the Software Design Description.
| Entity | Purpose |
| --- | --- |
| asset | One asset item: either a single tracked unit with a property tag and serial number, or a quantity of identical items (for example 49 laboratory stools), with department, room, accountable person and condition. Partial transfers split an item. |
| source_document / extracted_line | Uploaded sales invoices, POs and PRs, linked by PO and PR numbers, and the item lines read from them by OCR. |
| dept_request / issue_order / issue_order_line | Department requests and the issue orders that fulfil them, with the same fields as the OPC's printed issue order. |
| movement | Every issue, transfer, condition change, turnover and resignation turnover, with source, destination, standard reason, date returned and disposal date. |
| department / employee / category | Reference data: department codes and heads, accountable persons and item categories. |
| user / audit_log / import_batch / value_mapping | Accounts and roles, the audit trail, masterlist imports and the mappings that clean department labels and units. |

## Non-functional requirements
### Performance
| Requirement ID | Requirement |
| --- | --- |
| REQ-108 | Encoding one sales invoice, purchase order or purchase request, from upload to saved records, shall take 3 minutes or less. |
| REQ-206 | An issue order of up to 500 assets shall be ready to print within 10 seconds of confirmation. |
| REQ-109 | OCR shall extract fields from printed documents with at least 85% accuracy, counted field by field against the original document. Typewritten and handwritten documents are reported separately. |
| — | Performance targets are measured with the back end already running. On the Render free tier the back end sleeps after 15 minutes without traffic and takes about a minute to wake, so it is opened before every test session. |

### Security
| Requirement ID | Requirement |
| --- | --- |
| REQ-401 | Passwords shall be stored as BCrypt hashes; plain-text passwords are never stored or logged. |
| REQ-403 | Every API endpoint shall check the caller's role on the server; 100% of unauthorized actions in testing shall be refused. |
| REQ-404 | Every create, edit and delete shall be written to the audit log with the user, time and changed values; audit entries cannot be edited or deleted through the system. |
| REQ-406 | Personal data of accountable persons shall be visible only to permitted roles, in line with the Data Privacy Act of 2012; a Department Head account sees only its own department's assets, reports and accountable persons. |

### Reliability
| Requirement ID | Requirement |
| --- | --- |
| REQ-306 | Each movement shall be saved in one database transaction: the deduction from the source and the addition to the destination both succeed or both are rolled back, so the masterlist has zero discrepancies. |
| REQ-405 | The database shall be backed up daily by a scheduled GitHub Actions workflow that runs pg_dump against Supabase and keeps the encrypted dump as a private artifact; a full backup shall be restorable into a test database with no data lost. |
