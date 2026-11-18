plugins {
    alias(libs.plugins.duelloquiz.android.library)
    alias(libs.plugins.duelloquiz.android.compose)
}

android {
    namespace = "com.sabit.duelloquiz.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    // ObserveAsEvents (lifecycle-aware one-off UI events).
    api(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    // rememberNotificationPermissionRequester (Android 13+ runtime permission).
    implementation(libs.androidx.activity.compose)
    // Question images, review thumbnails and avatars (docs/mvp-spec.md §5).
    implementation(libs.coil.compose)

    // The tokens -> ColorScheme test parses design/tokens.json.
    testImplementation(libs.kotlinx.serialization.json)
}

// The OFL requires the licence to travel with the font (licenses.md): ship
// third_party/nunito/OFL.txt as the asset licenses/nunito/OFL.txt, without copying it in git.
abstract class LicenseAssetsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nunitoLicense: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val root = outputDir.get().asFile
        root.deleteRecursively()
        nunitoLicense.get().asFile.copyTo(root.resolve("licenses/nunito/OFL.txt"))
    }
}

val licenseAssets = tasks.register<LicenseAssetsTask>("licenseAssets") {
    nunitoLicense.set(layout.projectDirectory.file("third_party/nunito/OFL.txt"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(licenseAssets, LicenseAssetsTask::outputDir)
    }
}
