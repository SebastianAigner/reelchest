package io.sebi.logging

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import dz.jtsgen.annotations.TypeScript
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentLinkedQueue

fun InMemoryAppender.Companion.getSerializableRepresentation(): List<LogEntry> {
    return list.map {
        LogEntry(
            it
        )
    }
}

// Interface to enforce having a non-private encoder property (to be initialized by logback)
interface EncoderHaver {
    var encoder: PatternLayoutEncoder
}

class InMemoryAppender : AppenderBase<ILoggingEvent>(), EncoderHaver {
    @Suppress("MemberVisibilityCanBePrivate")
    override lateinit var encoder: PatternLayoutEncoder

    companion object {
        // todo: would be nice to not write this to a singleton list
        val list = ConcurrentLinkedQueue<String>()
    }

    override fun append(eventObject: ILoggingEvent) {
        val layouted = encoder.layout.doLayout(eventObject)
        list.add(layouted)
        if (list.size > 1000) {
            list.remove()
        }
    }
}

@TypeScript
@Serializable
data class LogEntry(val formattedMessage: String)