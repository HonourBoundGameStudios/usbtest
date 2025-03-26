package com.example.usbtest;
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.nio.ByteBuffer

class UsbSerialActivity : Activity() {
    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var statusTextView: TextView? = null

    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            connectToDevice(device)
                        }
                    } else {
                        Log.d(
                            TAG,
                            "Permission denied for device $device"
                        )
                        updateStatus("USB Permission denied")
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                requestPermission(device)
                updateStatus("USB Device attached")
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null && device == usbDevice) {
                    closeConnection()
                }
                updateStatus("USB Device detached")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usb_serial)

        statusTextView = findViewById(R.id.status_text);
        val connectButton = findViewById<Button>(R.id.connect_button);
        val sendButton = findViewById<Button>(R.id.send_button);


        // Get USB manager
        usbManager = getSystemService(USB_SERVICE) as UsbManager


        // Register BroadcastReceiver for USB events
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbReceiver, filter)


        // Connect button click listener
        connectButton.setOnClickListener { v: View? -> findDevice() }


        // Send data button click listener
        sendButton.setOnClickListener { v: View? ->
            if (connection != null) {
                sendData("Hello from Android!\n")
            } else {
                Toast.makeText(
                    this,
                    "Not connected to USB device",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeConnection()
        unregisterReceiver(usbReceiver)
    }

    private fun findDevice() {
        // Get list of connected USB devices
        val deviceList = usbManager!!.deviceList
        if (deviceList.isEmpty()) {
            updateStatus("No USB devices found")
            return
        }


        // For simplicity, use the first device found
        // In a real app, you might want to filter by vendor/product ID
        usbDevice = deviceList.values.iterator().next()
        updateStatus("Found device: " + usbDevice!!.deviceName)


        // Request permission for the device
        requestPermission(usbDevice)
    }

    private fun requestPermission(device: UsbDevice?) {
        if (device != null) {
            val permissionIntent = PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
            )
            usbManager!!.requestPermission(device, permissionIntent)
        }
    }

    private fun connectToDevice(device: UsbDevice?) {
        if (device != null) {
            // Open a connection to the device
            connection = usbManager!!.openDevice(device)
            if (connection != null) {
                // At this point, you would typically use a USB serial library
                // to handle specific communication protocols (CDC, FTDI, etc.)

                // For demonstration purposes, we'll just show direct endpoint communication

                val interfaceCount = device.interfaceCount
                if (interfaceCount > 0) {
                    connection!!.claimInterface(device.getInterface(0), true)
                    updateStatus("Connected to " + device.deviceName)
                } else {
                    updateStatus("Device has no interfaces")
                    closeConnection()
                }
            } else {
                updateStatus("Failed to open connection")
            }
        }
    }

    private fun sendData(data: String) {
        if (connection != null && usbDevice != null) {
            try {
                // Find an output endpoint (for simplicity, we'll use the first output endpoint found)
                var endpointAddress = -1
                for (i in 0 until usbDevice!!.interfaceCount) {
                    for (j in 0 until usbDevice!!.getInterface(i).endpointCount) {
                        if (usbDevice!!.getInterface(i)
                                .getEndpoint(j).direction == 0
                        ) { // OUT direction
                            endpointAddress = usbDevice!!.getInterface(i).getEndpoint(j).address
                            break
                        }
                    }
                    if (endpointAddress != -1) break
                }

                if (endpointAddress != -1) {
                    val bytes = data.toByteArray()
                    val result = connection!!.bulkTransfer(
                        usbDevice!!.getInterface(0).getEndpoint(0),  // Use first endpoint
                        bytes,
                        bytes.size,
                        1000
                    ) // Timeout in ms

                    if (result >= 0) {
                        updateStatus("Sent $result bytes")
                    } else {
                        updateStatus("Send failed")
                    }
                } else {
                    updateStatus("No output endpoint found")
                }
            } catch (e: Exception) {
                updateStatus("Error: " + e.message)
                Log.e(TAG, "Error sending data", e)
            }
        }
    }

    private fun readData(): ByteBuffer? {
        if (connection != null && usbDevice != null) {
            // Find an input endpoint
            var endpointAddress = -1
            for (i in 0 until usbDevice!!.interfaceCount) {
                for (j in 0 until usbDevice!!.getInterface(i).endpointCount) {
                    if (usbDevice!!.getInterface(i).getEndpoint(j).direction == 1) { // IN direction
                        endpointAddress = usbDevice!!.getInterface(i).getEndpoint(j).address
                        break
                    }
                }
                if (endpointAddress != -1) break
            }

            if (endpointAddress != -1) {
                val buffer = ByteBuffer.allocate(1024) // Buffer size
                val bytesRead = connection!!.bulkTransfer(
                    usbDevice!!.getInterface(0)
                        .getEndpoint(1),  // Assuming the second endpoint is for reading
                    buffer.array(),
                    buffer.capacity(),
                    1000
                ) // Timeout in ms

                if (bytesRead > 0) {
                    buffer.position(bytesRead)
                    buffer.flip()
                    return buffer
                }
            }

            return null
        }
        return null
    }

    private fun closeConnection() {
        if (connection != null) {
            connection!!.close()
            connection = null
        }
        usbDevice = null
        updateStatus("Disconnected")
    }

    private fun updateStatus(status: String) {
        runOnUiThread {
            statusTextView!!.text = status
            Log.d(TAG, status)
        }
    }

    companion object {
        private const val TAG = "UsbSerialActivity"
        private const val ACTION_USB_PERMISSION = "com.example.usbtest.USB_PERMISSION"
    }
}