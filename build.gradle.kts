plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.spotless)
}

// Kotlin style is ktlint's, configured in .editorconfig. `spotlessApply` fixes what it can.
spotless {
    // Git normalises line endings (.gitattributes); Windows checkouts may differ.
    lineEndings = com.diffplug.spotless.LineEnding.PRESERVE
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
        // Files are named for what they hold (Picker.kt, Wheels.kt), not after their one class.
        suppressLintsFor { shortCode = "standard:filename" }
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
