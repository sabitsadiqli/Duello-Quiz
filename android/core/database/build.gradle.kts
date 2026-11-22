plugins {
    alias(libs.plugins.duelloquiz.android.library)
    alias(libs.plugins.duelloquiz.hilt)
}

android {
    namespace = "com.sabit.duelloquiz.core.database"
}

// Exported schemas (core/database/schemas) are the reference for future migrations.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
