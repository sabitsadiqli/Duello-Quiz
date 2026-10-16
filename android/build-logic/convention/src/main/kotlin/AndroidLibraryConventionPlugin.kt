import com.android.build.api.dsl.LibraryExtension
import com.sabit.duelloquiz.buildlogic.AndroidSdk
import com.sabit.duelloquiz.buildlogic.configureAndroidCommon
import com.sabit.duelloquiz.buildlogic.lib
import com.sabit.duelloquiz.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `duelloquiz.android.library`: every :core and :feature module. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")

            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                testOptions.targetSdk = AndroidSdk.TARGET
            }

            dependencies {
                "testImplementation"(libs.lib("junit"))
                "testImplementation"(libs.lib("kotlin-test"))
            }
        }
    }
}
