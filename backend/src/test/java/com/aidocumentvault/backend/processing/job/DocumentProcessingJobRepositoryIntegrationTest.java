package com.aidocumentvault.backend.processing.job;

import com.aidocumentvault.backend.document.entity.Document;
import com.aidocumentvault.backend.document.entity.DocumentType;
import com.aidocumentvault.backend.document.repository.DocumentRepository;
import com.aidocumentvault.backend.storage.entity.StorageProvider;
import com.aidocumentvault.backend.storage.entity.StoredFile;
import com.aidocumentvault.backend.user.entity.User;
import com.aidocumentvault.backend.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "spring.flyway.baseline-on-migrate=false"
})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(
    classes = DocumentProcessingJobRepositoryIntegrationTest.JpaTestConfiguration.class
)
class DocumentProcessingJobRepositoryIntegrationTest {

    private static final String INSERT_JOB_SQL = """
        INSERT INTO document_processing_jobs (
            id,
            document_id,
            correlation_id,
            status,
            created_at,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?)
        """;

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
        .parse("pgvector/pgvector:0.8.6-pg18")
        .asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE)
        .withDatabaseName("document_processing_job_it");

    @Autowired
    private DocumentProcessingJobRepository jobRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void shouldValidateMigrationsWithNoPendingChangesAndAppliedV15() {
        var validationResult = flyway.validateWithResult();
        var migrationInfo = flyway.info();

        assertThat(validationResult.validationSuccessful).isTrue();
        assertThat(validationResult.invalidMigrations).isEmpty();
        assertThat(migrationInfo.pending()).isEmpty();
        assertThat(migrationInfo.current()).isNotNull();
        assertThat(migrationInfo.current().getVersion())
            .isGreaterThanOrEqualTo(MigrationVersion.fromVersion("15"));

        assertThat(Arrays.asList(migrationInfo.applied()))
            .filteredOn(info ->
                info.getVersion() != null && "15".equals(info.getVersion().getVersion())
            )
            .singleElement()
            .satisfies(info -> assertThat(info.getState()).isEqualTo(MigrationState.SUCCESS));
    }

    @Test
    void shouldCreateDocumentProcessingJobsTableWithExpectedColumns() {
        List<ColumnDefinition> columns = jdbcTemplate.query(
            """
                SELECT
                    column_name,
                    data_type,
                    is_nullable,
                    character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'document_processing_jobs'
                """,
            (resultSet, rowNumber) -> new ColumnDefinition(
                resultSet.getString("column_name"),
                resultSet.getString("data_type"),
                resultSet.getString("is_nullable"),
                (Integer) resultSet.getObject("character_maximum_length")
            )
        );

        assertThat(columns).containsExactlyInAnyOrder(
            new ColumnDefinition("id", "uuid", "NO", null),
            new ColumnDefinition("document_id", "uuid", "NO", null),
            new ColumnDefinition("correlation_id", "uuid", "NO", null),
            new ColumnDefinition("status", "character varying", "NO", 30),
            new ColumnDefinition("created_at", "timestamp with time zone", "NO", null),
            new ColumnDefinition("updated_at", "timestamp with time zone", "NO", null)
        );
    }

    @Test
    void shouldCreateDocumentProcessingJobIndexes() {
        List<String> indexNames = jdbcTemplate.queryForList(
            """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename = 'document_processing_jobs'
                """,
            String.class
        );

        assertThat(indexNames).contains(
            "document_processing_jobs_pkey",
            "idx_document_processing_jobs_document_id",
            "uq_document_processing_jobs_active_document"
        );
    }

    @Test
    void shouldSaveAndLoadProcessingJob() {
        Document document = createAndPersistDocument();
        UUID correlationId = UUID.randomUUID();

        DocumentProcessingJob saved = jobRepository.saveAndFlush(
            new DocumentProcessingJob(document, correlationId)
        );
        UUID jobId = saved.getId();
        UUID documentId = document.getId();
        entityManager.clear();

        DocumentProcessingJob loaded = jobRepository.findById(jobId).orElseThrow();

        assertThat(loaded.getId()).isEqualTo(jobId);
        assertThat(loaded.getDocument().getId()).isEqualTo(documentId);
        assertThat(loaded.getCorrelationId()).isEqualTo(correlationId);
        assertThat(loaded.getStatus()).isEqualTo(DocumentProcessingJobStatus.PENDING);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(
        value = DocumentProcessingJobStatus.class,
        names = {"SUCCEEDED", "FAILED"}
    )
    void shouldPersistStatusTransitions(DocumentProcessingJobStatus terminalStatus) {
        DocumentProcessingJob saved = jobRepository.saveAndFlush(
            new DocumentProcessingJob(createAndPersistDocument(), UUID.randomUUID())
        );
        UUID jobId = saved.getId();

        saved.start();
        jobRepository.saveAndFlush(saved);
        entityManager.clear();

        DocumentProcessingJob running = jobRepository.findById(jobId).orElseThrow();
        assertThat(running.getStatus()).isEqualTo(DocumentProcessingJobStatus.RUNNING);

        if (terminalStatus == DocumentProcessingJobStatus.SUCCEEDED) {
            running.succeed();
        } else {
            running.fail();
        }
        jobRepository.saveAndFlush(running);
        entityManager.clear();

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus())
            .isEqualTo(terminalStatus);
    }

    @Test
    void shouldRejectJobWithUnknownDocumentId() {
        assertSqlState(
            "23503",
            () -> insertRawJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PENDING",
                OffsetDateTime.now(),
                OffsetDateTime.now()
            )
        );
    }

    @Test
    void shouldRejectUnsupportedStatus() {
        Document document = createAndPersistDocument();
        entityManager.flush();

        assertSqlState(
            "23514",
            () -> insertRawJob(
                UUID.randomUUID(),
                document.getId(),
                UUID.randomUUID(),
                "CANCELLED",
                OffsetDateTime.now(),
                OffsetDateTime.now()
            )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "id", "document_id", "correlation_id", "status", "created_at", "updated_at"
    })
    void shouldRejectNullRequiredColumn(String nullColumn) {
        Document document = createAndPersistDocument();
        entityManager.flush();

        Object[] values = {
            UUID.randomUUID(),
            document.getId(),
            UUID.randomUUID(),
            "PENDING",
            OffsetDateTime.now(),
            OffsetDateTime.now()
        };
        values[columnIndex(nullColumn)] = null;

        assertSqlState("23502", () -> jdbcTemplate.update(INSERT_JOB_SQL, values));
    }

    @ParameterizedTest
    @CsvSource({
        "PENDING, PENDING",
        "PENDING, RUNNING",
        "RUNNING, PENDING",
        "RUNNING, RUNNING"
    })
    void shouldRejectSecondActiveJobForSameDocument(
        DocumentProcessingJobStatus firstStatus,
        DocumentProcessingJobStatus secondStatus
    ) {
        Document document = createAndPersistDocument();
        DocumentProcessingJob first = jobWithStatus(document, firstStatus);
        DocumentProcessingJob second = jobWithStatus(document, secondStatus);
        jobRepository.saveAndFlush(first);

        assertSqlState("23505", () -> jobRepository.saveAndFlush(second));
    }

    @ParameterizedTest
    @EnumSource(
        value = DocumentProcessingJobStatus.class,
        names = {"SUCCEEDED", "FAILED"}
    )
    void shouldAllowNewActiveJobAfterPreviousJobTerminates(
        DocumentProcessingJobStatus terminalStatus
    ) {
        Document document = createAndPersistDocument();
        UUID documentId = document.getId();
        DocumentProcessingJob terminalJob = jobRepository.saveAndFlush(
            new DocumentProcessingJob(document, UUID.randomUUID())
        );
        UUID terminalJobId = terminalJob.getId();

        terminalJob.start();
        jobRepository.saveAndFlush(terminalJob);

        if (terminalStatus == DocumentProcessingJobStatus.SUCCEEDED) {
            terminalJob.succeed();
        } else {
            terminalJob.fail();
        }
        jobRepository.saveAndFlush(terminalJob);
        entityManager.clear();

        Document reloadedDocument = documentRepository.findById(documentId).orElseThrow();
        DocumentProcessingJob activeJob = jobRepository.saveAndFlush(
            new DocumentProcessingJob(reloadedDocument, UUID.randomUUID())
        );
        UUID activeJobId = activeJob.getId();
        entityManager.clear();

        assertThat(jobRepository.findById(terminalJobId))
            .isPresent()
            .get()
            .extracting(DocumentProcessingJob::getStatus)
            .isEqualTo(terminalStatus);
        assertThat(jobRepository.findById(activeJobId))
            .isPresent()
            .get()
            .extracting(DocumentProcessingJob::getStatus)
            .isEqualTo(DocumentProcessingJobStatus.PENDING);
    }

    @Test
    void shouldAllowMultipleTerminalJobsForSameDocument() {
        Document document = createAndPersistDocument();

        jobRepository.saveAndFlush(jobWithStatus(
            document,
            DocumentProcessingJobStatus.SUCCEEDED
        ));
        jobRepository.saveAndFlush(jobWithStatus(
            document,
            DocumentProcessingJobStatus.FAILED
        ));
        jobRepository.saveAndFlush(jobWithStatus(
            document,
            DocumentProcessingJobStatus.SUCCEEDED
        ));

        assertThat(jobRepository.findAll())
            .filteredOn(job -> job.getDocument().getId().equals(document.getId()))
            .extracting(DocumentProcessingJob::getStatus)
            .containsExactlyInAnyOrder(
                DocumentProcessingJobStatus.SUCCEEDED,
                DocumentProcessingJobStatus.FAILED,
                DocumentProcessingJobStatus.SUCCEEDED
            );
    }

    @Test
    void shouldCascadeDeleteJobsWhenDocumentIsDeleted() {
        Document document = createAndPersistDocument();
        DocumentProcessingJob job = jobRepository.saveAndFlush(
            new DocumentProcessingJob(document, UUID.randomUUID())
        );
        UUID jobId = job.getId();

        int deletedDocuments = jdbcTemplate.update(
            "DELETE FROM documents WHERE id = ?",
            document.getId()
        );
        entityManager.clear();

        assertThat(deletedDocuments).isEqualTo(1);
        assertThat(jobRepository.existsById(jobId)).isFalse();
    }

    @Test
    void shouldCascadeDeleteJobWhenDocumentIsDeletedThroughRepository() {
        Document document = createAndPersistDocument();
        UUID documentId = document.getId();
        DocumentProcessingJob job = jobRepository.saveAndFlush(
            new DocumentProcessingJob(document, UUID.randomUUID())
        );
        UUID jobId = job.getId();
        entityManager.clear();

        Document reloadedDocument = documentRepository.findById(documentId).orElseThrow();
        documentRepository.delete(reloadedDocument);
        documentRepository.flush();
        entityManager.clear();

        assertThat(documentRepository.existsById(documentId)).isFalse();
        assertThat(jobRepository.existsById(jobId)).isFalse();
    }

    private Document createAndPersistDocument() {
        User owner = new User(
            "job-it-" + UUID.randomUUID() + "@example.com",
            "Integration",
            "Test",
            "{noop}password",
            UserStatus.ACTIVE
        );
        entityManager.persist(owner);

        DocumentType documentType = entityManager.createQuery(
                "SELECT type FROM DocumentType type WHERE type.code = :code",
                DocumentType.class
            )
            .setParameter("code", "OTHER")
            .getSingleResult();

        StoredFile storedFile = new StoredFile(
            "job-test.pdf",
            "documents/" + UUID.randomUUID() + ".pdf",
            UUID.randomUUID() + ".pdf",
            "application/pdf",
            "pdf",
            1024L,
            "checksum-" + UUID.randomUUID(),
            StorageProvider.LOCAL_PRIVATE
        );
        Document document = new Document(
            "Document processing job integration test",
            owner,
            documentType,
            storedFile
        );
        entityManager.persist(document);
        entityManager.flush();
        return document;
    }

    private DocumentProcessingJob jobWithStatus(
        Document document,
        DocumentProcessingJobStatus status
    ) {
        DocumentProcessingJob job = new DocumentProcessingJob(document, UUID.randomUUID());
        if (status != DocumentProcessingJobStatus.PENDING) {
            job.start();
        }
        if (status == DocumentProcessingJobStatus.SUCCEEDED) {
            job.succeed();
        } else if (status == DocumentProcessingJobStatus.FAILED) {
            job.fail();
        }
        return job;
    }

    private void insertRawJob(
        UUID id,
        UUID documentId,
        UUID correlationId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        jdbcTemplate.update(
            INSERT_JOB_SQL,
            id,
            documentId,
            correlationId,
            status,
            createdAt,
            updatedAt
        );
    }

    private int columnIndex(String columnName) {
        return switch (columnName) {
            case "id" -> 0;
            case "document_id" -> 1;
            case "correlation_id" -> 2;
            case "status" -> 3;
            case "created_at" -> 4;
            case "updated_at" -> 5;
            default -> throw new IllegalArgumentException("Unknown column: " + columnName);
        };
    }

    private void assertSqlState(String expectedSqlState, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(DataIntegrityViolationException.class)
            .satisfies(error -> assertThat(findSqlState(error)).isEqualTo(expectedSqlState));
    }

    private String findSqlState(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }

    private record ColumnDefinition(
        String name,
        String dataType,
        String nullable,
        Integer maximumLength
    ) {
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackages = "com.aidocumentvault.backend")
    @EnableJpaRepositories(basePackageClasses = {
        DocumentProcessingJobRepository.class,
        DocumentRepository.class
    })
    static class JpaTestConfiguration {
    }
}
