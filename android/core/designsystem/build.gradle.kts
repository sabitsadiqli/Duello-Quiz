plugins {
    alias(libs.plugins.duelloquiz.android.library)
    alias(libs.plugins.duelloquiz.android.compose)
}

android {
    namespace = "com.sabit.duelloquiz.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    // ObserveAsEvents (lifecycle-aware one-off UI events).
    api(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)

    // The tokens -> ColorScheme test parses design/tokens.json.
    testImplementation(libs.kotlinx.serialization.json)
}
