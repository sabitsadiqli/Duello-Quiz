package com.sabit.duelloquiz.core.data.repository

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryVerificationBannerRepositoryTest {

    @Test
    fun dismissalIsKeptPerAccount() = runTest {
        val repository = InMemoryVerificationBannerRepository()

        repository.dismissedUids.test {
            assertEquals(emptySet<String>(), awaitItem())
            repository.dismiss("uid-1")
            assertEquals(setOf("uid-1"), awaitItem())
            repository.dismiss("uid-2")
            assertEquals(setOf("uid-1", "uid-2"), awaitItem())
        }
    }
}
