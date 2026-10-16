package com.sabit.duelloquiz.backend.common

import kotlin.test.Test
import kotlin.test.assertEquals

class I18nTest {
    private val supported = setOf("az", "en", "ru")

    @Test
    fun `missing or unsupported Accept-Language falls back to az`() {
        assertEquals("az", I18n.resolveLanguage(null))
        assertEquals("az", I18n.resolveLanguage(""))
        assertEquals("az", I18n.resolveLanguage("de-DE,fr;q=0.8"))
        assertEquals("az", I18n.resolveLanguage("en-US"))
    }

    @Test
    fun `first supported language by quality wins`() {
        assertEquals("ru", I18n.resolveLanguage("de;q=0.9, ru-RU;q=0.8, en;q=0.5", supported))
        assertEquals("en", I18n.resolveLanguage("ru;q=0.4, en-GB", supported))
        assertEquals("az", I18n.resolveLanguage("az-Latn-AZ", supported))
        assertEquals("az", I18n.resolveLanguage("en;q=0", setOf("az", "en")))
    }

    @Test
    fun `text resolves with fallback to az`() {
        val text = mapOf("az" to "Coğrafiya", "en" to "Geography")
        assertEquals("Geography", text.resolve("en"))
        assertEquals("Coğrafiya", text.resolve("ru"))
        assertEquals("Only", mapOf("tr" to "Only").resolve("az"))
    }
}
