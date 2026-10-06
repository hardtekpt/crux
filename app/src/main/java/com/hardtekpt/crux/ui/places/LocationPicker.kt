package com.hardtekpt.crux.ui.places

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hardtekpt.crux.data.model.MapLocation
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.theme.CruxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.coroutines.resume

/** Opens the point in the phone's maps app (Google Maps or whichever handles geo: links). */
fun openInMaps(context: Context, location: MapLocation, label: String) {
    val query = Uri.encode("${location.latitude},${location.longitude}($label)")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${location.latitude},${location.longitude}?q=$query"))
    runCatching { context.startActivity(intent) }
}

/**
 * Pick where a place is: search for an address or place name, or drag the map under the pin,
 * or jump to where you are. The map is OpenStreetMap; search uses the phone's own geocoder.
 */
@Composable
fun LocationPickerDialog(
    initial: MapLocation?,
    placeName: String,
    onPick: (MapLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var query by rememberSaveable { mutableStateOf(initial?.address ?: placeName) }
    var results by remember { mutableStateOf<List<Address>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val map = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(if (initial != null) 17.0 else 3.0)
            controller.setCenter(GeoPoint(initial?.latitude ?: 30.0, initial?.longitude ?: 0.0))
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }
    val moveTo = { lat: Double, lng: Double ->
        map.controller.animateTo(GeoPoint(lat, lng), 17.0, 600L)
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val search = {
        val text = query.trim()
        keyboard?.hide()
        if (text.isNotEmpty()) {
            busy = true
            message = null
            scope.launch {
                val found = geocode(context, text)
                results = found.orEmpty()
                busy = false
                when {
                    found == null -> message = "Search needs the internet and the phone's location service. You can still move the map by hand."
                    found.isEmpty() -> message = "Nothing found for \"$text\"."
                    found.size == 1 -> {
                        moveTo(found[0].latitude, found[0].longitude)
                        results = emptyList()
                    }
                }
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            lastKnown(context)?.let { (lat, lng) -> moveTo(lat, lng) } ?: run { message = "Your location isn't known yet. Try again outdoors." }
        } else {
            message = "Location permission was not given."
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .testTag("location_picker"),
        ) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
            // The pin sits over the map's centre; its tip marks the spot.
            Icon(
                Icons.Rounded.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .offset(y = (-22).dp),
            )

            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(CruxTheme.space.s3),
                verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search an address or place") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = search) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = CruxTheme.space.s2)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                            .testTag("location_search"),
                    )
                }
                if (busy) Text("Searching…", style = MaterialTheme.typography.bodySmall)
                if (results.size > 1) {
                    CruxCard {
                        results.take(5).forEach { address ->
                            CruxListRow(
                                title = address.label(),
                                onClick = {
                                    moveTo(address.latitude, address.longitude)
                                    results = emptyList()
                                },
                                modifier = Modifier.testTag("location_result"),
                            )
                        }
                    }
                }
                message?.let {
                    CruxCard { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(CruxTheme.space.s4),
            ) {
                FilledTonalIconButton(
                    onClick = {
                        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (fine || coarse) {
                            lastKnown(context)?.let { (lat, lng) -> moveTo(lat, lng) } ?: run { message = "Your location isn't known yet. Try again outdoors." }
                        } else {
                            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    modifier = Modifier.testTag("my_location"),
                ) { Icon(Icons.Rounded.MyLocation, contentDescription = "My location") }
                CruxButton(
                    text = "Use this spot",
                    size = CruxButtonSize.Large,
                    onClick = {
                        val centre = map.mapCenter
                        scope.launch {
                            val address = reverseGeocode(context, centre.latitude, centre.longitude)
                            onPick(MapLocation(centre.latitude, centre.longitude, address?.label()))
                        }
                    },
                    modifier = Modifier.testTag("use_location"),
                )
            }
            Text(
                "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(CruxTheme.space.s2)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(horizontal = CruxTheme.space.s1),
            )
        }
    }
}

/** "Climbing Hangar, 12 Some Street, Lisbon", from whatever parts the geocoder knows. */
private fun Address.label(): String {
    val line = getAddressLine(0)
    val name = featureName?.takeIf { it != subThoroughfare && it != thoroughfare && line?.startsWith(it) != true }
    return listOfNotNull(name, line ?: listOfNotNull(thoroughfare, locality).joinToString(", ").ifBlank { null })
        .joinToString(", ")
        .ifBlank { "%.5f, %.5f".format(latitude, longitude) }
}

/** Address search; null when the phone has no geocoder or it failed (often: no internet). */
@Suppress("DEPRECATION")
private suspend fun geocode(context: Context, text: String): List<Address>? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context)
    return withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine<List<Address>?> { cont ->
                    geocoder.getFromLocationName(text, 5, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.toList())
                        override fun onError(errorMessage: String?) = cont.resume(null)
                    })
                }
            } else {
                geocoder.getFromLocationName(text, 5).orEmpty()
            }
        }.getOrNull()
    }
}

@Suppress("DEPRECATION")
private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): Address? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context)
    return withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                        override fun onError(errorMessage: String?) = cont.resume(null)
                    })
                }
            } else {
                geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
            }
        }.getOrNull()
    }
}

/** The freshest location any provider already has; no waiting for a new fix. */
@SuppressLint("MissingPermission")
private fun lastKnown(context: Context): Pair<Double, Double>? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return manager.getProviders(true)
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
        ?.let { it.latitude to it.longitude }
}
