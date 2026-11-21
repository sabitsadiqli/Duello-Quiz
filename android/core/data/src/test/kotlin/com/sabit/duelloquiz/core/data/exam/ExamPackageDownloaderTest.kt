package com.sabit.duelloquiz.core.data.exam

import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamPackage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExamPackageDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val network = FakeExamNetwork()
    private val progress = ExamDownloadProgress()
    private val audio = ByteArray(2_000) { (it % 7).toByte() }
    private val media = FakeExamNetwork.media("https://m/a.m4a", audio)

    private fun pkg(version: Int = 2) = ExamPackage(
        variantId = "v1",
        version = version,
        templateId = "buraxilis-ingilis",
        language = "en",
        durationMs = 3_600_000,
        graceMs = 120_000,
        optionCount = 5,
        listeningMaxPlays = 2,
        listeningPlayCountsAfterMs = 10_000,
        maxTextLength = 1_000,
        sections = emptyList(),
        groups = emptyList(),
        tasks = emptyList(),
        media = listOf(media),
        packageBytes = audio.size.toLong(),
    )

    private fun downloader(store: ExamPackageStore = ExamPackageStore(tmp.root)) =
        ExamPackageDownloader(network, store, progress) { 0L }

    @Test
    fun verifiedDownloadCommitsThePackage() = runTest {
        network.packageResult = DataResult.Success(pkg())
        network.mediaBytes[media.url] = ArrayDeque(listOf(audio))
        val store = ExamPackageStore(tmp.root)

        val outcome = downloader(store).download("v1", null, audio.size.toLong())

        assertTrue(outcome is ExamPackageDownloader.Outcome.Done)
        assertEquals(2, store.downloadedVersion("v1"))
        assertNotNull(store.verifiedMediaFile(media))
        assertEquals(ExamDownloadState.Done(2), progress.all.value["v1"])
    }

    @Test
    fun hashMismatchIsRetriedOnceThenFails() = runTest {
        network.packageResult = DataResult.Success(pkg())
        val corrupt = audio.copyOf().also { it[0] = 99 }
        network.mediaBytes[media.url] = ArrayDeque(listOf(corrupt, corrupt))
        val store = ExamPackageStore(tmp.root)

        val outcome = downloader(store).download("v1", null, audio.size.toLong())

        assertEquals(ExamPackageDownloader.Outcome.Failed(ExamDownloadFailure.Verify), outcome)
        assertNull(store.downloadedVersion("v1"))
        assertFalse(store.partialFile(media).exists())
    }

    @Test
    fun hashMismatchRecoversOnTheAutomaticRetry() = runTest {
        network.packageResult = DataResult.Success(pkg())
        val corrupt = audio.copyOf().also { it[0] = 99 }
        network.mediaBytes[media.url] = ArrayDeque(listOf(corrupt, audio))

        val outcome = downloader().download("v1", null, audio.size.toLong())

        assertTrue(outcome is ExamPackageDownloader.Outcome.Done)
    }

    @Test
    fun offlineMidwayKeepsThePartAndRetries() = runTest {
        network.packageResult = DataResult.Success(pkg())
        network.mediaError = DataError.Offline

        val outcome = downloader().download("v1", null, audio.size.toLong())

        assertEquals(ExamPackageDownloader.Outcome.Retry, outcome)
        assertTrue(progress.all.value["v1"] is ExamDownloadState.PausedOffline)
    }

    @Test
    fun resumeAppendsToThePartialFile() = runTest {
        network.packageResult = DataResult.Success(pkg())
        val store = ExamPackageStore(tmp.root)
        store.partialFile(media).apply { parentFile?.mkdirs() }.writeBytes(audio.copyOfRange(0, 500))
        network.mediaBytes[media.url] = ArrayDeque(listOf(audio.copyOfRange(500, audio.size)))

        val outcome = downloader(store).download("v1", null, audio.size.toLong())

        assertTrue(outcome is ExamPackageDownloader.Outcome.Done)
    }

    @Test
    fun unpublishedVariantDeletesTheLocalPackage() = runTest {
        val store = ExamPackageStore(tmp.root)
        network.packageResult = DataResult.Success(pkg())
        network.mediaBytes[media.url] = ArrayDeque(listOf(audio))
        downloader(store).download("v1", null, audio.size.toLong())
        network.packageResult = DataResult.Failure(DataError.Api(ApiErrorCode.ExamVariantNotFound))

        val outcome = downloader(store).download("v1", null, audio.size.toLong())

        assertEquals(ExamPackageDownloader.Outcome.Failed(ExamDownloadFailure.Unavailable), outcome)
        assertNull(store.readPackage("v1"))
    }

    @Test
    fun newVersionReplacesTheOldOneOnlyAfterSuccess() = runTest {
        val store = ExamPackageStore(tmp.root)
        network.packageResult = DataResult.Success(pkg(version = 1))
        network.mediaBytes[media.url] = ArrayDeque(listOf(audio))
        downloader(store).download("v1", null, audio.size.toLong())

        network.packageResult = DataResult.Failure(DataError.Server)
        downloader(store).download("v1", null, audio.size.toLong())
        assertEquals(1, store.downloadedVersion("v1"))

        network.packageResult = DataResult.Success(pkg(version = 2))
        downloader(store).download("v1", null, audio.size.toLong())
        assertEquals(2, store.downloadedVersion("v1"))
        assertNull(store.readPackage("v1", version = 1))
    }

    @Test
    fun tooLittleStorageFailsBeforeTheFirstByte() = runTest {
        network.packageResult = DataResult.Success(pkg().copy(packageBytes = Long.MAX_VALUE / 4))

        val outcome = downloader().download("v1", null, Long.MAX_VALUE / 4)

        assertTrue((outcome as ExamPackageDownloader.Outcome.Failed).failure is ExamDownloadFailure.Storage)
        assertTrue(network.mediaBytes.isEmpty())
    }
}
