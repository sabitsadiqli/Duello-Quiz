package com.sabit.duelloquiz

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.sabit.duelloquiz.core.data.repository.AccountRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DuelloApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var accountRepository: AccountRepository

    override fun onCreate() {
        super.onCreate()
        // AUTH-07: no data of a signed-out account stays on the device.
        accountRepository.startSignOutCleanup()
    }

    /**
     * Coil 3 (docs/mvp-spec.md §5): question images are immutable WebP files, so a disk cache
     * keeps them across sessions; composables request them at their displayed size.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .crossfade(true)
        .build()

    private companion object {
        const val MEMORY_CACHE_PERCENT = 0.15
        const val DISK_CACHE_BYTES = 50L * 1024 * 1024
    }
}
