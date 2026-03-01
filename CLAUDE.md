# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**steuerauszug-generator** is a monorepo containing two packages:
- **`frontend/`** — Angular 21 SPA (TypeScript, SCSS, standalone components)
- **`backend/`** — Spring Boot 3.5 REST API (Kotlin, Maven, JUnit 5)

## Commands

### Frontend (`cd frontend`)
```bash
npm start          # Dev server at http://localhost:4200 (hot reload)
npm run build      # Production build → dist/
npm test           # Karma/Jasmine tests (headless Chrome)
ng test --include="**/foo.spec.ts"  # Run a single test file
```

### Backend (`cd backend`)
```bash
./mvnw spring-boot:run     # Run app (default port 8080)
./mvnw test                # All JUnit 5 tests
./mvnw test -Dtest=FooTest # Run a single test class
./mvnw clean package       # Build JAR → target/
```

## Git Workflow

After completing any meaningful unit of work, commit and push to GitHub immediately so progress is never lost.

- Write concise, descriptive commit messages in the imperative mood (e.g. `add user login endpoint`, `fix null pointer in tax calculator`)
- Commit logical units of work — don't batch unrelated changes into one commit
- Always push after committing: `git push`
- Prefer small, focused commits over large ones

## Architecture

### Frontend
- Uses Angular 21 **standalone component** API (no NgModules). Every component/directive/pipe declares its own `imports` array.
- Routing defined in `src/app/app.routes.ts`, bootstrapped via `src/app/app.config.ts`.
- TypeScript strict mode + strict Angular template checks are enabled (`tsconfig.json`).
- Global styles in `src/styles.scss`; component styles use SCSS with view encapsulation.

### Backend
- Spring Boot auto-configuration with package root `com.steuerauszug.backend`.
- Main entry: `BackendApplication.kt` — standard `@SpringBootApplication` + `runApplication<>()`.
- Kotlin `all-open` compiler plugin is applied (required for Spring proxies).
- Jackson Kotlin module is on the classpath for JSON ↔ data class serialization.
