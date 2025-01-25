# reelchest 📦🎞️📽️ ![](https://img.shields.io/badge/-in%20early%20development!-blueviolet)

A basic web-based media manager. Download or upload clips, videos, or movies, and consume them from a basic web
interface.

Find duplicate videos in your library based on perceptual hashing, organize your materials using a basic tagging system,
and integrate with external search and video host adapters.

![](screenshot.png)

## Setting up for development

### Backend development
Start the backend with the Gradle `run`, either by executing it from the IDE or via `./gradlew run`.

#### DHash Tests

The project includes tests for DHash generation to ensure consistent perceptual hashing. To set up the tests:

1. Run the main function in `backend/src/test/kotlin/DHashTest.kt`. This will:
   - Automatically download a test video file (~2MB) if it doesn't exist
   - Generate reference hashes that will be used by the tests
2. After setup, you can run the tests normally through your IDE or with `./gradlew test`

The test video will be downloaded and stored in the `testData` directory.
The reference hashes will be stored in `backend/src/test/resources`.

### Frontend development
For frontend development, run `yarn dev` in the `frontend` directory to enable the Vite dev server. Access the frontend on port `3000`.

Requests to endpoints under `/api/` are automatically proxied to the backend running on port `8080`.

## TODO

- [ ] Improve hash generation with seeking instead of FPS filter
- [ ] Allow mobile clients to store media offline (e.g. to watch during flights)
- [ ] Centralize ffmpeg handling. Mutexify calls so as to not saturate the CPU
- [x] Improve ffmpeg thumbnail generation to use seeking instead of fps filter
- [x] reset download form after submission
- [x] Stop reading file timestamps from disk; use information stored in DB instead
- [x] merge add and download pages
- [x] Concurrency issue: "Add to queue" may accidentally allow duplicate URLs to be added if done so in rapid succession
