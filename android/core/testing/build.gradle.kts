plugins {
    alias(libs.plugins.duelloquiz.android.library)
}

android {
    namespace = "com.sabit.duelloquiz.core.testing"
}

dependencies {
    api(project(":core:auth"))
    api(project(":core:common"))
    api(project(":core:data"))
    api(project(":core:duel"))
    api(project(":core:model"))

    api(libs.junit)
    api(libs.kotlin.test)
    api(libs.kotlinx.coroutines.test)
    api(libs.mockk)
    api(libs.turbine)
}
