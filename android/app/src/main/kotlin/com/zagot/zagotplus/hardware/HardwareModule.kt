package com.zagot.zagotplus.hardware

import com.zagot.zagotplus.hardware.printer.MockPrinterService
import com.zagot.zagotplus.hardware.printer.PrinterService
import com.zagot.zagotplus.hardware.scales.MockScalesService
import com.zagot.zagotplus.hardware.scales.ScalesService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for hardware services.
 * 
 * Currently binds mock implementations for development.
 * When real hardware implementations are ready, switch bindings:
 * 
 * ```kotlin
 * @Binds
 * @Singleton
 * abstract fun bindScalesService(impl: TcpScalesService): ScalesService
 * 
 * @Binds
 * @Singleton
 * abstract fun bindPrinterService(impl: BluetoothPrinterService): PrinterService
 * ```
 * 
 * Or use a build flavor qualifier to switch between debug/release implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HardwareModule {

    @Binds
    @Singleton
    abstract fun bindScalesService(impl: MockScalesService): ScalesService

    @Binds
    @Singleton
    abstract fun bindPrinterService(impl: MockPrinterService): PrinterService
}
