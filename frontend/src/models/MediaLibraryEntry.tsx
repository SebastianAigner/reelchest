export interface MediaLibraryEntry {
    id: string
    name: string
    originUrl: string
    originPage: string
    tags: Array<string>
    hits: number
    markedForDeletion: boolean
}