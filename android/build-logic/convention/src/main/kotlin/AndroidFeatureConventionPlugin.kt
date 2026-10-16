import com.sabit.duelloquiz.buildlogic.lib
import com.sabit.duelloquiz.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * `duelloquiz.android.feature`: a :feature module (UI + ViewModels + type-safe navigation).
 * Features depend on :core modules only, never on another feature.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "duelloquiz.android.library")
            apply(plugin = "duelloquiz.android.compose")
            apply(plugin = "duelloquiz.hilt")
            // Type-safe navigation routes are @Serializable classes.
            apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                "implementation"(project(":core:common"))
                "implementation"(project(":core:designsystem"))
                "implementation"(project(":core:model"))

                "implementation"(libs.lib("androidx-compose-material3"))
                "implementation"(libs.lib("androidx-lifecycle-runtime-compose"))
                "implementation"(libs.lib("androidx-lifecycle-viewmodel-compose"))
                "implementation"(libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
                "implementation"(libs.lib("androidx-navigation-compose"))
                "implementation"(libs.lib("kotlinx-serialization-json"))

                "testImplementation"(project(":core:testing"))
            }
        }
    }
}
