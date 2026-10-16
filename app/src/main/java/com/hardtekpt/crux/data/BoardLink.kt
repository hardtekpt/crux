package com.hardtekpt.crux.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hardtekpt.crux.data.model.PairedLights
import com.hardtekpt.crux.data.model.Section
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * The Bluetooth link to a paired board's lights: connects when a climb is sent, writes the
 * board's messages in Bluetooth-sized pieces (with a response where the protocol needs one), and
 * clears the board and disconnects when asked. Only boards picked in Android's pairing list.
 */
@Singleton
class BoardLink @Inject constructor(@ApplicationContext private val context: Context) {
    /** What the board shows: nothing, a climb being sent, a climb lit, or why it couldn't be reached. */
    sealed interface State {
        data object Off : State

        data class Sending(val climb: String) : State

        data class Lit(val climb: String, val board: String) : State

        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    private val lock = Mutex()
    private var gatt: BluetoothGatt? = null
    private var address: String? = null
    private var write: BluetoothGattCharacteristic? = null
    private var connected: CompletableDeferred<Boolean>? = null
    private var discovered: CompletableDeferred<Boolean>? = null
    private var written: CompletableDeferred<Boolean>? = null

    /** Whether Crux may talk to Bluetooth devices: Nearby devices on Android 12 and up. */
    fun permitted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /**
     * Lights a board climb ([holds], for [holdsBoard]) on [section]'s paired board. Returns false,
     * with [state] saying why, when it can't.
     */
    suspend fun light(section: Section, holdsBoard: String, holds: String, climb: String): Boolean {
        val lights = section.lights ?: return fail("Pair this board first, in Edit place.")
        val messages = messages(section, holdsBoard, holds) ?: return fail("This climb is for another board or size.")
        _state.value = State.Sending(climb)
        return send(lights, messages).also { ok -> if (ok) _state.value = State.Lit(climb, lights.name) }
    }

    /** The messages lighting [holds] on [section]'s board, or null when they don't fit it. */
    internal fun messages(section: Section, holdsBoard: String, holds: String): List<ByteArray>? {
        val lights = section.lights ?: return null
        // The climb must be for this board: the same layout.
        if (holdsBoard.split(':').take(2) != sectionKey(section)?.split(':')?.take(2)) return null
        val (layout, sizeId) = BoardGeometry.layout(context, holdsBoard) ?: return null
        val parsed = BoardGeometry.holds(holds)
        return if (lights.protocol == BoardLights.MOONBOARD || lights.protocol == 0) {
            val rows = layout.rows ?: return null
            val columns = layout.columns ?: return null
            val order = BoardLights.MoonOrder.entries.getOrElse(lights.order) { BoardLights.MoonOrder.FROM_A1 }
            val leds = parsed.mapNotNull { hold ->
                layout.points[hold.id]?.let { p -> BoardLights.moonLed(p.x.toInt(), p.y.toInt(), rows, columns, order) to hold.role }
            }
            listOf(BoardLights.moonCommand(leds))
        } else {
            val leds = layout.leds[sectionSizeId(section) ?: sizeId] ?: return null
            // A hold this size doesn't have means the climb is for a bigger board.
            if (parsed.any { it.id !in leds }) return null
            BoardLights.aurora(BoardLights.kilterLights(parsed, leds), lights.protocol)
        }
    }

    /** The size the section's board is, as the hold table numbers it. */
    private fun sectionSizeId(section: Section): String? {
        val board = section.board ?: return null
        return BoardseshBoards.sizeIds(board, section.boardSize, section.boardHolds).firstOrNull()?.toString()
    }

    /** The hold table's key for a section's board: "kilter:8:25", "moonboard:7". */
    fun sectionKey(section: Section): String? {
        val board = section.board ?: return null
        return when {
            board.maker == "Kilter" -> "kilter:${if (board == com.hardtekpt.crux.data.model.BoardModel.KILTER_HOMEWALL) 8 else 1}:${sectionSizeId(section)}"
            else -> "moonboard:${moonLayout(board)}"
        }
    }

    /** Lights four corner holds, to check the pairing and the size. */
    suspend fun test(section: Section): Boolean {
        val lights = section.lights ?: return fail("Pair this board first.")
        val key = sectionKey(section) ?: return fail("Say which board this is first.")
        val (layout, _) = BoardGeometry.layout(context, key) ?: return fail("Climb Logger has no hold positions for this board.")
        val points = layout.points.filterKeys { id -> layout.leds.isEmpty() || layout.leds[sectionSizeId(section)]?.containsKey(id) == true }
        if (points.isEmpty()) return fail("Climb Logger has no hold positions for this size.")
        val corners = listOf(
            points.minByOrNull { it.value.x - it.value.y },
            points.minByOrNull { -it.value.x - it.value.y },
            points.minByOrNull { it.value.x + it.value.y },
            points.minByOrNull { -it.value.x + it.value.y },
        ).filterNotNull().distinctBy { it.key }
        val holds = corners.joinToString("") { "p${it.key}r43" }
        return light(section, key, holds, "the four corners").also { if (it) _state.value = State.Lit("the four corners", lights.name) }
    }

    /** Lights one hold, for the MoonBoard check: A1 in the order being tried. */
    suspend fun lightFirst(section: Section, order: BoardLights.MoonOrder): Boolean {
        val lights = section.lights ?: return fail("Pair this board first.")
        val layout = sectionKey(section)?.let { BoardGeometry.layout(context, it)?.first } ?: return fail("Say which board this is first.")
        val message = BoardLights.moonCommand(listOf(BoardLights.moonLed(1, 1, layout.rows ?: 18, layout.columns ?: 11, order) to HoldRole.START))
        return send(lights, listOf(message))
    }

    /** Turns the board's lights off and disconnects. */
    suspend fun clear(section: Section?) {
        val lights = section?.lights
        if (lights != null && gatt != null) {
            val off = if (lights.protocol == BoardLights.MOONBOARD ||
                lights.protocol == 0
            ) {
                listOf(BoardLights.moonCommand(emptyList()))
            } else {
                BoardLights.aurora(emptyList(), lights.protocol)
            }
            runCatching { send(lights, off) }
        }
        disconnect()
        _state.value = State.Off
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        write = null
        address = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun send(lights: PairedLights, messages: List<ByteArray>): Boolean = lock.withLock {
        if (!permitted()) return@withLock fail("Climb Logger needs Nearby devices to reach your board.")
        try {
            withTimeout(TIMEOUT) { connect(lights.address) }
            val characteristic = write ?: return@withLock fail(CANT_REACH.format(lights.name))
            // Aurora v2 boxes want a response to each write; the rest are faster without.
            val withResponse = lights.protocol == BoardLights.AURORA_V2
            messages.flatMap(BoardLights::chunks).forEach { chunk -> withTimeout(TIMEOUT) { writeChunk(characteristic, chunk, withResponse) } }
            true
        } catch (_: Exception) {
            disconnect()
            fail(CANT_REACH.format(lights.name))
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connect(target: String) {
        if (gatt != null && address == target && write != null) return
        disconnect()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: error("no bluetooth")
        val device: BluetoothDevice = adapter.getRemoteDevice(target)
        connected = CompletableDeferred()
        discovered = CompletableDeferred()
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        address = target
        if (connected?.await() != true) error("not connected")
        gatt?.discoverServices()
        if (discovered?.await() != true) error("no services")
        val services = gatt?.services.orEmpty()
        write = services.firstOrNull { it.uuid == BoardLights.UART_SERVICE }?.getCharacteristic(BoardLights.UART_WRITE)
            ?: services.firstOrNull { it.uuid == BoardLights.REDBEAR_SERVICE }?.getCharacteristic(BoardLights.REDBEAR_WRITE)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun writeChunk(characteristic: BluetoothGattCharacteristic, chunk: ByteArray, withResponse: Boolean) {
        val type = if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val done = CompletableDeferred<Boolean>().also { written = it }
        val g = gatt ?: error("gone")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(characteristic, chunk, type)
        } else {
            characteristic.writeType = type
            characteristic.value = chunk
            g.writeCharacteristic(characteristic)
        }
        if (!done.await()) error("write failed")
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected?.complete(true)
            } else {
                connected?.complete(false)
                discovered?.complete(false)
                written?.complete(false)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            discovered?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            written?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    private fun fail(message: String): Boolean {
        _state.value = State.Failed(message)
        return false
    }

    companion object {
        private const val TIMEOUT = 8_000L
        private const val CANT_REACH = "Can't reach %s. Is it on and close by?"

        /** Boardsesh's MoonBoard layout for a Crux model. */
        fun moonLayout(board: com.hardtekpt.crux.data.model.BoardModel): Int = when (board) {
            com.hardtekpt.crux.data.model.BoardModel.MOON_2016 -> 2
            com.hardtekpt.crux.data.model.BoardModel.MOON_2017 -> 4
            com.hardtekpt.crux.data.model.BoardModel.MOON_2019 -> 5
            com.hardtekpt.crux.data.model.BoardModel.MOON_2024 -> 3
            com.hardtekpt.crux.data.model.BoardModel.MOON_MINI_2020 -> 6
            else -> 7
        }
    }
}
