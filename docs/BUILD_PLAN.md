# OPC Fixed-Asset Management System: Build Notes and Plan

These notes come from the SRS and the SDD (both v1.0, 16 Nov 2026), the project proposal, and the
wireframes and ERD embedded in the `.docx` files. Where documents disagree, the **SRS and SDD-OPC
win**. The older *Software Design Document: Centralized FAMS* (MySQL, Docker on a CIT-U server) is
superseded.

## 1. What the system is

A web system for the CIT-U **Office of the Property Custodian (OPC), Inventory Section**. It replaces
the Excel *Masterlist of Issue Orders* (15,277 lines and 4,790 IOs from 2008 to 2026, with 203 spellings
of departments and 74 spellings of units) with one database. It covers fixed assets only, not consumables.

The OPC's six-step workflow, which the system mirrors:
1. Purchasing forwards the sales invoice (SI), purchase order (PO) and purchase request (PR).
2. Staff encode the items, which is **Module 1** (OCR plus staff verification).
3. Staff create an issue order with item photos, which is **Module 3**.
4. Staff keep a summary per department, which is **Module 5**.
5. Staff record turned-over items (defective, damaged or transferred), which is **Module 4**.
6. Staff print the issue order, which is **Module 3** (PDF).

## 2. Roles (RBAC is enforced on the server for every endpoint, per REQ-403)

| Action | ADMIN | STAFF | NAS | DEPT_HEAD |
|---|---|---|---|---|
| Upload doc / review OCR / encode assets (NAS entries are saved as **drafts**) | ✔ | ✔ | ✔ | |
| Confirm drafts, add or edit assets | ✔ | ✔ | | |
| Import Excel masterlist, manage departments and categories | ✔ | | | |
| Search assets and view history | ✔ | ✔ | ✔ | own dept only |
| Requests, generate and print issue orders | ✔ | ✔ | | |
| Transfer, condition, turnover, resignation | ✔ | ✔ | | |
| Department inventory report | ✔ | ✔ | | own dept only |
| Export reports | ✔ | ✔ | | |
| Users and audit log | ✔ | | | |

## 3. Hard rules (the parts that must never break)

- **R-Movement (REQ-306):** every issue, transfer, condition change or turnover is **one DB
  transaction**: the asset update and the `movement` row both commit or both roll back. A partial
  transfer **splits** the asset (a new row with `parent_asset_id`, for example 4 of 49 stools).
- **R-NoDirectCustodyEdit (UC-106 3a):** *Edit Asset* cannot change the department or accountable person.
  Those change only through a movement.
- **R-Resignation (UC-303):** one transaction **per asset**. A failure leaves that one asset pending,
  and the others still save.
- **R-Audit (REQ-404):** every create, edit and delete is written to `audit_log` with the user, the time
  and the old and new values as JSONB. The log is append-only, with no update or delete in the API and
  a DB trigger blocking them.
- **R-Privacy (REQ-406):** a Department Head sees only their own department's assets, reports and people.
- **R-Passwords (REQ-401):** passwords are stored as BCrypt hashes and never logged. A failed login says
  only "Invalid username or password."
- **Issue order number:** 7 digits, sequential (for example 1804352).
- **Property tag:** `year-office-itemtype-nn`, for example `2018-PCO-PRNT-01`. The system suggests one
  and the user can edit it.
- **Duplicate check:** same serial or tag number, or same category + description + brand + model.
- **Unit normalization:** pcs, pc and pieces become `pc`, and copy, COPY and copies become `copy`.
- **Conditions:** Good Condition, Functional, Defective, Damaged.
- **Turnover reasons:** Defective, Damaged, No longer used, For upgrade, Other (with a note).
- **Default categories:** CAPEX-Equipment, CAPEX-Furniture and Fixtures, Inventoriable Items.

## 4. Stack (per SRS 3.1.2)

| Layer | Choice |
|---|---|
| Front end | React 18 + Vite + React Router + Axios |
| Back end | Java 17, Spring Boot **3.5.x**, Spring Web, Data JPA, Security, Validation |
| DB | PostgreSQL (local: Docker; deployed: Supabase, Singapore region). Schema is managed by **Flyway**. |
| Auth | JWT (jjwt) + BCrypt |
| Files / OCR / docs | Supabase Storage, Tesseract 5 (Tess4J), Apache POI, OpenPDF (later steps) |

Notes on the stack:
- Spring Initializr no longer offers Boot 3, so the version is pinned in `pom.xml`.
- The SQL table `user` is a reserved word in PostgreSQL, so it is named **`app_user`**. Everything
  else follows the ERD.
- `movement` also stores `from_room` and `to_room`, because the Transfer wireframe moves assets between
  rooms and the history should show it.
- **UI:** the wireframes use a flat, office-form style: maroon top bar, dark sidebar, warm off-white
  page, bordered white cards, labels above inputs and amber notices. Stock MUI looks noticeably
  different, so the UI is built from a small in-house component set (`frontend/src/shared/ui`) that
  copies the wireframes. MUI can still be added later for complex widgets (autocomplete, date picker)
  and themed to match.

## 5. Code layout (modular by business module)

```
backend/src/main/java/edu/citu/opc/fams/
  common/        errors, base types, shared utilities
  access/        Module 6: auth, JWT, security config, users
  audit/         Module 6: append-only audit log
  reference/     departments, employees, categories (reference data)
  asset/         Module 2: asset register, duplicate check, tag suggestion
  movement/      Module 4: transfer, condition and turnover, resignation (the ledger)
  issueorder/    Module 3: requests, issue orders, IO PDF
  report/        Module 5: department summary, exports
  document/      Module 1: upload, OCR, review and confirm
  importer/      Module 2.3: Excel masterlist import

frontend/src/
  app/           router, route guards, navigation config (role-aware)
  shared/        api client, layout (top bar + sidebar), ui components, formatters
  modules/<x>/   one folder per business module: pages, components, api.js
```

Each module owns its entities, repositories, services, controllers and DTOs. Modules talk to each
other only through **services**, never another module's repository. For example, `movement` calls
`AssetService`, and everything calls `AuditService`.

## 6. Build order

Core functions come first. OCR and Excel import depend on a working asset register, so they come later.

| Step | Scope | Status |
|---|---|---|
| **1. Foundation + Accounts** | Repo layout, Supabase DB, full Flyway schema (ERD), login + JWT + RBAC, audit service, app shell; user accounts (UC-402): admin creates users with temporary passwords, forced password change, edit role/department, reset password, deactivate/reactivate | **done** |
| **2. Reference data + Asset Register** | Departments, employees, categories; Add, Edit, Search and History (UC-104, 106, 205); duplicate check; tag suggestion; Dept Head scoping | **done**. Search by issue order no. is added in Step 4. Photos use local disk storage until Step 9. |
| **3. Movements** | Transfer with split, condition and turnover, disposal date, resignation turnover (UC-301, 302, 303) | |
| **4. Issue Orders** | Department request, prepare IO (next 7-digit no., ISSUE movements), IO PDF in the OPC layout, history and reprint (UC-201, 202, 204) | |
| **5. Summaries and Reports** | Department inventory report by room, Excel and PDF exports of the 3 OPC reports (UC-304, 305) | |
| **6. Audit UI** | Audit log viewer (UC-404). User management was finished in Step 1. | |
| **7. Document Capture** | Upload, Tesseract OCR, review screen, NAS drafts, confirm to assets (UC-101, 102) | |
| **8. Masterlist Import** | .xlsx preview, value mapping, set-aside rows, import batch (UC-107) | |
| **9. Deploy** | Supabase DB and Storage, Render, GitHub Actions pg_dump backup (REQ-405) | |
