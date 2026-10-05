package com.hardtekpt.crux.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.ui.theme.CruxTheme

enum class CruxButtonVariant { Filled, Tonal, Outlined, Text, Destructive }

/** Sizes are visual heights; every size keeps a 48dp hit area via minimumInteractiveComponentSize. */
enum class CruxButtonSize(val height: Dp, val horizontalPadding: Dp) {
    Small(32.dp, 12.dp),
    Default(40.dp, 20.dp),
    Large(48.dp, 24.dp),
}

/** The single action set. One Filled button per screen. */
@Composable
fun CruxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CruxButtonVariant = CruxButtonVariant.Filled,
    size: CruxButtonSize = CruxButtonSize.Default,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val buttonColors = when (variant) {
        CruxButtonVariant.Filled -> ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
        )
        CruxButtonVariant.Tonal -> ButtonDefaults.buttonColors(
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        )
        CruxButtonVariant.Outlined, CruxButtonVariant.Text -> ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.primary,
        )
        CruxButtonVariant.Destructive -> ButtonDefaults.buttonColors(
            containerColor = colors.error,
            contentColor = colors.onError,
        )
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = buttonColors,
        border = if (variant == CruxButtonVariant.Outlined) {
            BorderStroke(CruxTheme.size.borderHairline, colors.outline)
        } else {
            null
        },
        contentPadding = PaddingValues(horizontal = size.horizontalPadding),
        modifier = modifier.defaultMinSize(minHeight = size.height),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(CruxTheme.size.iconSm))
            Spacer(Modifier.width(CruxTheme.space.s2))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Extended FAB: a tonal button at radius-2xl with elevation-2. */
@Composable
fun CruxFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(text, style = MaterialTheme.typography.labelLarge) },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
        modifier = modifier,
    )
}
