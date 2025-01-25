package io.sebi.phash

import io.sebi.ffmpeg.generateDHashes
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals

private object TestPaths {
    private val projectRoot = File(System.getProperty("user.dir"))
    val videoFile = File(projectRoot.parentFile, "testData/ForBiggerJoyrides.mp4").absoluteFile
    val expectedHashesFile = File(projectRoot, "src/test/resources/ForBiggerJoyrides.dhashes.bin").absoluteFile
    const val VIDEO_URL = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"
}

/**
 * Utility main function to generate reference hashes.
 * Run this function to create initial reference hashes that will be used by the test.
 */
private fun downloadVideo() {
    println("Downloading test video from ${TestPaths.VIDEO_URL}")
    TestPaths.videoFile.parentFile.mkdirs()
    URL(TestPaths.VIDEO_URL).openStream().use { input ->
        TestPaths.videoFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    println("Video downloaded to ${TestPaths.videoFile.absolutePath}")
}

fun main() = runBlocking {
    if (!TestPaths.videoFile.exists()) {
        downloadVideo()
    }

    println("Generating reference hashes from ${TestPaths.videoFile.absolutePath}")
    generateDHashes(TestPaths.videoFile)
    val generatedHashesFile = File(TestPaths.videoFile.parent, "dhashes.bin")
    generatedHashesFile.copyTo(TestPaths.expectedHashesFile, overwrite = true)
    println("Generated reference hashes and saved them to ${TestPaths.expectedHashesFile.absolutePath}")
}

class DHashTest {
    @OptIn(ExperimentalUnsignedTypes::class, ExperimentalUnsignedTypes::class)
    @Test(timeout = 5000L)
    fun `test DHash generation consistency`(): Unit = runBlocking {
        if (!TestPaths.videoFile.exists()) {
            error("Test video file not found. Please run the main function in DHashTest.kt first to download it.")
        }

        if (!TestPaths.expectedHashesFile.exists()) {
            error("Reference hashes not found. Please run the main function in DHashTest.kt first to generate them.")
        }

        // Generate new hashes
        generateDHashes(TestPaths.videoFile)
        val newHashesFile = File(TestPaths.videoFile.parent, "dhashes.bin")

        // Compare with expected hashes
        val expectedHashes = TestPaths.expectedHashesFile.readULongs()
        val newHashes = newHashesFile.readULongs()

        assertEquals(expectedHashes.size, newHashes.size, "Number of generated hashes should match")
        expectedHashes.zip(newHashes).forEachIndexed { index, (expected, actual) ->
            assertEquals(expected, actual, "Hash at position $index should match")
        }
    }
}
