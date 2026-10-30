package com.sabit.duelloquiz.backend.common

import com.sabit.duelloquiz.backend.duel.BotConfig
import com.sabit.duelloquiz.backend.duel.BotIdentity
import com.sabit.duelloquiz.backend.progress.ProgressConfig
import com.typesafe.config.ConfigFactory
import io.ktor.server.config.HoconApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/** The shipped application.conf parses and holds the decided numbers. */
class AppConfigTest {

    @Test
    fun `application conf parses and its bot and XP values are the decided defaults`() {
        val config = AppConfig.from(HoconApplicationConfig(ConfigFactory.parseResources("application.conf").resolve()))
        // OQ-106, OQ-108: all bot numbers come from config and match the code defaults.
        assertEquals(BotConfig(), config.duel.bot)
        // OQ-109: half duel XP for bot matches.
        assertEquals(ProgressConfig(), config.progress)
        assertEquals(10, config.progress.duelBotPlayedXp)
        assertEquals(8, config.progress.duelBotDrawXp)
    }

    @Test
    fun `the bot name comes from backend i18n`() {
        assertEquals("Duello Bot", BotIdentity.load().displayName("az"))
        assertEquals("Duello Bot", BotIdentity.load().displayName("en"), "falls back to az")
    }
}
