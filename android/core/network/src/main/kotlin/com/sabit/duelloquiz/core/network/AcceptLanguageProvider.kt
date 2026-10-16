package com.sabit.duelloquiz.core.network

import android.content.Context
import androidx.core.app.LocaleManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Value of the `Accept-Language` header for localized backend content (SET-02). */
fun interface AcceptLanguageProvider {
    fun acceptLanguage(): String
}

/**
 * Uses the per-app language (`AppCompatDelegate.setApplicationLocales` / `LocaleManager`) when one
 * is set, else the app's default locale `az` (`res/resources.properties`). The device language is
 * deliberately ignored: a Russian phone still gets Azerbaijani content (SET-02). `az` is always
 * appended as the fallback.
 */
internal class AppLocaleAcceptLanguageProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AcceptLanguageProvider {

    override fun acceptLanguage(): String {
        val appLocales = LocaleManagerCompat.getApplicationLocales(context)
        val language = if (appLocales.isEmpty) null else appLocales[0]?.language
        return acceptLanguageFor(language)
    }

    companion object {
        const val DEFAULT_LANGUAGE = "az"

        fun acceptLanguageFor(language: String?): String = when {
            language.isNullOrBlank() || language == DEFAULT_LANGUAGE -> DEFAULT_LANGUAGE
            else -> "$language, $DEFAULT_LANGUAGE;q=0.5"
        }
    }
}
