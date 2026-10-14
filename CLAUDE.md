# Duello Quiz — Project Context for Claude Code

Read this file fully at the start of every session. It is the single entry point:
product scope, tech stack, team, git workflow and release process. Details live in
`docs/mvp-spec.md` (product rules) and `docs/api/` (contracts).

## Working with the owner

- Owner: Sabit, Android developer. He is the tech lead; you work for him.
- Talk to Sabit in **Azerbaijani**. Code, comments, commit messages, docs in `docs/`
  and agent files are in **English**. All app UI text is in **Azerbaijani**.
- When a decision is not covered here or in `docs/`, ask before building. When you
  make an assumption, state it in one line in your reply.

## Product (MVP)

Azerbaijani quiz app for Android with category quizzes and a real-time 1v1
"confidence duel".

In scope:
1. Category quizzes (single player): Coğrafiya, Tarix, Biologiya, Fizika, İdman.
   Categories are rows in the database, not code.
2. Real-time 1v1 duel with the confidence score mechanic (summary below).
3. Auth: Firebase Authentication — email/password, password reset, Google sign-in.
4. Light and dark theme (System default, manual override in settings).
5. Azerbaijani UI only, fully i18n-ready (a new language = configuration, no refactor).
6. Optional image per question, stored compressed.

Out of scope for MVP (do not build, do not block): in-app purchases, ads, ghost
duels, friends/invites, extra languages, leaderboards beyond a basic Elo rating.

### Confidence duel in short
7 rounds. Each round: question text only (3 s) → player picks confidence 1/2/3,
hidden (5 s) → server sends 4 options (10 s) → simultaneous reveal (3 s).
Correct: +100/+200/+300; wrong or timeout: 0/−100/−200. Speed bonus 0–50 on correct
answers, not multiplied. Bet 3 max twice per match. Round 7 doubled. Tie → sudden
death. Full rules, disconnect handling and matchmaking: `docs/mvp-spec.md` §2.
UI never uses the word "bet" or casino visuals; the mechanic is called "Əminlik".

### Images in short
Backend converts uploads to WebP (long side ≤ 1024 px, quality ≈ 75, metadata
stripped) plus a 400 px thumbnail and keeps only metadata in PostgreSQL. Target
≤ 80 KB per image. No zipping. **Zero cost:** files live on the server's own disk
behind an `ImageStorage` interface and Caddy serves them with immutable cache
headers; no paid storage or CDN in the MVP. Details: `docs/mvp-spec.md` §5.

## Repository layout (monorepo)

```
android/            Gradle multi-module Android app        owner: android-dev
backend/            Ktor server                            owner: backend-dev
design/             Tokens, screen specs, Azerbaijani copy owner: designer
content/            Question format, samples, validator    owner: content-writer
content/bank/       Real question bank — separate PRIVATE repo, git-ignored here
infra/              Docker, docker-compose, deploy scripts owner: devops
.github/workflows/  GitHub Actions CI/CD                   owner: devops
docs/requirements/  User stories, acceptance criteria      owner: business-analyst
docs/               Spec, API contract, ADRs               shared
```

## Tech stack (change only through an ADR in docs/adr/)

| Layer | Choice |
| --- | --- |
| Android | Kotlin 2.x, Jetpack Compose, Material 3, Hilt, Coroutines/Flow, type-safe Navigation Compose, Room, DataStore, Coil 3, Ktor Client (HTTP + WebSocket), kotlinx.serialization |
| Backend | Kotlin, Ktor 3 (Netty), Koin, Exposed + PostgreSQL, Flyway, HikariCP, Ktor WebSockets, Firebase Admin SDK |
| Storage | PostgreSQL; images on the server's local disk (Docker volume) behind `ImageStorage` |
| Auth | Firebase Auth on device; backend verifies Firebase ID tokens |
| CI/CD | GitHub Actions; backend images in GitHub Container Registry (ghcr.io) |
| Release | Signed AAB uploaded from GitHub Actions to Google Play testing tracks |
| Monitoring | Firebase Crashlytics + Analytics (Android), structured JSON logs (backend) |

## Team (subagents in .claude/agents/)

| Agent | Use it for |
| --- | --- |
| `business-analyst` | User stories, acceptance criteria, edge cases, open questions, keeping the spec consistent |
| `designer` | Tokens (light/dark), screen specs with all states, Azerbaijani copy, accessibility |
| `android-dev` | Everything in `android/` |
| `backend-dev` | Everything in `backend/`, owner of `docs/api/` |
| `devops` | `infra/`, `.github/workflows/`, release and deploy |
| `content-writer` | Azerbaijani quiz questions in `content/`, sources for every fact, explanations |

The main session acts as tech lead: split work, delegate to the right agent, review
results against this file, keep `docs/` consistent. Delegate proactively.

Requirements-first: `business-analyst` writes the user stories before design and
code start on a feature. Contract-first: REST in `docs/api/openapi.yaml`, WebSocket in
`docs/api/duel-protocol.md`. Contract changes go in the same PR as the code.
Designer delivers the spec before Android builds a screen.

## Git workflow

- `main` is protected and always releasable. Never commit directly to `main`,
  never force-push, never rewrite published history.
- Branches: `feat/<scope>-<short>`, `fix/...`, `chore/...`, `docs/...`
  (scope: android, backend, design, infra, docs, content).
- Commits: Conventional Commits, e.g. `feat(android): add duel bet phase UI`.
- Small, focused commits. Open a pull request per feature; CI must be green.
- You may commit locally. **Ask Sabit before every `git push`** and before creating
  tags or releases.

## Release process

1. Merge to `main` → CI runs, backend auto-deploys to `dev`.
2. Sabit approves a version → tag `vX.Y.Z` on `main`.
3. `release-android.yml` builds the signed AAB (versionName from the tag) and uploads
   it to the Play **internal** or **closed** testing track.
4. Promotion to production is done manually by Sabit in Play Console. Never automate
   the production track.

Notes: the very first AAB must be uploaded manually in Play Console. A new personal
Play developer account needs a closed test with at least 12 testers opted in for
14 continuous days before production access can be requested.

## Non-negotiable rules

- Server is authoritative for timers, scoring and correct answers. The client never
  receives `isCorrect` before an answer is resolved.
- No hard-coded user-facing strings (Android `strings.xml`; backend i18n JSON
  `{"az": "..."}` resolved by `Accept-Language`, fallback `az`).
- No secrets in the repo: keystores, service-account JSON, `.env`, passwords.
  They live in GitHub Secrets and local git-ignored files only.
- Every feature includes tests and updates the docs it touches.
- This repository is **public**. The real question bank (answers!) never goes into
  it; it lives in the private repo cloned to `content/bank/`. Only the format,
  a few sample questions and tooling are public.
- Zero running cost is a goal of the MVP. Do not add a paid service, or one that
  needs a payment card, without Sabit's approval; record it in `infra/README.md`.

## Common commands

```
cp infra/.env.example infra/.env                     # once; set POSTGRES_PASSWORD
docker compose -f infra/docker-compose.yml up -d     # Postgres
docker compose -f infra/docker-compose.yml --profile backend up -d --build  # + backend (once backend/ exists)
cd backend && ./gradlew test build
cd android && ./gradlew lint test assembleDebug
```
Release: push tag `vX.Y.Z` (internal) or `vX.Y.Z-closed` (closed track); secrets and
first-release steps in `infra/README.md`.
(Keep this list updated when commands change.)

## Definition of done

CI green, tests pass, lint clean, contracts/docs updated, works in light and dark
theme, Azerbaijani strings do not truncate on a 360dp screen.

## Build order for the MVP

1. devops: monorepo skeleton, docker-compose, CI workflows, release workflow.
   business-analyst (in parallel): user stories for auth, home, category quiz.
2. backend-dev: `docs/api/` contract, DB schema, auth, categories, quiz endpoints,
   image pipeline.
3. designer: `design/tokens.json`, auth, home, quiz, settings screen specs.
4. android-dev: module skeleton, theme, auth, category quiz.
5. backend-dev + designer + android-dev: duel protocol, duel engine, duel UI.
6. devops: first internal-track release, then closed test with 15–20 testers.

content-writer works in parallel from day one: at least 60 reviewed questions per
category before the closed test. Only Sabit marks a question `reviewed`.
