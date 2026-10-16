# Local development

This guide starts RAG Document Assistant on a new workstation without committing credentials. It covers source development, Docker Compose, and a local Kubernetes cluster.

## Prerequisites

Install the tools required by the path you intend to use:

| Tool | Source development | Docker Compose | Kubernetes |
| --- | ---: | ---: | ---: |
| Git | Required | Required | Required |
| Java 25 | Backend only | No | No |
| Docker with Compose | Recommended for PostgreSQL and tests | Required | Required for Docker Desktop; cluster-dependent otherwise |
| Node.js 24 and npm | Frontend only | No | No |
| Tesseract 5 with English/Polish data | OCR in a source-run backend | Included in image | Included in image |
| `kubectl` and a local cluster | No | No | Required |
| PowerShell 7 or Windows PowerShell | Optional | Optional | Required by secret-sync scripts |

The backend Maven Wrapper downloads Maven 3.9.14 automatically. A separate Maven installation is not required.

## Clone the repository

```bash
git clone https://github.com/raf-83/rag-document-assistant.git
cd rag-document-assistant
git switch main
```

Release `v0.1.0` is available from the [GitHub release page](https://github.com/raf-83/rag-document-assistant/releases/tag/v0.1.0). Use the branch appropriate for development; do not move or overwrite an existing release tag.

## Runtime configuration

### Secrets

Never add real credentials to a tracked file. Root `.env` and `infra/k8s/backend-secret.env` are ignored; only their empty examples are committed.

Copy the Compose template:

```bash
cp .env.example .env
```

Set these values locally:

```dotenv
DB_PASSWORD=<strong-local-database-password>
OPENAI_API_KEY=<your-openai-api-key>
JWT_SECRET=<base64-encoded-random-secret>
CONFIG_DIR=<absolute-path-to-an-external-config-directory>
STORAGE_DIR=<absolute-path-to-an-external-document-storage-directory>
```

`JWT_SECRET` must decode from Base64. One way to generate 32 random bytes is:

```bash
openssl rand -base64 32
```

Do not paste the generated value into terminal history, documentation, screenshots, or issue reports.

### Non-sensitive backend configuration

Create the directories named by `CONFIG_DIR` and `STORAGE_DIR`. In the external configuration directory, create `application-local.yaml`:

```yaml
app:
  security:
    jwt:
      access-token-expiration-seconds: 900
    refresh-token:
      expiration-seconds: 604800
    email-verification-token:
      expiration-seconds: 86400
    password-reset-token:
      expiration-seconds: 3600

  storage:
    max-file-size-bytes: 10485760
    allowed-extensions: [pdf, jpg, jpeg, png]
    allowed-mime-types: [application/pdf, image/jpeg, image/png]
    max-pdf-pages: 50

  ocr:
    tesseract:
      language: pol+eng

  processing:
    chunking:
      max-chunk-chars: 1800
      overlap-chars: 250
      min-chunk-chars: 300
      strategy: PARAGRAPH_AWARE_V1

  ai:
    hybrid-search:
      rrf-k: 60
      semantic-weight: 0.60
      keyword-weight: 0.40
      candidate-multiplier: 3
      max-candidate-limit: 150
      max-chunks-per-document: 3
      max-document-bonus-share: 0.15
```

Compose supplies database settings, secrets, storage location, and the container's Tesseract data path through environment variables. The application classes provide defaults for the OpenAI base URL, timeout, embedding model/dimensions/batch size, chat model, output limit, and RAG context limits.

## Backend from source

### Start PostgreSQL

After preparing root `.env`, start only the database:

```bash
docker compose up -d postgres
docker compose ps postgres
```

PostgreSQL is available to the host on `127.0.0.1:5433`.

### Configure the process

Set environment variables in the shell that will run the backend. The following PowerShell example uses placeholders and a repository-adjacent runtime directory:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5433/ai_document_vault"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<same-value-as-root-dotenv>"
$env:APP_SECURITY_JWT_SECRET = "<base64-encoded-random-secret>"
$env:APP_AI_OPENAI_API_KEY = "<your-openai-api-key>"
$env:APP_STORAGE_LOCAL_BASE_PATH = "<absolute-document-storage-path>"
$env:APP_OCR_TESSERACT_DATA_PATH = "<absolute-path-to-tessdata>"
$env:APP_OCR_TESSERACT_LANGUAGE = "pol+eng"
$env:SPRING_CONFIG_ADDITIONAL_LOCATION = "file:<absolute-config-directory>/"
$env:SPRING_PROFILES_ACTIVE = "local"
```

Use the platform-correct `tessdata` path. The container image uses `/usr/share/tesseract-ocr/5/tessdata`; a native installation may differ.

### Test and run

From `backend`:

```powershell
.mvnw.cmd test
.mvnw.cmd spring-boot:run
```

On Unix-like systems:

```bash
./mvnw test
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`. Check it with:

```bash
curl http://localhost:8080/api/health
```

Some integration tests use Testcontainers, so keep Docker running while executing the full backend suite.

## Frontend from source

Copy the frontend environment example:

```bash
cd frontend
cp .env.example .env.local
npm ci
npm run dev
```

`.env.local` should contain:

```dotenv
BACKEND_API_URL=http://localhost:8080
```

Open `http://localhost:3000`. Browser calls go through the Next.js BFF, which contacts the backend URL server-side.

Before submitting frontend changes, run:

```bash
npm run lint
npm run typecheck
npm run build
```

## Full stack with Docker Compose

With `.env`, `application-local.yaml`, and both external directories prepared:

```bash
docker compose config --quiet
docker compose up --build
```

Endpoints:

- frontend: `http://localhost:3001`;
- backend: `http://localhost:8080`;
- PostgreSQL: `127.0.0.1:5433`.

Useful lifecycle commands:

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend
docker compose down
```

`docker compose down` keeps the named PostgreSQL volume. Adding `--volumes` deletes local database data and should only be used intentionally.

## Local Kubernetes

The checked-in manifests deploy release `0.1.0` from public GHCR packages into a local cluster. They create ClusterIP services and no Ingress, so they do not expose public hosting.

The image contains English and Polish Tesseract data, but the checked-in Kubernetes ConfigMap does not set `APP_OCR_TESSERACT_LANGUAGE`. PDF extraction is configured as shipped. Treat JPEG/PNG OCR as requiring a future manifest update that supplies a language such as `pol+eng`; do not rely on an imperative one-off override as production configuration.

### Select and verify a cluster

```powershell
kubectl config get-contexts
kubectl config current-context
kubectl version --client
```

Verify the context carefully before synchronizing Secrets or applying manifests.

### Prepare the namespace and Secrets

```powershell
kubectl create namespace rag-document-assistant
Copy-Item .infrak8sackend-secret.env.example .infrak8sackend-secret.env
```

Fill the ignored `backend-secret.env` with:

```dotenv
SPRING_DATASOURCE_PASSWORD=<strong-local-database-password>
APP_AI_OPENAI_API_KEY=<your-openai-api-key>
APP_SECURITY_JWT_SECRET=<base64-encoded-random-secret>
```

Synchronize both Secret objects:

```powershell
.infrak8sscriptssync-backend-secret.ps1 -Namespace rag-document-assistant
.infrak8sscriptssync-postgres-secret.ps1 -Namespace rag-document-assistant
```

Each script displays the exact operation, selected context, namespace, and Secret name, then asks `(y/n)`. Only `y` or `Y` proceeds; Enter, `n`/`N`, or any other input cancels. Secret values are neither displayed nor written to a generated file.

### Apply and observe

```powershell
kubectl apply -k .infrak8s -n rag-document-assistant
kubectl get pods -n rag-document-assistant
kubectl rollout status deployment/postgres -n rag-document-assistant --timeout=180s
kubectl rollout status deployment/backend -n rag-document-assistant --timeout=180s
kubectl rollout status deployment/frontend -n rag-document-assistant --timeout=180s
```

Expose the frontend to the current workstation:

```powershell
kubectl port-forward -n rag-document-assistant service/frontend 13000:3000
```

Open `http://localhost:13000`. Stop the port-forward with Ctrl+C.

See [Kubernetes deployment](../infra/k8s/README.md) and [Secret management](../infra/k8s/SECRETS.md) for operational details.

## Email delivery limitation

`AuthMailServiceImpl` does not send email in `v0.1.0`. It logs the user ID and email address, but never the raw token, and contains a pending mail-provider integration. Registration therefore creates a `PENDING_VERIFICATION` account, but the verification link is not delivered by the application.

For a controlled local demonstration only, an operator may activate a known test account directly in the local database:

```bash
docker compose exec postgres psql -U postgres -d ai_document_vault -c "UPDATE users SET status = 'ACTIVE', email_verified_at = NOW() WHERE email = 'demo@example.invalid' AND status = 'PENDING_VERIFICATION';"
```

Replace the example address with the exact local test account. This is a demonstration workaround, not a production verification design. A production deployment must integrate a mail provider, deliver single-use verification links, preserve expiry and revocation rules, and retain appropriate audit and abuse controls.

Password-reset emails have the same delivery limitation.

## Troubleshooting

### Backend fails during configuration binding

- Confirm that every secret is non-empty and the JWT secret is valid Base64.
- Confirm `SPRING_CONFIG_ADDITIONAL_LOCATION` ends in `/` and points to the directory containing `application-local.yaml`.
- Compare non-sensitive settings with `infra/k8s/backend-configmap.yaml`.
- Check that the configured storage directory exists and is writable.

### Database or Flyway errors

```bash
docker compose ps postgres
docker compose logs postgres
```

Confirm the backend and PostgreSQL use the same password and the JDBC URL points to port 5433 for a host process or to `postgres:5432` inside Compose/Kubernetes. Do not manually edit a migrated schema; inspect Flyway history and correct configuration first.

### OCR errors

- Verify `tesseract --version`.
- Verify the configured `tessdata` directory exists.
- Verify English and Polish language data are installed if using `pol+eng`.
- Check backend logs for the extraction method and failure state; do not upload sensitive documents to public issue trackers.

### Document remains in processing

Inspect backend logs for the document ID and the latest state. Processing uses in-process asynchronous events, so a process restart can interrupt work. There is no durable queue or automatic recovery worker in this release.

### Semantic search returns fewer results

The document can be `READY` before all chunk embeddings are `READY`. Inspect logs for OpenAI or embedding failures. Keyword results may still be available. Confirm the API key and outbound network access without printing the key.

### Kubernetes pods do not become ready

```powershell
kubectl get pods -n rag-document-assistant
kubectl describe pod -n rag-document-assistant <pod-name>
kubectl logs -n rag-document-assistant deployment/backend
kubectl get events -n rag-document-assistant --sort-by=.lastTimestamp
```

Check image access, PVC binding, Secret existence, and health probes. Read Secret key names with metadata commands if necessary, but never decode or print values during routine diagnosis.

### A changed Secret appears ignored

Environment variables are read when a container starts. Restart the affected deployment after an intentional Secret update:

```powershell
kubectl rollout restart deployment/backend -n rag-document-assistant
kubectl rollout status deployment/backend -n rag-document-assistant --timeout=180s
```

Changing the Kubernetes Secret does not change the password already stored for the PostgreSQL database user.
