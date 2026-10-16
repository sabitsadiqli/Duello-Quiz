plugins {
    alias(libs.plugins.duelloquiz.android.library)
    alias(libs.plugins.duelloquiz.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Backend base URL. Debug defaults to the host machine as seen from the Android emulator; release
// has a placeholder until the server domain is decided (OQ-006). Override for any build type with
// -PapiBaseUrl=https://... (cleartext http is allowed only for 10.0.2.2/localhost in debug).
val apiBaseUrl = providers.gradleProperty("apiBaseUrl")
fun baseUrlField(default: String) = "\"${apiBaseUrl.orElse(default).get().trimEnd('/')}/\""

android {
    namespace = "com.sabit.duelloquiz.core.network"

    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "API_BASE_URL", baseUrlField("http://10.0.2.2:8080"))
        }
        getByName("release") {
            buildConfigField("String", "API_BASE_URL", baseUrlField("https://api.duelloquiz.invalid"))
        }
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:auth"))
    implementation(project(":core:common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
