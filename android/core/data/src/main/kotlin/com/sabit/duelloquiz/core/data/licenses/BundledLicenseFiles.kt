package com.sabit.duelloquiz.core.data.licenses

/**
 * Licence files bundled into the APK. Bound in :app, which owns the generated
 * `res/raw/aboutlibraries.json` (AboutLibraries Gradle plugin, release runtime classpath).
 */
interface BundledLicenseFiles {
    /** The generated library metadata; throws when the resource is missing or unreadable. */
    fun librariesJson(): String

    /**
     * Full SIL OFL 1.1 text shipped with a bundled font (`core/designsystem/third_party/<font>/OFL.txt`);
     * [font] is the directory name ("fraunces", "plusjakartasans"). Null when missing.
     */
    fun fontLicenseText(font: String): String?
}
