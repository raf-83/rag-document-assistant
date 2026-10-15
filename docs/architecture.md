# Architecture Overview

## High-level architecture

The system is composed of two main applications:

- frontend: Next.js + TypeScript
- backend: Spring Boot

## Responsibilities

### Frontend
- user interface
- forms and views
- document upload UI
- document list and details UI
- search UI
- calling backend API

### Backend
- authentication and authorization
- file validation
- file storage handling
- metadata persistence
- text extraction
- document search
- AI integration
- access control

## Communication

Frontend communicates with backend over REST API using JSON.
File upload uses multipart/form-data.

## Data model concept

The backend stores:
- user data
- document metadata
- extracted document text
- file storage reference

## Security assumptions

- each user can access only their own documents
- no public file URLs
- secrets stored outside repository
- backend validates file type and size
- private documents require authenticated access

## Development assumptions for early stages

- frontend and backend run as separate local applications
- frontend communicates with backend over REST API
- backend owns business logic and access control
- frontend is responsible only for UI and API consumption
- file access must never be public by default
- environment-specific values must be stored outside committed source code
- database configuration will be introduced in the next stage
