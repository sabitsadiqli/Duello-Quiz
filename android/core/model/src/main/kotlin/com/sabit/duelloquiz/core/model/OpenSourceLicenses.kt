package com.sabit.duelloquiz.core.model

import androidx.compose.runtime.Immutable

/** A bundled open-source component (library, font or icon set) shown on the licences screen. */
@Immutable
data class OpenSourceComponent(
    /** Stable key, e.g. the Maven coordinates `group:artifact`. */
    val id: String,
    /** Proper name in English, never translated (licenses.md). */
    val name: String,
    val version: String?,
    /** Author or organisation; null when unknown. */
    val author: String?,
    val websiteUrl: String?,
    val licenses: List<OpenSourceLicense>,
)

@Immutable
data class OpenSourceLicense(
    val name: String,
    val url: String?,
    /** Full licence text; null when the metadata does not contain it. */
    val text: String?,
)

/**
 * Content of the licences screen. [assets] are the manual entries (Nunito, Material Symbols) and
 * always present; [libraries] is the generated list sorted by name, or null when the bundled
 * metadata could not be read.
 */
@Immutable
data class OpenSourceNotices(
    val assets: List<OpenSourceComponent>,
    val libraries: List<OpenSourceComponent>?,
)
