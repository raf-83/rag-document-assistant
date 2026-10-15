package com.aidocumentvault.backend.ai.search.persistence;

import com.aidocumentvault.backend.document.entity.DocumentChunk;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface KeywordSearchRepository extends Repository<DocumentChunk, UUID> {

    @Query(
        value = """
            WITH q AS (
                SELECT websearch_to_tsquery('simple', :query) AS query
            )
            SELECT
                d.id AS "documentId",
                d.title AS "documentTitle",
                dt.id AS "documentTypeId",
                dt.name AS "documentTypeName",
                dc.id AS "chunkId",
                dc.chunk_index AS "chunkIndex",
                ts_rank_cd(
                    to_tsvector('simple', coalesce(dc.text, '')),
                    q.query
                ) AS "score",
                dc.text AS "text",
                dc.start_char_index AS "startCharIndex",
                dc.end_char_index AS "endCharIndex",
                d.created_at AS "documentCreatedAt"
            FROM document_chunks dc
            JOIN document_contents dco
                ON dco.id = dc.document_content_id
            JOIN documents d
                ON d.id = dco.document_id
            JOIN document_types dt
                ON dt.id = d.document_type_id
            CROSS JOIN q
            WHERE d.owner_id = :ownerId
              AND d.status = 'READY'
              AND to_tsvector('simple', coalesce(dc.text, '')) @@ q.query
              AND (
                  CAST(:documentTypeId AS uuid) IS NULL
                  OR d.document_type_id = CAST(:documentTypeId AS uuid)
              )
              AND (
                  CAST(:createdFrom AS timestamptz) IS NULL
                  OR d.created_at >= CAST(:createdFrom AS timestamptz)
              )
              AND (
                  CAST(:createdTo AS timestamptz) IS NULL
                  OR d.created_at <= CAST(:createdTo AS timestamptz)
              )
              AND (:scopeDocumentIdsEmpty = true
                  OR d.id IN (:scopeDocumentIds)
              )
            ORDER BY "score" DESC, dc.chunk_index ASC
            LIMIT :limit
            """,
        nativeQuery = true
    )
    List<KeywordSearchResultRow> findKeywordChunks(
        @Param("query") String query,
        @Param("ownerId") UUID ownerId,
        @Param("documentTypeId") UUID documentTypeId,
        @Param("createdFrom") OffsetDateTime createdFrom,
        @Param("createdTo") OffsetDateTime createdTo,
        @Param("scopeDocumentIdsEmpty") boolean scopeDocumentIdsEmpty,
        @Param("scopeDocumentIds") Collection<UUID> scopeDocumentIds,
        @Param("limit") int limit
    );
}
