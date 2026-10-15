//package com.aidocumentvault.backend.processing.chunking.service;
//
//import com.aidocumentvault.backend.document.entity.Document;
//import com.aidocumentvault.backend.document.entity.DocumentChunk;
//import com.aidocumentvault.backend.document.entity.DocumentContent;
//import com.aidocumentvault.backend.document.entity.DocumentType;
//import com.aidocumentvault.backend.document.entity.EmbeddingStatus;
//import com.aidocumentvault.backend.document.entity.ExtractionMethod;
//import com.aidocumentvault.backend.document.repository.DocumentChunkRepository;
//import com.aidocumentvault.backend.document.repository.DocumentContentRepository;
//import com.aidocumentvault.backend.document.repository.DocumentRepository;
//import com.aidocumentvault.backend.document.repository.DocumentTypeRepository;
//import com.aidocumentvault.backend.processing.chunking.config.ChunkingProperties;
//import com.aidocumentvault.backend.processing.chunking.dto.ChunkCandidate;
//import com.aidocumentvault.backend.storage.entity.StorageProvider;
//import com.aidocumentvault.backend.storage.entity.StoredFile;
//import com.aidocumentvault.backend.user.entity.User;
//import com.aidocumentvault.backend.user.entity.UserStatus;
//import com.aidocumentvault.backend.user.repository.UserRepository;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
//import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
//import org.springframework.boot.test.context.TestConfiguration;
//import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Import;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//
//import java.util.List;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@DataJpaTest
//@Testcontainers
//@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
//@Import({
//    DocumentChunkPersistenceServiceImpl.class,
//    DocumentChunkPersistenceServiceIntegrationTest.TestConfig.class
//})
//@Transactional(propagation = Propagation.NOT_SUPPORTED)
//class DocumentChunkPersistenceServiceIntegrationTest {
//
//    @Container
//    @ServiceConnection
//    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");
//
//    @Autowired
//    private DocumentChunkPersistenceService documentChunkPersistenceService;
//
//    @Autowired
//    private DocumentChunkRepository documentChunkRepository;
//
//    @Autowired
//    private DocumentContentRepository documentContentRepository;
//
//    @Autowired
//    private DocumentRepository documentRepository;
//
//    @Autowired
//    private DocumentTypeRepository documentTypeRepository;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Test
//    void shouldReplaceChunksWithoutCreatingDuplicates() {
//        // given
//        DocumentContent documentContent = createAndSaveDocumentContent();
//
//        List<ChunkCandidate> firstChunkCandidates = List.of(
//            new ChunkCandidate(0, "Pierwszy chunk testowy.", 0, 22, 22, 6, 0, 5),
//            new ChunkCandidate(1, "Drugi chunk testowy.", 17, 38, 21, 6, 5, 5),
//            new ChunkCandidate(2, "Trzeci chunk testowy.", 33, 55, 22, 6, 5, 0)
//        );
//
//        List<ChunkCandidate> secondChunkCandidates = List.of(
//            new ChunkCandidate(0, "Pierwszy chunk po ponownym przeliczeniu.", 0, 41, 41, 11, 0, 5),
//            new ChunkCandidate(1, "Drugi chunk po ponownym przeliczeniu.", 36, 75, 39, 10, 5, 5),
//            new ChunkCandidate(2, "Trzeci chunk po ponownym przeliczeniu.", 70, 110, 40, 10, 5, 0)
//        );
//
//        // when
//        int firstSavedCount = documentChunkPersistenceService.replaceChunks(
//            documentContent,
//            firstChunkCandidates
//        );
//
//        int secondSavedCount = documentChunkPersistenceService.replaceChunks(
//            documentContent,
//            secondChunkCandidates
//        );
//
//        // then
//        assertThat(firstSavedCount).isEqualTo(3);
//        assertThat(secondSavedCount).isEqualTo(3);
//
//        List<DocumentChunk> savedChunks = documentChunkRepository
//            .findAllByDocumentContentIdOrderByChunkIndex(documentContent.getId());
//
//        assertThat(savedChunks).hasSize(3);
//
//        assertThat(savedChunks)
//            .extracting(DocumentChunk::getChunkIndex)
//            .containsExactly(0, 1, 2);
//
//        assertThat(savedChunks)
//            .extracting(DocumentChunk::getText)
//            .containsExactly(
//                "Pierwszy chunk po ponownym przeliczeniu.",
//                "Drugi chunk po ponownym przeliczeniu.",
//                "Trzeci chunk po ponownym przeliczeniu."
//            );
//
//        assertThat(savedChunks)
//            .extracting(DocumentChunk::getChunkingStrategy)
//            .containsOnly("PARAGRAPH_AWARE_V1");
//
//        assertThat(savedChunks)
//            .extracting(DocumentChunk::getEmbeddingStatus)
//            .containsOnly(EmbeddingStatus.NOT_CREATED);
//
//        long chunkCount = documentChunkRepository.countByDocumentContentId(documentContent.getId());
//
//        assertThat(chunkCount).isEqualTo(3);
//    }
//
//    private DocumentContent createAndSaveDocumentContent() {
//        User owner = userRepository.saveAndFlush(new User(
//            uniqueEmail(),
//            "Test",
//            "User",
//            "{noop}password",
//            UserStatus.ACTIVE
//        ));
//
//        DocumentType documentType = documentTypeRepository.findByCode("OTHER")
//            .orElseThrow(() -> new IllegalStateException("Missing OTHER document type seed"));
//
//        StoredFile storedFile = new StoredFile(
//            "test.pdf",
//            "documents/" + UUID.randomUUID() + ".pdf",
//            UUID.randomUUID() + ".pdf",
//            "application/pdf",
//            "pdf",
//            1024L,
//            "test-checksum",
//            StorageProvider.LOCAL_PRIVATE
//        );
//
//        Document document = documentRepository.saveAndFlush(new Document(
//            "Dokument testowy do chunkingu",
//            owner,
//            documentType,
//            storedFile
//        ));
//
//        DocumentContent documentContent = new DocumentContent(
//            document,
//            "To jest przykładowa oczyszczona treść dokumentu używana w teście chunkingu.",
//            ExtractionMethod.PDF_TEXT,
//            1
//        );
//
//        return documentContentRepository.saveAndFlush(documentContent);
//    }
//
//    private String uniqueEmail() {
//        return "chunk-test-" + UUID.randomUUID() + "@example.com";
//    }
//
//    @TestConfiguration
//    static class TestConfig {
//
//        @Bean
//        ChunkingProperties chunkingProperties() {
//            return new ChunkingProperties(
//                1800,
//                250,
//                300,
//                "PARAGRAPH_AWARE_V1"
//            );
//        }
//    }
//}
