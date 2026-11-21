-- A printed issue order is a signed paper record, so the names on it are kept exactly as printed,
-- even if a department head or the Property Custodian changes later (OPC issue order layout).
ALTER TABLE issue_order
    ADD COLUMN room               VARCHAR(80),
    ADD COLUMN issued_by_name     VARCHAR(100),
    ADD COLUMN approved_by_name   VARCHAR(120),
    ADD COLUMN noted_by_name      VARCHAR(120),
    ADD COLUMN noted_by_title     VARCHAR(120),
    ADD COLUMN received_by_name   VARCHAR(120),
    ADD COLUMN received_by_title  VARCHAR(120),
    ADD COLUMN created_at         TIMESTAMPTZ NOT NULL DEFAULT now();

-- The department request records who asked and when it was fulfilled.
ALTER TABLE dept_request
    ADD COLUMN requested_by       VARCHAR(120),
    ADD COLUMN created_by         BIGINT REFERENCES app_user (id),
    ADD COLUMN issue_order_id     BIGINT REFERENCES issue_order (id);

CREATE INDEX ix_dept_request_status ON dept_request (status, created_at DESC);
CREATE INDEX ix_asset_issue_order_line ON asset (issue_order_line_id);
