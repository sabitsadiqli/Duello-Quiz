plugins {
    alias(libs.plugins.duelloquiz.android.library)
    // Exam models are cached as JSON (package file, result and review caches).
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sabit.duelloquiz.core.model"
}

dependencies {
    // Only for the @Immutable annotation on UI-facing models; no Compose UI here.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.kotlinx.serialization.json)
}
