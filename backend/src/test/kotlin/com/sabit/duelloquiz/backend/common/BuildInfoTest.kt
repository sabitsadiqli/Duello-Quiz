package com.sabit.duelloquiz.backend.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class BuildInfoTest {

    @Test
    fun `version comes from the generated build-info resource`() {
        val info = BuildInfo.load(rawCommit = null)
        assertNotEquals("unknown", info.version, "build-info.properties must be on the classpath")
        assertNull(info.commit)
    }

    @Test
    fun `commit must be a hex sha`() {
        val sha = "0123456789abcdef0123456789abcdef01234567"
        assertEquals(sha, BuildInfo.of("1.0.0", sha).commit)
        assertEquals("abc1234", BuildInfo.of("1.0.0", " ABC1234 ").commit)
        listOf("", "main", "abc", "password=hunter2", "g".repeat(40), "a".repeat(41)).forEach {
            assertNull(BuildInfo.of("1.0.0", it).commit, "commit=$it")
        }
    }

    @Test
    fun `odd versions are reported as unknown`() {
        assertEquals("0.1.0", BuildInfo.of("0.1.0", null).version)
        assertEquals("0.1.0-SNAPSHOT", BuildInfo.of("0.1.0-SNAPSHOT", null).version)
        assertEquals("unknown", BuildInfo.of(null, null).version)
        assertEquals("unknown", BuildInfo.of("1.0 with spaces", null).version)
        assertEquals("unknown", BuildInfo.load(null, resource = "/missing.properties").version)
    }
}
