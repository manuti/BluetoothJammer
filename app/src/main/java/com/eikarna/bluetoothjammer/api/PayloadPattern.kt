package api

import android.content.Context
import com.eikarna.bluetoothjammer.R
import kotlin.random.Random

/**
 * Payload byte-pattern selectable per attack (analog of the signal types of the
 * PortaPack Mayhem "Jammer TX": noise, fixed pattern, sweep, chirp...).
 * At protocol level the exact shape rarely matters for the effect, but it is
 * useful to study and keeps the tool flexible.
 */
enum class PayloadPattern(val displayNameRes: Int, val fallbackName: String) {
    RANDOM(R.string.pattern_random, "Random noise"),
    FIXED(R.string.pattern_fixed, "Fixed pattern (A-Z)"),
    SAWTOOTH(R.string.pattern_sawtooth, "Sawtooth (0-255)"),
    CHIRP(R.string.pattern_chirp, "Wavy (chirp)");

    /** Builds a [size]-byte payload buffer with this pattern. */
    fun buffer(size: Int): ByteArray = when (this) {
        RANDOM -> ByteArray(size) { Random.nextInt(256).toByte() }
        FIXED -> ByteArray(size) { ('A'.code + (it % 26)).toByte() }
        SAWTOOTH -> ByteArray(size) { (it % 256).toByte() }
        CHIRP -> ByteArray(size) { ((it * 13) % 256).toByte() }
    }
}
