package com.hardtekpt.crux.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag

/** The official MoonBoard app. It opens no problem from a link, so Crux can only start it. */
const val MOONBOARD_APP = "com.trainingboard.moon"

/** What starts the MoonBoard app, or null when it isn't installed. */
fun moonBoardAppIntent(context: Context): Intent? = context.packageManager.getLaunchIntentForPackage(MOONBOARD_APP)

/**
 * Open in MoonBoard: copies the climb's name and starts the official app, to paste into its search.
 * Shows only for a MoonBoard climb ([holdsBoard] "moonboard:…") with the app installed.
 */
@Composable
fun MoonBoardAppButton(holdsBoard: String, climb: String, modifier: Modifier = Modifier) {
    if (!holdsBoard.startsWith("moonboard:")) return
    val context = LocalContext.current
    val launch = remember { moonBoardAppIntent(context) } ?: return
    CruxButton(
        text = "Open in MoonBoard",
        onClick = {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Climb", climb))
            Toast.makeText(context, "Copied \"$climb\": paste it into MoonBoard's search", Toast.LENGTH_SHORT).show()
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        icon = Icons.AutoMirrored.Rounded.OpenInNew,
        variant = CruxButtonVariant.Outlined,
        modifier = modifier.testTag("moonboard_app"),
    )
}
