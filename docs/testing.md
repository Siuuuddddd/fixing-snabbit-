# Testing and continuous integration

Run the complete suite locally with:

```powershell
./gradlew.bat test --no-configuration-cache
```

Unit tests run without infrastructure. PostgreSQL integration and concurrency tests use Testcontainers and run when Docker is available. They cover atomic slot contention, append-only wallet accounting, finance-refund auditing, Plus-pass persistence, worker compatibility and availability, and durable notification storage.

GitHub Actions runs `./gradlew build` on every push and pull request using Java 21. The workflow first verifies Docker, then fails if any JUnit test fails, errors or skips. This prevents an unavailable Docker daemon from quietly turning the database safety tests into a green build. Failed runs retain JUnit XML reports for seven days.

Dependabot checks Gradle and GitHub Actions dependencies weekly. Dependency updates still require this same CI gate before merge.

