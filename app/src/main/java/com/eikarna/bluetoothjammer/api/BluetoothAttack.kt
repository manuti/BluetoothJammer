package api

import android.content.Context
import com.eikarna.bluetoothjammer.R

/**
 * Common contract for the Bluetooth disruption techniques.
 * Implementations run on their own background coroutines and report progress
 * through [onLog] (called from background threads — the caller marshals to UI).
 */
interface BluetoothAttack {
    val displayName: String
    val description: String
    fun start(context: Context, onLog: (String) -> Unit)
    fun stop()
    fun isRunning(): Boolean
}

/**
 * Selectable attack types. [create] builds the concrete attack for a target,
 * applying the TX/Sleep duty cycle when configured.
 *
 * Names are localized through string resources ([labelRes]/[descRes]); the
 * English fallbacks are used only when no context is available (e.g. engine
 * log prefixes before [start] has run).
 */
enum class AttackType(
    val labelRes: Int,
    val descRes: Int,
    val fallbackLabel: String,
    val fallbackDesc: String
) {
    L2CAP_FLOOD(
        R.string.attack_l2cap_flood,
        R.string.attack_l2cap_flood_desc,
        "L2CAP Flood (classic)",
        "Floods RFCOMM/L2CAP connections with random UUIDs and saturates the socket."
    ),
    RFCOMM_CHANNEL_FLOOD(
        R.string.attack_rfcomm_channel_flood,
        R.string.attack_rfcomm_channel_flood_desc,
        "RFCOMM Channel Flood",
        "Sweeps RFCOMM channels 1-30 via reflection (hidden API) and saturates connected sockets."
    ),
    GATT_FLOOD(
        R.string.attack_gatt_flood,
        R.string.attack_gatt_flood_desc,
        "GATT Flood (BLE)",
        "Fills the target BLE peripheral's GATT connection table to lock out its owner."
    ),
    PAIRING_FLOOD(
        R.string.attack_pairing_flood,
        R.string.attack_pairing_flood_desc,
        "Pairing Flood",
        "Floods the target with pairing requests (dialog spam)."
    ),
    SDP_FLOOD(
        R.string.attack_sdp_flood,
        R.string.attack_sdp_flood_desc,
        "SDP Query Storm",
        "Saturates the target's SDP server with repeated service queries."
    ),
    ADVERTISE_FLOOD(
        R.string.attack_advertise_flood,
        R.string.attack_advertise_flood_desc,
        "Advertising Flood (BLE)",
        "Pollutes the BLE advertising channel with random UUIDs."
    ),
    PROFILE_SPOOF(
        R.string.attack_profile_spoof,
        R.string.attack_profile_spoof_desc,
        "Profile Spoofing",
        "Impersonates known profiles (A2DP, HID, HFP…) by trying connections with their UUIDs."
    ),
    COMBO(
        R.string.attack_combo,
        R.string.attack_combo_desc,
        "Combo (L2CAP+RFCOMM+GATT+Pairing+SDP)",
        "Layered attack: L2CAP, RFCOMM, GATT, Pairing and SDP simultaneously against the same target."
    );

    /** Localized display name for the current system locale. */
    fun displayName(context: Context): String = context.getString(labelRes)

    /** Localized description for the current system locale. */
    fun description(context: Context): String = context.getString(descRes)

    fun create(address: String, params: AttackParams = AttackParams()): BluetoothAttack {
        val base = when (this) {
            L2CAP_FLOOD -> L2capFloodAttack(
                address, params.threads, params.rateDelayMs,
                params.payloadPattern, params.payloadSize, params.bombard
            )
            RFCOMM_CHANNEL_FLOOD -> RfcommChannelFloodAttack(
                address, params.threads, params.rateDelayMs,
                params.payloadPattern, params.payloadSize
            )
            GATT_FLOOD -> GattFloodAttack(address, params.threads, params.rateDelayMs)
            PAIRING_FLOOD -> PairingFloodAttack(address, params.threads, params.rateDelayMs)
            SDP_FLOOD -> SdpFloodAttack(address, params.threads, params.rateDelayMs)
            ADVERTISE_FLOOD -> AdvertiseFloodAttack(address)
            PROFILE_SPOOF -> ProfileSpoofAttack(address, params.threads, params.rateDelayMs)
            COMBO -> ComboAttack(
                address, params.threads, params.rateDelayMs,
                params.payloadPattern, params.payloadSize, params.bombard
            )
        }
        return if (params.txSeconds >= 1 && params.sleepSeconds >= 1) {
            DutyCycleAttack(base, params.txSeconds, params.sleepSeconds)
        } else {
            base
        }
    }
}
