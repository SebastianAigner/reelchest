// Type definitions for IoSebi 0.0.1 generated by jtsgen
// Definitions by: unknown <unknown>


declare namespace IoSebi {
  export interface ProblematicTaskDTO extends WithOriginUrl {
    Companion: io.sebi.downloader.ProblematicTaskDTO.Companion;
    originUrl: string;
    error: string;
  }

  export interface LogEntry {
    Companion: io.sebi.LogEntry.Companion;
    formattedMessage: string;
  }

  export interface SearchRequest {
    Companion: io.sebi.api.SearchRequest.Companion;
    offset: number;
    term: string;
  }

  export interface File {
    parent: string;
    parentFile: any;
    hidden: boolean;
    freeSpace: number;
    pathSeparatorChar: string;
    totalSpace: number;
    usableSpace: number;
    separator: string;
    canonicalFile: any;
    directory: boolean;
    path: string;
    absoluteFile: any;
    file: boolean;
    pathSeparator: string;
    absolute: boolean;
    name: string;
    canonicalPath: string;
    separatorChar: string;
    absolutePath: string;
  }

  export interface MediaLibraryEntry {
    Companion: io.sebi.library.MediaLibraryEntry.Companion;
    hits: number;
    uid: string;
    file: File;
    originUrl: string;
    name: string;
    originPage: string;
    id: string;
    markedForDeletion: boolean;
    creationDate: number;
    thumbnails: File[];
    tags: string[];
  }

  export interface DuplicateResponse {
    Companion: io.sebi.api.DuplicateResponse.Companion;
    entry: MediaLibraryEntry;
    possibleDuplicate: MediaLibraryEntry;
    distance: number;
  }

  export interface DuplicatesDTO {
    Companion: io.sebi.api.DuplicatesDTO.Companion;
    dup_id: string;
    src_id: string;
    distance: number;
  }

  export interface UrlRequest {
    Companion: io.sebi.api.UrlRequest.Companion;
    url: string;
  }

  export interface WithOriginUrl {
    originUrl: string;
  }

  export interface DownloadTaskDTO extends WithOriginUrl {
    Companion: io.sebi.downloader.DownloadTaskDTO.Companion;
    originUrl: string;
    progress: number;
  }

  export interface MetadatedDownloadQueueEntry {
    Companion: io.sebi.api.MetadatedDownloadQueueEntry.Companion;
    queueEntry: DownloadTaskDTO;
    title: string;
  }

  export namespace io {
    export namespace sebi {
      export namespace library {
        export namespace MediaLibraryEntry {
          export interface Companion {
          }

        }
      }
      export namespace api {
        export namespace SearchRequest {
          export interface Companion {
          }

        }
        export namespace UrlRequest {
          export interface Companion {
          }

        }
        export namespace DuplicateResponse {
          export interface Companion {
          }

        }
        export namespace DuplicatesDTO {
          export interface Companion {
          }

        }
        export namespace MetadatedDownloadQueueEntry {
          export interface Companion {
          }

        }
      }
      export namespace LogEntry {
        export interface Companion {
        }

      }
      export namespace downloader {
        export namespace ProblematicTaskDTO {
          export interface Companion {
          }

        }
        export namespace DownloadTaskDTO {
          export interface Companion {
          }

        }
      }
    }
  }
 }
