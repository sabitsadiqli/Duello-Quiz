package com.sabit.duelloquiz.feature.settings.licenses

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sabit.duelloquiz.core.data.licenses.LicenseRepository
import com.sabit.duelloquiz.core.model.OpenSourceComponent
import com.sabit.duelloquiz.core.model.OpenSourceLicense
import com.sabit.duelloquiz.core.model.OpenSourceNotices
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LicensesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apache = OpenSourceLicense("Apache License 2.0", "https://spdx.org/licenses/Apache-2.0.html", "Apache License\n\n  TERMS\n\n\n1. Definitions.\n")
    private val nunito = OpenSourceComponent("asset:nunito", "Nunito", null, "Vernon Adams", "https://github.com/googlefonts/nunito", listOf(OpenSourceLicense("SIL Open Font License 1.1", null, null)))
    private val coil = OpenSourceComponent("io.coil-kt.coil3:coil", "Coil", "3.6.3", null, null, listOf(apache, OpenSourceLicense("MIT License", null, "MIT")))
    private val bare = OpenSourceComponent("x:y", "Bare", "1.0", null, null, emptyList())

    private class FakeRepository(private val notices: OpenSourceNotices) : LicenseRepository {
        override suspend fun notices() = notices
        override suspend fun component(id: String) = (notices.assets + notices.libraries.orEmpty()).firstOrNull { it.id == id }
    }

    @Test
    fun listShowsManualAndGeneratedRows() = runTest {
        val vm = LicensesViewModel(FakeRepository(OpenSourceNotices(listOf(nunito), listOf(coil, bare))))
        runCurrent()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertFalse(state.librariesError)
        assertEquals(listOf(LicenseRowUi("asset:nunito", "Nunito", null, "SIL Open Font License 1.1")), state.assets)
        assertEquals(
            listOf(
                LicenseRowUi("io.coil-kt.coil3:coil", "Coil", "3.6.3", "Apache License 2.0, MIT License"),
                LicenseRowUi("x:y", "Bare", "1.0", null),
            ),
            state.libraries,
        )
    }

    @Test
    fun unreadableMetadataShowsInlineErrorButKeepsAssets() = runTest {
        val vm = LicensesViewModel(FakeRepository(OpenSourceNotices(listOf(nunito), libraries = null)))
        runCurrent()

        assertTrue(vm.uiState.value.librariesError)
        assertEquals(1, vm.uiState.value.assets.size)
        assertTrue(vm.uiState.value.libraries.isEmpty())
    }

    @Test
    fun rowAndBackClicksBecomeEvents() = runTest {
        val vm = LicensesViewModel(FakeRepository(OpenSourceNotices(listOf(nunito), emptyList())))
        vm.events.test {
            vm.onAction(LicensesAction.RowClicked("asset:nunito"))
            assertEquals(LicensesEvent.OpenDetail("asset:nunito"), awaitItem())
            vm.onAction(LicensesAction.BackClicked)
            assertEquals(LicensesEvent.NavigateBack, awaitItem())
        }
    }

    @Test
    fun detailSplitsTextIntoParagraphsAndOpensLinks() = runTest {
        val repository = FakeRepository(OpenSourceNotices(listOf(nunito), listOf(coil)))
        val vm = LicenseDetailViewModel(SavedStateHandle(mapOf(LicenseDetailViewModel.ARG_ID to coil.id)), repository)
        runCurrent()

        val detail = requireNotNull(vm.uiState.value.detail)
        assertEquals("3.6.3", detail.version)
        assertNull(detail.websiteUrl)
        assertEquals("https://spdx.org/licenses/Apache-2.0.html", detail.licenseUrl)
        assertEquals(listOf("Apache License", "TERMS", "1. Definitions."), detail.licenses[0].paragraphs)
        assertEquals(listOf("MIT"), detail.licenses[1].paragraphs)

        vm.events.test {
            vm.onAction(LicenseDetailAction.OpenLicenseClicked)
            assertEquals(LicenseDetailEvent.OpenUrl("https://spdx.org/licenses/Apache-2.0.html"), awaitItem())
            // No website URL: the button is hidden and the action does nothing.
            vm.onAction(LicenseDetailAction.OpenWebsiteClicked)
            expectNoEvents()
        }
    }

    @Test
    fun detailWithoutTextMarksItMissing() = runTest {
        val vm = LicenseDetailViewModel(
            SavedStateHandle(mapOf(LicenseDetailViewModel.ARG_ID to nunito.id)),
            FakeRepository(OpenSourceNotices(listOf(nunito), null)),
        )
        runCurrent()

        val detail = requireNotNull(vm.uiState.value.detail)
        assertNull(detail.licenses.single().paragraphs)
        assertNull(detail.licenseUrl)
        assertEquals("https://github.com/googlefonts/nunito", detail.websiteUrl)
    }

    @Test
    fun unknownIdNavigatesBack() = runTest {
        val vm = LicenseDetailViewModel(
            SavedStateHandle(mapOf(LicenseDetailViewModel.ARG_ID to "gone")),
            FakeRepository(OpenSourceNotices(listOf(nunito), emptyList())),
        )
        vm.events.test {
            assertEquals(LicenseDetailEvent.NavigateBack, awaitItem())
        }
        assertNull(vm.uiState.value.detail)
        assertFalse(vm.uiState.value.loading)
    }
}
