package com.zagot.zagotplus.hardware

import android.content.Context
import com.zagot.zagotplus.BuildConfig
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.hardware.printer.BluetoothPrinterService
import com.zagot.zagotplus.hardware.printer.MockPrinterService
import com.zagot.zagotplus.hardware.printer.PrinterService
import com.zagot.zagotplus.hardware.scales.MockScalesService
import com.zagot.zagotplus.hardware.scales.ScalesService
import com.zagot.zagotplus.hardware.scales.TcpScalesService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hilt DI module for hardware services.
 *
 * In DEBUG builds: Uses mock implementations for development without hardware
 * In RELEASE builds: Uses real hardware implementations
 *
 * Real implementations:
 * - TcpScalesService: Connects to scales via USR-W610 WiFi converter (10.10.100.254:8899)
 * - BluetoothPrinterService: Connects to thermal printer via Bluetooth SPP
 *
 * Mock implementations (debug only):
 * - MockScalesService: Simulates weight readings with configurable behavior
 * - MockPrinterService: Logs print data to Logcat
 */
@Module
@InstallIn(SingletonComponent::class)
object HardwareModule {

    @Provides
    @Singleton
    fun provideScalesService(
        devicePreferences: Provider<DevicePreferences>
    ): ScalesService {
        return if (BuildConfig.DEBUG) {
            MockScalesService()
        } else {
            TcpScalesService(devicePreferences.get())
        }
    }

    @Provides
    @Singleton
    fun providePrinterService(
        @ApplicationContext context: Context,
        devicePreferences: Provider<DevicePreferences>
    ): PrinterService {
        return if (BuildConfig.DEBUG) {
            MockPrinterService()
        } else {
            BluetoothPrinterService(context, devicePreferences.get())
        }
    }
}
