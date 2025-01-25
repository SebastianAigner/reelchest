package io.sebi.phash

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

fun File.writeULongs(ulongs: List<ULong>) {
    DataOutputStream(this.outputStream()).use {
        for (ulong in ulongs) {
            it.writeLong(ulong.toLong())
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
fun File.readULongs(): ULongArray {
    val count = (this.length() / ULong.SIZE_BYTES).toInt()
    DataInputStream(this.inputStream()).use { dis ->
        val hashes = ULongArray(count) {
            dis.readLong().toULong()
        }
        return hashes
    }
}