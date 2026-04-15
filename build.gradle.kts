// Root build file.
// Subprojects apply their own plugins via the version catalog.
// Common configuration shared by all subprojects lives here.

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
}

subprojects {
    group   = "io.github.kmpmail"
    version = "0.1.0-SNAPSHOT"
}
