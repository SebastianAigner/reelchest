import {MediaLibraryEntry} from "./MediaLibraryEntry";


export interface AutoTaggedMediaLibraryEntry {
    mediaLibraryEntry: MediaLibraryEntry
    autoTags: Array<string>
}