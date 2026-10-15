package com.aidocumentvault.backend.document.repository;

import com.aidocumentvault.backend.document.entity.DocumentChunk;
import com.aidocumentvault.backend.document.entity.EmbeddingStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findAllByDocumentContentIdOrderByChunkIndex(UUID documentContentId);

    long countByDocumentContentId(UUID documentContentId);

    @Query("""
            select chunk
            from DocumentChunk chunk
            join chunk.documentContent content
            join content.document document
            join document.owner owner
            where content.id = :documentContentId
              and document.id = :documentId
              and owner.id = :ownerId
              and chunk.embeddingStatus in :embeddingStatuses
            order by chunk.chunkIndex asc
            """)
    List<DocumentChunk> findAllForEmbeddingGeneration(
        @Param("documentId") UUID documentId,
        @Param("documentContentId") UUID documentContentId,
        @Param("ownerId") UUID ownerId,
        @Param("embeddingStatuses") Collection<EmbeddingStatus> embeddingStatuses
    );

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("""
            update DocumentChunk chunk
            set chunk.embeddingStatus = :embeddingStatus
            where chunk.id in :chunkIds
            """)
    int updateEmbeddingStatusByIdIn(
        @Param("chunkIds") Collection<UUID> chunkIds,
        @Param("embeddingStatus") EmbeddingStatus embeddingStatus
    );

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("""
            delete from DocumentChunk chunk
            where chunk.documentContent.id = :documentContentId
            """)
    int deleteAllByDocumentContentId(@Param("documentContentId") UUID documentContentId);
}
