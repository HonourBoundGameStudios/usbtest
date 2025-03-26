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
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.nio.ByteBuffer

/*!
    https://docs.unity3d.com/2022.3/Documentation/Manual/android-custom-activity.html
    https://docs.unity3d.com/2022.3/Documentation/Manual/android-plugins-java-code-from-c-sharp.html
 */
class UsbSerialActivity : Activity() {
    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var statusTextView: TextView? = null

    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "Received USB action: $action")

            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    // Updated way to get Parcelable extras
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as UsbDevice?
                    }

                    val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.d(TAG, "Permission result: ${if (permissionGranted) "GRANTED" else "DENIED"} for device: ${device?.deviceName}")

                    if (permissionGranted) {
                        if (device != null) {
                            Log.d(TAG, "Connecting to device: ${device.deviceName}, vendorId: ${device.vendorId}, productId: ${device.productId}")
                            connectToDevice(device)
                        }
                    } else {
                        Log.d(TAG, "Permission denied for device $device")
                        updateStatus("USB Permission denied")
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                // Updated way to get Parcelable extras
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as UsbDevice?
                }

                if (device != null) {
                    Log.d(TAG, "Device attached: ${device.deviceName}, vendorId: ${device.vendorId}, productId: ${device.productId}")
                    requestPermission(device)
                    updateStatus("USB Device attached: ${device.deviceName}")
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                // Updated way to get Parcelable extras
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as UsbDevice?
                }

                if (device != null && device == usbDevice) {
                    Log.d(TAG, "Device detached: ${device.deviceName}")
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
        val deviceList = usbManager?.deviceList

        Log.d(TAG, "Found ${deviceList?.size} USB devices")

        if (deviceList?.isEmpty() == true) {
            updateStatus("No USB devices found")
            return
        }

        // Log all devices to help with debugging
        deviceList?.values?.forEach { device ->
            Log.d(TAG, "Device: ${device.deviceName}, " +
                    "VendorId: ${device.vendorId} (0x${device.vendorId.toString(16)}), " +
                    "ProductId: ${device.productId} (0x${device.productId.toString(16)}), " +
                    "Class: ${device.deviceClass}, " +
                    "Protocol: ${device.deviceProtocol}")
        }

        // Try to find Raspberry Pi Pico specifically (vendor ID: 11914 or 0x2E8A)
        val picoDevice = deviceList?.values?.find { it.vendorId == 11914 }

        if (picoDevice != null) {
            usbDevice = picoDevice
            Log.d(TAG, "Found Raspberry Pi Pico: ${picoDevice.deviceName}")
            updateStatus("Found Raspberry Pi Pico")
            requestPermission(picoDevice)
        } else {
            // If no Pico found, use first device (for testing)
            usbDevice = deviceList?.values?.iterator()?.next()
            Log.d(TAG, "No Pico found, using: ${usbDevice?.deviceName}")
            updateStatus("Found device: ${usbDevice?.deviceName}")
            requestPermission(usbDevice)
        }
    }

    private fun requestPermission(device: UsbDevice?) {
        if (device != null) {
            Log.d(TAG, "Requesting permission for device: ${device.deviceName}, vendorId: ${device.vendorId}, productId: ${device.productId}")

            // Make sure we have the correct flag for Android 12+ (PendingIntent.FLAG_IMMUTABLE)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

            val permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION),
                flags
            )

            // Check if we already have permission
            if (usbManager!!.hasPermission(device)) {
                Log.d(TAG, "Already have permission for device ${device.deviceName}")
                connectToDevice(device)
            } else {
                Log.d(TAG, "Requesting permission via intent for device ${device.deviceName}")
                usbManager!!.requestPermission(device, permissionIntent)
            }
        } else {
            Log.e(TAG, "Cannot request permission for null device")
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