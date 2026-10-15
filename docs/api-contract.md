# API Contract Draft

## Purpose

This document defines the first draft of communication between frontend and backend.

## Base path

`/api`

## Planned endpoints

### Auth
- POST /api/auth/register
- POST /api/auth/login
- POST /api/auth/logout

### Documents
- GET /api/documents
- POST /api/documents
- GET /api/documents/{id}
- GET /api/documents/{id}/download
- DELETE /api/documents/{id}

### Search
- GET /api/documents/search?query=

### AI
- POST /api/documents/{id}/summary
- POST /api/documents/{id}/ask

## Data exchange

- JSON for standard requests and responses
- multipart/form-data for file upload

## Rules

- frontend never accesses files directly from storage
- backend decides access permissions
- all private document operations require authentication

## Current implemented endpoint

### Health
- GET /api/health

Response:
- plain text: `AI Document Vault backend is running`
