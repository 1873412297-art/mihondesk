plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(mihonx.plugins.spotless)
}

// One module, layered by package: mihon.sync.core (models, merge policy), mihon.sync.transport.api,
// mihon.sync.engine and mihon.sync.transport.gdrive. Higher layers may depend on lower ones only.
kotlin {
    jvmToolchain(mihonx.versions.java.get().toInt())
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    api(libs.kotlinx.serialization.protobuf)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp.core)
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
