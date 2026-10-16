plugins {
    alias(libs.plugins.duelloquiz.android.feature)
}

android {
    namespace = "com.sabit.duelloquiz.feature.auth"
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:data"))
}
