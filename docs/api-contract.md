# Implemented API

This is a concise inventory of the REST endpoints implemented in RAG Document Assistant `v0.1.0`. The Java controllers remain the executable source of truth.

Base URL for a source-run backend: `http://localhost:8080`.

## Authentication

The backend accepts access tokens through:

```http
Authorization: Bearer <access-token>
```

The Next.js application does not expose these tokens to browser JavaScript. Its BFF stores the access and refresh tokens in `HttpOnly` cookies, proxies approved paths, and attaches the Bearer token server-side.

Health and the authentication operations listed below are public in `SecurityConfig`. Document types, documents, searches, downloads, content, and chat require authentication.

## Health

| Method | Path | Result |
| --- | --- | --- |
| `GET` | `/api/health` | Plain-text backend health response |

Implemented by `HealthController`.

## Authentication endpoints

Base path: `/api/auth`. Implemented by `AuthController`.

| Method | Path | Request | Result |
| --- | --- | --- | --- |
| `POST` | `/register` | JSON registration data | Generic message; account starts pending verification |
| `POST` | `/login` | Email and password | Access token, refresh token, and token metadata |
| `POST` | `/refresh` | Refresh token | Rotated refresh token and new access token |
| `POST` | `/logout` | Refresh token | Revokes that refresh-token session |
| `GET` | `/verify-email?token=...` | Query token | Verifies a valid pending account |
| `POST` | `/resend-verification-email` | Email | Generic message |
| `POST` | `/forgot-password` | Email | Generic message |
| `POST` | `/reset-password` | Reset token and new password | Changes the password and revokes active refresh tokens |

Responses for resend and forgotten-password operations are intentionally generic to reduce account enumeration. The current mail adapter does not deliver emails; see [Local development](local-development.md#email-delivery-limitation).

## Document types

Implemented by `DocumentTypeController`.

| Method | Path | Result |
| --- | --- | --- |
| `GET` | `/api/document-types` | Active document types |
| `GET` | `/api/document-types/{id}` | One document type |

## Documents

Implemented by `DocumentController`.

| Method | Path | Request | Result |
| --- | --- | --- | --- |
| `GET` | `/api/documents` | Optional `status` query parameter | Current user's documents |
| `GET` | `/api/documents/{id}` | Document UUID | Current user's document metadata |
| `POST` | `/api/documents` | `multipart/form-data` | Creates a document and starts asynchronous processing |
| `GET` | `/api/documents/{id}/file` | Document UUID | Original file as an attachment |
| `GET` | `/api/documents/{documentId}/content` | Document UUID | Extracted, sanitized document content |

The upload has two multipart parts:

- `file`: a PDF, JPEG, or PNG file allowed by runtime configuration;
- `metadata`: JSON matching `UploadDocumentRequest`, including title and document type.

There is no document-delete REST endpoint in this release.

## Document full-text search

Implemented by `DocumentSearchController`.

| Method | Path | Parameters | Result |
| --- | --- | --- | --- |
| `GET` | `/api/documents/search` | Required `query`; optional `page` (default 0) and `size` (default 20) | Page of owner-scoped `READY` documents ranked by PostgreSQL full-text search |

This endpoint searches weighted document titles and extracted content. It is distinct from chunk-level semantic and hybrid search.

## Semantic search

Implemented by `SemanticSearchController`.

| Method | Path | Request | Result |
| --- | --- | --- | --- |
| `POST` | `/api/search/semantic` | `SemanticSearchRequest` JSON | Ranked owner-scoped chunks with cosine-similarity scores and previews |

The request supports a query, result limit, document-type/date filters, and optional document scope as defined by the DTO. Only chunks with ready, model-compatible embeddings participate.

## Hybrid search

Implemented by `HybridSearchController`.

| Method | Path | Request | Result |
| --- | --- | --- | --- |
| `POST` | `/api/search/hybrid` | `HybridSearchRequest` JSON | Documents ranked from fused semantic and keyword chunk evidence |

The request supports the same filtering scope as retrieval criteria. The response groups up to the configured number of chunks per document and labels each chunk match as `HYBRID`, `SEMANTIC_ONLY`, or `KEYWORD_ONLY`.

## Document chat

Implemented by `DocumentChatController`.

| Method | Path | Request | Result |
| --- | --- | --- | --- |
| `POST` | `/api/documents/{documentId}/chat` | `DocumentChatRequest` with a question | Grounded answer and contributing source chunks |

The service requires an owner-scoped `READY` document. Retrieval is constrained to that document. Source metadata includes the chunk ID and index, character offsets, and retrieval score. When retrieval finds no context, the service returns a no-context response without invoking the chat model.

## Ownership and errors

All document-facing operations resolve the current authenticated user and include that owner in service or repository lookups. A UUID alone does not grant access to another user's data.

`GlobalExceptionHandler` returns structured error bodies for known validation, unsupported-media, not-found, conflict, and search errors, with a generic internal-error fallback. Validation constraints on request DTOs define the detailed accepted lengths, ranges, and formats.

## Frontend BFF routes

The browser normally calls Next.js routes rather than the backend directly:

- `/api/session/*` manages login, logout, refresh, and session status;
- `/api/auth/*` proxies the public account flows;
- `/api/backend/[...path]` proxies an explicit allowlist of authenticated backend routes;
- `/api/health` checks frontend-to-backend readiness;
- `/api/health/live` checks the frontend process.

State-changing BFF calls must pass its origin check. Backend access and refresh tokens remain server-managed cookies.
