plugins {
    alias(libs.plugins.duelloquiz.android.feature)
}

android {
    namespace = "com.sabit.duelloquiz.feature.profile"
}

dependencies {
    // Display name rules (OQ-042) shared with sign-up.
    implementation(project(":core:auth"))
    implementation(project(":core:data"))
}
