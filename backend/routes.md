# API Routes Documentation

## Media Library Routes

### General

- `GET /api/mediaLibrary` - Get all media library entries
    - Query params:
        - `auto` - (optional) Include auto-generated tags

### Media Entry Operations

- `GET /api/mediaLibrary/{id}` - Get specific media entry
    - Query params:
        - `auto` - (optional) Include auto-generated tags
- `POST /api/mediaLibrary/{id}` - Update media entry
- `GET /api/mediaLibrary/{id}/conversions` - Get conversion summary for an entry
- `POST /api/mediaLibrary/{id}/conversions` - Enqueue a conversion for an entry
    - Body fields:
        - `mode` - `IMMEDIATE` or `SCHEDULED`
        - `options` - conversion settings (resolution, fps, CRF, audio bitrate)
      - `convertAndDeleteOriginal` - when true, mark original for deletion after success (default: true)
- `POST /api/mediaLibrary/{id}/conversions/default` - Enqueue a conversion with default settings
    - Defaults:
        - Resolution: 1080p (1920×1080)
        - Frame rate: 30 fps
        - CRF: 21
        - Audio bitrate: 160 kbps
        - Mode: IMMEDIATE
        - Original deletion: enabled
- `GET /api/mediaLibrary/{id}/hit` - Record a hit for specific entry
- `GET /api/mediaLibrary/{id}/thumbnails` - Get list of thumbnails
- `GET /api/mediaLibrary/{id}/randomThumb` - Get a random thumbnail
- `GET /api/mediaLibrary/{id}/hash.{format}` - Get hash in specific format
    - Formats: json, bin
- `GET /api/mediaLibrary/{id}/media-information` - Get media metadata for playback diagnostics
    - Returns:
        - `mimeType`
        - `width`
        - `height`
        - `bitRate` (bits per second)
        - `frameRate` (frames per second)
        - `fileSize` (bytes)
      - `durationSeconds`
- `GET /api/mediaLibrary/media-information/search` - Query stored media metadata
    - Query params:
        - `sort` - resolution, frameRate, fileSize, bitRate, duration, width, height (default: resolution)
        - `order` - asc, desc (default: desc)
        - `limit` - max results (default: 10)
        - `exclude` - title substring to exclude (repeatable, case-insensitive)
        - `codec` - exact codec filter such as `video/h264` (repeatable, case-insensitive)
    - Returns array of:
        - `id`
        - `title`
        - `originUrl`
        - `mimeType` - codec string such as `video/h264`
        - `width`
        - `height`
        - `frameRate`
        - `fileSize`
        - `bitRate`
        - `durationSeconds`
- `POST /api/mediaLibrary/media-information/conversions/default` - Enqueue default conversions for the media
  information query results
    - Query params:
        - `sort` - resolution, frameRate, fileSize, bitRate, duration, width, height (default: resolution)
        - `order` - asc, desc (default: desc)
        - `limit` - max results (default: 10)
        - `exclude` - title substring to exclude (repeatable, case-insensitive)
        - `codec` - exact codec filter such as `video/h264` (repeatable, case-insensitive)
    - Defaults:
        - Mode: SCHEDULED
    - Returns:
        - `total`
        - `enqueued`
        - `skipped`
        - `missing`

### Duplicates Management

- `GET /api/mediaLibrary/duplicates/{id}` - Get duplicate info for specific ID
- `POST /api/mediaLibrary/duplicates/{id}` - Add duplicate information
- `GET /api/mediaLibrary/duplicates` - Get all duplicates
- `GET /api/mediaLibrary/{id}/possibleDuplicates` - Get possible duplicates (currently disabled)

### Hashing Operations

- `GET /api/mediaLibrary/hashing/unhashed` - Get first unhashed entry
- `GET /api/mediaLibrary/hashing/all` - Get all hash information
- `POST /api/mediaLibrary/hashing/hash/{id}` - Save hash for specific entry

### Debug Operations

- `GET /api/mediaLibrary/debug/missing-thumbnails` - List entries without thumbnails
- `POST /api/mediaLibrary/debug/regenerate-thumbnail/{id}` - Regenerate thumbnails
- `POST /api/mediaLibrary/debug/scan-orphans` - Start orphan scan (returns progress snapshot)
- `GET /api/mediaLibrary/debug/scan-orphans` - Get orphan scan progress
- `POST /api/mediaLibrary/debug/populate-media-info` - Populate stored media info for all entries (returns progress
  snapshot)
- `GET /api/mediaLibrary/debug/populate-media-info` - Get media info population progress
- `POST /api/mediaLibrary/debug/delete-marked` - Delete videos marked for deletion
    - Returns:
        - `totalMarked`
        - `deleted`
        - `failed`

## Download Manager Routes

- `POST /api/download` - Enqueue a new download task
    - Returns `409` with `{ "error": string }` when the URL is already queued, in progress, or present in metadata
      storage
- `GET /api/queue` - Get current and queued downloads
- `GET /api/problematic` - Get problematic downloads
- `POST /api/problematic/remove` - Remove a problematic download
- `POST /api/problematic/retry` - Retry a problematic download

## Conversion Routes

- `GET /api/conversions` - Get conversion queue snapshot
- `POST /api/conversions/clear` - Clear all queued/scheduled conversions and conversion errors (running conversion is
  cancelled)

## Search Routes

- `POST /api/search/{provider}` - Search using specified provider
    - Body: `{ "term": string, "offset": number }`
- `GET /api/searchers` - List available search providers

## Subtitle Routes

- `POST /api/shiftsubtitles` - Shift subtitles by specified offset
    - Multipart form data:
        - `subtitleFile` - SRT file
        - `offset` - Time offset in milliseconds
- `POST /api/embedsubtitles` - Embed subtitles into video file
    - Multipart form data:
        - `file1` - Video file
        - `file2` - Subtitle file (SRT)
        - `offset` - Time offset in milliseconds

## System Routes

- `GET /api/config` - Get current application configuration
    - Returns:
        - `development` - Development mode status
      - `startedAt` - Server start timestamp (milliseconds since epoch)
        - `port` - Server port
        - `shutdownUrl` - Shutdown endpoint URL
        - `connectionGroupSize` - Size of connection thread pool
        - `workerGroupSize` - Size of worker thread pool
        - `callGroupSize` - Size of call processing thread pool
        - `shutdownGracePeriod` - Grace period for shutdown in milliseconds
        - `shutdownTimeout` - Shutdown timeout in milliseconds
        - `requestQueueLimit` - Maximum number of queued requests
        - `runningLimit` - Maximum number of running requests
        - `responseWriteTimeoutSeconds` - Timeout for writing responses
- `GET /api/ffmpeg/mutex` - Get FFmpeg mutex status
    - Returns:
        - `locked` - Whether the global FFmpeg mutex is currently held
        - `lockedAt` - Lock acquisition time (milliseconds since epoch), only when locked
        - `label` - Lock label describing the current FFmpeg task, only when locked
- `GET /api/log` - Get system logs
- `GET /api/status` - Get worker status
- `POST /api/isUrlInLibraryOrProgress` - Check if URL exists in library or is being processed
