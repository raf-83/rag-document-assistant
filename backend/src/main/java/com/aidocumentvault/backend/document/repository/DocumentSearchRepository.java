package com.aidocumentvault.backend.document.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.aidocumentvault.backend.document.entity.Document;

import java.util.UUID;

public interface DocumentSearchRepository extends JpaRepository<Document, UUID> {

    @Query(
            value = """
                    WITH q AS (
                        SELECT websearch_to_tsquery('simple', :query) AS query
                    )
                    SELECT
                        d.id AS documentId,
                        d.title AS title,
                        dt.name AS documentType,
                        d.status AS status,

                        CASE
                            WHEN to_tsvector('simple', coalesce(d.title, '')) @@ q.query
                            THEN ts_headline('simple', d.title, q.query)
                            ELSE NULL
                        END AS titleSnippet,
                        CASE
                            WHEN to_tsvector('simple', coalesce(dc.sanitized_text, '')) @@ q.query
                            THEN ts_headline('simple', dc.sanitized_text, q.query)
                            ELSE NULL
                        END AS contentSnippet,

                        to_tsvector('simple', coalesce(d.title, '')) @@ q.query AS matchedInTitle,
                        to_tsvector('simple', coalesce(dc.sanitized_text, '')) @@ q.query AS matchedInContent,

                        ts_rank(dc.search_vector, q.query) AS rank,

                        d.created_at AS createdAt,
                        d.updated_at AS updatedAt
                    FROM documents d
                    JOIN document_contents dc ON dc.document_id = d.id
                    JOIN document_types dt ON dt.id = d.document_type_id
                    CROSS JOIN q
                    WHERE d.owner_id = :ownerId
                      AND d.status = 'READY'
                      AND dc.search_vector @@ q.query
                    ORDER BY rank DESC, d.updated_at DESC
                    """,
            countQuery = """
                    WITH q AS (
                        SELECT websearch_to_tsquery('simple', :query) AS query
                    )
                    SELECT COUNT(*)
                    FROM documents d
                    JOIN document_contents dc ON dc.document_id = d.id
                    CROSS JOIN q
                    WHERE d.owner_id = :ownerId
                      AND d.status = 'READY'
                      AND dc.search_vector @@ q.query
                    """,
            nativeQuery = true
    )
    Page<DocumentSearchProjection> searchDocuments(
            UUID ownerId,
            String query,
            Pageable pageable
    );
}
