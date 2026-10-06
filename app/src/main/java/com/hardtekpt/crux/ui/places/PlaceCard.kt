package com.hardtekpt.crux.ui.places

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.data.model.PlaceSummary
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.ui.components.areaImageFile
import com.hardtekpt.crux.ui.components.rememberLocalImage
import com.hardtekpt.crux.ui.relativeLabel
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import java.time.LocalDate

/** Each kind of place has its own accent, so gyms, crags and boards read apart at a glance. */
@Composable
private fun PlaceType.accent(): Color = when (this) {
    PlaceType.GYM -> MaterialTheme.colorScheme.primary
    PlaceType.CRAG -> MaterialTheme.colorScheme.secondary
    PlaceType.BOARD -> Color(0xFFB39DDB)
}

/**
 * A place as a card with presence: a banner (a wall photo, the map around its pin, or the
 * place's type drawn large), its kind and favourite star on the banner, then the name,
 * where it is, and its numbers: climbs, walls and open projects.
 */
@Composable
fun PlaceCard(summary: PlaceSummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val place = summary.place
    val accent = place.type.accent()
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, colors.outlineVariant, shape)
            .clickable(onClick = onOpen),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp),
        ) {
            PlaceBannerArt(place, summary.coverImage)
            // The favourite star, top right; what's here shows as module chips below.
            if (place.favourite) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(CruxTheme.space.s3)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.surface.copy(alpha = 0.88f)),
                ) {
                    Icon(Icons.Rounded.Star, contentDescription = "Favourite", tint = colors.primary, modifier = Modifier.size(20.dp))
                }
            }
        }

        Column(
            Modifier.padding(start = CruxTheme.space.s4, end = CruxTheme.space.s4, top = CruxTheme.space.s3, bottom = CruxTheme.space.s4),
            verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    place.name,
                    style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val where = place.location ?: place.mapLocation?.address
                val visit = summary.lastVisit?.let { "last visit ${it.relativeLabel(LocalDate.now()).lowercase()}" } ?: "not visited yet"
                Text(
                    listOfNotNull(where, visit).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The modules here: each part of the place with its kind, at a glance.
            if (place.sections.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                    verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                    modifier = Modifier.testTag("place_modules"),
                ) {
                    place.sections.forEach { section ->
                        val tint = section.type.accent()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(tint.copy(alpha = 0.12f))
                                .border(CruxTheme.size.borderHairline, tint.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("module_${section.name}"),
                        ) {
                            Icon(placeIcon(section.type), contentDescription = section.type.label, tint = tint, modifier = Modifier.size(16.dp))
                            Text(section.name, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 1)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.fillMaxWidth()) {
                PlaceStat("Climbs", summary.climbs, Modifier.weight(1f))
                PlaceStat(if (place.hasSeveralTypes) "Areas" else place.type.areaLabel + "s", summary.walls, Modifier.weight(1f))
                PlaceStat("Projects", summary.openProjects, Modifier.weight(1f), highlight = summary.openProjects > 0)
            }
        }
    }
}

/** The banner behind a place: a wall photo, the map around its pin, or its kind drawn large. */
@Composable
internal fun BoxScope.PlaceBannerArt(place: com.hardtekpt.crux.data.model.Place, coverImage: String?) {
    val accent = place.type.accent()
    val mapLocation = place.mapLocation
    when {
        coverImage != null -> {
            val image = rememberLocalImage(areaImageFile(coverImage), 900)
            Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.18f)))
            image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            BannerShade()
        }
        mapLocation != null -> {
            MapPreview(mapLocation, Modifier.fillMaxSize())
            BannerShade()
        }
        else -> TypeArt(place.type, accent)
    }
}

/** A kind's colour: gyms in the primary, crags in sandstone, boards in lilac. */
@Composable
internal fun kindAccent(type: PlaceType): Color = type.accent()

/** One number on a place card. Open projects light up so there is something to go back for. */
@Composable
private fun PlaceStat(label: String, value: Int, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .clip(shape)
            .background(if (highlight) colors.secondaryContainer else colors.surfaceContainer)
            .padding(horizontal = CruxTheme.space.s3, vertical = CruxTheme.space.s2),
    ) {
        Text(
            value.toString(),
            style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, fontFeatureSettings = "tnum"),
            color = if (highlight) colors.onSecondaryContainer else colors.onSurface,
        )
        Text(
            label.uppercase(),
            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.sp),
            color = if (highlight) colors.onSecondaryContainer else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Darkens the bottom of a photo or map banner so it sits well against the card. */
@Composable
private fun BannerShade() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f))),
    )
}

/** With no photo or map: the place's type drawn large in its accent, over a soft wash. */
@Composable
private fun TypeArt(type: PlaceType, accent: Color) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.32f), colors.surfaceContainer))),
    ) {
        Icon(
            placeIcon(type),
            contentDescription = null,
            tint = accent.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(120.dp)
                .offset(x = 18.dp, y = 14.dp),
        )
    }
}
