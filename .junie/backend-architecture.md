# reelchest Backend Architecture

## Overview

The backend is built using Kotlin and Ktor, implementing a modular architecture that separates concerns into distinct
components. The system is designed to handle media processing, storage, and API endpoints efficiently.

## Core Components

### Media Management

- **MediaLibrary**: Central component managing media entries
    - Handles media metadata
    - Manages file operations
    - Coordinates with storage system

- **Storage System**
    - `FileSystemVideoStorage`: Handles physical file storage
    - `MetadataStorage`: Manages metadata using SQLite
    - `SqliteMetadataStorage`: Implementation of metadata persistence

### Media Processing

- **Download Management**
    - `DownloadManager`: Coordinates download operations
    - `DownloadManagerImpl`: Implementation of download management
    - `IntoMediaLibraryDownloader`: Handles direct downloads to media library

- **Thumbnail Generation**
    - Automated thumbnail creation for media entries
    - FFmpeg integration for video frame extraction
    - Concurrent processing with mutex protection

### Content Analysis

- **Duplicate Detection**
    - `DuplicateCalculator`: Identifies duplicate media
    - Perceptual hashing (DHash) implementation
    - Automated hash generation for new content

- **Tagging System**
    - `Tagger`: Interface for content tagging
    - `CachingTagger`: Implements caching for tag operations
    - Supports organization and categorization

### Network Layer

- **URL Processing**
    - `UrlDecoder`: Interface for URL parsing
    - `UrlDecoderImpl`: Implementation of URL decoding
    - Support for various video hosting platforms

- **Network Management**
    - `NetworkManager`: Handles network operations
    - Request rate limiting
    - Connection pooling

### API Structure

- RESTful endpoints for:
    - Media management
    - Download operations
    - Search functionality
    - Tagging operations
    - Duplicate detection

## Server Configuration

- Ktor-based HTTP server
- CORS configuration
- Static content serving
- API routing
- WebSocket support for real-time operations

## Background Tasks

- Automated thumbnail generation
- Periodic cleanup operations
- Hash calculation for duplicate detection
- File system synchronization

## Security Considerations

- File system access control
- Download validation
- Resource usage limits
- Rate limiting for API endpoints

## Development Guidelines

1. **Error Handling**
    - Use appropriate error types
    - Implement proper logging
    - Handle edge cases

2. **Concurrency**
    - Use `globalFfmpegMutex` for FFmpeg operations
    - Implement proper coroutine scoping
    - Handle concurrent access to shared resources

3. **Testing**
    - Unit tests for core components
    - Integration tests for API endpoints
    - DHash testing with reference files

4. **Performance**
    - Implement caching where appropriate
    - Use connection pooling
    - Optimize media processing operations