package io.sebi.phash

import java.io.DataInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class DHashTest {
    @Test
    fun `dhash matches pre-extracted frames`() {
        val projectRoot = File(System.getProperty("user.dir"))
        val framesDir = File(projectRoot, "src/test/resources/dhash-frames")
        val expectedHashesFile = File(projectRoot, "src/test/resources/ForBiggerJoyrides.frames.dhashes.bin")

        check(framesDir.isDirectory) {
            "Missing extracted frames at ${framesDir.absolutePath}"
        }
        check(expectedHashesFile.isFile) {
            "Missing expected hashes at ${expectedHashesFile.absolutePath}"
        }

        val frames = framesDir.listFiles { file ->
            file.isFile && file.name.endsWith(".jpg")
        }?.sortedBy { it.name } ?: emptyList()

        check(frames.isNotEmpty()) { "No frames found in ${framesDir.absolutePath}" }

        val expectedHashes = DataInputStream(expectedHashesFile.inputStream()).use { dis ->
            val count = (expectedHashesFile.length() / Long.SIZE_BYTES).toInt()
            LongArray(count) { dis.readLong() }
        }

        assertEquals(expectedHashes.size, frames.size, "Expected hash count should match frame count")

        val actualHashes = frames.map { frame ->
            val image = ImageIO.read(frame) ?: error("Failed to read frame ${frame.name}")
            DHash.fromImage(image).raw.toLong()
        }

        actualHashes.forEachIndexed { index, actual ->
            assertEquals(expectedHashes[index], actual, "Hash at position $index should match")
        }
    }
}
