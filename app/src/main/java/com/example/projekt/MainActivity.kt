package com.example.projekt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.projekt.UIMIDI
import com.example.projekt.ui.theme.MIDI_pianoTheme
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val scanQrCodeLauncher = registerForActivityResult(ScanQRCode(), ::showData)
    private var textQr by mutableStateOf("")
    private val REQUEST_ENABLE_BT = 1
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var macAddress: String? = null
    private var mmDevice: BluetoothDevice? = null
    private var mmSocket: BluetoothSocket? = null
    private var mmOutputStream: OutputStream? = null
    private var mmInputStream: InputStream? = null
    private var workerThread: Thread? = null
    private lateinit var readBuffer: ByteArray
    private var readBufferPosition = 0
    private var stopWorker = false
    private var readData: String? = null
    private var connected = false
    private val keyList = mutableListOf<Int>()
    private var appUI = UIMIDI({pairBluetooth()}, {qrPair()})
    private val scanQRCodeLauncher = registerForActivityResult(ScanQRCode(), ::handleQRCodeResult)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MIDI_pianoTheme {
                appUI.MainScreen()
            }
        }

        val bluetoothManager: BluetoothManager =
            getSystemService(BluetoothManager::class.java)
                ?: throw IllegalStateException("BluetoothManager is null")

        bluetoothAdapter = bluetoothManager.adapter
    }

    private fun qrPair() {
        scanQRCode()

    }

    private fun scanQRCode() {
        scanQRCodeLauncher.launch(null)
        pairDevice(macAddress)
    }

    private fun handleQRCodeResult(result: QRResult) {
        when (result) {
            is QRResult.QRSuccess -> {
                val macAddress = result.content.rawValue
                Log.d("MainActivity", "QR Code scanned: $macAddress")
                if (macAddress != null) {
                    pairDevice(macAddress)
                }
            }
            is QRResult.QRUserCanceled -> {
                Toast.makeText(this, "QR scan canceled", Toast.LENGTH_LONG).show()
            }
            is QRResult.QRMissingPermission -> {
                Toast.makeText(this, "QR scan requires camera permission", Toast.LENGTH_LONG).show()
            }
            is QRResult.QRError -> {
                Toast.makeText(this, "QR scan error: ${result.exception.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(this, "Unknown QR scan result", Toast.LENGTH_LONG).show()
            }
        }
    }
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            when(action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = device?.name
                    val deviceHardwareAddress = device?.address // MAC address
                    Log.v("DiscoveredDevice", "Name: ${device?.name}, Address: ${device?.address}")
                }
            }
        }
    }
    private fun pairDevice(macAddress: String?) {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            return
        }

        val device: BluetoothDevice? = bluetoothAdapter.getRemoteDevice(macAddress)
        if (device != null) {
            try {
                device.createBond()
                Toast.makeText(this, "Pairing with $macAddress", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error pairing with device", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Device not found", Toast.LENGTH_LONG).show()
        }
    }
    @Throws(IOException::class)
    private fun connectDevice() {
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb") // Standard SerialPortService ID
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request BLUETOOTH_CONNECT permission if not granted
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
                return
            }

            mmSocket = mmDevice?.createRfcommSocketToServiceRecord(uuid)
            if (mmSocket != null) {
                mmSocket?.connect()
                mmOutputStream = mmSocket?.outputStream
                mmInputStream = mmSocket?.inputStream
                Log.v("MIDI PIANO", "Bluetooth opened")
                readBluetooth()
            } else {
                Log.e("MIDI PIANO", "Failed to connect to Bluetooth device")
                Toast.makeText(this, "Failed to connect to Bluetooth device", Toast.LENGTH_LONG).show()
            }

        } catch (e: IOException) {
            // Handle connection error
            Log.e("MIDI PIANO", "Failed to connect to Bluetooth device: ${e.message}")
            Toast.makeText(this, "Failed to connect to Bluetooth device: ${e.message}", Toast.LENGTH_LONG).show()
            // Add additional error handling as needed
        }
    }

    @Throws(IOException::class)
    fun readBluetooth() {
        val handler = Handler()
        val delimiter: Byte = 10
        stopWorker = false
        readBufferPosition = 0
        readBuffer = ByteArray(1024)
        workerThread = Thread {
            while (!Thread.currentThread().isInterrupted && !stopWorker && isConnected()) {
                try {
                    val bytesAvailable = mmInputStream!!.available()
                    if (bytesAvailable > 0) {
                        val packetBytes = ByteArray(bytesAvailable)
                        mmInputStream!!.read(packetBytes)
                        for (i in 0 until bytesAvailable) {
                            val b = packetBytes[i]
                            if (b == delimiter) {
                                val encodedBytes = ByteArray(readBufferPosition)
                                System.arraycopy(
                                    readBuffer, 0,
                                    encodedBytes, 0,
                                    encodedBytes.size
                                )
                                val data = String(
                                    encodedBytes, charset("US-ASCII")
                                )
                                readBufferPosition = 0
                                handler.post{
                                    Log.v(
                                        "InputStream",
                                        data
                                    )
                                    modifyKeyList(data, keyList)
                                    appUI.setList(keyList)
//                                    try {
//                                        // Attempt to convert the trimmed string to an integer
//                                        number = data.trim().toInt()
//                                        appComposable.setNum(number)
//
//                                    } catch (e: NumberFormatException) {
//                                        // Handle the case where the data is not a valid integer
//                                        Log.e("InputStream", "Invalid number format: $data")
//                                    }
                                }
                            } else {
                                readBuffer[readBufferPosition++] = b
                            }
                        }
                    }
                } catch (ex: IOException) {
                    stopWorker = true
                }
            }
            isConnected()
        }
        workerThread!!.start()
    }
    private var previousConnectionStatus: Boolean? = null

    private fun isConnected(): Boolean {
        val isConnected = mmSocket?.isConnected == true
        if (previousConnectionStatus == null || previousConnectionStatus != isConnected) {
            // Connection status has changed or it's the first check
            previousConnectionStatus = isConnected
            // Handle the connection status change here
            if (isConnected) {
                // Connection established
                connected = true
                appUI.connected = true
                Log.v(
                    "MIDI PIANO",
                    "true"
                )
            } else {
                // Connection lost
                connected = false
                appUI.connected = false
                Log.v(
                    "MIDI PIANO",
                    "false"
                )
            }
        }
        return isConnected
    }
    private fun findDevice(bluetoothAdapter: BluetoothAdapter, name: String): BluetoothDevice? {
        val pairedDevices: Set<BluetoothDevice> = if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.v(
                "Bluetooth",
                "Permission denied"
            )
            return null
        }else{
            bluetoothAdapter.bondedDevices
        }
        if (pairedDevices.isNotEmpty()) {
            for (device in pairedDevices) {
                if (device.name == name)
                {
                    return device
                }
            }
        }else{
            Log.e("error laczenia", "XD")
        }
        return null
    }
    private fun pairBluetooth(){
        checkBluetooth()
        findDevice(bluetoothAdapter, "HC-06")
        connectDevice()
    }
    //    private fun pairBluetooth(){
//
//        checkBluetooth()
//        if (ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.BLUETOOTH_SCAN
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            Log.e(
//                "MIDI Piano",
//                "nie udalo sie"
//            )
//        }else{
//            bluetoothAdapter.startDiscovery()
//        }
//        val filter = IntentFilter()
//        filter.addAction(BluetoothDevice.ACTION_FOUND)
//        registerReceiver(receiver, filter)
//    }
    private fun checkBluetooth() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val requestEnableBt = 1
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                requestEnableBt
            )
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                2
            )
        } else {
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                resultLauncher.launch(enableBtIntent)
            }
        }
    }
    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()

    }
    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.v(
                    "Bluetooth",
                    "Enabled"
                )
            } else {
                Log.v(
                    "Bluetooth",
                    "Declined/disabled"
                )
            }
        }
    private fun modifyKeyList(data: String, keyList: MutableList<Int>): MutableList<Int> {
        val regex = Regex("""\b\d+\b""")
        val matchResult = regex.find(data)
        val key = matchResult?.value?.trim()?.toIntOrNull()
        if (key != null) {
            if (data.startsWith("on")) {
                if (!keyList.contains(key)) {
                    keyList.add(key)
                }
            } else if (data.startsWith("off")) {
                keyList.remove(key)
            }
        }
        return keyList
    }

    @Composable
    fun ButtonQr(){
        Column{
            Button(onClick = {
//                scanQrCodeLauncher.launch(null)
                pairBluetooth()
            }){
                Text("Pair device")
            }
        }
    }

    @Composable
    fun DisplayTextQr(text: String) {
        var currentText by remember { mutableStateOf("") }
        currentText = textQr
        Text(text = currentText, color = Color.Black)
    }

    private fun showData(result: QRResult) {
        textQr = when (result) {
            is QRResult.QRSuccess -> result.content.rawValue.toString()
            QRResult.QRUserCanceled -> "User canceled"
            QRResult.QRMissingPermission -> "Missing permission"
            is QRResult.QRError -> "${result.exception.javaClass.simpleName}: ${result.exception.localizedMessage}"
        }
        Log.v(
            "app",
            textQr
        )
    }
}
