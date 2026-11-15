plugins {
    alias(companion.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(mihonx.plugins.spotless)
}

// The Android OAuth client in Google Cloud is bound to this package name + the signing SHA-1.
// It must live in the SAME Google Cloud project as the desktop OAuth client, otherwise Drive
// gives the phone a different (empty) appDataFolder. Override with -Pcompanion.applicationId=...
val companionApplicationId = providers.gradleProperty("companion.applicationId")
    .orElse("app.mihondesk.companion")

android {
    namespace = "mihon.companion"
    compileSdk { version = release(37) { minorApiLevel = 2 } }

    defaultConfig {
        applicationId = companionApplicationId.get()
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    packaging {
        resources.excludes +=
            setOf("META-INF/INDEX.LIST", "META-INF/*.kotlin_module", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(project(":sync"))

    implementation(platform(companion.compose.bom))
    implementation(companion.compose.material3)
    implementation(companion.compose.ui)
    implementation(companion.compose.icons)
    implementation(companion.compose.ui.tooling.preview)
    implementation(companion.activity.compose)
    implementation(companion.lifecycle.viewmodel.compose)
    implementation(companion.lifecycle.runtime.compose)
    implementation(companion.core.ktx)
    implementation(companion.work.runtime)
    implementation(companion.documentfile)
    implementation(companion.play.services.auth)
    implementation(companion.coroutines.play.services)
    implementation(companion.coroutines.android)
    implementation(companion.coil.compose)
    implementation(companion.coil.okhttp)
    implementation(libs.okhttp.core)

    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

dependencies {
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
