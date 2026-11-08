buildscript {
    dependencies {
        classpath(libs.kotlin.gradle)
    }
}

plugins {
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineProfile) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.moko.resources) apply false
    alias(libs.plugins.sqldelight) apply false

    alias(mihonx.plugins.spotless)
    id("dev.detekt") version "2.0.0-alpha.6" apply false
}

val buildLogic: IncludedBuild = gradle.includedBuild("build-logic")
tasks {
    listOf("clean", "spotlessApply", "spotlessCheck").forEach { task ->
        named(task) {
            dependsOn(buildLogic.task(":$task"))
        }
    }
}

// ---- Desktop quality gates (CLAUDE.md) -------------------------------------------------------
val desktopModules = setOf(
    "desktop-app",
    "desktop-library-data",
    "desktop-webview-host",
    "extension-host",
    "extension-sdk",
    "reader-core",
    "sync",
)

subprojects {
    if (name !in desktopModules) return@subprojects
    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline.set(rootProject.file("config/detekt/baseline/$name.xml"))
        source.setFrom("src/main/kotlin")
    }
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("checkFileLength"))
    }
    // JUnit silently skips a @Test that returns a value — the `fun x() = runBlocking { ... }` trap,
    // which once hid 32 tests here. Escalating discovery warnings turns that into a build failure.
    tasks.withType<Test>().configureEach {
        systemProperty("junit.platform.discovery.issue.severity.critical", "WARNING")
    }
}

/**
 * No Kotlin source file in a desktop module may exceed [maxLines] lines. Files that were already
 * larger are listed in config/file-length-baseline.txt with their size at the time; they may shrink
 * (and should then be removed from the list) but never grow.
 */
val checkFileLength by tasks.registering {
    val maxLines = 400
    val baselineFile = file("config/file-length-baseline.txt")
    val rootDirectory = rootDir
    val sourceTrees = desktopModules.map { file("$it/src/main/kotlin") }
    inputs.files(sourceTrees).withPropertyName("sources")
    inputs.file(baselineFile).withPropertyName("baseline")
    doLast {
        val allowed = baselineFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line -> line.substringBeforeLast(' ') to line.substringAfterLast(' ').toInt() }
        val violations = mutableListOf<String>()
        val shrunk = mutableListOf<String>()
        sourceTrees.filter { it.isDirectory }.forEach { tree ->
            tree.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { source ->
                val path = source.relativeTo(rootDirectory).invariantSeparatorsPath
                val lines = source.useLines { it.count() }
                val limit = allowed[path] ?: maxLines
                when {
                    lines > limit && path in allowed ->
                        violations += "$path: $lines lines, grew past its legacy size $limit (split it instead)"
                    lines > limit -> violations += "$path: $lines lines (max $maxLines)"
                    path in allowed && lines <= maxLines -> shrunk += path
                }
            }
        }
        shrunk.forEach {
            logger.lifecycle("checkFileLength: $it is now within $maxLines lines; remove it from the baseline")
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Kotlin files over the size limit:\n" + violations.joinToString("\n"))
        }
    }
}
