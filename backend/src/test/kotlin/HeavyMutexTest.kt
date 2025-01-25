import io.sebi.heavymutex.HeavyMutex
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.Marker
import kotlin.test.Test
import kotlin.test.assertTrue

class TestLogger : Logger {
    var lastMessage: String? = null
    var lastDurationMs: Long? = null

    override fun debug(format: String?, arg1: Any?, arg2: Any?) {
        lastMessage = format
        // The duration comes as kotlin.time.Duration
        if (arg2 is kotlin.time.Duration) {
            lastDurationMs = arg2.inWholeMilliseconds
        }
    }

    // Required Logger interface implementations
    override fun getName(): String = "TestLogger"
    override fun isTraceEnabled(): Boolean = false
    override fun isTraceEnabled(marker: Marker?): Boolean = false
    override fun trace(msg: String?) {}
    override fun trace(format: String?, arg: Any?) {}
    override fun trace(format: String?, arg1: Any?, arg2: Any?) {}
    override fun trace(format: String?, vararg arguments: Any?) {}
    override fun trace(msg: String?, t: Throwable?) {}
    override fun trace(marker: Marker?, msg: String?) {}
    override fun trace(marker: Marker?, format: String?, arg: Any?) {}
    override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}
    override fun trace(marker: Marker?, format: String?, vararg argArray: Any?) {}
    override fun trace(marker: Marker?, msg: String?, t: Throwable?) {}
    override fun isDebugEnabled(): Boolean = true
    override fun isDebugEnabled(marker: Marker?): Boolean = true
    override fun debug(msg: String?) {}
    override fun debug(format: String?, arg: Any?) {}
    override fun debug(format: String?, vararg arguments: Any?) {}
    override fun debug(msg: String?, t: Throwable?) {}
    override fun debug(marker: Marker?, msg: String?) {}
    override fun debug(marker: Marker?, format: String?, arg: Any?) {}
    override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}
    override fun debug(marker: Marker?, format: String?, vararg arguments: Any?) {}
    override fun debug(marker: Marker?, msg: String?, t: Throwable?) {}
    override fun isInfoEnabled(): Boolean = false
    override fun isInfoEnabled(marker: Marker?): Boolean = false
    override fun info(msg: String?) {}
    override fun info(format: String?, arg: Any?) {}
    override fun info(format: String?, arg1: Any?, arg2: Any?) {}
    override fun info(format: String?, vararg arguments: Any?) {}
    override fun info(msg: String?, t: Throwable?) {}
    override fun info(marker: Marker?, msg: String?) {}
    override fun info(marker: Marker?, format: String?, arg: Any?) {}
    override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}
    override fun info(marker: Marker?, format: String?, vararg arguments: Any?) {}
    override fun info(marker: Marker?, msg: String?, t: Throwable?) {}
    override fun isWarnEnabled(): Boolean = false
    override fun isWarnEnabled(marker: Marker?): Boolean = false
    override fun warn(msg: String?) {}
    override fun warn(format: String?, arg: Any?) {}
    override fun warn(format: String?, arg1: Any?, arg2: Any?) {}
    override fun warn(format: String?, vararg arguments: Any?) {}
    override fun warn(msg: String?, t: Throwable?) {}
    override fun warn(marker: Marker?, msg: String?) {}
    override fun warn(marker: Marker?, format: String?, arg: Any?) {}
    override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}
    override fun warn(marker: Marker?, format: String?, vararg arguments: Any?) {}
    override fun warn(marker: Marker?, msg: String?, t: Throwable?) {}
    override fun isErrorEnabled(): Boolean = false
    override fun isErrorEnabled(marker: Marker?): Boolean = false
    override fun error(msg: String?) {}
    override fun error(format: String?, arg: Any?) {}
    override fun error(format: String?, arg1: Any?, arg2: Any?) {}
    override fun error(format: String?, vararg arguments: Any?) {}
    override fun error(msg: String?, t: Throwable?) {}
    override fun error(marker: Marker?, msg: String?) {}
    override fun error(marker: Marker?, format: String?, arg: Any?) {}
    override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}
    override fun error(marker: Marker?, format: String?, vararg arguments: Any?) {}
    override fun error(marker: Marker?, msg: String?, t: Throwable?) {}
}

class HeavyMutexTest {
    @Test
    fun testHeavyMutexLocking(): Unit = runBlocking {
        val testLogger = TestLogger()
        val heavyMutex = HeavyMutex("Heavy", testLogger)
        heavyMutex.withLock {
            delay(100)
        }

        val durationMs = testLogger.lastDurationMs
        requireNotNull(durationMs) { "Duration was not logged" }

        // Check if the duration is roughly 100ms (between 90ms and 110ms to account for small variations)
        assertTrue(
            durationMs in 90..110,
            "Expected lock duration around 100ms but was ${durationMs}ms"
        )
    }
}
