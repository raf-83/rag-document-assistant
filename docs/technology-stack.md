# Technology stack

This reference describes technologies that are present in the source, build files, container definitions, or deployment manifests for RAG Document Assistant `v0.1.0`. Versions are taken from `backend/pom.xml`, Maven Wrapper properties, `frontend/package.json` and its lockfile, Dockerfiles, and deployment configuration.

## Backend platform

### Java 25

Java is the backend implementation language. The Maven compiler release is 25, GitHub Actions installs Temurin 25, and the backend container builds with Maven on Temurin 25 before running on an Eclipse Temurin 25 JRE.

Java records are used for API and internal data transfer types, while classes and interfaces define the domain, persistence, processing, and integration layers. The version is consistent across local builds, CI, and containers.

### Spring Boot 4.0.5

Spring Boot provides the application runtime, dependency management, auto-configuration, typed configuration properties, and test infrastructure. The parent version is declared in `backend/pom.xml`.

The application is one Spring Boot deployable organized as a modular monolith. It uses:

- Spring MVC for REST controllers and multipart upload;
- Spring Validation for request and configuration constraints;
- Spring Data JPA for repositories and transaction integration;
- Spring Security for authentication and endpoint protection;
- Spring's event and `@Async` support for post-commit document processing;
- the Spring `RestClient` for OpenAI HTTP integrations.

### Spring Security

Spring Security defines the stateless backend filter chain in `SecurityConfig`. `JwtAuthenticationFilter` extracts Bearer tokens, validates them, and loads only active users. Public routes are limited to health and selected authentication operations; other API routes require an authenticated principal.

`BCryptPasswordEncoder` hashes passwords. Authorization is complemented by owner-scoped repository and service methods for every document operation and retrieval path.

### JWT and JJWT 0.12.7

JJWT creates and validates short-lived HMAC-signed access tokens in `JwtServiceImpl`. The signing key comes from a required Base64-encoded secret. Access-token claims contain the user ID as subject and the email as a claim.

Refresh tokens are deliberately not JWTs. They are opaque random values, stored as hashes and rotated by the authentication service. This separates short-lived API authentication from revocable sessions.

### JPA and Hibernate

Jakarta Persistence annotations model users, documents, extracted content, chunks, embeddings, stored files, and authentication tokens. Spring Data repositories provide common persistence operations, while custom native queries implement PostgreSQL-specific retrieval.

Hibernate validates the Flyway-owned schema at startup (`ddl-auto: validate`) and maps pgvector data with the Hibernate Vector module. Open Session in View is disabled, keeping database access within service transactions.

### Flyway

Flyway applies ordered schema changes from `backend/src/main/resources/db/migration`. Migrations define tables, constraints, indexes, search triggers, the pgvector extension, and the current processing states. It is the authoritative schema-management mechanism for both local and deployed environments.

### Maven 3.9.14 and Maven Wrapper 3.3.4

Maven builds and tests the backend. The checked-in wrapper pins Maven 3.9.14 and wrapper version 3.3.4, so contributors do not need a separately installed Maven distribution.

The standard validation command is:

```bash
./mvnw test
```

On Windows, use `mvnw.cmd test`.

## Data and retrieval

### PostgreSQL 18

PostgreSQL is the only application database. Docker Compose and Kubernetes use the `pgvector/pgvector:0.8.6-pg18` image, which combines PostgreSQL 18 with the vector extension.

PostgreSQL stores transactional domain data, token hashes, extracted text, chunks, vector embeddings, and search indexes. It also executes both document- and chunk-level full-text ranking.

### pgvector 0.8.6

pgvector adds the `vector` data type and distance operators to PostgreSQL. Flyway migration `V10` enables the extension and creates 1,536-dimensional embeddings; `V11` creates an HNSW index with cosine operator semantics.

`SemanticSearchRepository` compares the query vector with ready chunk embeddings by cosine distance. Keeping relational metadata, authorization filters, text search, and vectors in one database simplifies owner-scoped hybrid retrieval.

### PostgreSQL full-text search

PostgreSQL native text-search functions power two paths:

- weighted document search, where title and extracted content populate a trigger-maintained search vector;
- chunk keyword search using `websearch_to_tsquery`, `to_tsvector`, and `ts_rank_cd`.

A GIN index supports chunk text search. The `simple` text-search configuration avoids coupling retrieval to one language-specific stemmer.

## Document processing and AI

### Apache Tika 3.3.0

Tika detects the actual media type of an uploaded file. `FileValidationService` compares it with the submitted media type and filename extension before accepting storage, reducing reliance on client-provided metadata.

### Apache PDFBox 3.0.7

PDFBox extracts text from PDF documents. It also supports PDF-specific validation, including configured page limits and rejection of active content. This path avoids OCR when a PDF already contains extractable text.

### Tess4J 5.13.0 and Tesseract OCR

Tess4J connects Java processing code to Tesseract for JPEG and PNG text extraction. The backend runtime image installs English and Polish Tesseract language data. The OCR data path and language set are configurable.

### OpenAI APIs

`OpenAiEmbeddingClient` uses the OpenAI Embeddings API. The deployment default is `text-embedding-3-small` with 1,536 dimensions.

`OpenAiChatCompletionClient` uses the OpenAI Responses API for grounded document answers. The default chat model is `gpt-4o-mini`, with a configured output limit of 800 tokens. Both clients use Spring's synchronous `RestClient`; calls made by document-processing listeners run off the request thread.

The API key is supplied at runtime and is never stored in source or Kubernetes manifests.

## Frontend

### Next.js 16.4.0

Next.js provides the App Router UI, server-side route handlers, protected-route proxy, and standalone production build. It is also the security boundary between browser cookies and backend Bearer authentication:

- authentication route handlers set and clear cookies;
- the backend proxy uses an allowlist and attaches access tokens server-side;
- failed backend requests can trigger serialized refresh-token rotation;
- origin checks protect state-changing proxy calls.

The frontend Dockerfile copies Next.js standalone output into a small runtime image.

### React 19.2.4

React renders the interactive authentication, dashboard, upload, search, document, and chat experiences. Client hooks manage API state and poll asynchronous processing status.

### TypeScript 5.9.3

TypeScript provides static checking for UI components, BFF handlers, API models, and client utilities. The exact installed version is recorded in `frontend/package-lock.json`.

### Tailwind CSS 4.2.2

Tailwind CSS supplies utility-based styling through the Next.js build. Its exact installed version is recorded in the lockfile.

### ESLint 9.39.4

ESLint and `eslint-config-next` enforce frontend code-quality and framework rules. Frontend validation runs linting, TypeScript checking, and a production build.

### Node.js 24

Both frontend Docker build and runtime stages use Node 24 Alpine. Contributors should use Node 24 locally to match the container environment.

## Packaging and local runtime

### Docker

Each application has a multi-stage Dockerfile:

- the backend builds the Spring Boot JAR with Maven and runs it on a non-root Temurin JRE image with Tesseract installed;
- the frontend installs locked npm dependencies, creates standalone Next.js output, and runs as a non-root Node user.

No secrets are baked into either image.

### Docker Compose

`compose.yaml` orchestrates PostgreSQL, backend, and frontend containers. It builds application images locally, waits for the PostgreSQL health check before starting the backend, mounts external backend configuration and document storage, and receives secrets from a local ignored `.env` file. Host bindings are intended for local development: PostgreSQL on `127.0.0.1:5433`, backend on port 8080, and frontend on port 3001.

### Kubernetes

The deployment procedure creates a dedicated namespace. Manifests under `infra/k8s` define ConfigMaps, persistent claims, Deployments, and ClusterIP Services; local scripts create the two Secret objects. Version `v0.1.0` uses images tagged `0.1.0` from GHCR.

There is one replica per component, no Ingress, and local access uses port-forwarding. Kubernetes therefore demonstrates declarative deployment and health management; it is not presented as public hosting or a high-availability platform.

## Testing and delivery

### Backend tests

The Maven test suite uses the Spring Boot test platform, JUnit, Spring Security test support, and Testcontainers modules for PostgreSQL and JUnit Jupiter. Tests cover services, controllers, security, persistence behavior, document processing, retrieval, and OpenAI client contracts at appropriate boundaries.

### Frontend quality gates

The frontend currently has no separate automated unit-test suite. Its checked-in scripts provide:

- `npm run lint`;
- `npm run typecheck`;
- `npm run build`.

This distinction is intentional: documentation does not claim frontend test coverage that the repository does not contain.

### GitHub Actions

`.github/workflows/backend-ci.yml` runs backend tests for pull requests and pushes to `main`, and supports manual dispatch.

`.github/workflows/container-images.yml` validates backend and frontend container builds on relevant pull requests. On relevant pushes to `main` or version tags, it can publish multi-architecture `linux/amd64` and `linux/arm64` images.

GitHub Actions is the hosted automation platform; each YAML file defines a workflow with one or more jobs. A workflow run is not itself a release or deployment.

### GitHub Container Registry

GHCR stores the published backend and frontend OCI images. The workflow generates branch, semantic-version, `latest` for version tags, and long-SHA tags according to the event. Public repositories can also receive build provenance attestations from the workflow.

### GitHub Release and Kubernetes deployment

A GitHub Release is the repository release record associated with a Git tag, such as [v0.1.0](https://github.com/raf-83/rag-document-assistant/releases/tag/v0.1.0). It is distinct from:

- GitHub Actions, which runs validation and publishing jobs;
- GHCR, which stores container images;
- Kubernetes Deploy, which applies configuration and rolls out those images to the selected local cluster.

The PowerShell workflow keeps these concerns in separate `Validate`, `Submit`, `Release`, `Deploy`, and `Verify` stages. Details are in [Release workflow](release-workflow.md).
