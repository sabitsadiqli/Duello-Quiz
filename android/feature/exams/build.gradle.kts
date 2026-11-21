plugins {
    alias(libs.plugins.duelloquiz.android.feature)
}

android {
    namespace = "com.sabit.duelloquiz.feature.exams"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.activity.compose)
}
