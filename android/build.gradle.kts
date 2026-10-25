// Every plugin used by a subproject is declared here with `apply false`, so all subprojects share
// one build classpath. This also pins the Kotlin Gradle plugin that AGP 9's built-in Kotlin uses
// (otherwise AGP falls back to its own, older runtime dependency on KGP).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
}
