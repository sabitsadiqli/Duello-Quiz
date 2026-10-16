plugins {
    `kotlin-dsl`
}

group = "com.sabit.duelloquiz.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Only the AGP DSL is compiled against. The other plugins (Kotlin, Compose, KSP, Hilt,
    // serialization) are applied by id, so their versions come from the root build classpath.
    compileOnly(libs.android.gradleApiPlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.duelloquiz.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.duelloquiz.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.duelloquiz.android.feature.get().pluginId
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.duelloquiz.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.duelloquiz.hilt.get().pluginId
            implementationClass = "HiltConventionPlugin"
        }
    }
}
