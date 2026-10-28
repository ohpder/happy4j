# Repository Guidelines

## Project Structure & Module Organization

- `src/main/java/cc/superbaby/happy4j/` contains the Java 17 Spring Boot application. Keep domain and use-case code in `domain/` and `application/`; adapters belong under `infrastructure/`, `web/`, or `websocket/`.
- Protocol definitions are in `src/main/proto/` and protocol implementations in `protocol/`. Protobuf Java classes and frontend build output are generated under `build/`; do not edit generated files.
- `src/test/java/` holds JUnit tests. The Vue 3 frontend is in `frontend/src/`, with assets and ONNX models under `frontend/public/`. Design and protocol notes are in `docs/`; helper model tools are in `tools/`.

## Build, Test, and Development Commands

- `docker compose up -d mysql` starts the local MySQL dependency.
- `./gradlew clean test bootJar --no-daemon` generates protobufs, checks/builds the frontend, runs all tests, and creates the executable JAR.
- `./gradlew test` runs JUnit and the frontend check task.
- `cd frontend && npm ci && npm run check` installs frontend dependencies and runs Prettier, ESLint, and Vitest.
- `cd frontend && npm run dev` starts Vite with `/api` and `/ws` proxied to the backend.
- Run the packaged app with `java -jar build/libs/happy4j-0.0.1-SNAPSHOT.jar`.

## Coding Style & Naming Conventions

Use `.editorconfig`: four spaces and a 120-character limit for Java, two spaces for JavaScript/Vue/CSS, and LF line endings. Java types use `PascalCase`, methods and fields use `camelCase`, and constants use `UPPER_SNAKE_CASE`. Name Java tests `*Test.java` and frontend tests `*.test.js`. Run `npm run format:check` and `npm run lint` before submitting frontend changes.

## Testing Guidelines

Backend tests use JUnit 5/Spring Boot and frontend tests use Vitest. Add focused tests beside the code they cover, then run the relevant suite and the full Gradle test task. Tests should verify protocol boundaries, state transitions, and user-visible behavior rather than implementation details.

## Commit & Pull Request Guidelines

Use concise, imperative commit subjects with a conventional prefix such as `docs:`, `fix:`, `feat:`, or `test:` (for example, `fix: preserve game session on reconnect`). Pull requests should explain the behavior change, link the issue when applicable, list validation commands, and include screenshots for UI changes. Call out database migrations, configuration changes, or protocol-impacting work explicitly.

## Security & Configuration

Keep credentials and tokens in environment variables such as `HAPPY4J_DB_PASSWORD`, `HAPPY4J_CREDENTIAL_KEY`, and provider secrets; never commit real secrets. Add schema changes as numbered Flyway migrations under `src/main/resources/db/migration/` and verify them with the test suite.
