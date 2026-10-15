# Git Workflow

## Main branch

- main

## Branch strategy

Each bigger task is developed on a separate feature branch.

Examples:
- feature/init-repo-structure
- feature/backend-bootstrap
- feature/frontend-bootstrap
- feature/auth-module

## Commit style

Use simple conventional commit style.

Examples:
- feat: initialize backend project
- feat: initialize frontend project
- docs: add mvp definition
- chore: add gitignore
- fix: validate file upload type

## Rules

- one logical change per commit
- do not commit secrets
- do not commit build artifacts
- keep main branch stable
- use English commit messages

## Early-stage workflow

For early project stages:
- implement one closed technical step at a time
- verify the application works locally before committing
- prefer small and logical commits
- keep the `main` branch stable and runnable
