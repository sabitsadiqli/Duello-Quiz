plugins {
    alias(libs.plugins.duelloquiz.android.feature)
}

android {
    namespace = "com.sabit.duelloquiz.feature.home"
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:data"))
    // The saved running match (resume after process death, DUEL-16).
    implementation(project(":core:duel"))
}
