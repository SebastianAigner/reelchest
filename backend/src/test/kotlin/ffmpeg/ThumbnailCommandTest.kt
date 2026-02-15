package io.sebi.ffmpeg

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThumbnailCommandTest {
    @Test
    fun buildThumbnailFfmpegParameters_buildsExpectedArguments() {
        val videoFile = File("/tmp/video.mp4")
        val timestamps = listOf(0L, 10L, 20L)
        val scaleFilter =
            "scale=w='min(1920,iw)':h='min(1080,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2"

        val parameters = buildThumbnailFfmpegParameters(
            videoFile = videoFile,
            timestamps = timestamps,
            startIndex = 5,
            scaleFilter = scaleFilter,
            threadCount = 1,
            filterThreadCount = 1
        )

        assertEquals(listOf("-y", "-threads", "1", "-filter_threads", "1"), parameters.take(5))
        assertEquals(timestamps.size, parameters.count { it == "-ss" })
        assertEquals(timestamps.size, parameters.windowed(2).count { it[0] == "-vf" && it[1] == scaleFilter })
        assertTrue(parameters.contains("thumb0005.jpg"))
        assertTrue(parameters.contains("thumb0006.jpg"))
        assertTrue(parameters.contains("thumb0007.jpg"))
        assertSubsequence(parameters, listOf("-map", "0:v:0", "-frames:v", "1", "-update", "1", "thumb0005.jpg"))
        assertSubsequence(parameters, listOf("-map", "1:v:0", "-frames:v", "1", "-update", "1", "thumb0006.jpg"))
        assertSubsequence(parameters, listOf("-map", "2:v:0", "-frames:v", "1", "-update", "1", "thumb0007.jpg"))
    }

    private fun assertSubsequence(list: List<String>, subsequence: List<String>) {
        val found = list.windowed(subsequence.size).any { it == subsequence }
        assertTrue(found, "Expected subsequence not found: $subsequence")
    }
}
