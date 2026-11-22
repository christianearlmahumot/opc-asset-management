-- UC-101 Upload Purchasing Document and UC-102 Review and Confirm Extracted Fields.

-- What the OCR read, how sure it was, and when the document was encoded (REQ-108: upload to saved
-- records in 3 minutes or less).
ALTER TABLE source_document
    ADD COLUMN original_name     VARCHAR(255),
    ADD COLUMN content_type      VARCHAR(100),
    ADD COLUMN page_count        INT,
    ADD COLUMN ocr_text          TEXT,
    ADD COLUMN ocr_error         VARCHAR(500),
    ADD COLUMN header_confidence JSONB,
    ADD COLUMN ocr_seconds       INT,
    ADD COLUMN encoded_at        TIMESTAMPTZ,
    ADD COLUMN encoded_by        BIGINT REFERENCES app_user (id);
CREATE INDEX ix_source_document_status ON source_document (ocr_status, created_at DESC);

-- Each line keeps its total, how sure the OCR was of every field, and the asset it became.
ALTER TABLE extracted_line ALTER COLUMN description TYPE VARCHAR(2500);
ALTER TABLE extracted_line
    ADD COLUMN total_cost  NUMERIC(14, 2),
    ADD COLUMN confidences JSONB,
    ADD COLUMN asset_id    BIGINT REFERENCES asset (id);

CREATE INDEX ix_asset_source_document ON asset (source_document_id);
