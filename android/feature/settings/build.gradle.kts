plugins {
    alias(libs.plugins.duelloquiz.android.feature)
}

android {
    namespace = "com.sabit.duelloquiz.feature.settings"
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:data"))
    // System notification state and settings intents (SET-07).
    implementation(project(":core:notifications"))
    // BackHandler on the delete-account screen.
    implementation(libs.androidx.activity.compose)
}
