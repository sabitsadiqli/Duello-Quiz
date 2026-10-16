package com.sabit.duelloquiz.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal object AndroidSdk {
    const val COMPILE = 37
    const val TARGET = 37
    const val MIN = 24
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/**
 * Shared Android configuration for application and library modules.
 * With AGP 9 built-in Kotlin, the Kotlin JVM target follows compileOptions.targetCompatibility.
 */
internal fun configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = AndroidSdk.COMPILE
        defaultConfig.apply {
            minSdk = AndroidSdk.MIN
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        testOptions.apply {
            // ViewModel tests run on the JVM; android.* stubs return defaults instead of throwing.
            unitTests.isReturnDefaultValues = true
        }
    }
}
