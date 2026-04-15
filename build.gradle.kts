import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

// Root build file.
// Subprojects apply their own plugins via the version catalog.
// Common configuration shared by all subprojects lives here.

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kover)
}

subprojects {
    group   = "io.github.kmpmail"
    version = "0.1.0-SNAPSHOT"
}

// Aggregate coverage from all submodules into the root project.
dependencies {
    kover(project(":kmp-mime"))
    kover(project(":kmp-smtp"))
    kover(project(":kmp-imap"))
}

kover {
    reports {
        filters {
            // Exclude thin ktor network adapters — they wrap real TCP sockets and
            // cannot be meaningfully unit-tested without a live server.
            excludes {
                classes(
                    "io.github.kmpmail.smtp.SmtpConnection",
                    "io.github.kmpmail.imap.ImapConnection",
                )
            }
        }
        verify {
            rule {
                // Floors of the measured values (LINE 92.9%, INSTRUCTION 89.1%).
                // Rounded down to whole numbers so a single added line does not
                // flip the gate. Kover 0.9.x supports LINE, INSTRUCTION, BRANCH.
                minBound(92, CoverageUnit.LINE)
                minBound(89, CoverageUnit.INSTRUCTION)
            }
        }
    }
}

