package io.sebi.ffmpeg

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class FfmpegTest {
    @Test
    fun testGetMediaType() = runBlocking {
        val testFile = File("src/test/resources/ForBiggerJoyrides.mp4")
        val mediaTypeInfo = getMediaType(testFile)

        assertEquals("video", mediaTypeInfo.codecType)
        assertEquals("h264", mediaTypeInfo.codecName)
    }
}
