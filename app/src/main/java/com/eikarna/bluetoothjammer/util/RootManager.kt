package util

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Result of a shell/su execution. `error` holds timeout/exception info. */
data class SuResult(val exitCode: Int, val output: String, val error: String = "") {
    val ok: Boolean get() = exitCode == 0
    val text: String
        get() {
            val parts = listOf(output.trim(), error.trim()).filter { it.isNotEmpty() }
            return if (parts.isEmpty()) "" else parts.joinToString("\n")
        }
}

/**
 * Minimal root access layer (Magisk / SuperSU style su).
 *
 * Design notes (see CONTEXT.md / README "con root"):
 *  - Detection never triggers the su grant prompt: it only looks for the
 *    binary (`command -v su` or well-known paths).
 *  - [requestRoot] runs `su -c id`, which IS what pops the su manager prompt
 *    the first time; call it from a background thread and give it a generous
 *    timeout (the user must answer the prompt).
 *  - [suExec] runs an arbitrary command as root via `su -c <command>`.
 *
 * Honest limits: on stock ROMs SELinux enforcing may deny HCI access even
 * with su (documented in the README); output/errors are returned verbatim
 * so the UI can show real denials instead of pretending success.
 */
object RootManager {

    @Volatile
    var granted: Boolean = false
        private set

    @Volatile
    private var cachedSuPath: String? = null

    private val suCandidates = listOf(
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/vendor/bin/su",
        "/su/bin/su"
    )

    /** Locates the su binary without triggering the grant prompt. */
    @Synchronized
    fun suPath(): String? {
        cachedSuPath?.let { return it }
        val lookup = exec(arrayOf("sh", "-c", "command -v su 2>/dev/null || true"))
        val found = lookup.output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("/") }
            ?: suCandidates.firstOrNull { File(it).canExecute() }
        cachedSuPath = found
        return found
    }

    /**
     * Requests root. On rooted devices this pops the su manager grant
     * dialog (Magisk/Superuser); the call blocks until the user answers.
     */
    fun requestRoot(timeoutMs: Long = 90_000): SuResult {
        val path = suPath()
        if (path == null) {
            granted = false
            return SuResult(-3, "", "no su binary found on this device")
        }
        val result = exec(arrayOf(path, "-c", "id"), timeoutMs)
        granted = result.exitCode == 0 && result.output.contains("uid=0")
        return result
    }

    /** Runs one shell command as root (blocking; use from Dispatchers.IO). */
    fun suExec(command: String, timeoutMs: Long = 30_000): SuResult {
        val path = suPath()
        if (path == null) return SuResult(-3, "", "no su binary found on this device")
        val result = exec(arrayOf(path, "-c", command), timeoutMs)
        if (result.exitCode == 0) granted = true
        return result
    }

    /**
     * Lists which Bluetooth CLI tools (bluez) are reachable from a root
     * shell: hcitool, hciconfig, hcidump, btmon. Runs unprivileged.
     */
    fun bluetoothToolsInstalled(): List<String> {
        val result = exec(
            arrayOf(
                "sh", "-c",
                "command -v hcitool 2>/dev/null; command -v hciconfig 2>/dev/null; " +
                    "command -v hcidump 2>/dev/null; command -v btmon 2>/dev/null"
            ),
            10_000
        )
        return result.output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("/") }
            .map { it.substringAfterLast('/') }
            .distinct()
            .toList()
    }

    // ---------- internals ----------

    private fun exec(command: Array<String>, timeoutMs: Long): SuResult {
        return try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            val out = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            if (!finished) {
                process.destroyForcibly()
                SuResult(-1, out, "timed out after ${timeoutMs}ms")
            } else {
                SuResult(process.exitValue(), out)
            }
        } catch (e: Exception) {
            SuResult(-2, "", e.message ?: e.toString())
        }
    }
}
