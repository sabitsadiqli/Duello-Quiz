plugins {
    alias(libs.plugins.duelloquiz.android.feature)
}

android {
    namespace = "com.sabit.duelloquiz.feature.quiz"
}

dependencies {
    implementation(project(":core:data"))
    // BackHandler for the leave-quiz dialog.
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
}
