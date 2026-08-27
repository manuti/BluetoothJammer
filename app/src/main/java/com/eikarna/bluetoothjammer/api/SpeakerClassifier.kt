package api

import com.eikarna.bluetoothjammer.R

/**
 * Classifies whether a nearby Bluetooth device is likely an audio speaker.
 *
 * Signals, strongest first:
 *  1. Classic Bluetooth device class (major = Audio/Video + speaker minor).
 *  2. BLE GAP appearance field (Generic Speaker = 0x0017).
 *  3. Device name heuristics (fallback when the device publishes no metadata).
 *
 * Note: constants below mirror android.bluetooth.BluetoothClass.Device.* values
 * as plain literals so this class stays unit-testable on the JVM.
 */
object SpeakerClassifier {

    enum class Confidence { HIGH, MEDIUM, LOW }

    /** Machine-readable reason so the UI can localize the label. */
    enum class Reason { DEVICE_CLASS, BLE_APPEARANCE, NAME }

    data class Result(
        val isSpeaker: Boolean,
        val confidence: Confidence,
        val reason: Reason?,
        val matchedKeyword: String? = null
    )

    // --- Classic device class (raw layout: bits 8-12 major, bits 2-7 minor) ---
    // BluetoothClass.Device.Major.AUDIO_VIDEO = 0x400; shifted value used by majorDeviceClass is 0x04
    private const val MAJOR_AUDIO_VIDEO = 0x04

    // Minor values of Audio/Video devices (BluetoothClass.Device.AUDIO_VIDEO_* >> 2)
    private const val MINOR_LOUDSPEAKER = 0x05          // AUDIO_VIDEO_LOUDSPEAKER (0x414)
    private const val MINOR_HIFI = 0x0A                 // AUDIO_VIDEO_HIFI_AUDIO (0x428)
    private const val MINOR_DISPLAY_AND_SPEAKER = 0x10  // AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER (0x440)
    private const val MINOR_PORTABLE_AUDIO = 0x07       // AUDIO_VIDEO_PORTABLE_AUDIO (0x41C)
    private const val MINOR_CAR_AUDIO = 0x08            // AUDIO_VIDEO_CAR_AUDIO (0x420)

    // --- BLE GAP appearance values (Bluetooth SIG Assigned Numbers) ---
    private const val APPEARANCE_GENERIC_SPEAKER = 0x0017

    // --- Name heuristics (fallback; lowercase substring match) ---
    private val SPEAKER_NAME_KEYWORDS = listOf(
        "speaker", "altavoz", "soundbar", "boombox", "boom box", "homepod",
        "sonos", "marshall", "harman", "ultimate ears", "ue boom", "ue megaboom",
        "echo dot", "echo show", "echo pop", "jbl", "alexa", "bose",
        "bluetooth speaker", "bt speaker", "mini speaker",
    )

    fun classify(name: String?, deviceClass: Int?, bleAppearance: Int?): Result {
        // 1. Classic device class (strongest)
        if (deviceClass != null) {
            val major = (deviceClass shr 8) and 0x1F
            val minor = (deviceClass and 0xFF) shr 2
            if (major == MAJOR_AUDIO_VIDEO) {
                when (minor) {
                    MINOR_LOUDSPEAKER, MINOR_HIFI, MINOR_DISPLAY_AND_SPEAKER ->
                        return Result(true, Confidence.HIGH, Reason.DEVICE_CLASS)
                    MINOR_PORTABLE_AUDIO, MINOR_CAR_AUDIO ->
                        return Result(true, Confidence.MEDIUM, Reason.DEVICE_CLASS)
                }
            }
        }

        // 2. BLE appearance
        if (bleAppearance == APPEARANCE_GENERIC_SPEAKER) {
            return Result(true, Confidence.HIGH, Reason.BLE_APPEARANCE)
        }

        // 3. Name heuristics
        val lower = name?.lowercase() ?: ""
        if (lower.isNotEmpty()) {
            val hit = SPEAKER_NAME_KEYWORDS.firstOrNull { lower.contains(it) }
            if (hit != null) return Result(true, Confidence.MEDIUM, Reason.NAME, matchedKeyword = hit)
        }

        return Result(false, Confidence.LOW, null)
    }

    /**
     * String resource id for a classic device class label, or null when unknown.
     */
    fun deviceTypeLabelRes(deviceClass: Int?): Int? {
        if (deviceClass == null) return null
        val major = (deviceClass shr 8) and 0x1F
        val minor = (deviceClass and 0xFF) shr 2
        return when (major) {
            0x01 -> R.string.device_type_computer
            0x02 -> R.string.device_type_phone
            0x04 -> when (minor) {
                MINOR_LOUDSPEAKER -> R.string.device_type_speaker
                MINOR_HIFI -> R.string.device_type_hifi
                MINOR_DISPLAY_AND_SPEAKER -> R.string.device_type_display_speaker
                0x06 -> R.string.device_type_headphones        // AUDIO_VIDEO_HEADPHONES
                0x01 -> R.string.device_type_headset           // AUDIO_VIDEO_WEARABLE_HEADSET
                0x02 -> R.string.device_type_handsfree         // AUDIO_VIDEO_HANDSFREE
                MINOR_PORTABLE_AUDIO -> R.string.device_type_portable_audio
                MINOR_CAR_AUDIO -> R.string.device_type_car_audio
                else -> R.string.device_type_audio_video
            }
            0x05 -> R.string.device_type_peripheral
            0x07 -> R.string.device_type_wearable
            0x09 -> R.string.device_type_health
            else -> null
        }
    }
}
