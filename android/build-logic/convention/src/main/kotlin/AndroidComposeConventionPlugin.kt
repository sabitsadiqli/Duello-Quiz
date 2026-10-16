import com.android.build.api.dsl.CommonExtension
import com.sabit.duelloquiz.buildlogic.lib
import com.sabit.duelloquiz.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * `duelloquiz.android.compose`: Compose compiler plugin + BOM + preview tooling.
 * Apply after `duelloquiz.android.application` or `duelloquiz.android.library`.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")

            extensions.getByType<CommonExtension>().apply {
                buildFeatures.compose = true
            }

            dependencies {
                val bom = platform(libs.lib("androidx-compose-bom"))
                "implementation"(bom)
                "androidTestImplementation"(bom)
                "implementation"(libs.lib("androidx-compose-ui-tooling-preview"))
                "debugImplementation"(libs.lib("androidx-compose-ui-tooling"))
            }
        }
    }
}
