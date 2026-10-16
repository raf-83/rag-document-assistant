# Git workflow

`main` is the stable integration branch. Each logical change is prepared on a short-lived branch and submitted through a Pull Request.

## Branches

Use a descriptive prefix and topic:

- `feature/<topic>` for product behavior;
- `fix/<topic>` for corrections;
- `docs/<topic>` for documentation;
- `chore/<topic>` for maintenance.

Create a branch from an up-to-date `main`:

```bash
git switch main
git pull --ff-only origin main
git switch -c docs/example-topic
```

## Commits

Use an English conventional-style subject, for example:

- `feat: add document chat`;
- `fix: preserve owner filter in search`;
- `docs: describe local deployment`;
- `chore: update build tooling`.

Keep each commit coherent. Do not commit credentials, local environment files, generated Secret manifests, document uploads, dependency directories, or build output.

## Validation and review

Before opening a Pull Request:

1. inspect `git status` and the complete diff;
2. run tests and quality gates appropriate to the changed area;
3. run `git diff --check`;
4. confirm the diff contains only intended files;
5. scan for credentials and sensitive local data.

Pull Requests target `main`. Required GitHub workflows must pass for the exact current head commit. The automated submission workflow uses squash merge so `main` receives one reviewed commit for the change.

## Releases

Version tags use `vMAJOR.MINOR.PATCH` and are created only from a verified `main` commit. A release tag can trigger GHCR image publication, but publishing, GitHub Release creation, and Kubernetes deployment remain separate operations.

Do not move or overwrite an existing release tag. See [Release workflow](release-workflow.md).
