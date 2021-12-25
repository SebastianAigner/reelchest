package io.sebi

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import dz.jtsgen.annotations.TypeScript
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentLinkedQueue

class InMemoryAppender : AppenderBase<ILoggingEvent>() {
    companion object {
        val list = ConcurrentLinkedQueue<ILoggingEvent>()

        fun getSerializableRepresentation(): List<LogEntry> {
            return list.map { LogEntry(it.formattedMessage) }
        }
    }

    override fun append(eventObject: ILoggingEvent?) {
        list.add(eventObject)
        if (list.size > 100) {
            list.remove()
        }
    }


}

@TypeScript
@Serializable
data class LogEntry(val formattedMessage: String)