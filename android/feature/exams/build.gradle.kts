plugins {
    alias(libs.plugins.duelloquiz.android.feature)
}

android {
    namespace = "com.sabit.duelloquiz.feature.exams"
}

dependencies {
    implementation(project(":core:data"))
    // System notification state for the info-screen hint (EXAM-27 never asks for the permission).
    implementation(project(":core:notifications"))
    // BackHandler for the leave / time-up rules.
    implementation(libs.androidx.activity.compose)
    // Listening audio (ADR 0003): local playback and the media session for background play.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
}
