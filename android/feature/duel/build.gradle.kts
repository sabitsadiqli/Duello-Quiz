plugins {
    alias(libs.plugins.duelloquiz.android.feature)
}

android {
    namespace = "com.sabit.duelloquiz.feature.duel"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:duel"))
    // Removes the own streak reminder once the day became active (NOTIF-04).
    implementation(project(":core:notifications"))
    // BackHandler for the leave dialog.
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
}
