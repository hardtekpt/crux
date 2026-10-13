package com.hardtekpt.crux.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile: `./gradlew :app:generateReleaseBaselineProfile` with an
 * emulator running. The result lands in app/src/main/generated/baselineProfiles and is committed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, maxIterations = 5, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        everydayJourney()
    }
}
