# TODO: HttpRequestLifecycle cancellation readiness

## Current risks / behavior changes

- Request cancellation now stops handler execution; any side effects in request scope may not complete.
- External processes (ffmpeg/ffprobe) are not tied to coroutine cancellation, so they can keep running after a request
  is canceled.
- Some handlers perform blocking work on the request thread pool.

## Specific issues to address

- `hashingInProgress` in `backend/src/main/kotlin/api/handlers/HashingHandler.kt`:
    - Global mutable list shared across handlers with no synchronization (data race).
    - Entry is added on `getUnhashedEntryHandler`, but never removed if the client disconnects or fails to upload
      hashes.
    - Consider a concurrency-safe structure plus a timeout/lease mechanism (or server-side job tracking).
- External processes:
    - `FfmpegTask.execute` uses `runExternalProcess` which does not terminate the OS process on coroutine cancellation.
    - `generateDHashes` and `getMediaType` spawn `ProcessBuilder` directly and do not kill the process on cancellation.
    - Add cancellation hooks that destroy processes (`destroyForcibly` if needed) and ensure temp files are cleaned up.
- Blocking I/O in handlers (audit):
    - File reads/writes in handlers (config read/write, hash write, logs, thumbnails listing, etc.).
    - Image decoding via `ImageIO` (potentially blocking).
    - Multipart processing and file copying in uploads.
    - Ensure all blocking operations run on `Dispatchers.IO` (or a dedicated dispatcher) and not on the call thread
      pool.

## Follow‑up actions

- Add a shared application scope job manager for background work and job tracking (progress/cancellation/visibility).
- Implement cancellation-aware external process handling (attach to coroutine context).
- Replace `hashingInProgress` with a concurrency-safe structure + expiry/cleanup.
- Audit other handlers for blocking work and move to IO dispatcher as needed.
