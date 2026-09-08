package util

import android.content.Context
import android.provider.Settings

/**
 * Read-only queries about the Android developer options that matter for
 * Bluetooth research.
 *
 * Honest limits:
 *  - A normal app CANNOT enable developer options nor most of their
 *    Bluetooth toggles (they need WRITE_SECURE_SETTINGS / user action).
 *    This helper only reads state; toggling is done by the user manually
 *    or via root (`settings put global bluetooth_hci_log 1`).
 *  - Some ROMs keep the HCI snoop setting in different namespaces, so the
 *    queries try Global and Secure and report `null` when not readable.
 */
object DevOptions {

    const val KEY_HCI_SNOOP_LOG = "bluetooth_hci_log"

    /** true = developer options enabled, false = disabled, null = not readable. */
    fun developerOptionsEnabled(context: Context): Boolean? = try {
        Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1
    } catch (e: Exception) {
        null
    }

    /** true = HCI snoop log on, false = off, null = not readable. */
    fun hciSnoopEnabled(context: Context): Boolean? {
        val cr = context.contentResolver
        try {
            return Settings.Global.getInt(cr, KEY_HCI_SNOOP_LOG, 0) == 1
        } catch (e: Exception) {
            // fall through: some ROMs keep it under Secure
        }
        return try {
            Settings.Secure.getInt(cr, KEY_HCI_SNOOP_LOG, 0) == 1
        } catch (e: Exception) {
            null
        }
    }
}
