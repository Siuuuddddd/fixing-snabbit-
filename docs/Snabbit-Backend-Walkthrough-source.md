# Snabbit Backend Walkthrough — source manifest

This Markdown file is the reviewable source manifest for the generated Word document. The full authored prose, tables, diagrams and code-derived method inventory live in `build_snabbit_walkthrough.py`; rerun that script with the bundled document runtime to reproduce the DOCX.

## Generated deliverables

- `Snabbit-Backend-Walkthrough.docx`
- `walkthrough-assets/*.png` (architecture and sequence diagrams)

## Code evidence read by the generator

- **api-gateway**: 5 main Kotlin files; 3 test files
- **user-service**: 123 main Kotlin files; 18 test files
- **booking-service**: 109 main Kotlin files; 14 test files
- **worker-service**: 74 main Kotlin files; 10 test files
- **wallet-service**: 86 main Kotlin files; 13 test files
- **support-service**: 28 main Kotlin files; 3 test files
- **notification-service**: 57 main Kotlin files; 7 test files
- **platform-common**: 2 main Kotlin files; 2 test files

## Repository sources also reviewed

- Root Gradle settings/build files, Dockerfile, compose.yaml, compose.apps.yaml, .env.example and .dockerignore
- README.md, DEPLOY.md and every existing Markdown design/API/operations document under docs/
- Every Flyway migration under each state-owning service
- All Kotlin tests, including Testcontainers concurrency, ledger, webhook and persistence suites

## Rebuild

```powershell
& $BUNDLED_PYTHON docs/build_snabbit_walkthrough.py
```

The document intentionally contains environment variable names only and excludes secrets, tokens, OTPs, phone numbers and provider credentials.
