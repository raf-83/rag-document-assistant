# Release workflow: PR, container publishing, and Kubernetes deployment

The repository uses one PowerShell entry point for the release process:

```powershell
.\scripts\release-workflow.ps1 -Stage All
```

The command is interactive. It performs safe validation automatically and stops
for a `(y/n)` confirmation before every operation that changes Git, GitHub, GHCR
or the Kubernetes cluster. Only `y` or `Y` approves an operation; Enter, `n`,
`N` and every other response cancel it. A cancelled or interrupted run can be
resumed with a specific stage:

```powershell
.\scripts\release-workflow.ps1 -Stage Validate
.\scripts\release-workflow.ps1 -Stage Submit
.\scripts\release-workflow.ps1 -Stage Release
.\scripts\release-workflow.ps1 -Stage Deploy -KubeContext docker-desktop
.\scripts\release-workflow.ps1 -Stage Verify -KubeContext docker-desktop
```

## Responsibilities

### PowerShell and the local machine

The script:

- checks prerequisites, the expected branch and Git whitespace;
- scans source filenames for common secret formats without printing values;
- runs backend tests, frontend lint/typecheck/build, Compose validation,
  `kubectl kustomize` and `actionlint`;
- creates the commit, pushes the feature branch and creates the Pull Request only
  after separate confirmations;
- waits for PR checks and asks again before squash merge;
- asks before creating release `v0.1.0` (the tag triggers image publication);
- checks that both GHCR images are anonymously downloadable and verifies their
  provenance attestations;
- asks before changing the selected Kubernetes cluster, synchronizes secrets via
  dedicated scripts, applies the manifests and verifies the rollout and health
  endpoints.

PowerShell never prints secret values. The secret synchronization scripts build
Secret manifests in memory and send them directly to `kubectl`.

### GitHub Actions

- `backend-ci.yml` runs backend unit tests for the Pull Request and `main`.
- `container-images.yml` builds both Dockerfiles for a Pull Request without
  publishing anything.
- A push to `main` publishes `main` and immutable SHA tags.
- Tag `v0.1.0`, created together with the GitHub Release, publishes `0.1.0`,
  `0.1`, `latest` and SHA tags for `linux/amd64` and `linux/arm64`.
- A public repository receives OCI provenance attestations.

### Codex CLI

Codex reviews the proposed local changes and test results, invokes individual
stages when requested, and stops for the owner's approval before commit, push,
PR, merge, release/publication and deployment. The deterministic work remains in
the scripts and GitHub Actions, so another person does not need a Codex session
to reproduce the release.

## One-time preparation

For the initial `v0.1.0` release in a newly created public repository, push the
single clean `main` commit, wait for both workflows on that exact commit, and run
`release-workflow.ps1 -Stage Release`. The Release stage resolves its target only
from the matching local `main`, `origin/main` and actual remote `main`; it does not
depend on historical Pull Request metadata.

Required tools:

- Git, Docker with a running engine, Node.js/npm and `kubectl`;
- GitHub CLI (`gh`) authenticated with access to the repository;
- a local Kubernetes cluster;
- Windows PowerShell 5.1 or newer.

Prepare ignored local files (never commit them):

```powershell
Copy-Item .\infra\k8s\backend-secret.env.example .\infra\k8s\backend-secret.env
```

The two synchronization scripts use this single source to create separate backend
and PostgreSQL Secrets with the same database password. See
[Kubernetes secret documentation](../infra/k8s/SECRETS.md).

Before creating `v0.1.0`, the repository must be public so the release workflow
can create attestations. GitHub creates newly published container packages as
private. After the first successful publication, open both package settings and
change their visibility to **Public**. The script deliberately stops until an
anonymous manifest check succeeds. This is the only one-time GitHub UI step and
is necessary for deployment on a new computer without a registry token.

## Approval gates

Every gate uses the same `Continue? (y/n)` question. Before asking, the script
shows the exact operation and its relevant target: repository and branch for Git
and GitHub changes, or Kubernetes context, namespace and Secret name for cluster
changes. Secret values are never displayed.

Separate gates protect staging and committing, pushing, Pull Request creation,
squash merge, release/tag creation, deployment, and both Secret synchronization
operations.

The script is state-aware for normal retries: it skips an already-created commit,
an up-to-date push, an existing PR and an existing release. If package visibility
blocks the first run, set both packages to Public and approve the next `(y/n)`
gate; the script repeats an anonymous read check without changing package
settings itself.

## After deployment

The script waits for PostgreSQL, backend and frontend rollouts. It then checks
PostgreSQL readiness, the frontend liveness endpoint, frontend readiness through
the backend, and the backend health endpoint. To open the application:

```powershell
kubectl --namespace=rag-document-assistant port-forward service/frontend 13000:3000
```

Visit `http://localhost:13000`.
