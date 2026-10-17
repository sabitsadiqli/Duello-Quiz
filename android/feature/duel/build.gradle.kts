plugins {
    alias(libs.plugins.duelloquiz.android.feature)
}

android {
    namespace = "com.sabit.duelloquiz.feature.duel"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:duel"))
    // BackHandler for the leave dialog.
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
}
