import com.android.build.api.dsl.ApplicationExtension
import com.sabit.duelloquiz.buildlogic.AndroidSdk
import com.sabit.duelloquiz.buildlogic.configureAndroidCommon
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/** `duelloquiz.android.application`: the :app module. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")

            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = AndroidSdk.TARGET
            }
        }
    }
}
