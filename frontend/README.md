# RAG Document Assistant frontend

Next.js 16, React 19, and TypeScript frontend for RAG Document Assistant. Route Handlers form a backend-for-frontend (BFF) between the browser and Spring Boot API.

## Requirements

- Node.js 24 and npm;
- a running RAG Document Assistant backend;
- backend database and runtime configuration.

## Configuration

```powershell
Copy-Item .env.example .env.local
```

`.env.local`:

```dotenv
BACKEND_API_URL=http://localhost:8080
```

`BACKEND_API_URL` is server-only. Do not put user tokens or backend API keys in frontend environment files.

## Security boundary

Session route handlers keep access and refresh tokens in `HttpOnly`, `SameSite=Lax` cookies. The backend proxy uses an explicit path allowlist, attaches the access token server-side, and coordinates one refresh attempt after a 401. Protected pages are redirected by `proxy.ts` when no access cookie is present.

State-changing BFF requests compare `Origin` with the effective request host and protocol. Missing, malformed, or mismatched origins return 403. Local development and `kubectl port-forward` use `Host`; a trusted reverse proxy can supply `X-Forwarded-Host` and `X-Forwarded-Proto`. Such a proxy must overwrite client-supplied forwarding headers. An HTTPS environment must also set `SESSION_COOKIE_SECURE=true`.

## Run

```powershell
npm ci
npm run dev
```

Open `http://localhost:3000`.

## Quality gates

```powershell
npm run lint
npm run typecheck
npm run build
```

The repository does not currently contain a separate frontend unit-test suite.

## Implemented areas

- registration, login, verification, and password-reset screens;
- server-managed cookie sessions;
- document list, upload, details, content, and download;
- processing-status polling;
- full-text, semantic, and hybrid search;
- RAG chat for a selected ready document.

See [Local development](../docs/local-development.md), [Architecture](../docs/architecture.md), and [Implemented API](../docs/api-contract.md).
