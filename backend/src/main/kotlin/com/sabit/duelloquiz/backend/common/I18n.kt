package com.sabit.duelloquiz.backend.common

import io.ktor.http.HttpHeaders
import io.ktor.http.parseHeaderValue
import io.ktor.server.application.ApplicationCall

/** Localized content as stored in JSONB columns: `{"az": "..."}`. */
typealias I18nText = Map<String, String>

object I18n {
    const val FALLBACK_LANGUAGE = "az"

    /** Languages the app ships. Adding one is configuration only (docs/mvp-spec.md §4). */
    val supportedLanguages: Set<String> = setOf("az")

    /**
     * Picks the first supported language from an `Accept-Language` header, honouring
     * q-values; anything unknown or missing falls back to `az`.
     */
    fun resolveLanguage(acceptLanguage: String?, supported: Set<String> = supportedLanguages): String {
        if (acceptLanguage.isNullOrBlank()) return FALLBACK_LANGUAGE
        return parseHeaderValue(acceptLanguage)
            .filter { it.quality > 0.0 }
            .sortedByDescending { it.quality }
            .map { it.value.substringBefore('-').substringBefore('_').trim().lowercase() }
            .firstOrNull { it in supported }
            ?: FALLBACK_LANGUAGE
    }
}

/** Text in [language], falling back to `az`, then to any value. */
fun I18nText.resolve(language: String): String =
    this[language] ?: this[I18n.FALLBACK_LANGUAGE] ?: values.firstOrNull().orEmpty()

val ApplicationCall.language: String
    get() = I18n.resolveLanguage(request.headers[HttpHeaders.AcceptLanguage])
