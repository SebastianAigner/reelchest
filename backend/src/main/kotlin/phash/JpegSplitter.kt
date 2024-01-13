package io.sebi.phash

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream


object JpegSplitter {
    fun split(inputStream: BufferedInputStream, onConcludeFile: (ByteArray) -> Unit) {

        val startA: UByte = 0xffu
        val startB: UByte = 0xd8u
        val endA: UByte = 0xffu
        val endB: UByte = 0xd9u

        var state = State.WAITING_FOR_STARTA
        var fc = 0
        val baos = ByteArrayOutputStream()
        val concludeFile: () -> Unit = {
            onConcludeFile(baos.toByteArray())
            fc++
            baos.close()
            baos.reset()
        }
        for (b in inputStream) {
            val byte = b.toUByte()
            when (state) {
                State.WAITING_FOR_STARTA -> {
                    if (byte == startA) {
                        state = State.WAITING_FOR_STARTB
                    }
                }

                State.WAITING_FOR_STARTB -> {
                    if (byte == startB) {
                        // we have a valid header!
                        baos += startA
                        baos += startB
                        state = State.WAITING_FOR_ENDA
                    }
                }

                State.WAITING_FOR_ENDA -> {
                    if (byte == endA) {
                        state = State.WAITING_FOR_ENDB
                    } else {
                        baos += byte
                    }
                }

                State.WAITING_FOR_ENDB -> {
                    if (byte == endB) {
                        baos += endA
                        baos += endB
                        concludeFile()
                        // we go back and hope there's more bytes!
                        state = State.WAITING_FOR_STARTA
                    } else {
                        // we didn't imm. hit ENDB. write the "false positive" endA, the current byte, and go back to waiting for the postamble
                        baos += endA
                        baos += byte
                        state = State.WAITING_FOR_ENDA
                    }
                }
            }
        }
    }


    enum class State {
        WAITING_FOR_STARTA,
        WAITING_FOR_STARTB,
        WAITING_FOR_ENDA,
        WAITING_FOR_ENDB,
    }
}

fun ByteArrayOutputStream.writeUByte(uByte: UByte) {
    write(uByte.toInt())
}

operator fun ByteArrayOutputStream.plusAssign(uByte: UByte) {
    writeUByte(uByte)
}
