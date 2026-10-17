package com.sabit.duelloquiz.backend.image

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalDiskImageStorageTest {
    private val root = Files.createTempDirectory("media")
    private val storage = LocalDiskImageStorage(root)

    @Test
    fun `files are written atomically under their key and can be deleted`() = runBlocking {
        val key = MediaUrls.questionImageKey("0b6a0f55-3c43-4f63-a0c1-5d0c2f7c9e11")
        assertEquals("questions/0b6a0f55-3c43-4f63-a0c1-5d0c2f7c9e11.webp", key)
        storage.put(key, byteArrayOf(1, 2, 3), "image/webp")
        assertTrue(storage.exists(key))
        assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(root.resolve(key)))
        // No temp file is left next to it.
        assertEquals(listOf(root.resolve(key).fileName.toString()), Files.list(root.resolve("questions")).use { s -> s.map { it.fileName.toString() }.toList() })
        storage.delete(key)
        assertFalse(storage.exists(key))
    }

    @Test
    fun `keys cannot leave the media root`() = runBlocking {
        listOf("../escape.webp", "/etc/passwd.webp", "questions/../../x.webp", "questions/a b.webp").forEach { key ->
            assertFailsWith<IllegalArgumentException>(key) { storage.put(key, byteArrayOf(1), "image/webp") }
        }
        assertEquals("https://media.example/media/questions/x.webp", MediaUrls("https://media.example/media/").url("questions/x.webp"))
    }
}
