package com.hardtekpt.crux.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** The release app's id; the profile and benchmarks run against it. */
const val PACKAGE = "com.hardtekpt.crux"

private const val WAIT = 5_000L

/** Waits for a Compose test tag (exposed as a resource id) and returns it. */
fun MacrobenchmarkScope.tag(tag: String): UiObject2? = device.wait(Until.findObject(By.res(tag)), WAIT)

fun MacrobenchmarkScope.tap(tag: String) {
    tag(tag)?.click()
    device.waitForIdle()
}

/** Flings a scrolling list down and back up. */
fun MacrobenchmarkScope.scrollList(tag: String) {
    val list = tag(tag) ?: return
    // Keep clear of the edges, where the system's back gesture lives.
    list.setGestureMargin(device.displayWidth / 6)
    repeat(2) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
    list.fling(Direction.UP)
    device.waitForIdle()
}

/** A fresh install opens on first-run setup: skip it, once, to reach Home. */
fun MacrobenchmarkScope.passSetup() {
    device.wait(Until.findObject(By.res("setup_skip_all")), 2_000L)?.click()
    device.waitForIdle()
}

/** The everyday path: Home, the Journal, Train's week and plans, Progress and You, scrolling each. */
fun MacrobenchmarkScope.everydayJourney() {
    passSetup()
    tag("screen_Home")
    scrollList("home_list")
    tap("nav_Journal")
    scrollList("journal_list")
    tap("nav_Train")
    scrollList("train_list")
    tap("nav_Progress")
    tag("screen_Progress")
    device.findObject(By.scrollable(true))?.let {
        it.setGestureMargin(device.displayWidth / 6)
        it.fling(Direction.DOWN)
    }
    device.waitForIdle()
    tap("nav_You")
    tap("nav_Home")
}
