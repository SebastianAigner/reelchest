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
- `GET /api/mediaLibrary/{id}/hit` - Record a hit for specific entry
- `GET /api/mediaLibrary/{id}/thumbnails` - Get list of thumbnails
- `GET /api/mediaLibrary/{id}/randomThumb` - Get a random thumbnail
- `GET /api/mediaLibrary/{id}/hash.{format}` - Get hash in specific format
    - Formats: json, bin

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

## Download Manager Routes

- `POST /api/download` - Enqueue a new download task
- `GET /api/queue` - Get current and queued downloads
- `GET /api/problematic` - Get problematic downloads
- `POST /api/problematic/remove` - Remove a problematic download
- `POST /api/problematic/retry` - Retry a problematic download

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
- `GET /api/log` - Get system logs
- `GET /api/status` - Get worker status
- `POST /api/isUrlInLibraryOrProgress` - Check if URL exists in library or is being processed
