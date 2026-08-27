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
import api.BluetoothDeviceInfo
import api.DeviceSource
import api.ScanNearbyDevices
import api.SpeakerClassifier
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var deviceListAdapter: DeviceAdapter
    private lateinit var btnScan: Button
    private lateinit var switchSpeakersOnly: MaterialSwitch
    private lateinit var txtStatus: TextView
    private val scanner = ScanNearbyDevices.getInstance()
    private var onlySpeakers = false
    private var currentDevices: List<BluetoothDeviceInfo> = emptyList()
    private lateinit var btnAttackSelected: Button
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
        switchSpeakersOnly = findViewById(R.id.switchSpeakersOnly)
        txtStatus = findViewById(R.id.txtStatus)

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
    }

    override fun onPause() {
        super.onPause()
        scanner.stopScanning()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.stopScanning()
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
