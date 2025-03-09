# reelchest Project Guidelines

## Project Overview

reelchest is a web-based media management system that provides a comprehensive solution for storing, organizing, and
consuming video content. The project aims to simplify media management while offering advanced features like duplicate
detection and content organization.

### Key Features

- Video downloading and uploading capabilities
- Web-based interface for media consumption
- Duplicate video detection using perceptual hashing
- Basic tagging system for content organization
- Integration with external search and video host adapters

## Architecture Overview

### Frontend

- Built using Vite development server
- Runs on port 3000
- Automatic proxy configuration for `/api/` endpoints to backend
- Modern web interface for media management

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

## Documentation

The project documentation is organized in the `.junie` directory:

- `guidelines.md`: Project overview and setup instructions
- `backend-architecture.md`: Detailed backend architecture and components

## Current Development Status

The project is in early development, with ongoing improvements focused on:

- Optimizing hash generation
- Improving ffmpeg integration
- Enhancing mobile support
- Strengthening concurrent operations handling
