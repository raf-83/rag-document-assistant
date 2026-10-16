# Architecture

This document describes the implementation shipped in RAG Document Assistant `v0.1.0`. Class and configuration names refer to the repository, not to a proposed architecture.

## System context

RAG Document Assistant is a modular monolith: one Spring Boot process contains the HTTP API, authentication, document processing, retrieval, and AI integrations. A separate Next.js application renders the UI and acts as a backend-for-frontend (BFF). PostgreSQL stores application data, full-text indexes, and vectors; uploaded originals are kept in private filesystem storage.

```mermaid
flowchart TB
    Browser["Browser"]

    subgraph Web["Next.js application"]
        UI["App Router UI"]
        BFF["/api/auth and /api/backend route handlers"]
        Proxy["proxy.ts route protection"]
    end

    subgraph Backend["Spring Boot modular monolith"]
        REST["REST controllers"]
        Security["Spring Security and JWT"]
        Domain["Document and user services"]
        Pipeline["Processing event listeners"]
        Retrieval["Keyword, semantic, and hybrid retrieval"]
        AI["OpenAI clients"]
    end

    DB[("PostgreSQL + pgvector")]
    Files["Private filesystem storage"]
    OCR["PDFBox / Tesseract"]
    OpenAI["OpenAI Embeddings and Responses APIs"]

    Browser --> UI
    UI --> BFF
    Proxy --> UI
    BFF --> REST
    REST --> Security
    Security --> Domain
    Domain --> DB
    Domain --> Files
    Domain -. application events .-> Pipeline
    Pipeline --> OCR
    Pipeline --> DB
    Pipeline --> AI
    Retrieval --> DB
    Retrieval --> AI
    AI --> OpenAI
```

Docker Compose and Kubernetes run the same two application images. The supplied Kubernetes manifests create single backend, frontend, and PostgreSQL replicas in a local cluster. They do not define an Ingress or a highly available topology.

## Backend modules

The application entry point is `backend/src/main/java/com/aidocumentvault/backend/AiDocumentVaultBackendApplication.java`. The main packages divide responsibilities while remaining in one build and one process:

| Package | Responsibility | Representative implementation |
| --- | --- | --- |
| `security` | Registration, login, JWT authentication, refresh-token rotation, verification/reset tokens | `SecurityConfig`, `JwtAuthenticationFilter`, `JwtServiceImpl`, `AuthServiceImpl` |
| `user` | User persistence and account state | `User`, `UserRepository`, `UserStatus` |
| `document` | Uploads, metadata, ownership, document types, content access, document-level search | `DocumentController`, `DocumentService`, `DocumentSearchRepository` |
| `storage` | File validation and private local storage | `FileValidationService`, `LocalStorageService` |
| `processing` | Extraction, OCR, sanitization, chunking, processing state transitions | `DocumentProcessingServiceImpl`, `BasicTextSanitizer`, `ParagraphAwareTextChunker` |
| `ai` | Embeddings, semantic/hybrid retrieval, ranking, and document chat | `OpenAiEmbeddingClient`, `HybridRetrievalServiceImpl`, `DocumentChatServiceImpl` |
| `config` | Typed application configuration | configuration property records under `config.properties`; asynchronous execution is enabled on `AiDocumentVaultBackendApplication` |
| `common` | Cross-cutting API errors and shared persistence support | `GlobalExceptionHandler`, `BaseEntity` |

Package boundaries organize the code, but they are not independently deployed services. Communication between processing steps uses in-process Spring events. Upload and content-ready listeners use `AFTER_COMMIT`; the embedding listener is an asynchronous `@EventListener` rather than a transactional event listener.

## REST API

Controllers expose JSON endpoints under `/api`; document upload is multipart. The implemented endpoint groups are:

- `HealthController`: service health.
- `AuthController`: registration, login, token refresh, logout, email verification, and password-reset flows.
- `DocumentTypeController`: document-type lookup.
- `DocumentController`: upload, listing, metadata, original file download, and extracted content.
- `DocumentSearchController`: document-level PostgreSQL full-text search.
- `SemanticSearchController`: chunk-level vector search.
- `HybridSearchController`: hybrid results grouped by document.
- `DocumentChatController`: document-scoped RAG answers.

The exact methods and paths are listed in [Implemented API](api-contract.md).

## Data model

Flyway migrations in `backend/src/main/resources/db/migration` own the schema. JPA runs with `ddl-auto: validate`, so Hibernate checks rather than creates it.

```mermaid
erDiagram
    USER ||--o{ DOCUMENT : owns
    DOCUMENT_TYPE ||--o{ DOCUMENT : classifies
    DOCUMENT ||--|| STORED_FILE : references
    DOCUMENT ||--o| DOCUMENT_CONTENT : contains
    DOCUMENT ||--o{ DOCUMENT_SUMMARY : has
    DOCUMENT_CONTENT ||--o{ DOCUMENT_CHUNK : splits_into
    DOCUMENT_CHUNK ||--o| DOCUMENT_CHUNK_EMBEDDING : embeds
    USER ||--o{ REFRESH_TOKEN : receives
    USER ||--o{ EMAIL_VERIFICATION_TOKEN : receives
    USER ||--o{ PASSWORD_RESET_TOKEN : receives

    USER {
        uuid id PK
        string email
        string password_hash
        string status
    }
    DOCUMENT {
        uuid id PK
        uuid owner_id FK
        uuid document_type_id FK
        string status
        string title
    }
    DOCUMENT_CONTENT {
        uuid id PK
        uuid document_id FK
        text sanitized_text
        string extraction_method
    }
    DOCUMENT_CHUNK {
        uuid id PK
        uuid document_content_id FK
        int chunk_index
        text content
        int start_char_index
        int end_char_index
        string embedding_status
    }
    DOCUMENT_CHUNK_EMBEDDING {
        uuid id PK
        uuid document_chunk_id FK
        vector embedding
        string model
    }
```

`DocumentSummary` is present in the persistence model, but no summary-generation endpoint or workflow is implemented in this release.

Important database features include:

- migration `V8`: a weighted document search vector (title weight A, extracted content weight B) and update triggers;
- migration `V10`: the `vector` extension and 1,536-dimensional chunk embeddings;
- migration `V11`: an HNSW cosine-distance index;
- migration `V12`: a GIN index for chunk full-text search;
- migration `V14`: the current document-status constraint.

## Upload and processing pipeline

`DocumentService.uploadDocument` validates the request, writes the original file with a generated storage name, persists document metadata, and publishes `DocumentUploadedEvent`. The upload transaction commits before the first asynchronous listener runs.

```mermaid
sequenceDiagram
    actor User
    participant API as DocumentController
    participant Documents as DocumentService
    participant Storage as LocalStorageService
    participant Events as Spring application events
    participant Processor as DocumentProcessingServiceImpl
    participant Extractor as PDF/OCR extractor
    participant Chunker as ParagraphAwareTextChunker
    participant Embeddings as Embedding generation
    participant DB as PostgreSQL

    User->>API: multipart upload
    API->>Documents: uploadDocument
    Documents->>Documents: validate metadata and file
    Documents->>Storage: store original
    Documents->>DB: persist UPLOADED document
    Documents-->>Events: DocumentUploadedEvent after commit
    Events-->>Processor: asynchronous listener
    Processor->>DB: set TEXT_PROCESSING
    Processor->>Extractor: extract PDF text or run OCR
    Processor->>Processor: sanitize extracted text
    Processor->>DB: save content and set CONTENT_READY
    Processor-->>Events: content-ready event after commit
    Events-->>Chunker: create chunks
    Chunker->>DB: replace chunks and set READY
    Chunker-->>Events: embedding event
    Events-->>Embeddings: asynchronous batch generation
    Embeddings->>DB: persist vectors and embedding status
```

### Validation and storage

`FileValidationService` checks configured size and extension limits, the declared and Apache Tika-detected media type, and extension/media-type consistency. PDF-specific validation uses `PdfValidationService` and PDFBox to enforce a page limit and reject active content. The default deployment accepts PDF, JPEG, and PNG files up to 10 MiB and PDFs up to 50 pages.

`LocalStorageService` stores files outside the public web tree under randomly generated names, records a SHA-256 checksum, and rejects path traversal outside the configured base directory.

### Extraction, OCR, sanitization, and chunking

- PDF text extraction uses PDFBox and records the `PDF_TEXT` method.
- JPEG and PNG extraction uses Tess4J/Tesseract and records `OCR`.
- `BasicTextSanitizer` normalizes whitespace and removes control and zero-width characters.
- `ParagraphAwareTextChunker` prefers paragraph, line, sentence, and whitespace boundaries before a hard split. Deployment defaults are 1,800 characters per chunk and 250 characters of overlap; token counts are estimated from character counts.

### Processing states

```mermaid
stateDiagram-v2
    [*] --> UPLOADED
    UPLOADED --> TEXT_PROCESSING
    TEXT_PROCESSING --> CONTENT_READY
    CONTENT_READY --> CHUNKING
    CHUNKING --> READY
    TEXT_PROCESSING --> FAILED
    CONTENT_READY --> FAILED
    CHUNKING --> FAILED
    READY --> [*]
```

`READY` means the extracted content and chunks have been persisted. Embeddings have their own `NOT_CREATED`, `PENDING`, `IN_PROGRESS`, `READY`, and `FAILED` states. Consequently, keyword search can become available before every semantic vector is ready.

## Retrieval and ranking

### Full-text search

`DocumentSearchRepository` performs document-level search with PostgreSQL `websearch_to_tsquery('simple', ...)` and `ts_rank` against the weighted search vector. It filters by the authenticated owner and `READY` document state.

`KeywordSearchRepository` performs chunk-level search using `to_tsvector('simple', ...)`, `websearch_to_tsquery`, and `ts_rank_cd`. It applies the same ownership and readiness boundaries plus optional document type, date, and document-scope filters.

### Semantic search

`OpenAiEmbeddingClient` requests a query vector using the configured embedding model. `SemanticSearchRepository` uses pgvector cosine distance (`<=>`) and converts it to similarity with `1 - distance`. Queries require a matching embedding model and dimension, `READY` embeddings, a `READY` document, and the authenticated owner. The default model is `text-embedding-3-small` with 1,536 dimensions.

### Hybrid retrieval and RRF

`HybridRetrievalServiceImpl` retrieves a larger candidate set from the semantic and keyword branches, combines identical chunk IDs, and computes:

```text
rawRrf = semanticWeight / (rrfK + semanticRank)
       + keywordWeight  / (rrfK + keywordRank)

normalizedScore = rawRrf
                / ((semanticWeight + keywordWeight) / (rrfK + 1))
```

A missing rank contributes zero, and the normalized result is clamped to `[0, 1]`. The Kubernetes configuration uses `rrfK = 60`, semantic weight `0.60`, keyword weight `0.40`, candidate multiplier `3`, and a maximum of 150 candidates.

`HybridSearchServiceImpl` groups chunks by document. The best chunk is the base document score. Further chunks add a bounded evidence bonus, limited by the configured 0.15 share of remaining score space. Results retain whether evidence was `HYBRID`, `SEMANTIC_ONLY`, or `KEYWORD_ONLY`, and ties are deterministic.

```mermaid
flowchart LR
    Q["User query"] --> E["Query embedding"]
    Q --> K["PostgreSQL chunk FTS"]
    E --> V["pgvector cosine search"]
    K --> R["Weighted normalized RRF"]
    V --> R
    R --> G["Group chunks by document"]
    G --> H["Ranked hybrid results"]
```

## Document-grounded answers

`DocumentChatServiceImpl` first loads the selected document by ID and current owner and requires it to be `READY`. `DocumentChunkRetrievalServiceImpl` executes hybrid retrieval scoped to that document, keeps at most five chunks by default, and caps combined context at 8,000 characters. With no usable chunks, it returns a no-context response without calling the language model.

`DocumentChatPromptBuilder` separates system instructions, source passages, and the question. It directs the model to:

- answer only from the supplied sources;
- state when the sources are insufficient;
- treat source text as data, not as instructions;
- cite source labels;
- answer in the question's language.

`OpenAiChatCompletionClient` calls the OpenAI Responses API. The default chat model is `gpt-4o-mini`, with a configured maximum of 800 output tokens. The API response includes the answer and source chunk IDs, indexes, character offsets, and retrieval scores.

## Security and user-data isolation

`SecurityConfig` creates a stateless Spring Security filter chain. Health and explicitly listed authentication operations are public; other backend routes require authentication. CSRF is disabled at the stateless backend boundary. Passwords use BCrypt.

`JwtAuthenticationFilter` validates a Bearer access token and establishes a principal only if the referenced user is still `ACTIVE`. `JwtServiceImpl` signs HMAC JWTs from a Base64-configured key. Refresh tokens are opaque, stored only as hashes, rotated on use, and revoked on logout or password reset. Verification and password-reset tokens are also stored as hashes.

The Next.js BFF keeps access and refresh tokens in `HttpOnly`, `SameSite=Lax` cookies instead of browser JavaScript storage. It validates the request origin for state-changing proxy calls, injects the backend Bearer token server-side, and serializes automatic refresh attempts after a 401.

Authorization is enforced again in persistence operations: document reads, downloads, content access, document search, semantic search, hybrid search, and chat include the authenticated owner in their query or service lookup. This is application-level isolation in one database, not separate databases per tenant.

## Transactions, errors, and asynchronous work

- Upload metadata is transactional; processing begins through an `AFTER_COMMIT` event listener.
- The filesystem write occurs before the upload database transaction commits, and the current service has no compensating file deletion on a later persistence failure. Operational cleanup may therefore be needed for a rare orphaned file.
- Processing status, extracted content, chunks, and embedding batches use explicit new transactions so progress and failures can be recorded independently.
- Extraction or chunking failures move the document to `FAILED`.
- Embedding failures mark affected embeddings `FAILED`; they do not change the already-`READY` document state.
- `GlobalExceptionHandler` maps known validation, media-type, not-found, conflict, and search errors to structured HTTP responses and provides a generic 500 fallback.

Spring `@Async` uses the framework executor selected after `@EnableAsync`; the application does not define a custom durable work queue. There is no broker-backed queue in this release, so an application restart can interrupt outstanding work. The local filesystem and single database are also deliberate portfolio-scale choices, not high-availability guarantees.

## Frontend integration

The Next.js App Router provides registration/login screens, a protected dashboard, upload and document views, hybrid search, and document chat. Browser requests target the frontend route handlers rather than the backend directly. The BFF exposes a strict backend-path allowlist. `useDocumentPolling` checks processing status every three seconds until a document becomes `READY` or `FAILED`.

The frontend health route used by Kubernetes readiness checks calls the backend health endpoint. A separate liveness route checks the frontend process itself.

## Deployment topology

- Docker Compose builds both application images, starts PostgreSQL with pgvector, mounts external configuration and document storage, and binds services only to local host ports.
- Kubernetes creates the `rag-document-assistant` namespace, ConfigMap, locally supplied Secrets, two 2 GiB persistent volume claims, and one replica per Deployment.
- Kubernetes services are `ClusterIP`; the frontend is accessed locally with `kubectl port-forward`.
- GitHub Actions can validate builds and publish versioned multi-architecture images to GHCR. Creating a GitHub Release and deploying those images are separate workflow stages.

The backend image installs English and Polish Tesseract data and sets its data path. The checked-in Kubernetes ConfigMap does not set `APP_OCR_TESSERACT_LANGUAGE`; therefore PDF processing is configured as shipped, while JPEG/PNG OCR needs that non-sensitive setting added in a future manifest change before it should be considered operationally configured.

Operational instructions are in [Local development](local-development.md), [Release workflow](release-workflow.md), and [Kubernetes deployment](../infra/k8s/README.md).
