plugins {
    alias(libs.plugins.duelloquiz.android.library)
    alias(libs.plugins.duelloquiz.hilt)
}

android {
    namespace = "com.sabit.duelloquiz.core.notifications"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    // Channel names and descriptions, the small icon.
    implementation(project(":core:designsystem"))
    // :core:designsystem exposes Compose artifacts without versions; the BOM pins them.
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    // FCM (free, no card). Compiles without google-services.json; used at runtime only when a
    // FirebaseApp is configured, otherwise a no-op token source is bound (NotificationsModule).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.kotlinx.coroutines.test)
}
