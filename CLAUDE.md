# Meal Planning Service

## Project purpose

This is a portfolio project demonstrating senior full-stack engineering for
job-search purposes. The hiring audience is engineering managers at mid-size
startups evaluating senior full-stack candidates.

I (Joe) need to be able to defend every design decision in an interview.
That means **understanding > shipping fast**. Optimize for my learning, not
for your throughput.

## How to work with me

- **Propose before you implement.** When I ask for a feature, outline the
  approach in 3-8 bullets and wait for confirmation before writing code.
- **Surface tradeoffs.** When there are multiple reasonable approaches,
  name 2-3 and recommend one with a one-line "why." 
- **Teach, don't just do.** If you use a pattern, library, or AWS service
  I haven't used before, briefly explain what it does and why it fits.
- **Ask when ambiguous.** Don't guess at requirements. One clarifying
  question now beats a rewrite later.
- **Small diffs.** Prefer multiple focused commits over one large change.
- **Don't write ADRs or the README for me.** Those are my voice for the
  hiring audience. You can critique drafts I write.

## Features to reach a MVP

- **Recipe storage.** Users should be able to see a title, ingredients, and steps within the app.
- **Recipe retrieval.** When a user pastes a link to a recipe, the app should automatically 
  scrape the essential info like ingredients and steps.
- **Assigning recipes to meals.** Start with a basic three meal/day schedule and let users build
  out a week. Assume that the schedule will be configurable later.
- **Centralized ingredient view.** Each dish's ingredients should be rolled up for the week into a 
  single view.
- **User accounts.** Users need to be able to optionally login to save plans and access them on multiple devices.

## Tech stack

- **Backend:** Java 21, Spring Boot 3.x (upgrade to 4 when released), Spring Security with JWT
- **DB:** MySQL, Flyway for migrations
- **Async:** AWS SQS with a separate worker process (Java lambda)
- **Frontend:** React 18, TypeScript (strict), Vite, Yarn
- **Infra:** AWS RDS, EB
- **CI/CD:** GitHub Actions
- **Testing:** JUnit 5, Testcontainers,
  Vitest + React Testing Library for frontend

## Repo layout

- `/api` — Spring Boot REST API
- `/worker` — SQS consumer for article fetching/extraction
- `/web` — React + TypeScript frontend
- `/adr` — Architecture Decision Records (one file per decision)
- `/.github/workflows` — CI/CD pipelines

## Conventions

- **No secrets in the repo.** Use environment variables locally and
  AWS Secrets Manager / SSM Parameter Store in prod. Flag any code
  that would commit a secret.
- **Conventional Commits** for commit messages (`feat:`, `fix:`,
  `chore:`, `docs:`, `refactor:`, `test:`).
- **Branch per feature**, PR to `main`, squash merge.

## Prohibited

- No `any` types in TypeScript (use `unknown` if truly needed)
- No `@SuppressWarnings` without a comment explaining why
- No business logic in controllers — services own logic, controllers
  own HTTP concerns
- No skipped or deleted tests without explicit confirmation from me
- No new top-level dependencies without a one-line justification
- No silent catch blocks — log and rethrow, or handle deliberately

## Workflow for adding a backend feature

1. Discuss the API contract first (path, method, request/response shape)
2. Write the Flyway migration if schema changes
3. Domain/entity → repository → service → controller
4. Unit tests for the service layer
5. One Testcontainers integration test covering the happy path
6. Update OpenAPI spec if endpoints changed

## Verification commands

- API tests: `cd api && ./mvnw test`
- API integration tests: `cd api && ./mvnw verify`
- Frontend tests: `cd web && npm test`
- Frontend typecheck: `cd web && npm run typecheck`
- CloudFormation validate: `cd infra && aws cloudformation validate-template --template-body file://template.yml` (TBD — add when first template exists)
- Lint everything before commit: `make lint` (TBD — add when set up)

## Current state

Week 1 of an 8-week build plan. Foundation phase: repo skeleton,
"hello world" Spring Boot deploy,
GitHub Actions building on PR. No real features yet.

## Out of scope (don't suggest these)

- Mobile apps, browser extensions, sharing/social features
- Highlights, annotations, full-text search beyond simple ILIKE
- OpenTelemetry / distributed tracing (CloudWatch is enough for v1)
- Kubernetes / EKS
- Auth providers beyond JWT (no OAuth, no Cognito) for v1