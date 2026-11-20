-- OPC Fixed-Asset Management System: full schema (SDD Figure 2, ERD).
-- Enumerations are VARCHAR + CHECK so JPA maps them with EnumType.STRING.
-- The table "user" is a reserved word in PostgreSQL, so it is named app_user.

-- ---------------------------------------------------------------- reference data
CREATE TABLE department (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(20)  NOT NULL UNIQUE,
    name             VARCHAR(120) NOT NULL,
    head_name        VARCHAR(100),
    head_designation VARCHAR(60)
);

CREATE TABLE category (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE employee (
    id            BIGSERIAL PRIMARY KEY,
    employee_no   VARCHAR(20) UNIQUE,
    full_name     VARCHAR(100) NOT NULL,
    department_id BIGINT REFERENCES department (id),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'RESIGNED', 'INACTIVE'))
);
CREATE INDEX ix_employee_department ON employee (department_id);
CREATE INDEX ix_employee_name ON employee (lower(full_name));

-- ---------------------------------------------------------------- access control
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL
        CHECK (role IN ('ADMIN', 'STAFF', 'NAS', 'DEPT_HEAD')),
    department_id BIGINT REFERENCES department (id),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- a Department Head account is always linked to one department (UC-402)
    CONSTRAINT ck_dept_head_has_department CHECK (role <> 'DEPT_HEAD' OR department_id IS NOT NULL)
);

CREATE TABLE audit_log (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT REFERENCES app_user (id),
    action     VARCHAR(20) NOT NULL
        CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGIN_FAILED', 'MOVEMENT', 'IMPORT', 'EXPORT')),
    entity     VARCHAR(40) NOT NULL,
    entity_id  BIGINT,
    old_values JSONB,
    new_values JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_created ON audit_log (created_at DESC);
CREATE INDEX ix_audit_entity ON audit_log (entity, entity_id);
CREATE INDEX ix_audit_user ON audit_log (user_id);

-- REQ-404: audit entries can never be edited or deleted, not even by a bug in the API.
CREATE FUNCTION audit_log_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

-- ---------------------------------------------------------------- module 1: document capture
CREATE TABLE source_document (
    id               BIGSERIAL PRIMARY KEY,
    doc_type         VARCHAR(20)  NOT NULL CHECK (doc_type IN ('SALES_INVOICE', 'PURCHASE_ORDER', 'PURCHASE_REQUEST')),
    doc_no           VARCHAR(40)  NOT NULL,
    po_no            VARCHAR(20),
    pr_no            VARCHAR(40),
    department_code  VARCHAR(20),
    supplier         VARCHAR(120),
    doc_date         DATE,
    file_path        VARCHAR(255) NOT NULL,
    ocr_status       VARCHAR(20)  NOT NULL DEFAULT 'QUEUED'
        CHECK (ocr_status IN ('QUEUED', 'PROCESSING', 'READ', 'FAILED', 'ENCODED')),
    uploaded_by      BIGINT REFERENCES app_user (id),
    encoding_seconds INT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_source_document_po ON source_document (po_no);
CREATE INDEX ix_source_document_pr ON source_document (pr_no);
CREATE INDEX ix_source_document_type_no ON source_document (doc_type, doc_no);

CREATE TABLE extracted_line (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES source_document (id) ON DELETE CASCADE,
    line_no     INT    NOT NULL,
    quantity    INT,
    unit        VARCHAR(20),
    description VARCHAR(500),
    unit_cost   NUMERIC(12, 2),
    category    VARCHAR(60),
    confidence  NUMERIC(5, 2),
    confirmed   BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_extracted_line_document ON extracted_line (document_id);

-- ---------------------------------------------------------------- module 3: issue orders
CREATE TABLE dept_request (
    id            BIGSERIAL PRIMARY KEY,
    department_id BIGINT       NOT NULL REFERENCES department (id),
    pr_no         VARCHAR(40),
    purpose       VARCHAR(255),
    items_needed  TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'FULFILLED', 'CANCELLED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_dept_request_department ON dept_request (department_id);

CREATE TABLE issue_order (
    id             BIGSERIAL PRIMARY KEY,
    io_no          CHAR(7)      NOT NULL UNIQUE CHECK (io_no ~ '^[0-9]{7}$'),
    request_id     BIGINT REFERENCES dept_request (id),
    department_id  BIGINT       NOT NULL REFERENCES department (id),
    employee_id    BIGINT REFERENCES employee (id),
    issued_by      BIGINT REFERENCES app_user (id),
    purchased_from VARCHAR(120),
    invoice_no     VARCHAR(30),
    invoice_date   DATE,
    issued_on      DATE         NOT NULL DEFAULT CURRENT_DATE,
    remarks        VARCHAR(255),
    pdf_path       VARCHAR(255)
);
CREATE INDEX ix_issue_order_department ON issue_order (department_id);
CREATE INDEX ix_issue_order_issued_on ON issue_order (issued_on DESC);

CREATE TABLE issue_order_line (
    id             BIGSERIAL PRIMARY KEY,
    issue_order_id BIGINT       NOT NULL REFERENCES issue_order (id) ON DELETE CASCADE,
    quantity       INT          NOT NULL CHECK (quantity > 0),
    unit           VARCHAR(20)  NOT NULL,
    description    VARCHAR(500) NOT NULL,
    unit_cost      NUMERIC(12, 2),
    total_cost     NUMERIC(14, 2)
);
CREATE INDEX ix_issue_order_line_order ON issue_order_line (issue_order_id);

-- ---------------------------------------------------------------- module 2: asset register
CREATE TABLE asset (
    id                  BIGSERIAL PRIMARY KEY,
    property_tag_no     VARCHAR(40) UNIQUE,
    description         VARCHAR(500) NOT NULL,
    category_id         BIGINT REFERENCES category (id),
    brand               VARCHAR(60),
    model_no            VARCHAR(60),
    serial_no           VARCHAR(60),
    color               VARCHAR(40),
    dimension           VARCHAR(60),
    quantity            INT          NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit                VARCHAR(20)  NOT NULL,
    acquisition_date    DATE,
    unit_cost           NUMERIC(12, 2),
    supplier            VARCHAR(120),
    invoice_no          VARCHAR(30),
    department_id       BIGINT REFERENCES department (id),   -- NULL = in OPC storage
    room                VARCHAR(80),
    employee_id         BIGINT REFERENCES employee (id),     -- accountable person
    asset_condition     VARCHAR(20)  NOT NULL DEFAULT 'GOOD'
        CHECK (asset_condition IN ('GOOD', 'FUNCTIONAL', 'DEFECTIVE', 'DAMAGED')),
    status              VARCHAR(20)  NOT NULL DEFAULT 'IN_STORAGE'
        CHECK (status IN ('DRAFT', 'IN_STORAGE', 'ISSUED', 'TURNED_OVER', 'DISPOSED')),
    issue_order_line_id BIGINT REFERENCES issue_order_line (id),
    parent_asset_id     BIGINT REFERENCES asset (id),        -- set when a partial transfer splits an item
    source_document_id  BIGINT REFERENCES source_document (id),
    photo_path          VARCHAR(255),
    created_by          BIGINT REFERENCES app_user (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0       -- optimistic locking for concurrent movements
);
CREATE INDEX ix_asset_serial ON asset (lower(serial_no));
CREATE INDEX ix_asset_department ON asset (department_id);
CREATE INDEX ix_asset_employee ON asset (employee_id);
CREATE INDEX ix_asset_status ON asset (status);
CREATE INDEX ix_asset_description ON asset (lower(description));

-- ---------------------------------------------------------------- module 4: movements (the ledger)
CREATE TABLE movement (
    id                 BIGSERIAL PRIMARY KEY,
    asset_id           BIGINT      NOT NULL REFERENCES asset (id),
    type               VARCHAR(20) NOT NULL
        CHECK (type IN ('ENCODE', 'ISSUE', 'TRANSFER', 'CONDITION', 'TURNOVER', 'RESIGNATION', 'DISPOSAL')),
    quantity           INT         NOT NULL CHECK (quantity > 0),
    from_department_id BIGINT REFERENCES department (id),
    to_department_id   BIGINT REFERENCES department (id),
    from_employee_id   BIGINT REFERENCES employee (id),
    to_employee_id     BIGINT REFERENCES employee (id),
    from_room          VARCHAR(80),
    to_room            VARCHAR(80),
    condition_after    VARCHAR(20)
        CHECK (condition_after IN ('GOOD', 'FUNCTIONAL', 'DEFECTIVE', 'DAMAGED')),
    reason             VARCHAR(20)
        CHECK (reason IN ('DEFECTIVE', 'DAMAGED', 'NO_LONGER_USED', 'FOR_UPGRADE', 'OTHER')),
    note               VARCHAR(255),
    turned_over_by     VARCHAR(100),
    date_returned      DATE,
    disposal_date      DATE,
    issue_order_id     BIGINT REFERENCES issue_order (id),
    recorded_by        BIGINT REFERENCES app_user (id),
    recorded_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_movement_asset ON movement (asset_id, recorded_at DESC);

-- ---------------------------------------------------------------- module 2.3: masterlist import
CREATE TABLE import_batch (
    id              BIGSERIAL PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    total_rows      INT          NOT NULL DEFAULT 0,
    imported_rows   INT          NOT NULL DEFAULT 0,
    set_aside_rows  INT          NOT NULL DEFAULT 0,
    imported_by     BIGINT REFERENCES app_user (id),
    imported_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE value_mapping (
    id            BIGSERIAL PRIMARY KEY,
    kind          VARCHAR(20)  NOT NULL CHECK (kind IN ('DEPARTMENT', 'UNIT')),
    source_value  VARCHAR(255) NOT NULL,
    mapped_value  VARCHAR(60)  NOT NULL,
    CONSTRAINT uq_value_mapping UNIQUE (kind, source_value)
);
