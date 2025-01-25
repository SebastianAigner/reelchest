import io.sebi.heavymutex.HeavyMutex
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class MathTest {

    @Test
    fun testHeavyMutexLocking(): Unit = runBlocking {
        val heavyMutex = HeavyMutex("Heavy")
        heavyMutex.withLock {
            delay(100)
        }
    }
}