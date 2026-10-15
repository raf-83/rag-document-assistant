# Kubernetes — Secret Management

## Purpose

The RAG Document Assistant backend uses Kubernetes Secrets to store sensitive configuration outside the application source code.

The backend requires three secrets:

| Environment variable | Purpose |
|---|---|
| `APP_AI_OPENAI_API_KEY` | OpenAI API authentication |
| `APP_SECURITY_JWT_SECRET` | JWT signing and verification |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL authentication |

## Local setup

### 1. Prepare the secrets file

From the repository root, run:

```powershell
Copy-Item .\infra\k8s\backend-secret.env.example .\infra\k8s\backend-secret.env
```

Edit `backend-secret.env` and provide real values for all three variables.

The file must contain actual secret values, not placeholders such as `${OPENAI_API_KEY_DV}`.

The same ignored file is the single local source for both Kubernetes Secrets.
The PostgreSQL synchronization script maps `SPRING_DATASOURCE_PASSWORD` to the
database container's `POSTGRES_PASSWORD`, eliminating the risk of two different
passwords being configured.

### 2. Verify the Kubernetes context

```powershell
kubectl config current-context
```

Make sure the selected cluster is the intended deployment target.

### 3. Synchronize Kubernetes Secret

```powershell
.\infra\k8s\scripts\sync-backend-secret.ps1
```

The script validates the required values, displays the selected Kubernetes context and namespace, requests confirmation, and creates or updates the `backend-secret` object.

Synchronize the PostgreSQL Secret with the same context and namespace:

```powershell
.\infra\k8s\scripts\sync-postgres-secret.ps1
```

Both scripts keep generated Secret manifests in memory and never print the secret
values.

### 4. Restart the backend when secrets change

```powershell
kubectl rollout restart deployment/backend
kubectl rollout status deployment/backend --timeout=120s
```

A restart is necessary because the backend receives secrets as environment variables when its container starts.

## Kubernetes integration

The backend Deployment uses `secretKeyRef` to explicitly reference the required keys in `backend-secret`.

Non-sensitive configuration is stored separately in the `backend-config` ConfigMap.

## Security rules

- Never commit `backend-secret.env` or real credentials to Git.
- Never store real credentials in Dockerfiles or container images.
- Never print secret values in logs or terminal output.
- Do not use Base64 as a substitute for encryption.
- Limit access to Kubernetes Secrets using RBAC.
- For production environments, use encryption at rest and consider a dedicated secrets manager.
- Rotate compromised secrets immediately.

## Deployment notes

The PostgreSQL password supplied to the backend must match the password configured for the actual PostgreSQL database. Updating a Kubernetes Secret does not automatically change the database user's password.

Kubernetes Secrets are scoped to a namespace and must be provisioned separately in each target cluster.

The local script is intended for development and controlled deployments. Production deployments should use a managed secrets solution and appropriately scoped deployment credentials.
