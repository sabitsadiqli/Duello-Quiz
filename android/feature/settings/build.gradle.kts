plugins {
    alias(libs.plugins.duelloquiz.android.feature)
}

android {
    namespace = "com.sabit.duelloquiz.feature.settings"
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:data"))
    // BackHandler on the delete-account screen.
    implementation(libs.androidx.activity.compose)
}
