package com.example.usbtest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/*!
    https://docs.unity3d.com/2022.3/Documentation/Manual/android-custom-activity.html
    https://docs.unity3d.com/2022.3/Documentation/Manual/android-plugins-java-code-from-c-sharp.html
 */
class UsbSerialActivity : Activity() {
    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var statusTextView: TextView? = null
    private var textSent: TextView? = null
    private var textReceived: TextView? = null
    private var outEndpoint: UsbEndpoint? = null
    private var inEndpoint: UsbEndpoint? = null
    private var controlEndpoint: UsbEndpoint? = null
    private var usbInterface: UsbInterface? = null

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

        statusTextView = findViewById(R.id.status_text)
        textSent = findViewById(R.id.text_sent)
        textReceived = findViewById(R.id.text_received)
        val connectButton = findViewById<Button>(R.id.connect_button)
        val sendButton = findViewById<Button>(R.id.send_button)


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
//                sendData("Hello from Android!\n")
                sendData("r!\n")
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

    private fun findInterfaceAndEndpoints(device: UsbDevice) {
        // For debugging, log all interfaces and endpoints
        for (i in 0 until device.interfaceCount) {
            val usbIface = device.getInterface(i)
            Log.d(TAG, "Interface $i: class=${usbIface.interfaceClass}, " +
                    "subclass=${usbIface.interfaceSubclass}, protocol=${usbIface.interfaceProtocol}")

            for (j in 0 until usbIface.endpointCount) {
                val endpoint = usbIface.getEndpoint(j)
                val direction = if (endpoint.direction == UsbConstants.USB_DIR_OUT) "OUT" else "IN"
                val type = when (endpoint.type) {
                    UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "CONTROL"
                    UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
                    UsbConstants.USB_ENDPOINT_XFER_INT -> "INTERRUPT"
                    UsbConstants.USB_ENDPOINT_XFER_ISOC -> "ISOCHRONOUS"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "  Endpoint $j: address=0x${endpoint.address.toString(16)}, " +
                        "direction=$direction, type=$type")
            }
        }

        // First, try to find a CDC interface (Communication Device Class)
        // CDC typically has class=2 for the control interface and class=10 for data
        var cdcControlInterface: UsbInterface? = null
        var cdcDataInterface: UsbInterface? = null

        for (i in 0 until device.interfaceCount) {
            val usbIface = device.getInterface(i)

            // CDC Control Interface
            if (usbIface.interfaceClass == UsbConstants.USB_CLASS_COMM) {
                cdcControlInterface = usbIface
            }
            // CDC Data Interface
            else if (usbIface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
                cdcDataInterface = usbIface
            }
        }

        // If we found a CDC data interface, use it
        if (cdcDataInterface != null) {
            Log.d(TAG, "Found CDC Data Interface")
            usbInterface = cdcDataInterface

            // Find IN and OUT endpoints
            for (i in 0 until cdcDataInterface.endpointCount) {
                val endpoint = cdcDataInterface.getEndpoint(i)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                        outEndpoint = endpoint
                        Log.d(TAG, "Found bulk OUT endpoint: ${endpoint.address}")
                    } else if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                        inEndpoint = endpoint
                        Log.d(TAG, "Found bulk IN endpoint: ${endpoint.address}")
                    }
                }
            }

            // If we have a control interface, find its control endpoint
            if (cdcControlInterface != null) {
                for (i in 0 until cdcControlInterface.endpointCount) {
                    val endpoint = cdcControlInterface.getEndpoint(i)
                    if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                        controlEndpoint = endpoint
                        Log.d(TAG, "Found interrupt control endpoint: ${endpoint.address}")
                        break
                    }
                }
            }
        }
        // If no CDC interface, try to find any interface with bulk endpoints
        else {
            Log.d(TAG, "No CDC interface found, looking for bulk endpoints")
            for (i in 0 until device.interfaceCount) {
                val usbIface = device.getInterface(i)
                var foundOut = false
                var foundIn = false

                for (j in 0 until usbIface.endpointCount) {
                    val endpoint = usbIface.getEndpoint(j)

                    if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                            outEndpoint = endpoint
                            foundOut = true
                            Log.d(TAG, "Found bulk OUT endpoint: ${endpoint.address}")
                        } else if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                            inEndpoint = endpoint
                            foundIn = true
                            Log.d(TAG, "Found bulk IN endpoint: ${endpoint.address}")
                        }
                    }
                }

                if (foundOut && foundIn) {
                    usbInterface = usbIface
                    Log.d(TAG, "Using interface ${usbIface.id} for communication")
                    break
                }
            }
        }

        if (usbInterface == null) {
            Log.e(TAG, "No suitable interface with bulk endpoints found")
        }
    }

    private fun setupCdcDevice() {
        // This is only needed for CDC ACM devices
        // If it's not a CDC device, this won't hurt but also won't help
        try {
            // Set line coding - 9600 baud, 8 data bits, no parity, 1 stop bit
            val lineCoding = byteArrayOf(
                0x00, 0x26, 0x00, 0x00,  // 9600 baud rate (little-endian)
                0x00,                    // 1 stop bit
                0x00,                    // No parity
                0x08                     // 8 data bits
            )

            // CDC SetLineCoding request
            connection?.controlTransfer(
                0x21,  // REQUEST_TYPE_CLASS | RECIPIENT_INTERFACE | DIRECTION_OUT
                0x20,  // SET_LINE_CODING
                0,     // Value
                0,     // Index (interface)
                lineCoding,
                lineCoding.size,
                1000   // Timeout
            )

            // Set control line state (DTR and RTS)
            connection?.controlTransfer(
                0x21,  // REQUEST_TYPE_CLASS | RECIPIENT_INTERFACE | DIRECTION_OUT
                0x22,  // SET_CONTROL_LINE_STATE
                0x03,  // Value (DTR ON, RTS ON)
                0,     // Index (interface)
                null,
                0,
                1000   // Timeout
            )

            Log.d(TAG, "CDC device setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up CDC device", e)
        }
    }

    private fun connectToDevice(device: UsbDevice?) {
        if (device != null) {
            // Open a connection to the device
            connection = usbManager!!.openDevice(device)

            if (connection != null) {
                Log.d(TAG, "USB Connection established")

                // Find and store the interface and endpoints
                findInterfaceAndEndpoints(device)

                if (usbInterface != null) {
                    // Claim the interface
                    val claimed = connection!!.claimInterface(usbInterface, true)

                    if (claimed) {
                        Log.d(TAG, "Interface claimed successfully")

                        // For CDC devices, we might need to perform control transfer setup
                        setupCdcDevice()

                        usbDevice = device
                        updateStatus("Connected to ${device.deviceName}")
                    } else {
                        Log.e(TAG, "Failed to claim interface")
                        updateStatus("Failed to claim interface")
                        closeConnection()
                    }
                } else {
                    Log.e(TAG, "No suitable interface found")
                    updateStatus("No suitable interface found")
                    closeConnection()
                }
            } else {
                Log.e(TAG, "Failed to open connection")
                updateStatus("Failed to open connection")
            }
        }
    }

    private fun sendData(data: String) {
        if (connection == null || usbDevice == null || outEndpoint == null) {
            Log.e(TAG, "Cannot send data - connection not established properly")
            updateStatus("Cannot send data - not connected")
            return
        }

        try {
            val bytes = data.toByteArray()
            textSent?.text = data  // Update UI

            Log.d(TAG, "Sending ${bytes.size} bytes to endpoint 0x${outEndpoint?.address?.toString(16)}")

            // Use the actual OUT endpoint we found during connection
            val result = connection!!.bulkTransfer(
                outEndpoint,
                bytes,
                bytes.size,
                1000  // Timeout in ms
            )

            if (result >= 0) {
                Log.d(TAG, "Successfully sent $result bytes")
                updateStatus("Sent $result bytes")

                // After sending, we might want to read the response
                readResponse()
            } else {
                Log.e(TAG, "Send failed with result $result")
                updateStatus("Send failed with code $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data", e)
            updateStatus("Error: ${e.message}")
        }
    }

    private fun readResponse() {
        if (connection == null || usbDevice == null || inEndpoint == null) {
            Log.e(TAG, "Cannot read data - connection not established properly")
            return
        }

        try {
            val buffer = ByteArray(64)  // Buffer size

            Log.d(TAG, "Reading from endpoint 0x${inEndpoint?.address?.toString(16)}")

            val bytesRead = connection!!.bulkTransfer(
                inEndpoint,
                buffer,
                buffer.size,
                1000  // Timeout in ms
            )

            if (bytesRead > 0) {
                val response = String(buffer, 0, bytesRead)
                Log.d(TAG, "Received response: $response")
                updateStatus("Received: $response")
                textReceived?.text = response  // Assuming you have a TextView for received data
            } else if (bytesRead == 0) {
                Log.d(TAG, "No data received")
            } else {
                Log.e(TAG, "Read failed with result $bytesRead")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading data", e)
        }
    }

    private fun closeConnection() {
        try {
            // Release interface if it was claimed
            if (connection != null && usbInterface != null) {
                connection!!.releaseInterface(usbInterface)
            }

            // Close connection
            if (connection != null) {
                connection!!.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing connection", e)
        } finally {
            connection = null
            usbDevice = null
            usbInterface = null
            outEndpoint = null
            inEndpoint = null
            controlEndpoint = null
            updateStatus("Disconnected")
        }
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