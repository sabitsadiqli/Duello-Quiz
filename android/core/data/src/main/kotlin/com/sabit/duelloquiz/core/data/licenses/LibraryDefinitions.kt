package com.sabit.duelloquiz.core.data.licenses

import com.sabit.duelloquiz.core.model.OpenSourceComponent
import com.sabit.duelloquiz.core.model.OpenSourceLicense
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Subset of the AboutLibraries `aboutlibraries.json` format that the licences screen needs. */
@Serializable
internal data class LibraryDefinitionsJson(
    val libraries: List<LibraryJson> = emptyList(),
    val licenses: Map<String, LicenseJson> = emptyMap(),
)

@Serializable
internal data class LibraryJson(
    val uniqueId: String,
    val artifactVersion: String? = null,
    val name: String? = null,
    val website: String? = null,
    val developers: List<DeveloperJson> = emptyList(),
    val organization: OrganizationJson? = null,
    val licenses: List<String> = emptyList(),
)

@Serializable
internal data class DeveloperJson(val name: String? = null)

@Serializable
internal data class OrganizationJson(val name: String? = null)

@Serializable
internal data class LicenseJson(
    val name: String? = null,
    val url: String? = null,
    val content: String? = null,
)

internal object LibraryDefinitions {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): LibraryDefinitionsJson = json.decodeFromString(LibraryDefinitionsJson.serializer(), text)

    /** Libraries sorted by name, case-insensitive (licenses.md, list item 5). */
    fun LibraryDefinitionsJson.toComponents(): List<OpenSourceComponent> = libraries
        .map { library ->
            OpenSourceComponent(
                id = library.uniqueId,
                name = library.name?.takeIf { it.isNotBlank() } ?: library.uniqueId,
                version = library.artifactVersion?.takeIf { it.isNotBlank() },
                author = library.organization?.name?.takeIf { it.isNotBlank() }
                    ?: library.developers.mapNotNull { it.name?.takeIf(String::isNotBlank) }
                        .joinToString(", ")
                        .ifEmpty { null },
                websiteUrl = library.website?.takeIf { it.isNotBlank() },
                licenses = library.licenses.map { key -> licenses[key].toModel(key) },
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun LibraryDefinitionsJson.license(key: String): OpenSourceLicense? = licenses[key]?.toModel(key)

    private fun LicenseJson?.toModel(key: String) = OpenSourceLicense(
        name = this?.name?.takeIf { it.isNotBlank() } ?: key,
        url = this?.url?.takeIf { it.isNotBlank() },
        text = this?.content?.takeIf { it.isNotBlank() },
    )
}
