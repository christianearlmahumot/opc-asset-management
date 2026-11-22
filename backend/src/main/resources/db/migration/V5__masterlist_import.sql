-- UC-107 Import Excel Masterlist.

-- The import runs in the background, so the batch records its progress and outcome.
ALTER TABLE import_batch
    ADD COLUMN sheet_name          VARCHAR(100),
    ADD COLUMN status              VARCHAR(20) NOT NULL DEFAULT 'DONE'
        CHECK (status IN ('RUNNING', 'DONE', 'FAILED')),
    ADD COLUMN rejected_rows       INT         NOT NULL DEFAULT 0,
    ADD COLUMN issue_orders        INT         NOT NULL DEFAULT 0,
    ADD COLUMN created_persons     INT         NOT NULL DEFAULT 0,
    ADD COLUMN error               VARCHAR(500),
    ADD COLUMN finished_at         TIMESTAMPTZ;

-- Rows that were not imported, with the reason, so the OPC can review and encode them by hand.
CREATE TABLE import_row (
    id        BIGSERIAL PRIMARY KEY,
    batch_id  BIGINT       NOT NULL REFERENCES import_batch (id) ON DELETE CASCADE,
    row_no    INT          NOT NULL,
    outcome   VARCHAR(20)  NOT NULL CHECK (outcome IN ('SET_ASIDE', 'REJECTED')),
    reason    VARCHAR(255) NOT NULL,
    cells     JSONB        NOT NULL
);
CREATE INDEX ix_import_row_batch ON import_row (batch_id, row_no);

-- Which import created an issue order or an asset.
ALTER TABLE issue_order ADD COLUMN import_batch_id BIGINT REFERENCES import_batch (id);
ALTER TABLE asset ADD COLUMN import_batch_id BIGINT REFERENCES import_batch (id);
CREATE INDEX ix_asset_import_batch ON asset (import_batch_id);

-- The masterlist has descriptions of up to 2,311 characters (book lists), invoice numbers of up to
-- 82 characters ("18347 / 18350 / …") and names of up to 121 characters.
ALTER TABLE asset ALTER COLUMN description TYPE VARCHAR(2500);
ALTER TABLE issue_order_line ALTER COLUMN description TYPE VARCHAR(2500);
ALTER TABLE asset ALTER COLUMN invoice_no TYPE VARCHAR(100);
ALTER TABLE issue_order ALTER COLUMN invoice_no TYPE VARCHAR(100);
ALTER TABLE issue_order ALTER COLUMN issued_by_name TYPE VARCHAR(150);
ALTER TABLE issue_order ALTER COLUMN received_by_name TYPE VARCHAR(150);
ALTER TABLE employee ALTER COLUMN full_name TYPE VARCHAR(150);
