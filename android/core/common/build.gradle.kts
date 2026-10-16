plugins {
    alias(libs.plugins.duelloquiz.android.library)
}

android {
    namespace = "com.sabit.duelloquiz.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
}
