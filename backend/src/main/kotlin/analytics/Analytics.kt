package io.sebi.analytics

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.sql.DriverManager

private val adb = AnalyticsDatabase()
fun Route.analyticsApi() {
    post("event") {
        val eventText = call.receiveText()
        adb.log(eventText)
        call.respond(HttpStatusCode.OK)
    }
}

class AnalyticsDatabase {
    val mutex = Mutex()
    val connection = DriverManager.getConnection("jdbc:sqlite:analytics.sqlite")

    init {
        connection.createStatement().apply {
            queryTimeout = 30 // set timeout to 30 sec.
            executeUpdate(
                """
                    create table if not exists events
                    (
                        id        integer primary key,
                        event     string,
                        TIMESTAMP DATETIME DEFAULT(STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW'))
                    )
                """.trimIndent()
            )
        }
    }

    val statement = connection.prepareStatement(
        "INSERT INTO events (event) VALUES (?)"
    )

    suspend fun log(msg: String) {
        mutex.withLock {
            statement?.setString(1, msg)
            statement.execute()
        }
    }
}

suspend fun main() {
    val ab = AnalyticsDatabase()
    repeat(10) {
        ab.log("beef")
    }
}