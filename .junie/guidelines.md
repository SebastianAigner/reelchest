# reelchest Project Guidelines

## Project Overview

reelchest is a web-based media management system that provides a comprehensive solution for storing, organizing, and
consuming media.

## Architecture Overview

### Frontend

- Built using Vite development server
- Runs on port 3000
- Automatic proxy configuration for `/api/` endpoints to backend
- React

### Backend

- Kotlin-based backend server
- Runs on port 8080
- Handles media processing, storage, and API endpoints
- Implements perceptual hashing (DHash) for duplicate detection

## Development Setup

### Backend Development

1. Start the backend using Gradle:
   ```
   ./gradlew run
   ```
   or launch it directly from your IDE

2. DHash Testing Setup:
    - Run the main function in `backend/src/test/kotlin/DHashTest.kt`
    - Test video (~2MB) will be automatically downloaded to `testData` directory
    - Reference hashes will be generated in `backend/src/test/resources`
    - Run tests via IDE or `./gradlew test`

### Frontend Development

1. Navigate to the `frontend` directory
2. Run the development server:
   ```
   yarn dev
   ```
3. Access the frontend at `http://localhost:3000`

When working on the frontend, always make sure that everything typechecks before you finish your task. To typecheck the
frontend, `cd frontend && yarn typecheck`

## Project Structure

- `/backend`: Kotlin backend implementation
    - Main application logic
    - API endpoints
    - Media processing
    - Database integration
      For detailed information about the backend architecture,
      see [Backend Architecture](.junie/backend-architecture.md)
- `/frontend`: Web interface implementation
    - User interface components
    - Media playback
    - Content management interface