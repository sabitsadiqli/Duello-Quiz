plugins {
    alias(libs.plugins.duelloquiz.android.library)
}

android {
    namespace = "com.sabit.duelloquiz.core.model"
}

dependencies {
    // Only for the @Immutable annotation on UI-facing models; no Compose UI here.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
}
