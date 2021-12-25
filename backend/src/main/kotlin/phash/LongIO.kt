package io.sebi.phash

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File

fun File.writeULongs(ulongs: List<ULong>) {
    val dos = DataOutputStream(this.outputStream())
    for (ulong in ulongs) {
        dos.writeLong(ulong.toLong())
    }
}

fun File.readULongs(): List<ULong> {
    val dis = DataInputStream(this.inputStream())
    val hashes = mutableListOf<ULong>()
    try {
        while (true) {
            hashes += dis.readLong().toULong()
        }
    } catch (io: EOFException) {
        // done
        //todo: this can probably be prettified without catching exceptions all the time.
    }
    return hashes
}