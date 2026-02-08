package com.zagot.zagotplus.hardware.printer

/**
 * Information about a discovered Bluetooth device.
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String,  // MAC address
    val isPaired: Boolean,
    val isConnected: Boolean = false
)
