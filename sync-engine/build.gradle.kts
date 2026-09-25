plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
}

kotlin {
    jvmToolchain(mihonx.versions.java.get().toInt())
}

dependencies {
    api(project(":sync-core"))
    api(project(":sync-transport-api"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
