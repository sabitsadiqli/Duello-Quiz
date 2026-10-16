import com.sabit.duelloquiz.buildlogic.lib
import com.sabit.duelloquiz.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/** `duelloquiz.hilt`: Hilt with KSP for Android modules. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.google.devtools.ksp")
            apply(plugin = "com.google.dagger.hilt.android")

            dependencies {
                "implementation"(libs.lib("hilt-android"))
                "ksp"(libs.lib("hilt-compiler"))
                // Lets Dagger read Kotlin metadata written by the project's (newer) Kotlin compiler.
                "ksp"(libs.lib("kotlin-metadata-jvm"))
            }
        }
    }
}
