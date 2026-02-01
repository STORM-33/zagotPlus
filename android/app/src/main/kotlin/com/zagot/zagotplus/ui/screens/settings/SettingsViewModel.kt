package com.zagot.zagotplus.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.BuildConfig
import com.zagot.zagotplus.data.local.DatabaseExporter
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.preferences.AuthPreferences
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.hardware.printer.BluetoothDeviceInfo
import com.zagot.zagotplus.hardware.printer.PrinterConfig
import com.zagot.zagotplus.hardware.printer.PrinterConnectionState
import com.zagot.zagotplus.hardware.printer.PrinterService
import com.zagot.zagotplus.hardware.printer.escpos.EscPosEncoder
import com.zagot.zagotplus.hardware.scales.ScalesConfig
import com.zagot.zagotplus.hardware.scales.ScalesConnectionState
import com.zagot.zagotplus.hardware.scales.ScalesService
import com.zagot.zagotplus.sync.SyncManager
import com.zagot.zagotplus.sync.SyncStatus
import com.zagot.zagotplus.sync.SyncStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SettingsUiState(
    val syncStatus: SyncStatus = SyncStatus.idle(),
    val pendingCount: Int = 0,
    val deviceId: String = "",
    val locations: List<Location> = emptyList(),
    val selectedLocationId: UUID? = null,
    val appVersion: String = "",
    val copySuccess: Boolean = false,
    val isRestrictedMode: Boolean = false,
    val isExporting: Boolean = false,
    val exportSuccess: Boolean = false,
    val exportError: String? = null,
    // Hardware state
    val scalesConnectionState: ScalesConnectionState = ScalesConnectionState.Disconnected,
    val scalesConfig: ScalesConfig? = null,
    val printerConnectionState: PrinterConnectionState = PrinterConnectionState.Disconnected,
    val printerConfig: PrinterConfig? = null,
    val availablePrinters: List<BluetoothDeviceInfo> = emptyList(),
    val isTestingScales: Boolean = false,
    val isTestingPrinter: Boolean = false,
    val hardwareTestResult: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncStatusRepository: SyncStatusRepository,
    private val syncManager: SyncManager,
    private val devicePreferences: DevicePreferences,
    private val authPreferences: AuthPreferences,
    private val locationRepository: LocationRepository,
    private val transactionDao: TransactionDao,
    private val databaseExporter: DatabaseExporter,
    private val scalesService: ScalesService,
    private val printerService: PrinterService
) : ViewModel() {

    private val _copySuccess = MutableStateFlow(false)
    private val _selectedLocationId = MutableStateFlow(devicePreferences.getSelectedLocationId())
    private val _isRestrictedMode = MutableStateFlow(authPreferences.isRestrictedMode())
    private val _isExporting = MutableStateFlow(false)
    private val _exportSuccess = MutableStateFlow(false)
    private val _exportError = MutableStateFlow<String?>(null)
    private val _isTestingScales = MutableStateFlow(false)
    private val _isTestingPrinter = MutableStateFlow(false)
    private val _hardwareTestResult = MutableStateFlow<String?>(null)

    private data class LocalState(
        val selectedLocationId: UUID?,
        val isRestrictedMode: Boolean,
        val copySuccess: Boolean,
        val isExporting: Boolean,
        val exportSuccess: Boolean,
        val exportError: String?,
        val isTestingScales: Boolean,
        val isTestingPrinter: Boolean,
        val hardwareTestResult: String?
    )

    private val localState = combine(
        combine(_selectedLocationId, _isRestrictedMode, _copySuccess) { a, b, c -> Triple(a, b, c) },
        combine(_isExporting, _exportSuccess, _exportError) { a, b, c -> Triple(a, b, c) },
        combine(_isTestingScales, _isTestingPrinter, _hardwareTestResult) { a, b, c -> Triple(a, b, c) }
    ) { (selectedLocationId, isRestrictedMode, copySuccess), (isExporting, exportSuccess, exportError), (isTestingScales, isTestingPrinter, hardwareTestResult) ->
        LocalState(
            selectedLocationId = selectedLocationId,
            isRestrictedMode = isRestrictedMode,
            copySuccess = copySuccess,
            isExporting = isExporting,
            exportSuccess = exportSuccess,
            exportError = exportError,
            isTestingScales = isTestingScales,
            isTestingPrinter = isTestingPrinter,
            hardwareTestResult = hardwareTestResult
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        syncStatusRepository.syncStatus,
        transactionDao.getUnsyncedCountFlow(),
        locationRepository.getAllLocations(),
        localState,
        scalesService.connectionState,
        printerService.connectionState,
        printerService.availableDevices
    ) { values ->
        val syncStatus = values[0] as SyncStatus
        val pendingCount = values[1] as Int
        @Suppress("UNCHECKED_CAST")
        val locations = values[2] as List<Location>
        val local = values[3] as LocalState
        val scalesState = values[4] as ScalesConnectionState
        val printerState = values[5] as PrinterConnectionState
        @Suppress("UNCHECKED_CAST")
        val availablePrinters = values[6] as List<BluetoothDeviceInfo>

        SettingsUiState(
            syncStatus = syncStatus,
            pendingCount = pendingCount,
            deviceId = devicePreferences.getDeviceId(),
            locations = locations,
            selectedLocationId = local.selectedLocationId,
            appVersion = BuildConfig.VERSION_NAME,
            copySuccess = local.copySuccess,
            isRestrictedMode = local.isRestrictedMode,
            isExporting = local.isExporting,
            exportSuccess = local.exportSuccess,
            exportError = local.exportError,
            scalesConnectionState = scalesState,
            scalesConfig = devicePreferences.getScalesConfig(),
            printerConnectionState = printerState,
            printerConfig = devicePreferences.getPrinterConfig(),
            availablePrinters = availablePrinters,
            isTestingScales = local.isTestingScales,
            isTestingPrinter = local.isTestingPrinter,
            hardwareTestResult = local.hardwareTestResult
        )
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(
            deviceId = devicePreferences.getDeviceId(),
            selectedLocationId = devicePreferences.getSelectedLocationId(),
            appVersion = BuildConfig.VERSION_NAME,
            isRestrictedMode = authPreferences.isRestrictedMode(),
            scalesConfig = devicePreferences.getScalesConfig(),
            printerConfig = devicePreferences.getPrinterConfig()
        )
    )

    fun triggerSync() {
        syncManager.triggerManualSync()
    }

    fun selectLocation(locationId: UUID) {
        devicePreferences.setSelectedLocationId(locationId)
        _selectedLocationId.value = locationId
    }

    fun setRestrictedMode(restricted: Boolean) {
        authPreferences.setRestrictedMode(restricted)
        _isRestrictedMode.value = restricted
    }

    fun copyDeviceId() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Device ID", devicePreferences.getDeviceId())
        clipboard.setPrimaryClip(clip)
        _copySuccess.update { true }
    }

    fun dismissCopySuccess() {
        _copySuccess.update { false }
    }

    fun exportDatabase() {
        if (_isExporting.value) return
        
        viewModelScope.launch {
            _isExporting.value = true
            _exportError.value = null
            _exportSuccess.value = false
            
            try {
                val file = databaseExporter.exportToFile()
                val uri = databaseExporter.getShareUri(file)
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                context.startActivity(Intent.createChooser(shareIntent, "Експорт бази даних").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                
                _exportSuccess.value = true
            } catch (e: Exception) {
                _exportError.value = e.message ?: "Помилка експорту"
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun dismissExportSuccess() {
        _exportSuccess.value = false
    }

    fun dismissExportError() {
        _exportError.value = null
    }

    // ==================== Hardware Methods ====================

    /**
     * Save scales configuration.
     */
    fun saveScalesConfig(config: ScalesConfig) {
        devicePreferences.setScalesConfig(config)
    }

    /**
     * Test scales connection.
     */
    fun testScalesConnection() {
        if (_isTestingScales.value) return

        viewModelScope.launch {
            _isTestingScales.value = true
            _hardwareTestResult.value = null

            try {
                scalesService.connect()
                kotlinx.coroutines.delay(2000) // Wait for connection

                val state = scalesService.connectionState.value
                _hardwareTestResult.value = when (state) {
                    is ScalesConnectionState.Connected -> "Підключено до ваг"
                    is ScalesConnectionState.Connecting -> "Підключення..."
                    is ScalesConnectionState.Error -> state.error.toDisplayMessage()
                    else -> "Не вдалося підключитися"
                }
            } catch (e: Exception) {
                _hardwareTestResult.value = "Помилка: ${e.message}"
            } finally {
                _isTestingScales.value = false
            }
        }
    }

    /**
     * Disconnect scales.
     */
    fun disconnectScales() {
        viewModelScope.launch {
            scalesService.disconnect()
        }
    }

    /**
     * Save printer configuration.
     */
    fun savePrinterConfig(config: PrinterConfig) {
        devicePreferences.setPrinterConfig(config)
    }

    /**
     * Start scanning for Bluetooth printers.
     */
    fun scanForPrinters() {
        viewModelScope.launch {
            printerService.scan()
        }
    }

    /**
     * Stop scanning for printers.
     */
    fun stopPrinterScan() {
        printerService.stopScan()
    }

    /**
     * Connect to a printer by address.
     */
    fun connectPrinter(address: String) {
        viewModelScope.launch {
            printerService.connect(address)
        }
    }

    /**
     * Disconnect printer.
     */
    fun disconnectPrinter() {
        viewModelScope.launch {
            printerService.disconnect()
        }
    }

    /**
     * Test print a sample receipt.
     */
    fun testPrint() {
        if (_isTestingPrinter.value) return

        viewModelScope.launch {
            _isTestingPrinter.value = true
            _hardwareTestResult.value = null

            try {
                // Create test receipt
                val testReceipt = EscPosEncoder()
                    .initUkrainian()
                    .alignCenter()
                    .boldOn()
                    .doubleSize()
                    .text("ЗАГОТ+")
                    .newLine()
                    .normalSize()
                    .boldOff()
                    .text("Тестовий друк")
                    .newLine()
                    .separator('=')
                    .alignLeft()
                    .text("Якщо ви бачите цей текст,")
                    .newLine()
                    .text("принтер працює правильно.")
                    .newLine()
                    .text("Українська: ЇЄІҐ їєіґ")
                    .newLine()
                    .separator('=')
                    .alignCenter()
                    .text("Дякуємо!")
                    .feedLines(3)
                    .cut()
                    .toByteArray()

                val result = printerService.print(testReceipt)
                _hardwareTestResult.value = if (result.isSuccess) {
                    "Друк успішний"
                } else {
                    "Помилка друку: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _hardwareTestResult.value = "Помилка: ${e.message}"
            } finally {
                _isTestingPrinter.value = false
            }
        }
    }

    /**
     * Dismiss hardware test result.
     */
    fun dismissHardwareTestResult() {
        _hardwareTestResult.value = null
    }
}
