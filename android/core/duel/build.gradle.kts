plugins {
    alias(libs.plugins.duelloquiz.android.library)
    alias(libs.plugins.duelloquiz.hilt)
    // The stored active duel is JSON.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sabit.duelloquiz.core.duel"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
}
