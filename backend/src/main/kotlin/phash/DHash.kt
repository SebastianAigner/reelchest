package io.sebi.phash

import java.awt.image.BufferedImage

// Implemented after https://www.hackerfactor.com/blog/?/archives/529-Kind-of-Like-That.html

@JvmInline
value class DHash(val raw: ULong) {
    fun distanceTo(other: DHash): Int {
        return (raw xor other.raw).countOneBits()
    }

    override fun toString(): String {
        return raw.toString(16)
    }

    companion object {
        fun fromImage(img: BufferedImage): DHash {
            val scl = BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY)
            scl.graphics.drawImage(img, 0, 0, scl.width, scl.height, 0, 0, img.width, img.height, null)
            var hash = 0UL
            for (row in 0 until 8) {
                for (col in 0 until 8) {
                    val risingGrad = scl.getRGB(col, row) <= scl.getRGB(col + 1, row)
                    hash = hash or if (risingGrad) 1UL else 0UL
                    if (row == 7 && col == 7) break // otherwise we lose the last digit.
                    hash = hash shl 1
                }
            }
            return DHash(hash)
        }
    }
}

fun List<DHash>.getMinimalDistance(target: DHash): Int {
    val thatHash = this.minByOrNull {
        target.distanceTo(it)
    } ?: error("Could not get minimal distance! Whose fault is this?!")
    return thatHash.distanceTo(target)
}