plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kover)
}

kotlin {
    jvm()
    linuxX64()
    // Uncomment as CI runners become available:
    // macosX64(); macosArm64()
    // iosArm64(); iosX64(); iosSimulatorArm64()
    // mingwX64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
