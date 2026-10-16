# RAG Document Assistant backend

Java 25 and Spring Boot 4 backend for authentication, document ingestion, PostgreSQL/pgvector retrieval, and document-grounded answers.

## Responsibilities

- stateless Spring Security with JWT access tokens and rotating opaque refresh tokens;
- owner-scoped document metadata, original-file access, and extracted content;
- PDF/JPEG/PNG validation and private local storage;
- PDFBox text extraction, Tesseract OCR, sanitization, and paragraph-aware chunking;
- OpenAI embedding generation and pgvector persistence;
- document full-text, semantic, and RRF-based hybrid search;
- document-scoped RAG through the OpenAI Responses API.

## Build

The Maven Wrapper pins Maven 3.9.14. Java 25 and Docker are required for the full test suite:

```powershell
.\mvnw.cmd test
```

Run the application after supplying the database, security, AI, storage, OCR, chunking, and retrieval configuration described in [Local development](../docs/local-development.md):

```powershell
.\mvnw.cmd spring-boot:run
```

Flyway owns the database schema, while Hibernate validates it at startup.

## Reference

- [Architecture](../docs/architecture.md)
- [Implemented API](../docs/api-contract.md)
- [Technology stack](../docs/technology-stack.md)
