package com.hardtekpt.crux.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import com.hardtekpt.crux.BuildConfig
import com.hardtekpt.crux.R
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

private const val SOURCE_URL = "https://github.com/hardtekpt/crux"
private const val LICENCE_URL = "$SOURCE_URL/blob/main/LICENSE"

/** Version, licence and source, then every open-source library the app is built on. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    val space = CruxTheme.space
    Column(Modifier.fillMaxSize().testTag("screen_About")) {
        CruxTopAppBar(title = "About", onBack = onBack)
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = space.s4 + LocalNavBarClearance.current),
            header = {
                item {
                    Column(
                        Modifier.padding(horizontal = space.s4),
                        verticalArrangement = Arrangement.spacedBy(space.s3),
                    ) {
                        AboutCard()
                        Eyebrow("Open-source libraries", Modifier.padding(top = space.s2))
                    }
                }
            },
        )
    }
}

@Composable
private fun AboutCard() {
    val uriHandler = LocalUriHandler.current
    val space = CruxTheme.space
    CruxCard {
        Text("Crux", style = MaterialTheme.typography.titleLarge)
        Text(
            "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = CruxTheme.type.code,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = space.s2),
        )
        Text(
            "Crux is free software: you can share and change it under the GNU General Public License, " +
                "version 3 or later. It comes with no warranty.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Map data © OpenStreetMap contributors, available under the Open Database License.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = space.s2),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(space.s2),
            modifier = Modifier.padding(top = space.s3),
        ) {
            CruxButton(
                text = "Source code",
                onClick = { uriHandler.openUri(SOURCE_URL) },
                icon = Icons.Rounded.Code,
                modifier = Modifier.testTag("open_source_code"),
            )
            CruxButton(
                text = "Licence",
                onClick = { uriHandler.openUri(LICENCE_URL) },
                variant = CruxButtonVariant.Outlined,
                icon = Icons.Rounded.Description,
            )
        }
    }
}
