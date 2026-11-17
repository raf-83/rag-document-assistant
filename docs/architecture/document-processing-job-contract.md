# Document processing job contract

## Message version 1

```json
{
  "eventType": "document.processing.requested",
  "schemaVersion": 1,
  "jobId": "10000000-0000-0000-0000-000000000001",
  "documentId": "20000000-0000-0000-0000-000000000002",
  "correlationId": "30000000-0000-0000-0000-000000000003"
}
```

All five fields are required. Identifiers use UUID values.
The current contract accepts event type `document.processing.requested`
and schema version `1`.

Messages contain identifiers only. Document content, filenames, storage
paths, user identifiers, JWTs and secrets must be excluded.

Document ownership and storage information must be resolved from the
database. Future consumers must verify that the message's documentId
matches the document associated with jobId. API access must continue
to enforce ownership using the authenticated user.

## Persistent job model

Flyway migration V15 introduces `document_processing_jobs`.

A new job starts as PENDING. Supported transitions are:

- PENDING → RUNNING
- RUNNING → SUCCEEDED
- RUNNING → FAILED

SUCCEEDED and FAILED are terminal states in the current model.
Existing document statuses retain their current meaning.

A partial unique index allows at most one PENDING or RUNNING job per
document. Multiple completed jobs may be retained for the same document.
Deleting a document also deletes its jobs through ON DELETE CASCADE.

Repeated delivery with the same jobId identifies the same job.
An intentional new processing run uses a new jobId.

The current entity validates transitions in memory. Atomic worker claims,
recovery after interruption and duplicate delivery handling require
additional implementation.

## Implementation status

Implemented in ADV-13:

- Java message contract and JSON contract tests.
- Job entity, status transitions and Spring Data JPA repository.
- Additive Flyway V15 migration and database constraints.
- PostgreSQL/pgvector integration tests using Testcontainers.

Upload integration, transactional outbox, RabbitMQ publishing and consuming,
publisher confirms, manual ACK, retry and DLQ are planned follow-up work.

## Verification

Run from the backend directory with Docker available:

```powershell
.\mvnw.cmd test
```

Integration tests execute the actual Flyway migrations against a temporary
PostgreSQL database with pgvector. They verify persistence, constraints,
status updates and document deletion through both JDBC and the repository.
