package io.sebi.phash

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

fun File.writeULongs(ulongs: List<ULong>) {
    val dos = DataOutputStream(this.outputStream())
    for (ulong in ulongs) {
        dos.writeLong(ulong.toLong())
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
fun File.readULongs(): ULongArray {
    val count = (this.length() / ULong.SIZE_BYTES).toInt()
    val dis = DataInputStream(this.inputStream())
    val hashes = ULongArray(count) {
        dis.readLong().toULong()
    }
    return hashes
}