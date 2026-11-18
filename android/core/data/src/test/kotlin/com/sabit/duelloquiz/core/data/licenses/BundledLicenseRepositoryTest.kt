package com.sabit.duelloquiz.core.data.licenses

import com.sabit.duelloquiz.core.common.Telemetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BundledLicenseRepositoryTest {

    private class RecordingTelemetry : Telemetry {
        val errors = mutableListOf<Throwable>()
        override fun setUserId(userId: String?) = Unit
        override fun recordNonFatal(throwable: Throwable) {
            errors += throwable
        }
    }

    private class Files(private val json: () -> String, private val ofl: String? = "Copyright Nunito\n\nSIL OFL") :
        BundledLicenseFiles {
        var reads = 0
        override fun librariesJson(): String {
            reads++
            return json()
        }
        override fun nunitoLicenseText(): String? = ofl
    }

    private val sample = """
        {
          "libraries": [
            {"uniqueId": "io.ktor:ktor-client-core", "artifactVersion": "3.6.0", "name": "ktor-client-core",
             "developers": [{"name": "Jetbrains Team"}], "licenses": ["Apache-2.0"], "funding": []},
            {"uniqueId": "io.coil-kt.coil3:coil", "artifactVersion": "3.6.3", "name": "Coil",
             "website": "https://github.com/coil-kt/coil", "organization": {"name": "Coil Contributors"},
             "licenses": ["Apache-2.0", "MIT"]},
            {"uniqueId": "com.google.android.gms:play-services-base", "name": "", "licenses": ["ASDKL"]},
            {"uniqueId": "example:no-licence", "artifactVersion": "", "name": "Android Thing", "licenses": []}
          ],
          "licenses": {
            "Apache-2.0": {"name": "Apache License 2.0", "url": "https://spdx.org/licenses/Apache-2.0.html", "content": "Apache text", "hash": "x"},
            "MIT": {"name": "MIT License", "url": "https://spdx.org/licenses/MIT.html", "content": "MIT text"},
            "ASDKL": {"name": "Android Software Development Kit License", "url": "https://developer.android.com/studio/terms.html"}
          }
        }
    """.trimIndent()

    private fun repository(files: BundledLicenseFiles, telemetry: Telemetry = RecordingTelemetry(), scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        BundledLicenseRepository(files, telemetry, StandardTestDispatcher(scheduler))

    @Test
    fun parsesSortsAndMapsGeneratedLibraries() = runTest {
        val notices = repository(Files({ sample }), scheduler = testScheduler).notices()

        val libraries = requireNotNull(notices.libraries)
        // Case-insensitive by name; a blank name falls back to the Maven coordinates.
        assertEquals(
            listOf("Android Thing", "Coil", "com.google.android.gms:play-services-base", "ktor-client-core"),
            libraries.map { it.name },
        )
        val coil = libraries.first { it.name == "Coil" }
        assertEquals("3.6.3", coil.version)
        assertEquals("Coil Contributors", coil.author)
        assertEquals("https://github.com/coil-kt/coil", coil.websiteUrl)
        assertEquals(listOf("Apache License 2.0", "MIT License"), coil.licenses.map { it.name })
        assertEquals("MIT text", coil.licenses[1].text)

        val ktor = libraries.first { it.name == "ktor-client-core" }
        assertEquals("Jetbrains Team", ktor.author)
        val gms = libraries.first { it.id == "com.google.android.gms:play-services-base" }
        assertNull(gms.licenses.single().text)
        val thing = libraries.first { it.name == "Android Thing" }
        assertNull(thing.version)
        assertTrue(thing.licenses.isEmpty())
    }

    @Test
    fun manualEntriesComeFirstWithBundledTexts() = runTest {
        val notices = repository(Files({ sample }), scheduler = testScheduler).notices()

        assertEquals(listOf("Nunito", "Material Symbols"), notices.assets.map { it.name })
        val nunito = notices.assets[0].licenses.single()
        assertEquals("SIL Open Font License 1.1", nunito.name)
        assertEquals("Copyright Nunito\n\nSIL OFL", nunito.text)
        // Material Symbols reuses the Apache 2.0 text of the generated metadata.
        assertEquals("Apache text", notices.assets[1].licenses.single().text)
    }

    @Test
    fun unreadableMetadataKeepsManualEntriesAndReportsOnce() = runTest {
        val telemetry = RecordingTelemetry()
        val files = Files({ error("missing raw resource") })
        val repository = repository(files, telemetry, testScheduler)

        val notices = repository.notices()
        repository.notices()

        assertNull(notices.libraries)
        assertEquals(2, notices.assets.size)
        assertNull(notices.assets[1].licenses.single().text)
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0", notices.assets[1].licenses.single().url)
        assertEquals(1, telemetry.errors.size)
        assertEquals(1, files.reads)
    }

    @Test
    fun findsComponentsById() = runTest {
        val repository = repository(Files({ sample }), scheduler = testScheduler)

        assertEquals("Coil", repository.component("io.coil-kt.coil3:coil")?.name)
        assertEquals("Nunito", repository.component(BundledLicenseRepository.NUNITO_ID)?.name)
        assertNull(repository.component("unknown"))
    }
}
