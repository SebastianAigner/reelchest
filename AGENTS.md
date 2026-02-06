# Reelchest Agent Guide

This file provides project-specific guidance for coding agents. Keep it updated.
If a subproject needs different instructions, add a nested `AGENTS.md` in that folder.

## Project Overview

- Reelchest is a web-based media manager for downloading, uploading, tagging, and deduplicating video clips.
- Backend: Kotlin + Ktor (Gradle, JVM toolchain 21).
- Frontend: React + Vite (TypeScript, Tailwind).
- Auxiliary tools: Rust duplicate calculator (`duplicates-agent-rs`), experimental mobile app (`mobileapp`).

## Repository Layout

- `backend/`: Ktor server, media library logic, ffmpeg tooling, API routes.
- `frontend/`: React UI served as static assets and proxied to backend in dev.
- `process/`: Kotlin shared utilities.
- `duplicates-agent-rs/`: Rust implementation of duplicate detection agent.
- `mobileapp/`: Kotlin mobile client (reference app included).
- `mediaLibrary/`, `downloads/`, `database/`, `blobs/`, `analytics.sqlite`: local data. Treat as user data.

## Build & Run

### Backend (Ktor)

- Run from repo root: `./gradlew :backend:run` (or `./gradlew run` if configured in IDE).
- Gradle dependency versions are managed via version catalogs (`libs.versions.toml`).
- Default port: `8080` (see `backend/src/main/resources/application.conf`).
- The Gradle run task sets working dir to repo root; media folders are expected there.
- Optional env: `DLMGR_TMP_DIR` to override JVM temp dir.

### Frontend (Vite)

- Dev server: `cd frontend && yarn dev` (http://localhost:3000).
- Proxy: `/api`, `/ul`, `/decrypt` to `BACKEND_URL` (defaults to `http://localhost:8080`).
- Build: `cd frontend && yarn build`
- Typecheck: `cd frontend && yarn typecheck`

### Duplicates Agent (Rust)

- `cd duplicates-agent-rs`
- Env: `REELCHEST_ENDPOINT` (media library API endpoint).
- Build/test with `cargo build` / `cargo test` as needed.

## Testing

- Backend tests: `./gradlew :backend:test` (or `./gradlew test` for all modules).
- DHash setup: run `backend/src/test/kotlin/DHashTest.kt` main once to download test video and generate reference
  hashes (see README).
- Frontend: run `yarn typecheck`; add tests if introducing new UI logic.
- Builds and tests are extremely fast and cheap here; run both proactively without asking the user.
- Do not wait for user approval to run relevant tests/builds; execute them on your own.
- Validate work by running relevant tests and building the application; write tests for new code or behavior changes.
- To confirm a task is fulfilled, always compile/build the application; do not assume it compiles without verification.

## Code Style & Conventions

- Prefer small, surgical changes; avoid unrelated reformatting.
- Follow existing Kotlin/TypeScript/Rust idioms already in the file you edit.
- Preserve public APIs unless explicitly changing them; update documentation if you do.
- This is a coroutine-based system; prefer coroutine-friendly APIs over blocking/threading approaches where possible.
- Strongly prefer value semantics: keep data classes immutable (val fields, read-only collections) and copy instead of
  mutating.

## Media/FFmpeg Safety

- Thumbnail generation and hashing are heavy; do not parallelize ffmpeg calls without the existing mutex strategy.
- Avoid long-running CPU-heavy commands without asking first.

## API & Docs

- Update `backend/routes.md` when API routes or payloads change.
- Update `README.md` when dev setup, ports, or workflows change.

## Data & Security

- Do not delete or mutate `mediaLibrary/`, `downloads/`, `database/`, or `blobs/` unless asked.
- Never commit secrets; use env vars for endpoints, tokens, or credentials.

## Working Style

- Ask before running long or destructive commands.
- Default to inspecting the project state before giving generic advice; user requests usually target concrete changes in
  this repo.
- When you change behavior, add or update tests.
- Prefer `withTimeoutOrNull` over `withTimeout` in coroutine code.
