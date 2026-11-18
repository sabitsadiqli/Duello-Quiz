package com.sabit.duelloquiz.core.data.licenses

import android.util.Log
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.data.licenses.LibraryDefinitions.license
import com.sabit.duelloquiz.core.data.licenses.LibraryDefinitions.toComponents
import com.sabit.duelloquiz.core.model.OpenSourceComponent
import com.sabit.duelloquiz.core.model.OpenSourceLicense
import com.sabit.duelloquiz.core.model.OpenSourceNotices
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Open-source notices for "Açıq mənbə lisenziyaları" (SET-03, licenses.md); works offline. */
interface LicenseRepository {
    suspend fun notices(): OpenSourceNotices

    /** One component of [notices] by [OpenSourceComponent.id]; null when unknown. */
    suspend fun component(id: String): OpenSourceComponent?
}

@Singleton
internal class BundledLicenseRepository(
    private val files: BundledLicenseFiles,
    private val telemetry: Telemetry,
    private val ioDispatcher: CoroutineDispatcher,
) : LicenseRepository {

    @Inject
    constructor(files: BundledLicenseFiles, telemetry: Telemetry) : this(files, telemetry, Dispatchers.IO)

    private val mutex = Mutex()

    @Volatile
    private var cached: OpenSourceNotices? = null

    override suspend fun notices(): OpenSourceNotices = cached ?: mutex.withLock {
        cached ?: withContext(ioDispatcher) { load() }.also { cached = it }
    }

    override suspend fun component(id: String): OpenSourceComponent? =
        notices().let { (it.assets + it.libraries.orEmpty()).firstOrNull { component -> component.id == id } }

    private fun load(): OpenSourceNotices {
        val definitions = runCatching { LibraryDefinitions.parse(files.librariesJson()) }
            .onFailure { error ->
                // Retrying cannot fix a bundled file: report once, show the manual section only.
                Log.w(TAG, "Bundled licence metadata unreadable", error)
                telemetry.recordNonFatal(error)
            }
            .getOrNull()
        val libraries = definitions?.toComponents()
        if (libraries != null && libraries.isEmpty()) {
            Log.w(TAG, "aboutlibraries.json lists no libraries: did the AboutLibraries plugin run?")
        }
        val apache = definitions?.license(APACHE_2) ?: OpenSourceLicense(APACHE_2_NAME, APACHE_2_URL, text = null)
        return OpenSourceNotices(assets = manualEntries(apache), libraries = libraries)
    }

    /** Entries no Gradle scan can find, always first and in this order (licenses.md). */
    private fun manualEntries(apache: OpenSourceLicense) = listOf(
        OpenSourceComponent(
            id = NUNITO_ID,
            name = "Nunito",
            version = null,
            author = "Vernon Adams, Cyreal, Jacques Le Bailly",
            websiteUrl = "https://github.com/googlefonts/nunito",
            licenses = listOf(
                OpenSourceLicense(
                    name = "SIL Open Font License 1.1",
                    url = "https://openfontlicense.org",
                    text = runCatching { files.nunitoLicenseText() }.getOrNull()?.takeIf { it.isNotBlank() },
                ),
            ),
        ),
        OpenSourceComponent(
            id = MATERIAL_SYMBOLS_ID,
            name = "Material Symbols",
            version = null,
            author = "Google",
            websiteUrl = "https://fonts.google.com/icons",
            licenses = listOf(apache.copy(url = apache.url ?: APACHE_2_URL)),
        ),
    )

    internal companion object {
        const val NUNITO_ID = "asset:nunito"
        const val MATERIAL_SYMBOLS_ID = "asset:material-symbols"
        private const val APACHE_2 = "Apache-2.0"
        private const val APACHE_2_NAME = "Apache License 2.0"
        private const val APACHE_2_URL = "https://www.apache.org/licenses/LICENSE-2.0"
        private const val TAG = "Licenses"
    }
}
