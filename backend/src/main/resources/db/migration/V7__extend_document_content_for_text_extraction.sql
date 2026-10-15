ALTER TABLE document_contents
  ADD COLUMN extraction_method VARCHAR(50),
    ADD COLUMN character_count INTEGER,
    ADD COLUMN page_count INTEGER,
    ADD COLUMN extracted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN sanitized_at TIMESTAMP WITH TIME ZONE;

UPDATE document_contents
SET
  extraction_method = 'PDF_TEXT',
  character_count = LENGTH(sanitized_text),
  extracted_at = CURRENT_TIMESTAMP,
  sanitized_at = CURRENT_TIMESTAMP
WHERE sanitized_text IS NOT NULL;

ALTER TABLE document_contents
  ALTER COLUMN extraction_method SET NOT NULL,
ALTER COLUMN character_count SET NOT NULL,
    ALTER COLUMN extracted_at SET NOT NULL,
    ALTER COLUMN sanitized_at SET NOT NULL;
