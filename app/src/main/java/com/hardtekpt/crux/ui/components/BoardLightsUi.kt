package com.hardtekpt.crux.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.os.Build
import android.os.ParcelUuid
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.BoardLights
import com.hardtekpt.crux.data.BoardLink
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.model.Section
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Your facilities with a paired board, and the link to light them. */
@HiltViewModel
class BoardLightsViewModel @Inject constructor(places: PlaceRepository, val link: BoardLink) : ViewModel() {
    val paired: StateFlow<List<Section>> = places.observePlaces()
        .map { list -> list.flatMap { it.place.sections }.filter { it.lights != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<BoardLink.State> = link.state

    /** The paired facility a climb for [holdsBoard] lights on: [preferred] when it fits, else the first that does. */
    fun boardFor(holdsBoard: String, holds: String, preferred: Long?): Section? {
        val fits = paired.value.filter { link.messages(it, holdsBoard, holds) != null }
        return fits.firstOrNull { it.id == preferred } ?: fits.firstOrNull()
    }

    fun light(section: Section, holdsBoard: String, holds: String, climb: String) {
        viewModelScope.launch { link.light(section, holdsBoard, holds, climb) }
    }

    fun off(section: Section?) {
        viewModelScope.launch { link.clear(section) }
    }
}

/**
 * Light: sends a board climb to a paired board that fits it (the facility you're at first). Shows
 * nothing when no paired board can take it. Lit, it says where, with Off; it never lights by itself.
 */
@Composable
fun LightButton(
    holdsBoard: String,
    holds: String,
    climb: String,
    modifier: Modifier = Modifier,
    preferredSection: Long? = null,
    viewModel: BoardLightsViewModel = hiltViewModel(),
) {
    val paired by viewModel.paired.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val board = remember(paired, holdsBoard, holds, preferredSection) { viewModel.boardFor(holdsBoard, holds, preferredSection) } ?: return
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.light(board, holdsBoard, holds, climb)
    }
    Column(modifier.testTag("light_button"), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s1)) {
        val lit = (state as? BoardLink.State.Lit)?.takeIf { it.climb == climb }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
            CruxButton(
                text = when {
                    state is BoardLink.State.Sending -> "Sending…"
                    lit != null -> "Lit on ${lit.board}"
                    else -> "Light"
                },
                onClick = {
                    if (!viewModel.link.permitted()) {
                        permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        viewModel.light(board, holdsBoard, holds, climb)
                    }
                },
                icon = Icons.Rounded.Lightbulb,
                variant = if (lit != null) CruxButtonVariant.Tonal else CruxButtonVariant.Outlined,
                size = CruxButtonSize.Small,
                modifier = Modifier.testTag("light_climb"),
            )
            if (lit != null) TextButton(onClick = { viewModel.off(board) }, modifier = Modifier.testTag("light_off")) { Text("Off") }
        }
        (state as? BoardLink.State.Failed)?.let {
            Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** A board found in Android's pairing picker: its address and name. */
data class FoundBoard(val address: String, val name: String)

/**
 * Pairing through Android's companion device picker: the system finds the board and the climber
 * picks it, so Crux never scans and needs no location. A Kilter shows only Aurora boards; a
 * MoonBoard's box is picked from the nearby Bluetooth devices. Asks for Nearby devices first on
 * Android 12 and up, so the board's name can be read.
 */
@Composable
fun rememberBoardPairing(onFound: (FoundBoard) -> Unit, onFailed: (String) -> Unit): (kilter: Boolean) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Boolean?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        found(result.data)?.let(onFound) ?: onFailed("That device can't be used.")
    }
    val start = { kilter: Boolean ->
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
        if (cdm == null) {
            onFailed("This phone can't pair Bluetooth devices.")
        } else {
            val filter = BluetoothLeDeviceFilter.Builder().apply {
                if (kilter) setScanFilter(ScanFilter.Builder().setServiceUuid(ParcelUuid(BoardLights.AURORA_SERVICE)).build())
            }.build()
            val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build()
            val callback = object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) = picker.launch(IntentSenderRequest.Builder(intentSender).build())

                @Deprecated("Before Android 13")
                override fun onDeviceFound(intentSender: IntentSender) = picker.launch(IntentSenderRequest.Builder(intentSender).build())

                override fun onFailure(error: CharSequence?) = onFailed(error?.toString() ?: "No board found nearby. Is it on?")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cdm.associate(request, Executor { it.run() }, callback)
            } else {
                @Suppress("DEPRECATION")
                cdm.associate(request, callback, null)
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kilter = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (granted) start(kilter) else onFailed("Crux needs Nearby devices to reach your board.")
    }
    return { kilter ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pending = kilter
            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            start(kilter)
        }
    }
}

/** The device the picker returned, on any Android version. */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun found(data: android.content.Intent?): FoundBoard? {
    data ?: return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)?.let { info ->
            val address = info.deviceMacAddress?.toString()?.uppercase() ?: return null
            return FoundBoard(address, info.displayName?.toString() ?: address)
        }
    }
    return when (val device = data.getParcelableExtra<android.os.Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)) {
        is ScanResult -> FoundBoard(device.device.address, device.device.name ?: device.scanRecord?.deviceName ?: device.device.address)
        is BluetoothDevice -> FoundBoard(device.address, device.name ?: device.address)
        else -> null
    }
}
