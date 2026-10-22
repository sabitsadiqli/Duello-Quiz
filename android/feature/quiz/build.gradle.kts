plugins {
    alias(libs.plugins.duelloquiz.android.feature)
}

android {
    namespace = "com.sabit.duelloquiz.feature.quiz"
}

dependencies {
    implementation(project(":core:data"))
    // System notification state and own reminder cancel (permission card, NOTIF-03/04).
    implementation(project(":core:notifications"))
    // BackHandler for the leave-quiz dialog.
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
}
