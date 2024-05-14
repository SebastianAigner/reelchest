# reelchest 📦🎞️📽️ ![](https://img.shields.io/badge/-in%20early%20development!-blueviolet)

A basic web-based media manager. Download or upload clips, videos, or movies, and consume them from a basic web
interface.

Find duplicate videos in your library based on perceptual hashing, organize your materials using a basic tagging system,
and integrate with external search and video host adapters.

![](screenshot.png)

## Setting up for development

### Backend development
Start the backend with the Gradle `run`, either by executing it from the IDE or via `./gradlew run`.

### Frontend development
For frontend development, run `yarn dev` in the `frontend` directory to enable the Vite dev server. Access the frontend on port `3000`.

Requests to endpoints under `/api/` are automatically proxied to the backend running on port `8080`.

## TODO

- [ ] Allow mobile clients to store media offline (e.g. to watch during flights)
- [ ] merge add and download pages
- [ ] reset download form after submission
