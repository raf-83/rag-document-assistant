# Local Kubernetes deployment

The manifests in this directory run RAG Document Assistant `v0.1.0` in a local Kubernetes cluster. They use public, versioned images from GitHub Container Registry:

- `ghcr.io/raf-83/rag-document-assistant-backend:0.1.0`;
- `ghcr.io/raf-83/rag-document-assistant-frontend:0.1.0`.

The application version tag is treated as immutable. The container workflow also publishes `sha-<full-commit-SHA>` tags for source-level traceability.

This is a local deployment: services use `ClusterIP`, no Ingress is defined, and access uses `kubectl port-forward`. Public source and images do not make the running application publicly hosted.

## Resources

`kustomization.yaml` composes:

- PostgreSQL with pgvector, a ConfigMap, Secret reference, 2 GiB persistent claim, Deployment, and Service;
- the backend with non-sensitive ConfigMap, Secret references, 2 GiB document-storage claim, Deployment, Service, and health probes;
- the frontend with a ConfigMap, Deployment, Service, and liveness/readiness probes.

Each Deployment has one replica. This topology demonstrates declarative local deployment and persistent storage; it is not a high-availability architecture.

## Prerequisites

- Docker and a local Kubernetes cluster, such as Docker Desktop, Rancher Desktop, kind, or minikube;
- `kubectl` configured for that cluster;
- PowerShell for the Secret synchronization scripts;
- anonymous access to both `0.1.0` GHCR images.

Confirm the target before making changes:

```powershell
kubectl config get-contexts
kubectl config current-context
kubectl version --client
```

## Prepare Secrets

Copy the ignored local template:

```powershell
Copy-Item .\infra\k8s\backend-secret.env.example .\infra\k8s\backend-secret.env
```

Populate all three variables locally:

```dotenv
SPRING_DATASOURCE_PASSWORD=<strong-local-database-password>
APP_AI_OPENAI_API_KEY=<your-openai-api-key>
APP_SECURITY_JWT_SECRET=<base64-encoded-random-secret>
```

The same database password is mapped into separate `backend-secret` and `postgres-secret` objects. See [Secret management](SECRETS.md) for constraints and behavior.

## Deploy

Create the namespace once:

```powershell
kubectl create namespace rag-document-assistant
```

Synchronize the two Secrets:

```powershell
.\infra\k8s\scripts\sync-backend-secret.ps1 -Namespace rag-document-assistant
.\infra\k8s\scripts\sync-postgres-secret.ps1 -Namespace rag-document-assistant
```

Both scripts display the selected context, namespace, operation, and Secret name before asking `Continue? (y/n)`. Only `y` or `Y` approves. Enter, `n`/`N`, and every other response decline without applying the Secret. Values are never displayed, and generated manifests remain in memory.

Apply the remaining resources:

```powershell
kubectl apply -k .\infra\k8s -n rag-document-assistant
kubectl get pods -n rag-document-assistant
```

Wait for readiness:

```powershell
kubectl rollout status deployment/postgres -n rag-document-assistant --timeout=180s
kubectl rollout status deployment/backend -n rag-document-assistant --timeout=180s
kubectl rollout status deployment/frontend -n rag-document-assistant --timeout=180s
```

Expose the frontend locally:

```powershell
kubectl port-forward -n rag-document-assistant service/frontend 13000:3000
```

Open `http://localhost:13000`. Stop the port-forward with Ctrl+C.

## Configuration boundaries

Non-sensitive backend settings are in `backend-configmap.yaml`, including:

- PostgreSQL service URL and username;
- private storage mount path;
- token expiration intervals;
- upload types and size/page limits;
- chunking strategy and sizes;
- hybrid search weights and candidate limits.

The frontend ConfigMap points the server-side BFF to `http://backend:8080` and disables the Secure cookie flag for local HTTP. A real HTTPS deployment must set the appropriate cookie and trusted-proxy configuration.

Sensitive values exist only in locally created Secret objects. Do not insert them into ConfigMaps, manifests, Dockerfiles, or documentation.

The backend image sets the Tesseract data path and installs English and Polish language files. The current ConfigMap does not set `APP_OCR_TESSERACT_LANGUAGE`, so image OCR is not fully configured by the checked-in manifests. Add that non-sensitive setting in a reviewed manifest change before relying on JPEG/PNG OCR; PDF text extraction is unaffected.

## Persistence

- `postgres-pvc.yaml` requests 2 GiB for database data.
- `backend-storage-pvc.yaml` requests 2 GiB for original uploaded files.

Both use `ReadWriteOnce` and the cluster's default storage class. Deleting a Deployment does not delete its claim. Review cluster reclaim behavior before deleting PVCs.

The database and document files are separate persistence domains. Backing up one without the other does not produce a complete application backup.

## Image publishing

`.github/workflows/container-images.yml`:

- validates both Dockerfiles for relevant Pull Requests and manual runs without publishing;
- publishes `main` and full-SHA tags for relevant pushes to `main`;
- publishes semantic-version, `latest`, and SHA tags for a `vMAJOR.MINOR.PATCH` tag;
- builds `linux/amd64` and `linux/arm64` manifests;
- uses `GITHUB_TOKEN`, OCI labels, pinned action commits, and provenance attestations for a public repository.

GitHub Actions executes the build; GHCR stores images; a GitHub Release records the version/tag; Kubernetes deploys an already published image. These steps are related but not interchangeable.

For private packages, configure a read-only registry credential and `imagePullSecrets`; never put a registry token in the repository. The supplied `v0.1.0` manifests assume public packages and do not define an image pull Secret.

## Automated workflow

The repository workflow exposes independent stages:

```powershell
.\scripts\release-workflow.ps1 -Stage Validate -Branch <working-branch>
.\scripts\release-workflow.ps1 -Stage Submit -Branch <working-branch>
.\scripts\release-workflow.ps1 -Stage Release -Version <new-version>
.\scripts\release-workflow.ps1 -Stage Deploy -Version <version> -KubeContext <context-name>
.\scripts\release-workflow.ps1 -Stage Verify -KubeContext <context-name>
```

Every mutating Git, GitHub, or Kubernetes operation has the shared strict `(y/n)` gate. See [Release workflow](../../docs/release-workflow.md).

Release `v0.1.0` and its tag already exist and must not be recreated or changed.

## Updating to a future version

For a later release:

1. merge tested changes to `main`;
2. create a new semantic version and GitHub Release;
3. wait for both GHCR images and attestations;
4. update both image references in a separate Pull Request;
5. validate rendered manifests before deploying.

Stable manifests use a full version, never `main` or `latest`. An environment requiring stronger immutability can pin an image digest after verifying it in GHCR.

## Diagnostics

```powershell
kubectl get deployments,pods,services,pvc -n rag-document-assistant
kubectl get events -n rag-document-assistant --sort-by=.lastTimestamp
kubectl logs -n rag-document-assistant deployment/postgres
kubectl logs -n rag-document-assistant deployment/backend
kubectl logs -n rag-document-assistant deployment/frontend
```

Health checks performed by the workflow include:

- `pg_isready` in the PostgreSQL pod;
- frontend `/api/health/live`;
- frontend `/api/health`, which reaches the backend;
- backend `/api/health`.

If a Secret changes intentionally, restart the consuming Deployment because environment variables are read at container start. Updating `postgres-secret` does not change the password of an already initialized PostgreSQL user.
