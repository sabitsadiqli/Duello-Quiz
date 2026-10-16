plugins {
    alias(libs.plugins.duelloquiz.android.library)
    alias(libs.plugins.duelloquiz.hilt)
    // Cache entries are stored as JSON.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sabit.duelloquiz.core.data"
}

dependencies {
    api(project(":core:model"))
    // Repository interfaces expose Flow/StateFlow.
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)
}
