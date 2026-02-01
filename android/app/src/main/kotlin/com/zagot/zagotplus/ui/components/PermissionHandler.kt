package com.zagot.zagotplus.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Bluetooth permissions required for printer functionality.
 */
object BluetoothPermissions {

    /**
     * Get required Bluetooth permissions based on Android version.
     */
    fun getRequired(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ requires new Bluetooth permissions
        listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        // Android 11 and below requires location for Bluetooth scanning
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    /**
     * Check if all required Bluetooth permissions are granted.
     */
    fun areGranted(context: Context): Boolean {
        return getRequired().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get list of missing permissions.
     */
    fun getMissing(context: Context): List<String> {
        return getRequired().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
}

/**
 * Composable that handles Bluetooth permission requests.
 *
 * Usage:
 * ```
 * BluetoothPermissionHandler(
 *     onPermissionsGranted = { /* proceed with Bluetooth operation */ },
 *     onPermissionsDenied = { /* show error or alternative */ }
 * ) { requestPermissions ->
 *     Button(onClick = { requestPermissions() }) {
 *         Text("Scan for printers")
 *     }
 * }
 * ```
 */
@Composable
fun BluetoothPermissionHandler(
    onPermissionsGranted: () -> Unit,
    onPermissionsDenied: () -> Unit,
    content: @Composable (requestPermissions: () -> Unit) -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            onPermissionsDenied()
        }
    }

    val requestPermissions = {
        permissionLauncher.launch(BluetoothPermissions.getRequired().toTypedArray())
    }

    content(requestPermissions)
}

/**
 * State holder for permission status.
 */
data class PermissionState(
    val isGranted: Boolean,
    val shouldShowRationale: Boolean
)

/**
 * Remember Bluetooth permission state.
 */
@Composable
fun rememberBluetoothPermissionState(context: Context): PermissionState {
    var state by remember {
        mutableStateOf(
            PermissionState(
                isGranted = BluetoothPermissions.areGranted(context),
                shouldShowRationale = false
            )
        )
    }

    // Re-check on composition
    LaunchedEffect(Unit) {
        state = state.copy(isGranted = BluetoothPermissions.areGranted(context))
    }

    return state
}
