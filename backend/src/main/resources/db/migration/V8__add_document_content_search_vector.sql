ALTER TABLE document_contents
  ADD COLUMN search_vector tsvector;


CREATE OR REPLACE FUNCTION update_document_content_search_vector()
RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(
            to_tsvector(
                'simple',
                coalesce(
                    (
                        SELECT d.title
                        FROM documents d
                        WHERE d.id = NEW.document_id
                    ),
                    ''
                )
            ),
            'A'
        )
        ||
        setweight(
            to_tsvector(
                'simple',
                coalesce(NEW.sanitized_text, '')
            ),
            'B'
        );

RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER trg_document_contents_search_vector
  BEFORE INSERT OR UPDATE OF sanitized_text, document_id
                   ON document_contents
                     FOR EACH ROW
                     EXECUTE FUNCTION update_document_content_search_vector();


CREATE OR REPLACE FUNCTION refresh_document_content_search_vector_on_document_title_change()
RETURNS trigger AS $$
BEGIN
UPDATE document_contents dc
SET search_vector =
      setweight(
        to_tsvector('simple', coalesce(NEW.title, '')),
        'A'
      )
        ||
      setweight(
        to_tsvector('simple', coalesce(dc.sanitized_text, '')),
        'B'
      )
WHERE dc.document_id = NEW.id;

RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER trg_documents_title_search_vector
  AFTER UPDATE OF title
  ON documents
  FOR EACH ROW
  EXECUTE FUNCTION refresh_document_content_search_vector_on_document_title_change();


UPDATE document_contents dc
SET search_vector =
      setweight(
        to_tsvector('simple', coalesce(d.title, '')),
        'A'
      )
        ||
      setweight(
        to_tsvector('simple', coalesce(dc.sanitized_text, '')),
        'B'
      )
  FROM documents d
WHERE d.id = dc.document_id;


CREATE INDEX idx_document_contents_search_vector
  ON document_contents
  USING GIN (search_vector);
