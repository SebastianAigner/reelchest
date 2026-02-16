package io.sebi.library

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaLibraryEntryTest {
    @Test
    fun shouldGenerateThumbnails_returnsFalseWhenMarkedForDeletion() {
        val entry = MediaLibraryEntry(
            name = "to-delete",
            originUrl = "https://example.com/delete",
            creationDate = 0,
            markedForDeletion = true
        )

        assertFalse(entry.shouldGenerateThumbnails())
    }

    @Test
    fun shouldGenerateThumbnails_returnsTrueWhenNotMarkedForDeletion() {
        val entry = MediaLibraryEntry(
            name = "keep",
            originUrl = "https://example.com/keep",
            creationDate = 0,
            markedForDeletion = false
        )

        assertTrue(entry.shouldGenerateThumbnails())
    }
}
