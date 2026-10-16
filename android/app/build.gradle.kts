plugins {
    alias(libs.plugins.duelloquiz.android.application)
    alias(libs.plugins.duelloquiz.android.compose)
    alias(libs.plugins.duelloquiz.hilt)
}

// Firebase is configured only when android/app/google-services.json exists (git-ignored; CI
// decodes it from the GOOGLE_SERVICES_JSON_BASE64 secret). Without it the app builds and runs
// with the in-memory fake AuthRepository (see :core:auth AuthModule).
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Release signing is configured only when a keystore is provided (CI release job or a local
// keystore). A Gradle property wins over the env var; without both, release stays unsigned.
fun signingValue(property: String, env: String): String? =
    providers.gradleProperty(property).orElse(providers.environmentVariable(env)).orNull
val releaseStoreFile = signingValue("releaseStoreFile", "ANDROID_KEYSTORE_PATH")

// Public privacy policy page (GitHub Pages from docs/legal/, OQ-015). Override with
// -PprivacyPolicyUrl=... until the final URL is known.
val privacyPolicyUrl: String = providers.gradleProperty("privacyPolicyUrl")
    .orElse("https://sabitsadiqli.github.io/DuelloQuiz/legal/privacy-policy.html")
    .get()

android {
    namespace = "com.sabit.duelloquiz"

    defaultConfig {
        applicationId = "com.sabit.duelloquiz"
        // CI passes these from the release tag; see infra/README.md
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.1.0"

        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
    }

    if (releaseStoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("releaseStorePassword", "ANDROID_KEYSTORE_PASSWORD")
                keyAlias = signingValue("releaseKeyAlias", "ANDROID_KEY_ALIAS")
                keyPassword = signingValue("releaseKeyPassword", "ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    androidResources {
        // Locales come from res/values-* of the app and its modules; the default (unqualified)
        // locale is set in res/resources.properties (unqualifiedResLocale=az).
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    testImplementation(project(":core:testing"))

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
