package com.eikarna.bluetoothjammer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import api.BluetoothDeviceInfo
import api.DeviceSource
import api.ScanNearbyDevices
import api.SpeakerClassifier
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import util.DevOptions
import util.RootManager
import util.SuResult

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var deviceListAdapter: DeviceAdapter
    private lateinit var btnScan: Button
    private lateinit var btnAttackSelected: Button
    private lateinit var btnDevOptions: Button
    private lateinit var btnRoot: Button
    private lateinit var switchSpeakersOnly: MaterialSwitch
    private lateinit var txtStatus: TextView
    private val scanner = ScanNearbyDevices.getInstance()
    private var onlySpeakers = false
    private var currentDevices: List<BluetoothDeviceInfo> = emptyList()
    private val selectedTargets = LinkedHashMap<String, String>()

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.deviceListView)
        btnScan = findViewById(R.id.btnScan)
        btnAttackSelected = findViewById(R.id.btnAttackSelected)
        btnDevOptions = findViewById(R.id.btnDevOptions)
        btnRoot = findViewById(R.id.btnRootStatus)
        switchSpeakersOnly = findViewById(R.id.switchSpeakersOnly)
        txtStatus = findViewById(R.id.txtStatus)

        btnDevOptions.setOnClickListener { showDevOptionsDialog() }
        btnRoot.setOnClickListener { onRootButtonClick() }

        deviceListAdapter = DeviceAdapter(this, mutableListOf())
        listView.adapter = deviceListAdapter

        btnScan.setOnClickListener { startScan() }
        switchSpeakersOnly.setOnCheckedChangeListener { _, checked ->
            onlySpeakers = checked
            refreshList()
        }

        btnAttackSelected.setOnClickListener { launchSelectedAttack() }
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val device = deviceListAdapter.getItem(position) ?: return@setOnItemLongClickListener false
            val label = device.name ?: getString(R.string.unknown)
            if (selectedTargets.containsKey(device.address)) {
                selectedTargets.remove(device.address)
                Toast.makeText(this, getString(R.string.target_removed, label), Toast.LENGTH_SHORT).show()
            } else {
                selectedTargets[device.address] = label
                Toast.makeText(this, getString(R.string.target_added, label), Toast.LENGTH_SHORT).show()
            }
            updateAttackButton()
            true
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedDevice = deviceListAdapter.getItem(position) ?: return@setOnItemClickListener
            showDeviceInfo(selectedDevice)
        }

        checkBluetoothStatusAndPermissions()
        updateDevChip()
        updateRootChip()
    }

    // ---------- Educational warning ----------

    private fun showEducationalWarning() {
        AlertDialog.Builder(this)
            .setTitle(R.string.warning_title)
            .setMessage(R.string.warning_message)
            .setPositiveButton(R.string.warning_accept) { _, _ -> checkBluetoothStatusAndPermissions() }
            .setCancelable(false)
            .show()
    }

    // ---------- Scanning ----------

    private fun startScan() {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            showBluetoothDisabledDialog()
            return
        }
        if (!permissionsGranted()) {
            checkBluetoothStatusAndPermissions()
            return
        }
        txtStatus.text = getString(R.string.scanning)
        scanner.startScanning(this) { devices -> runOnUiThread { onDevicesUpdated(devices) } }
    }

    private fun onDevicesUpdated(devices: List<BluetoothDeviceInfo>) {
        currentDevices = devices
        refreshList()
    }

    private fun refreshList() {
        val filtered = if (onlySpeakers) currentDevices.filter { it.isSpeaker } else currentDevices
        deviceListAdapter.update(filtered)
        val speakers = currentDevices.count { it.isSpeaker }
        txtStatus.text = when {
            currentDevices.isEmpty() -> getString(R.string.no_devices)
            onlySpeakers -> getString(R.string.status_filtered, filtered.size)
            else -> getString(R.string.status_total, currentDevices.size, speakers)
        }
    }

    // ---------- Multi-target selection ----------

    private fun updateAttackButton() {
        btnAttackSelected.text = getString(R.string.attack_selected, selectedTargets.size)
        btnAttackSelected.isEnabled = selectedTargets.isNotEmpty()
    }

    private fun launchSelectedAttack() {
        if (selectedTargets.isEmpty()) return
        scanner.stopScanning()
        val list = ArrayList(selectedTargets.map { (addr, name) -> "$name|$addr" })
        val intent = Intent(this, AttackActivity::class.java).apply {
            putStringArrayListExtra("EXTRA_TARGETS", list)
            putExtra("THREADS", 8)
        }
        startActivity(intent)
    }

    // ---------- Permissions ----------

    private fun permissionsGranted(): Boolean {
        val permissions = requiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    private fun checkBluetoothStatusAndPermissions() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            showBluetoothDisabledDialog()
        } else if (!permissionsGranted()) {
            ActivityCompat.requestPermissions(this, requiredPermissions(), PERMISSION_REQUEST_CODE)
        } else {
            startScan()
        }
    }

    private fun showBluetoothDisabledDialog() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startScan()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.permission_required),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ---------- Device dialog (existing behavior) ----------

    private fun showDeviceInfo(device: BluetoothDeviceInfo) {
        val speakerTag = if (device.isSpeaker) "\n" + getString(R.string.device_info_speaker_type) else ""
        val vendorLine = device.vendor?.let { "\n" + getString(R.string.device_info_vendor, it) } ?: ""
        val servicesLine = device.serviceUuids?.take(4)?.joinToString(", ")?.let { "\n" + getString(R.string.device_info_services, it) } ?: ""
        val message = getString(R.string.device_info_name, device.name ?: getString(R.string.unknown)) +
            "\n" + getString(R.string.device_info_address, device.address) + speakerTag + vendorLine + servicesLine

        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(R.string.device_info_title)
            .setMessage(message)
            .setPositiveButton(R.string.attack_btn) { dialog, _ ->
                dialog.dismiss()
                scanner.stopScanning()
                val intent = Intent(this, AttackActivity::class.java).apply {
                    putExtra("DEVICE_NAME", device.name ?: getString(R.string.unknown))
                    putExtra("ADDRESS", device.address)
                    putExtra("THREADS", 8)
                }
                startActivity(intent)
            }
            .setNegativeButton(R.string.close) { dialog, _ -> dialog.dismiss() }
            .setNeutralButton(R.string.copy_info) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("Device Info", message)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, getString(R.string.device_info_copied), Toast.LENGTH_SHORT).show()
            }
        dialogBuilder.create().show()
    }

    // ---------- Lifecycle ----------

    override fun onResume() {
        super.onResume()
        if (permissionsGranted()) startScan()
        updateDevChip()
        updateRootChip()
    }

    override fun onPause() {
        super.onPause()
        scanner.stopScanning()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.stopScanning()
    }

    // ---------- Developer options & root (v1.6) ----------

    private fun updateDevChip() {
        btnDevOptions.text = getString(
            R.string.dev_chip_status,
            getString(
                when (DevOptions.developerOptionsEnabled(this)) {
                    true -> R.string.dev_state_on
                    false -> R.string.dev_state_off
                    null -> R.string.dev_state_unknown
                }
            )
        )
    }

    private fun updateRootChip() {
        if (RootManager.granted) {
            btnRoot.text = getString(R.string.root_chip_status, getString(R.string.root_state_granted))
            return
        }
        lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) { RootManager.suPath() != null }
            btnRoot.text = getString(
                R.string.root_chip_status,
                getString(if (available) R.string.root_state_avail else R.string.root_state_none)
            )
        }
    }

    private fun onRootButtonClick() {
        if (RootManager.granted) {
            showRootToolsDialog()
            return
        }
        lifecycleScope.launch {
            val hasSu = withContext(Dispatchers.IO) { RootManager.suPath() != null }
            if (!hasSu) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.root_no_su_title)
                    .setMessage(R.string.root_no_su_message)
                    .setPositiveButton(R.string.close, null)
                    .show()
                return@launch
            }
            Toast.makeText(this@MainActivity, getString(R.string.root_requesting), Toast.LENGTH_LONG).show()
            val result = withContext(Dispatchers.IO) { RootManager.requestRoot() }
            updateRootChip()
            if (RootManager.granted) {
                Toast.makeText(this@MainActivity, getString(R.string.root_yes), Toast.LENGTH_SHORT).show()
                showRootToolsDialog()
            } else {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.root_denied_title)
                    .setMessage(getString(R.string.root_denied_message, result.text.take(1200).ifBlank { "-" }))
                    .setPositiveButton(R.string.close, null)
                    .show()
            }
        }
    }

    private fun showRootToolsDialog() {
        lifecycleScope.launch {
            val tools = withContext(Dispatchers.IO) { RootManager.bluetoothToolsInstalled() }
            val toolsLine = getString(
                R.string.root_tools_found,
                if (tools.isEmpty()) getString(R.string.root_tools_none) else tools.joinToString(", ")
            )
            val labels = arrayOf(
                getString(R.string.root_tool_hci),
                getString(R.string.root_tool_con),
                getString(R.string.root_tool_snoop_on),
                getString(R.string.root_tool_snoop_off),
                getString(R.string.root_tool_bt_off),
                getString(R.string.root_tool_bt_on)
            )
            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.root_tools_title)
                .setMessage(toolsLine)
                .setItems(labels) { _, which ->
                    when (which) {
                        0 -> runRootCommand(labels[0], "hcitool dev; echo '---'; hciconfig hci0 2>&1 || true")
                        1 -> runRootCommand(labels[1], "hcitool con")
                        2 -> runRootCommand(labels[2], "settings put global bluetooth_hci_log 1", getString(R.string.root_note_snoop))
                        3 -> runRootCommand(labels[3], "settings put global bluetooth_hci_log 0", getString(R.string.root_note_snoop))
                        4 -> AlertDialog.Builder(this@MainActivity)
                            .setTitle(R.string.root_confirm_bt_off_title)
                            .setMessage(R.string.root_confirm_bt_off_message)
                            .setPositiveButton(R.string.root_tool_bt_off) { _, _ ->
                                runRootCommand(labels[4], "svc bluetooth disable")
                            }
                            .setNegativeButton(R.string.close, null)
                            .show()
                        5 -> runRootCommand(labels[5], "svc bluetooth enable")
                    }
                }
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

    private fun runRootCommand(title: String, command: String, note: String? = null) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { RootManager.suExec(command) }
            showCommandOutput(title, command, result, note)
        }
    }

    private fun showCommandOutput(title: String, command: String, result: SuResult, note: String?) {
        val body = buildString {
            append(result.output.ifBlank { getString(R.string.cmd_no_output) })
            append("\n\n")
            append(getString(R.string.cmd_exit, result.exitCode))
            if (result.error.isNotBlank()) {
                append("\n")
                append(result.error)
            }
            if (note != null) {
                append("\n\n")
                append(note)
            }
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body.take(4000))
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun showDevOptionsDialog() {
        val dev = DevOptions.developerOptionsEnabled(this)
        val snoop = DevOptions.hciSnoopEnabled(this)
        val devLabel = when (dev) {
            true -> getString(R.string.dev_state_on)
            false -> getString(R.string.dev_state_off)
            null -> getString(R.string.dev_state_unknown)
        }
        val snoopLabel = when (snoop) {
            true -> getString(R.string.dev_state_on)
            false -> getString(R.string.dev_state_off)
            null -> getString(R.string.dev_state_unknown)
        }
        val body = getString(R.string.dev_line_mode, devLabel) + "\n" +
            getString(R.string.dev_line_snoop, snoopLabel) + "\n\n" +
            (if (dev == true) getString(R.string.dev_hint_on) else getString(R.string.dev_hint_off))

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.dev_dialog_title)
            .setMessage(body)
            .setPositiveButton(R.string.dev_open_settings) { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.dev_state_unknown), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.close, null)

        if (RootManager.granted && snoop != true) {
            builder.setNeutralButton(R.string.dev_snoop_on_root) { _, _ ->
                runRootCommand(getString(R.string.dev_snoop_on_root), "settings put global bluetooth_hci_log 1", getString(R.string.root_note_snoop))
            }
        } else if (RootManager.granted && snoop == true) {
            builder.setNeutralButton(R.string.dev_snoop_off_root) { _, _ ->
                runRootCommand(getString(R.string.dev_snoop_off_root), "settings put global bluetooth_hci_log 0", getString(R.string.root_note_snoop))
            }
        }
        builder.show()
    }

    // ---------- Adapter ----------

    private class DeviceAdapter(context: Context, items: List<BluetoothDeviceInfo>) :
        ArrayAdapter<BluetoothDeviceInfo>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_device, parent, false)
            val info = getItem(position) ?: return view

            val nameView = view.findViewById<TextView>(R.id.textDeviceName)
            val metaView = view.findViewById<TextView>(R.id.textDeviceMeta)

            nameView.text = buildString {
                if (info.isSpeaker) append("🔊 ")
                append(info.name ?: context.getString(R.string.unknown))
            }

            val pieces = mutableListOf<String>()
            when (info.source) {
                DeviceSource.PAIRED -> pieces.add(context.getString(R.string.paired))
                DeviceSource.CLASSIC -> pieces.add(context.getString(R.string.classic))
                DeviceSource.BLE -> pieces.add(context.getString(R.string.ble))
            }
            info.deviceTypeLabelRes?.let { pieces.add(context.getString(it)) }
            info.vendor?.let { pieces.add(it) }
            info.serviceUuids?.take(3)?.let { pieces.add(context.getString(R.string.services_short, it.joinToString(","))) }
            if (info.isSpeaker) info.speakerReason?.let { reason ->
                val reasonText = when (reason) {
                    SpeakerClassifier.Reason.DEVICE_CLASS -> context.getString(R.string.speaker_reason_class)
                    SpeakerClassifier.Reason.BLE_APPEARANCE -> context.getString(R.string.speaker_reason_ble)
                    SpeakerClassifier.Reason.NAME ->
                        context.getString(R.string.speaker_reason_name, info.speakerKeyword ?: "")
                }
                pieces.add(context.getString(R.string.speaker_with_reason, reasonText))
            }
            info.rssi?.let { pieces.add(context.getString(R.string.rssi_label, it)) }
            metaView.text = if (pieces.isEmpty()) context.getString(R.string.no_data) else pieces.joinToString(" · ")

            return view
        }

        fun update(items: List<BluetoothDeviceInfo>) {
            clear()
            addAll(items)
            notifyDataSetChanged()
        }
    }
}
