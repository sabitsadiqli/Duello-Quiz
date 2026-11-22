package com.sabit.duelloquiz.core.data.exam

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Dependencies of the exam workers (no hilt-work: the workers read them through this entry point). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExamWorkerEntryPoint {
    fun examRepository(): ExamRepository

    fun examPackageDownloader(): ExamPackageDownloader
}

internal class WorkManagerExamScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExamWorkScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<ExamSyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, SYNC_BACKOFF_S, TimeUnit.SECONDS)
            .build()
        // A sync already running finishes first; the new one runs after it with the new queue.
        workManager.enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun enqueueDownload(variantId: String, version: Int?, expectedBytes: Long) {
        val request = OneTimeWorkRequestBuilder<ExamDownloadWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.LINEAR, DOWNLOAD_BACKOFF_S, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    ExamDownloadWorker.KEY_VARIANT to variantId,
                    ExamDownloadWorker.KEY_VERSION to (version ?: NO_VERSION),
                    ExamDownloadWorker.KEY_BYTES to expectedBytes,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(downloadWork(variantId), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancelDownload(variantId: String) {
        workManager.cancelUniqueWork(downloadWork(variantId))
    }

    override fun downloadActive(variantId: String): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(downloadWork(variantId))
            .map { infos -> infos.any { !it.state.isFinished } }
            .distinctUntilChanged()

    companion object {
        const val SYNC_WORK = "exam-sync"
        const val NO_VERSION = -1
        private const val SYNC_BACKOFF_S = 30L
        private const val DOWNLOAD_BACKOFF_S = 15L

        fun downloadWork(variantId: String) = "exam-download-$variantId"
    }
}

/**
 * Uploads every queued answer, listening state, submit and mark (EXAM-13, EXAM-14, EXAM-16) once
 * the network is back. Retries with backoff; attempts past their late-upload window are closed
 * by the repository, so the queue always drains.
 */
internal class ExamSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, ExamWorkerEntryPoint::class.java).examRepository()
        return when {
            repository.syncAllPending() -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 40
    }
}

/** Downloads one exam package in the background (EXAM-03: continues after leaving the info screen). */
internal class ExamDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val variantId = inputData.getString(KEY_VARIANT) ?: return Result.failure()
        val version = inputData.getInt(KEY_VERSION, WorkManagerExamScheduler.NO_VERSION).takeIf { it > 0 }
        val bytes = inputData.getLong(KEY_BYTES, 0)
        val downloader = EntryPointAccessors.fromApplication(applicationContext, ExamWorkerEntryPoint::class.java)
            .examPackageDownloader()
        return when (downloader.download(variantId, version, bytes)) {
            is ExamPackageDownloader.Outcome.Done -> Result.success()
            ExamPackageDownloader.Outcome.Retry -> Result.retry()
            is ExamPackageDownloader.Outcome.Failed -> Result.failure()
        }
    }

    companion object {
        const val KEY_VARIANT = "variantId"
        const val KEY_VERSION = "version"
        const val KEY_BYTES = "bytes"
    }
}
