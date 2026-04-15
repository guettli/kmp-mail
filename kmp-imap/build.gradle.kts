plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kover)
}

kotlin {
    jvm()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":kmp-mime"))
            implementation(libs.ktor.network)
            implementation(libs.ktor.network.tls)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
