package com.hardtekpt.crux.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hardtekpt.crux.data.images.AreaImageStore
import com.hardtekpt.crux.ui.theme.CruxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The file for a stored wall image. */
@Composable
fun areaImageFile(name: String): File {
    val context = LocalContext.current
    return remember(name) { File(File(context.filesDir, AreaImageStore.DIR), name) }
}

/** Loads an image file off the main thread, decoded no larger than [maxPx] on its long side. */
@Composable
fun rememberLocalImage(file: File, maxPx: Int): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, file, maxPx) {
        value = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    return image
}

/** A small square crop of a stored image; tap to open it. */
@Composable
fun ImageThumbnail(
    name: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val colors = MaterialTheme.colorScheme
    val px = with(LocalDensity.current) { size.roundToPx() } * 2
    val image = rememberLocalImage(areaImageFile(name), px)
    val shape = MaterialTheme.shapes.small
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.surfaceContainerHigh, shape)
            .border(CruxTheme.size.borderHairline, colors.outlineVariant, shape)
            .clickable(onClickLabel = "View $description", onClick = onClick),
    ) {
        if (image != null) {
            Image(image, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.Image, contentDescription = description, tint = colors.onSurfaceVariant)
        }
    }
}

/** A stored image on its own, full screen: pinch to zoom, drag to pan, double-check the wall. */
@Composable
fun ImageViewer(name: String, title: String, onDismiss: () -> Unit) {
    val image = rememberLocalImage(areaImageFile(name), 2048)
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val zoom = rememberTransformableState { zoomBy, panBy, _ ->
        scale = (scale * zoomBy).coerceIn(1f, 5f)
        pan = if (scale == 1f) Offset.Zero else pan + panBy
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("image_viewer"),
        ) {
            if (image != null) {
                Image(
                    image,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(zoom)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = pan.x
                            translationY = pan.y
                        },
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(CruxTheme.space.s4),
            )
            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(CruxTheme.space.s2),
            ) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
        }
    }
}
