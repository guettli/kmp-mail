pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kmp-mail"

include(":kmp-mime")
include(":kmp-smtp")
include(":kmp-imap")
