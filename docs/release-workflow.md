# Release workflow

`scripts/release-workflow.ps1` coordinates local validation, Pull Request submission, GitHub release creation, GHCR publication checks, and deployment to a selected local Kubernetes cluster. These are separate concerns and remain separate stages.

Release `v0.1.0` already exists. Do not rerun its `Release` stage or move its tag. This document describes the implemented, release-specific workflow for auditability. Review its defaults, Pull Request text, version, and image references before adapting it to a later release.

## Concepts

| Concept | Role |
| --- | --- |
| GitHub Actions | Hosted service that executes repository automation |
| Workflow | One YAML definition, such as backend tests or container-image build/publish |
| GHCR | Registry that stores the backend and frontend OCI images |
| GitHub Release | Versioned repository record associated with a Git tag |
| Kubernetes Deploy | Applies configuration and a chosen image version to a selected cluster and namespace |

A successful Actions run does not create a deployment. A GHCR image is not a GitHub Release. The release tag can trigger image publication, while deployment is an explicit local operation after the images exist.

## Entry point

From the repository root:

```powershell
.\scripts\release-workflow.ps1 -Stage <All|Validate|Submit|Release|Deploy|Verify> -Branch <working-branch> -Version <semantic-version> -Namespace rag-document-assistant -KubeContext <context-name> -CommitMessage "<message>"
```

For intelligent submission from the current branch, the minimal command is:

```powershell
.\scripts\release-workflow.ps1 -Stage Submit
```

`Submit` infers the current branch when `-Branch` is omitted, refuses `main`/`master`, and still verifies an explicitly supplied branch. For the other stages, pass the actual working branch and intended version rather than relying on release-specific defaults. Parameters do not update Kubernetes image references; those remain version-pinned in the manifests.

## Stages

| Stage | What it does | External changes |
| --- | --- | --- |
| `Validate` | Checks prerequisites and expected branch; checks whitespace and common secret patterns; runs backend tests, frontend lint/type/build, Compose validation, Kustomize rendering, and Actionlint | None |
| `Submit` | Uses Codex CLI or manual fallback to prepare a bounded commit/PR proposal, creates a local commit when needed, pushes the branch, opens or resumes a Pull Request to `main`, waits for required PR workflow runs on the exact head commit, and offers squash merge | Git and GitHub, each behind a confirmation |
| `Release` | Resolves identical local/remote `main`, waits for required main checks, creates a GitHub Release and tag if absent, waits for image publication, verifies anonymous image access and provenance | GitHub release/tag; no Kubernetes change |
| `Deploy` | Verifies public release images, resolves the selected context, creates/updates the namespace and both Secrets, applies Kustomize resources, then invokes verification | Selected Kubernetes context and namespace, behind confirmations |
| `Verify` | Waits for all three rollouts, checks PostgreSQL readiness and frontend/backend health, and prints the port-forward command | Read-only checks against the cluster |
| `All` | Runs `Validate`, `Submit`, `Release`, and `Deploy`; `Deploy` calls `Verify` | Union of the explicitly approved stage effects |

Stages can be resumed individually. State-aware checks reuse an existing Pull Request or release and avoid repeating work that is already complete.

## Confirmation contract

All mutating operations use `scripts/common/confirmation.ps1`.

Before `Continue? (y/n)`, the script displays:

- the exact operation;
- the repository, branch, remote, Pull Request, commit, or tag when relevant;
- the Kubernetes context and namespace for cluster operations;
- the Secret object name for Secret synchronization.

Input handling is deliberately strict:

- `y` or `Y`: approve;
- `n` or `N`: decline;
- Enter without text: decline;
- every other value: decline.

A declined answer throws an operation-cancelled error before the guarded command runs. Long phrases such as `COMMIT`, `PUSH`, `PR`, `MERGE`, `RELEASE v...`, or `DEPLOY ...` are not accepted or required.

The guarded operations include:

- staging only the accepted proposal files and committing them;
- pushing the selected branch;
- creating a Pull Request;
- squash merging after required checks pass;
- creating a GitHub Release and tag;
- repeating an anonymous GHCR availability check;
- applying the Kubernetes deployment;
- synchronizing `backend-secret` and `postgres-secret`.

The regression coverage for approval and denial is in `scripts/tests/confirmation-regression.ps1`. `scripts/tests/submit-proposal-regression.ps1` covers proposal parsing, manual fallback, editing, regeneration, cancellation, safe path selection, untracked and ignored files, exact staging, and resumable state. Additional workflow regression scripts protect exact PR-head and `main`-history checks.

## Validate

`Validate` requires Git, Docker with a running engine, `kubectl`, Node.js/npm, `robocopy`, and a compatible PowerShell. Frontend dependencies and build output are isolated in a temporary copy. The stage:

1. verifies the current branch;
2. confirms local Secret files are not tracked;
3. runs `git diff --check` for unstaged and staged changes;
4. scans tracked and untracked source filenames for common credential formats without printing matches;
5. runs `backend/mvnw.cmd -B test`;
6. installs frontend dependencies in a temporary copy, then runs lint, typecheck, and build;
7. validates Compose with non-secret placeholder values;
8. renders `infra/k8s` with `kubectl kustomize`;
9. runs Actionlint against GitHub workflow YAML.

Validation does not release or deploy.

## Submit

`Submit` uses authenticated GitHub CLI access and optionally Codex CLI. It first derives candidate paths from tracked diffs, the index, and untracked files reported by Git with standard ignore rules applied. Known local reports, local Secret files, environment files, private-key files, paths outside the repository, and files containing common credential formats are excluded before any content is prepared for Codex. Secret values are never displayed.

When Codex CLI is available, the script:

1. builds an in-memory, size-limited context only from the filtered candidates;
2. redacts credential-like assignments as an additional safeguard;
3. runs `codex exec` in an isolated temporary directory with the shell tool disabled, `--sandbox read-only`, `--ephemeral`, and `--ignore-user-config`;
4. requests a JSON Schema-constrained response through `--output-schema` and reads only `--output-last-message`;
5. accepts only a file list, commit message, Pull Request title, and Pull Request body with `Summary` and `Tests` sections;
6. validates every returned path again against the current candidate set.

The prompt supplies the change context directly, so Codex is not started in the repository and is instructed not to inspect the filesystem or propose commands. The script never evaluates model output as PowerShell or shell input. If Codex is missing, exits unsuccessfully, or returns invalid JSON, Submit switches to manual proposal entry.

The displayed proposal can be accepted (`a`), edited (`e`), regenerated (`r`), or cancelled (`c`). This proposal menu does not itself change repository state. After acceptance, the existing strict `(y/n)` gates remain separate for:

- staging exactly the selected paths and creating the commit;
- pushing the branch;
- creating the Pull Request;
- squash merging after CI.

Pre-staged paths that are not selected are removed from the index without changing their working-tree content. Submit verifies that the final staged set exactly equals the accepted file list; it does not use `git add --all`. After a successful commit, the accepted PR metadata is stored under the repository's private Git metadata directory. A rerun for the same branch and HEAD can resume after an interrupted commit or push without regenerating inconsistent PR text; state for another branch or commit is ignored.

The stage waits for these workflow files on the exact Pull Request head SHA:

- `.github/workflows/backend-ci.yml`;
- `.github/workflows/container-images.yml`.

It refuses to merge when a required run is missing, pending beyond the timeout, failed, or associated with a different head commit. It rechecks the PR head and CI immediately before calling GitHub CLI with `--match-head-commit`. The merge strategy is squash.

## Release and container publication

`Release` requires local `main`, `origin/main`, and the actual remote `main` to identify the same commit and requires a clean working tree. It waits for the backend and container workflows on that commit before offering release creation.

Creating a version tag such as `v0.1.0` triggers `.github/workflows/container-images.yml`. For a version tag, the workflow publishes:

- backend and frontend images;
- semantic-version tags such as `0.1.0` and `0.1`;
- `latest`;
- a full commit-SHA tag;
- `linux/amd64` and `linux/arm64` manifests.

Relevant pushes to `main` publish `main` and SHA tags. Pull Requests and manual dispatch validate image builds without publishing them. The workflow uses the short-lived `GITHUB_TOKEN`; public repositories can receive OCI provenance attestations.

The Release stage never changes package visibility. If a newly published GHCR package is private, an owner must intentionally change its settings before anonymous deployment can continue.

## Deploy and Verify

`Deploy` targets the explicitly supplied `KubeContext` or the current context and the supplied namespace. Before mutation it prints both values and the affected resource categories. It then invokes the two Secret synchronization scripts, which add their own confirmations.

Secret values:

- come from ignored `infra/k8s/backend-secret.env`;
- are validated without being printed;
- are converted to Secret manifests in memory;
- are piped directly to `kubectl`;
- are cleared from script variables in cleanup paths.

After applying `infra/k8s`, `Verify` waits for PostgreSQL, backend, and frontend Deployments; checks `pg_isready`; and checks frontend liveness, frontend-to-backend readiness, and backend health.

The manifests use ClusterIP services and no Ingress. Access is local:

```powershell
kubectl --context=<context-name> --namespace=rag-document-assistant port-forward service/frontend 13000:3000
```

Then open `http://localhost:13000`.

## Supporting documentation

- [Kubernetes deployment](../infra/k8s/README.md)
- [Kubernetes Secret management](../infra/k8s/SECRETS.md)
- [Local development](local-development.md)
