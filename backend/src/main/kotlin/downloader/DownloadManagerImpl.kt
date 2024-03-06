package io.sebi.downloader

import io.ktor.events.*
import io.ktor.server.application.*
import io.sebi.datastructures.SuspendingQueue
import io.sebi.datastructures.shaHashed
import io.sebi.network.NetworkManager
import io.sebi.storage.MetadataResult
import io.sebi.storage.MetadataStorage
import io.sebi.urldecoder.UrlDecoder
import io.sebi.urldecoder.makeDownloadTask
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

class DownloadManagerImpl(
    private val metadataStorage: MetadataStorage,
    private val urlDecoder: UrlDecoder,
    private val networkManager: NetworkManager,
    private val defaultOnComplete: suspend (CompletedDownloadTask) -> Unit,
    private val serverEvents: Events
) : DownloadManager {
    val scope = CoroutineScope(context = Dispatchers.Default)
    val logger = LoggerFactory.getLogger("Download Manager")

    override val allDownloads: List<WithOriginUrl>
        get() {
            return queuedDownloads + workers.mapNotNull { it.currentTask } + problematicDownloads
        }


    @OptIn(ExperimentalStdlibApi::class)
    override fun getDownloads(vararg d: DownloadType): List<DownloadTask> {
        return buildList {
            for (type in d.toSet()) {
                when (type) {
                    DownloadType.QUEUED -> addAll(queuedDownloads.toList())
                    DownloadType.CURRENT -> addAll(workers.mapNotNull { it.currentTask }.toList())
                }
            }
        }
    }

    override val queuedDownloads: SuspendingQueue<DownloadTask> = SuspendingQueue<DownloadTask>()
    override val finishedDownloads: SuspendingQueue<CompletedDownloadTask> = SuspendingQueue<CompletedDownloadTask>()
    override val problematicDownloads: MutableList<ProblematicTask> = mutableListOf<ProblematicTask>()
    override val downloadsInProgress: List<DownloadTask>
        get() {
            return workers.mapNotNull { it.currentTask }
        }

    init {
        scope.launch(Dispatchers.IO) {
            startWorkers()
        }
        setupShutdownHook()
    }

    private fun setupShutdownHook() {
        serverEvents.subscribe(ApplicationStopped) {
            runBlocking {
                shutdown()
            }
        }
    }

    suspend fun shutdown() {
        logger.info("Shutdown initiated! Cancelling scope...")
        scope.cancel("Cancelling Download Manager tasks in preparation for shutdown.")
        scope.coroutineContext.job.join()
        persistQueue()
    }

    override fun downloadProgressForId(id: String): Double? {
        queuedDownloads.firstOrNull { it.originUrl.shaHashed() == id }?.let {
            return it.progress
        }

        workers.mapNotNull { it.currentTask }.firstOrNull { it.originUrl.shaHashed() == id }?.let {
            return it.progress
        }

        finishedDownloads.firstOrNull { it.originUrl.shaHashed() == id }?.let {
            return 1.0
        }

        return null
    }

    override fun enqueueTask(d: DownloadTask, skipDuplicatesCheck: Boolean) {
        if (skipDuplicatesCheck) {
            logger.info("Adding job with skipped duplicates check.")
            queuedDownloads.add(d)
            return
        }

        val abortConditions = listOf(queuedDownloads.any { it.originUrl == d.originUrl } to "duplicate download job",
            workers.mapNotNull { it.currentTask }.any { it.originUrl == d.originUrl } to "download job in progress",
            (runBlocking { metadataStorage.retrieveMetadata(d.originUrl.shaHashed()) } != MetadataResult.None) to "URL already in metadata storage")

        abortConditions.firstOrNull { it.first }?.let {
            logger.info("Attempting to add ${it.second}, not adding to queue.")
            return
        }

        logger.info("Adding download job.")
        queuedDownloads.add(d)
    }

    override fun persistQueue() {
        logger.info("Persisting queue..")
        val dtos = buildList {
            for (queued in queuedDownloads.toList()) {
                add(DownloadTaskDTO.from(queued))
            }
            for (problematic in problematicDownloads.toList()) {
                add(DownloadTaskDTO.from(problematic))
            }
        }
        File("userConfig/queue.json").writeText(
            Json.encodeToString(dtos)
        )
    }

    override fun restoreQueue() {
        val list =
            Json.runCatching { decodeFromString<List<DownloadTaskDTO>>(File("userConfig/queue.json").readText()).distinctBy { it.originUrl } }
        list.onFailure {
            logger.error("Queue restore failed. $it")
        }.onSuccess {
            it.reversed().forEach {
                enqueueTask(urlDecoder.makeDownloadTask(it.originUrl, defaultOnComplete))
            }
            logger.info("Re-enqueued ${it.size} tasks.")
        }
    }

    val workers = mutableListOf<DownloadWorker>()
    fun CoroutineScope.startWorkers(n: Int = 1) {
        repeat(n) {
            val newWorker = DownloadWorker(networkManager = networkManager, provideDownload = {
                queuedDownloads.remove()
            }, id = it, onComplete = {
                finishedDownloads.add(it)
            }, onError = { task, error ->
                // eof, connection reset or other stuff.
                logger.error("Worker $it failed with $error, adding to problematic tasks.")
                logger.error(error.stackTraceToString())
                problematicDownloads.add(ProblematicTask(task.originUrl, error))
            })
            launch { newWorker.run() }
            workers += newWorker
        }
    }
}