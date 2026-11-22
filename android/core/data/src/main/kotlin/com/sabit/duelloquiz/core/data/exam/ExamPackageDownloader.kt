package com.sabit.duelloquiz.core.data.exam

import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamPackage
import com.sabit.duelloquiz.core.network.ExamNetworkDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Download state of one variant's package (EXAM-03, exam-info.md bottom area). */
sealed interface ExamDownloadState {
    data object Idle : ExamDownloadState

    /** Waiting for the worker (or for the network: [waitingForNetwork]). */
    data class Queued(val waitingForNetwork: Boolean = false) : ExamDownloadState

    data class Running(val bytesDone: Long, val bytesTotal: Long) : ExamDownloadState {
        val fraction: Float get() = if (bytesTotal <= 0) 0f else (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
    }

    /** The network dropped; resumes by itself (EXAM-03). */
    data class PausedOffline(val bytesDone: Long, val bytesTotal: Long) : ExamDownloadState

    data class Failed(val reason: ExamDownloadFailure, val bytesDone: Long = 0, val bytesTotal: Long = 0) : ExamDownloadState

    data class Done(val version: Int) : ExamDownloadState
}

sealed interface ExamDownloadFailure {
    /** 5xx, timeout or an unreadable response. */
    data object Server : ExamDownloadFailure

    /** A file failed its hash check twice (one automatic retry). */
    data object Verify : ExamDownloadFailure

    /** Not enough free space; [neededBytes] = package size × 1.1. */
    data class Storage(val neededBytes: Long) : ExamDownloadFailure

    /** 404 / `variant_unavailable`: the variant is gone; the local package was deleted. */
    data object Unavailable : ExamDownloadFailure

    data object Offline : ExamDownloadFailure
}

/** Live progress of package downloads in this process, shared by the info screen and catalogue cards. */
@Singleton
class ExamDownloadProgress @Inject constructor() {
    private val states = MutableStateFlow<Map<String, ExamDownloadState>>(emptyMap())
    val all: StateFlow<Map<String, ExamDownloadState>> = states.asStateFlow()

    fun set(variantId: String, state: ExamDownloadState) {
        states.update { it + (variantId to state) }
    }

    fun clear(variantId: String) {
        states.update { it - variantId }
    }
}

/**
 * Downloads one package: free-space check before the first byte, package JSON (no keys), then
 * every media file resumed from its partial file, size + SHA-256 verified with one automatic
 * retry, the package file committed last (EXAM-03). Plain class so the rules are unit-tested; the
 * WorkManager worker only runs it.
 */
class ExamPackageDownloader(
    private val network: ExamNetworkDataSource,
    private val store: ExamPackageStore,
    private val progress: ExamDownloadProgress,
    /** Reports at most every [PROGRESS_INTERVAL_MS] (EXAM-03: updates ≤ every 250 ms). */
    private val nowMs: () -> Long,
) {

    sealed interface Outcome {
        data class Done(val pkg: ExamPackage) : Outcome

        /** Transient (offline / timeout): the worker retries when the network returns. */
        data object Retry : Outcome

        data class Failed(val failure: ExamDownloadFailure) : Outcome
    }

    suspend fun download(variantId: String, version: Int?, expectedBytes: Long): Outcome {
        val needed = (expectedBytes * STORAGE_FACTOR).toLong()
        val pkg = when (val fetched = network.getPackage(variantId, version)) {
            is DataResult.Success -> fetched.value
            is DataResult.Failure -> return failureOf(variantId, fetched.error, bytesDone = 0, total = expectedBytes)
        }
        val total = pkg.packageBytes.takeIf { it > 0 } ?: expectedBytes
        val onDisk = pkg.media.sumOf { store.bytesOnDisk(it) }
        if (store.usableBytes() < needed - onDisk) {
            progress.set(variantId, ExamDownloadState.Failed(ExamDownloadFailure.Storage(needed), onDisk, total))
            return Outcome.Failed(ExamDownloadFailure.Storage(needed))
        }
        var verifiedBytes = pkg.media.filter { store.verifiedMediaFile(it) != null }.sumOf { it.bytes }
        var lastReport = Long.MIN_VALUE
        fun report(current: Long, force: Boolean = false) {
            val now = nowMs()
            if (force || now - lastReport >= PROGRESS_INTERVAL_MS) {
                lastReport = now
                progress.set(variantId, ExamDownloadState.Running((verifiedBytes + current).coerceAtMost(total), total))
            }
        }
        report(0, force = true)
        for (media in pkg.media) {
            if (store.verifiedMediaFile(media) != null) continue
            var attempt = 0
            while (true) {
                val result = network.downloadMedia(media.url, store.partialFile(media)) { bytes -> report(bytes) }
                if (result is DataResult.Failure) {
                    return failureOf(variantId, result.error, bytesDone = verifiedBytes, total = total)
                }
                if (store.verifyAndCommit(media)) break
                attempt++
                if (attempt > VERIFY_RETRIES) {
                    progress.set(variantId, ExamDownloadState.Failed(ExamDownloadFailure.Verify, verifiedBytes, total))
                    return Outcome.Failed(ExamDownloadFailure.Verify)
                }
            }
            verifiedBytes += media.bytes
            report(0, force = true)
        }
        // The old version (EXAM-03 "Yenilənib") is removed only now, after the new one is complete.
        store.commitPackage(pkg)
        progress.set(variantId, ExamDownloadState.Done(pkg.version))
        return Outcome.Done(pkg)
    }

    private fun failureOf(variantId: String, error: DataError, bytesDone: Long, total: Long): Outcome = when {
        error == DataError.Offline || error == DataError.Timeout || error == DataError.ServerUnreachable -> {
            progress.set(variantId, ExamDownloadState.PausedOffline(bytesDone, total))
            Outcome.Retry
        }
        error is DataError.Api && (error.code == ApiErrorCode.ExamVariantNotFound || error.code == ApiErrorCode.VariantUnavailable) -> {
            store.deleteVariant(variantId)
            progress.set(variantId, ExamDownloadState.Failed(ExamDownloadFailure.Unavailable))
            Outcome.Failed(ExamDownloadFailure.Unavailable)
        }
        else -> {
            progress.set(variantId, ExamDownloadState.Failed(ExamDownloadFailure.Server, bytesDone, total))
            Outcome.Failed(ExamDownloadFailure.Server)
        }
    }

    companion object {
        const val STORAGE_FACTOR = 1.1
        const val VERIFY_RETRIES = 1
        const val PROGRESS_INTERVAL_MS = 250L
    }
}
