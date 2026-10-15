# RAG Document Assistant

RAG Document Assistant is a secure document management application with AI-powered document processing.

## Project goal

The goal of this project is to build a web application that allows users to securely store documents, extract text, search content, generate summaries, and ask questions about documents using AI.

## Tech stack

- Backend: Java 25 + Spring Boot
- Frontend: Next.js + TypeScript
- Repository: monorepo
- Infrastructure: Docker Compose / Kubernetes with versioned GHCR images

## Current status

Stage 0 — foundation completed

At this stage the project already includes:
- initialized Spring Boot backend
- initialized Next.js frontend
- basic backend package structure
- frontend base structure
- health endpoint
- frontend-backend connectivity test
- local development setup

## Running the application

- For a local source build, use `docker compose up --build` after preparing the
  environment variables required by `compose.yaml`.
- For a repeatable Kubernetes deployment, see
  [infra/k8s/README.md](infra/k8s/README.md). The Kubernetes manifests use
  versioned backend and frontend images from GitHub Container Registry.
- For the interactive release workflow (tests, Pull Request, GitHub Release,
  GHCR publishing and local Kubernetes deployment), see
  [docs/release-workflow.md](docs/release-workflow.md).

## Repository structure

```text
rag-document-assistant/
├─ backend/
├─ frontend/
├─ docs/
├─ .editorconfig
├─ .gitignore
└─ README.md
