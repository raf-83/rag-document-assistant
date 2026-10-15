package com.aidocumentvault.backend.ai.search.persistence;

import com.aidocumentvault.backend.document.entity.DocumentChunkEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SemanticSearchRepository extends JpaRepository<DocumentChunkEmbedding, UUID> {


    @Query(
        value = """
        SELECT
            d.id AS "documentId",
            d.title AS "documentTitle",
            dt.id AS "documentTypeId",
            dt.name AS "documentTypeName",
            dc.id AS "chunkId",
            dc.chunk_index AS "chunkIndex",
            (1 - (dce.embedding <=> q.query_vector)) AS "score",
            dc.text AS "text",
            dc.start_char_index AS "startCharIndex",
            dc.end_char_index AS "endCharIndex",
            d.created_at AS "documentCreatedAt"
        FROM document_chunk_embeddings dce
        JOIN document_chunks dc
            ON dc.id = dce.document_chunk_id
        JOIN document_contents dco
            ON dco.id = dc.document_content_id
        JOIN documents d
            ON d.id = dco.document_id
        JOIN document_types dt
            ON dt.id = d.document_type_id
        CROSS JOIN (
            SELECT CAST(:queryEmbedding AS vector) AS query_vector
        ) q
        WHERE d.owner_id = :ownerId
          AND d.status = 'READY'
          AND dc.embedding_status = 'READY'
          AND dce.model = :model
          AND dce.dimensions = :dimensions
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
          AND (
              :scopeDocumentIdsEmpty = true
              OR d.id IN (:scopeDocumentIds)
          )
        ORDER BY dce.embedding <=> q.query_vector
        LIMIT :limit
        """,
        nativeQuery = true
    )
    List<SemanticSearchResultRow> findNearestChunks(
        @Param("queryEmbedding") String queryEmbedding,
        @Param("ownerId") UUID ownerId,
        @Param("model") String model,
        @Param("dimensions") Integer dimensions,
        @Param("documentTypeId") UUID documentTypeId,
        @Param("createdFrom") OffsetDateTime createdFrom,
        @Param("createdTo") OffsetDateTime createdTo,
        @Param("scopeDocumentIdsEmpty") boolean scopeDocumentIdsEmpty,
        @Param("scopeDocumentIds") Collection<UUID> scopeDocumentIds,
        @Param("limit") int limit
    );

}
