package io.sebi.conversion

import io.ktor.events.*
import io.ktor.server.application.*
import io.sebi.config.AppConfig
import io.sebi.datastructures.SuspendingQueue
import io.sebi.datastructures.shaHashed
import io.sebi.ffmpeg.FfmpegTask
import io.sebi.ffmpeg.getMediaType
import io.sebi.library.MediaLibraryEntry
import io.sebi.library.file
import io.sebi.library.id
import io.sebi.library.persist
import io.sebi.storage.MetadataResult
import io.sebi.storage.MetadataStorage
import io.sebi.storage.VideoStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

class ConversionManager(
    private val metadataStorage: MetadataStorage,
    private val videoStorage: VideoStorage,
    private val serverEvents: Events,
) {
    private val logger = LoggerFactory.getLogger("Conversion Manager")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val immediateQueue = SuspendingQueue<ConversionTask>()
    private val scheduledQueue = ConcurrentLinkedQueue<ConversionTask>()
    private val problematicTasks = mutableListOf<ProblematicConversionTask>()
    private val runningTask = AtomicReference<ConversionTask?>(null)
    private val queueMutex = Mutex()
    private val executionMutex = Mutex()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val queueFile = File(AppConfig.userConfigPath, "conversions.json")

    init {
        scope.launch(Dispatchers.IO) {
            processImmediateQueue()
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
        logger.info("Shutdown initiated. Cancelling conversion scope.")
        scope.cancel("Cancelling conversion manager tasks in preparation for shutdown.")
        persistQueue()
    }

    suspend fun enqueueConversion(
        sourceEntry: MediaLibraryEntry,
        mode: ConversionMode,
        options: ConversionOptions,
        convertAndDeleteOriginal: Boolean = false,
    ): ConversionTask {
        val task = ConversionTask(
            id = UUID.randomUUID().toString(),
            sourceId = sourceEntry.id,
            sourceTitle = sourceEntry.name,
            mode = mode,
            options = options,
            convertAndDeleteOriginal = convertAndDeleteOriginal,
            createdAt = System.currentTimeMillis()
        )
        queueMutex.withLock {
            when (mode) {
                ConversionMode.IMMEDIATE -> immediateQueue.add(task)
                ConversionMode.SCHEDULED -> scheduledQueue.add(task)
            }
            persistQueueLocked()
        }
        return task
    }

    suspend fun snapshot(): ConversionQueueSnapshot = queueMutex.withLock {
        val queued = buildList {
            immediateQueue.forEach { add(it.toQueueEntry(ConversionStatus.QUEUED)) }
            scheduledQueue.forEach { add(it.toQueueEntry(ConversionStatus.QUEUED)) }
        }
        val running = runningTask.get()?.toQueueEntry(ConversionStatus.RUNNING)
        ConversionQueueSnapshot(
            queued = queued,
            running = running,
            problematic = problematicTasks.toList()
        )
    }

    suspend fun summaryForSource(sourceId: String): ConversionSourceSummary {
        val (queued, running, problematic) = queueMutex.withLock {
            val queued = buildList {
                immediateQueue.forEach { if (it.sourceId == sourceId) add(it.toQueueEntry(ConversionStatus.QUEUED)) }
                scheduledQueue.forEach { if (it.sourceId == sourceId) add(it.toQueueEntry(ConversionStatus.QUEUED)) }
            }
            val running = runningTask.get()
                ?.takeIf { it.sourceId == sourceId }
                ?.toQueueEntry(ConversionStatus.RUNNING)
            val problematic = problematicTasks.filter { it.sourceId == sourceId }
            Triple(queued, running, problematic)
        }
        val alreadyConverted = metadataStorage
            .listAllMetadata()
            .any { entry -> entry.tags.any { it == "$ORIGIN_TAG_PREFIX$sourceId" } }
        return ConversionSourceSummary(
            sourceId = sourceId,
            alreadyConverted = alreadyConverted,
            queued = queued,
            running = running,
            problematic = problematic
        )
    }

    suspend fun runScheduledConversions() {
        logger.info("Running scheduled conversions.")
        while (true) {
            val task = scheduledQueue.poll() ?: break
            runConversion(task)
            queueMutex.withLock {
                persistQueueLocked()
            }
        }
    }

    suspend fun restoreQueue() {
        if (!queueFile.exists()) {
            logger.info("No conversions queue file found.")
            return
        }
        val state = try {
            json.decodeFromString(PersistedConversionState.serializer(), queueFile.readText())
        } catch (e: Exception) {
            logger.error("Failed to restore conversions queue.", e)
            return
        }

        queueMutex.withLock {
            state.immediate.forEach { immediateQueue.add(it) }
            state.scheduled.forEach { scheduledQueue.add(it) }
            state.running?.let { task ->
                when (task.mode) {
                    ConversionMode.IMMEDIATE -> immediateQueue.add(task)
                    ConversionMode.SCHEDULED -> scheduledQueue.add(task)
                }
            }
            problematicTasks.clear()
            problematicTasks.addAll(state.problematic)
        }
        val restoredCount = state.immediate.size + state.scheduled.size + if (state.running != null) 1 else 0
        logger.info("Restored $restoredCount conversion tasks.")
    }

    suspend fun persistQueue() {
        queueMutex.withLock {
            persistQueueLocked()
        }
    }

    private fun persistQueueLocked() {
        val running = runningTask.get()
        val immediate = immediateQueue.toList()
        val scheduled = scheduledQueue.toList()
        val state = PersistedConversionState(
            immediate = immediate,
            scheduled = scheduled,
            running = running,
            problematic = problematicTasks.toList()
        )
        queueFile.parentFile?.mkdirs()
        queueFile.writeText(json.encodeToString(PersistedConversionState.serializer(), state))
    }

    private suspend fun processImmediateQueue() {
        while (true) {
            val task = immediateQueue.remove()
            runConversion(task)
            queueMutex.withLock {
                persistQueueLocked()
            }
        }
    }

    private suspend fun runConversion(task: ConversionTask) {
        executionMutex.withLock {
            runningTask.set(task)
            queueMutex.withLock {
                persistQueueLocked()
            }
            try {
                executeConversion(task)
            } catch (e: Exception) {
                logger.error("Conversion failed for ${task.sourceId}", e)
                queueMutex.withLock {
                    problematicTasks.add(task.toProblematic(e))
                }
            } finally {
                runningTask.set(null)
                queueMutex.withLock {
                    persistQueueLocked()
                }
            }
        }
    }

    private suspend fun executeConversion(task: ConversionTask) {
        val entry = when (val result = metadataStorage.retrieveMetadata(task.sourceId)) {
            is MetadataResult.Just -> result.entry
            MetadataResult.None,
            MetadataResult.Tombstone,
                -> error("Source entry ${task.sourceId} not found.")
        }
        val sourceFile = entry.file
        if (!sourceFile.exists()) {
            error("Source file ${sourceFile.absolutePath} does not exist.")
        }

        val options = task.options
        validateOptions(options)

        val outputFile = File.createTempFile("reelchest-convert-", ".mp4")
        try {
            val parameters = buildFfmpegParameters(sourceFile, outputFile, options)
            FfmpegTask(parameters).execute(
                directory = sourceFile.parentFile,
                mutexLabel = "Conversion ${task.sourceId}"
            )
            if (!outputFile.exists() || outputFile.length() <= 0) {
                error("Conversion failed to produce a valid output file.")
            }
            try {
                getMediaType(outputFile)
            } catch (e: Exception) {
                error("Converted file is not a valid video: ${e.message}")
            }
            val newId = UUID.randomUUID().toString().shaHashed()
            val newTags = (entry.tags + listOf(CONVERTED_TAG, "$ORIGIN_TAG_PREFIX${task.sourceId}")).toSet()
            val newEntry = MediaLibraryEntry(
                name = entry.name,
                originUrl = entry.originUrl,
                tags = newTags,
                uid = newId,
                creationDate = System.currentTimeMillis() / 1000,
                hits = 0,
                markedForDeletion = false,
            )
            videoStorage.storeVideo(newId, outputFile.toPath())
            newEntry.persist(metadataStorage)
            val persisted = metadataStorage.retrieveMetadata(newId).just()
                ?: error("Converted entry failed to persist.")
            if (!persisted.file.exists()) {
                error("Converted video file missing after persistence.")
            }
            if (task.convertAndDeleteOriginal) {
                entry.markedForDeletion = true
                entry.persist(metadataStorage)
                logger.info("Marked original ${entry.id} for deletion after conversion.")
            }
            logger.info("Conversion completed for ${task.sourceId}, created ${newEntry.id}")
        } finally {
            outputFile.delete()
        }
    }

    fun validateOptions(options: ConversionOptions) {
        if (options.crf !in 18..28) {
            error("CRF must be between 18 and 28.")
        }
        if (options.fps != null && options.fps <= 0) {
            error("FPS must be positive.")
        }
        val hasWidth = options.maxWidth != null
        val hasHeight = options.maxHeight != null
        if (hasWidth != hasHeight) {
            error("Both maxWidth and maxHeight must be set together.")
        }
        if (options.audioBitrateKbps <= 0) {
            error("Audio bitrate must be positive.")
        }
    }

    private fun buildFfmpegParameters(
        inputFile: File,
        outputFile: File,
        options: ConversionOptions,
    ): List<String> {
        val filters = buildList {
            if (options.maxWidth != null && options.maxHeight != null) {
                add(
                    "scale=w='min(${options.maxWidth},iw)':h='min(${options.maxHeight},ih)'" +
                            ":force_original_aspect_ratio=decrease:force_divisible_by=2"
                )
            }
            options.fps?.let { add("fps=$it") }
        }
        return buildList {
            add("-y")
            add("-i")
            add(inputFile.absolutePath)
            if (filters.isNotEmpty()) {
                add("-vf")
                add(filters.joinToString(","))
            }
            add("-c:v")
            add("libx264")
            add("-crf")
            add(options.crf.toString())
            add("-preset")
            add("medium")
            add("-pix_fmt")
            add("yuv420p")
            add("-c:a")
            add("aac")
            add("-b:a")
            add("${options.audioBitrateKbps}k")
            add("-movflags")
            add("+faststart")
            add(outputFile.absolutePath)
        }
    }

    private fun ConversionTask.toQueueEntry(status: ConversionStatus): ConversionQueueEntry {
        return ConversionQueueEntry(
            id = id,
            sourceId = sourceId,
            sourceTitle = sourceTitle,
            mode = mode,
            options = options,
            convertAndDeleteOriginal = convertAndDeleteOriginal,
            status = status,
            createdAt = createdAt,
        )
    }

    private fun ConversionTask.toProblematic(error: Exception): ProblematicConversionTask {
        return ProblematicConversionTask(
            id = id,
            sourceId = sourceId,
            sourceTitle = sourceTitle,
            mode = mode,
            options = options,
            convertAndDeleteOriginal = convertAndDeleteOriginal,
            createdAt = createdAt,
            error = error.stackTraceToString()
        )
    }
}

@kotlinx.serialization.Serializable
private data class PersistedConversionState(
    val immediate: List<ConversionTask>,
    val scheduled: List<ConversionTask>,
    val running: ConversionTask? = null,
    val problematic: List<ProblematicConversionTask>,
)
