plugins {
    alias(libs.plugins.duelloquiz.android.library)
    alias(libs.plugins.duelloquiz.hilt)
}

android {
    namespace = "com.sabit.duelloquiz.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.browser)
}
