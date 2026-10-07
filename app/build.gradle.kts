import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.kover)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.roborazzi)
}

// Version lives in version.properties; versionCode follows from it so it always increases.
val versionProps = Properties().apply { rootProject.file("version.properties").inputStream().use(::load) }
val appVersionName: String = versionProps.getProperty("VERSION_NAME")
val appVersionCode: Int = appVersionName.split(".").map(String::toInt)
    .let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

// Release signing comes from the environment (CI secrets); without it, release builds use the debug key.
val releaseKeystore: String? = System.getenv("CRUX_KEYSTORE_FILE")

android {
    namespace = "com.hardtekpt.crux"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.hardtekpt.crux"
        minSdk = 28
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "com.hardtekpt.crux.HiltTestRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("CRUX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CRUX_KEY_ALIAS")
                keyPassword = System.getenv("CRUX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric's native graphics (screenshot tests) need more than the default 512 MB.
        unitTests.all { it.maxHeapSize = "2g" }
        animationsDisabled = true
    }

    // F-Droid rebuilds the APK from source and rejects Google's encrypted dependency report in it.
    // App bundles (Play) keep it.
    dependenciesInfo {
        includeInApk = false
    }

    // Existing findings live in the baseline; anything new fails the build.
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkDependencies = false
        // Version checks need the network and change by the day; Dependabot covers updates.
        disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    // OpenStreetMap map view for picking a place on a map; no account or API key needed.
    implementation(libs.osmdroid.android)
    // Open-source licences screen (Settings → About); the list is generated at build time.
    implementation(libs.aboutlibraries.compose.m3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // JVM unit tests (incl. Robolectric-backed Compose tests, no emulator needed)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Screenshot tests: goldens in src/test/screenshots, checked by verifyRoborazziDebug.
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    // Instrumented tests (run on the emulator)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}

kotlin {
    compilerOptions {
        // Material 3 still marks top app bar scrolling, sheets and date pickers experimental.
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

// Unit-test coverage of the debug build: ./gradlew koverHtmlReportDebug (report only, no threshold yet).
kover {
    reports {
        filters {
            excludes {
                // Generated code: Hilt, Room, Compose singletons, BuildConfig.
                classes(
                    "*Hilt_*", "*_HiltModules*", "*_Factory*", "*_MembersInjector*", "hilt_aggregated_deps.*", "dagger.hilt.*",
                    "*_Impl*", "*ComposableSingletons*", "*.BuildConfig",
                )
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }
    }
}

aboutLibraries {
    // No network at build time: licences come from the dependencies' own metadata, so the
    // generated list (and the APK) is the same on every machine, as F-Droid's builds need.
    offlineMode = true
}
