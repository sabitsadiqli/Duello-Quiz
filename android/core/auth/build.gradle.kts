plugins {
    alias(libs.plugins.duelloquiz.android.library)
    alias(libs.plugins.duelloquiz.hilt)
}

android {
    namespace = "com.sabit.duelloquiz.core.auth"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))

    // Firebase Auth compiles without google-services.json. It is only used at runtime when a
    // FirebaseApp is configured (see AuthModule); otherwise the in-memory fake is bound.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.kotlinx.coroutines.test)
}
