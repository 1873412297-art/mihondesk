// Standalone Android build for the mihondesk phone companion. It is deliberately NOT part of the
// root (desktop) build: run it with `./gradlew -p mobile-companion assembleDebug`.
// It reuses the repository's pure-JVM `:sync` module so the phone speaks exactly the same
// Google Drive changeset protocol and merge rules as the desktop app.
pluginManagement {
    includeBuild("../gradle/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
        create("mihonx") { from(files("../gradle/mihon.versions.toml")) }
        create("companion") { from(files("gradle/companion.versions.toml")) }
    }

    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    // Provisions the JDK 17 toolchain the shared :sync module asks for.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "mihondesk-companion"
include(":app")
include(":sync")
project(":sync").projectDir = file("../sync")
