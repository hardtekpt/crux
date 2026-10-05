package com.hardtekpt.crux.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 4dp grid. `s4` is the gutter and card padding, `s6` separates sections. */
object CruxSpace {
    val s0 = 0.dp
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s10 = 40.dp
    val s12 = 48.dp
    val s16 = 64.dp
}

object CruxSize {
    val touchTarget = 48.dp
    val controlHeight = 36.dp
    val controlHeightLarge = 44.dp
    val controlHeightSmall = 28.dp
    val iconSm = 18.dp
    val iconMd = 22.dp
    val iconLg = 32.dp
    val navBarHeight = 56.dp
    val appBarHeight = 48.dp
    val borderHairline = 1.dp
    val borderEmphasis = 2.dp
    val borderFocus = 3.dp
}

/** Shapes outside the M3 `Shapes` scale; `radius-full` is `CircleShape`. */
object CruxShape {
    val xl = RoundedCornerShape(16.dp)
}

val CruxShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
