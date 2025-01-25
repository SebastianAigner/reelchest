package io.sebi.ffmpeg

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FfmpegTest {
    @Test
    fun testGetMediaType() = runBlocking {
        val testFile = File("src/test/resources/ForBiggerJoyrides.mp4")
        val mediaTypeInfo = getMediaType(testFile)

        assertEquals("video", mediaTypeInfo.codecType)
        assertEquals("h264", mediaTypeInfo.codecName)

        // Verify video dimensions
        assertNotNull(mediaTypeInfo.width, "Video width should not be null")
        assertNotNull(mediaTypeInfo.height, "Video height should not be null")

        // Verify specific dimensions
        assertEquals(1280, mediaTypeInfo.width, "Video width should be 1280")
        assertEquals(720, mediaTypeInfo.height, "Video height should be 720")
    }
}
