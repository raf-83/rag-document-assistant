# Kubernetes Secret management

RAG Document Assistant keeps sensitive runtime configuration outside source control. Two PowerShell scripts create or update Kubernetes Secret objects from one ignored local file.

## Required values

| Local variable | Kubernetes use |
| --- | --- |
| `APP_AI_OPENAI_API_KEY` | OpenAI API authentication in `backend-secret` |
| `APP_SECURITY_JWT_SECRET` | JWT signing and validation in `backend-secret` |
| `SPRING_DATASOURCE_PASSWORD` | Backend database authentication in `backend-secret`; mapped to `POSTGRES_PASSWORD` in `postgres-secret` |

The JWT secret must be a sufficiently strong Base64-encoded random value. Base64 is an encoding, not encryption.

## Prepare the ignored file

From the repository root:

```powershell
Copy-Item .\infra\k8s\backend-secret.env.example .\infra\k8s\backend-secret.env
```

Fill every value locally:

```dotenv
SPRING_DATASOURCE_PASSWORD=<strong-local-database-password>
APP_AI_OPENAI_API_KEY=<your-openai-api-key>
APP_SECURITY_JWT_SECRET=<base64-encoded-random-secret>
```

The scripts reject missing, empty, duplicate, unexpected, or unresolved placeholder values. The local file is ignored by Git and must remain untracked.

## Verify the target

```powershell
kubectl config current-context
kubectl get namespace
```

Specify context and namespace explicitly when multiple clusters are configured:

```powershell
.\infra\k8s\scripts\sync-backend-secret.ps1 -KubeContext <context-name> -Namespace rag-document-assistant
.\infra\k8s\scripts\sync-postgres-secret.ps1 -KubeContext <context-name> -Namespace rag-document-assistant
```

## Confirmation behavior

Before changing a Secret, each script displays:

- the operation;
- Kubernetes context;
- namespace;
- Secret name.

It then asks `Continue? (y/n)`:

- `y` or `Y` applies the Secret;
- `n` or `N` cancels;
- Enter cancels;
- any other answer cancels.

Cancellation occurs before `kubectl apply`. The scripts never display secret values. They build generated manifests in memory, pipe them directly to `kubectl`, and clear sensitive variables in cleanup paths.

## Deployment integration

`backend-deployment.yaml` uses explicit `secretKeyRef` entries for backend values. `postgres-deployment.yaml` reads its database password from `postgres-secret`. Non-sensitive settings are kept separately in ConfigMaps.

Kubernetes Secrets are namespace-scoped. Create them independently in every intended cluster and namespace.

## After an intentional update

The backend receives values as environment variables at container startup, so restart it after changing its Secret:

```powershell
kubectl rollout restart deployment/backend -n rag-document-assistant
kubectl rollout status deployment/backend -n rag-document-assistant --timeout=180s
```

Changing `postgres-secret` does not update the password stored for an already initialized PostgreSQL database user. Coordinate database password rotation separately before restarting consumers.

## Security rules

- Never commit `backend-secret.env`, real credentials, generated Secret YAML, or decoded Secret data.
- Never place credentials in Dockerfiles, images, ConfigMaps, documentation, terminal transcripts, or issue reports.
- Limit Secret and deployment access with Kubernetes RBAC.
- Enable appropriate encryption at rest for the target cluster.
- Rotate credentials after suspected exposure.
- Prefer a managed secrets solution and narrowly scoped deployment identity for a production environment.

These local scripts support development and controlled local deployment; they are not a complete production secrets-management system.
