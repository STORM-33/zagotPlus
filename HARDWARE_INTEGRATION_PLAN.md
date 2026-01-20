# Zagot+ Hardware Integration Plan

## Executive Summary

This document outlines the integration of scales (Dniprovеs ВПД405Е-Т via USR-W610) and thermal printer (Xprinter XP-58IIH via Bluetooth) into the Zagot+ Android application. Based on code review and protocol research, approximately 80% of the integration can be built before hardware arrives.

**Current State:** The codebase already has stubs for scales integration (`scaleWeight`, `isManualWeightMode` in `PurchaseEntryViewModel`) and a TODO comment for receipt printing.

**Estimated Work:**
- Pre-hardware development: 10-12 hours
- Post-hardware integration: 4-6 hours

---

## Part 1: Network Topology & Protocol Research

### 1.0 Network Architecture (No Router - Kiosk Environment)

**Constraint:** No WiFi router available in kiosk. Solution: USR-W610 operates as Access Point.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         KIOSK NETWORK TOPOLOGY                               │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────┐      WiFi AP       ┌──────────────┐      RS-232      ┌──────────────┐
│   Android    │◄──────────────────►│  USR-W610    │◄────────────────►│   Scales     │
│   Tablet     │   SSID: ZAGOT-W    │  (AP Mode)   │                  │  ВПД405Е-Т   │
│              │   10.10.100.x      │ 10.10.100.254│                  │              │
└──────┬───────┘                    └──────────────┘                  └──────────────┘
       │
       │ Bluetooth SPP
       ▼
┌──────────────┐
│   Printer    │
│  XP-58IIH    │
└──────────────┘
       │
       │ Mobile Data (LTE)
       ▼
┌──────────────┐
│  Supabase    │
│   Cloud      │
└──────────────┘
```

**How It Works:**
1. USR-W610 creates its own WiFi network (AP mode)
2. Android tablet connects to USR-W610 WiFi for scales communication
3. Tablet uses **mobile data (LTE)** for Supabase sync - WiFi and mobile data work simultaneously on Android
4. Printer connects via Bluetooth (independent of WiFi)

**Advantages:**
- Simple, reliable - dedicated WiFi link for scales
- Fixed IP address (10.10.100.254) - no DHCP issues
- Mobile data available for cloud sync
- No router dependency

**Network Configuration Summary:**

| Device | Connection | IP Address | Role |
|--------|------------|------------|------|
| USR-W610 | WiFi AP | 10.10.100.254 | TCP Server (port 8899) |
| Android Tablet | WiFi Client | 10.10.100.x (DHCP) | TCP Client to scales |
| Android Tablet | Mobile Data | Carrier IP | Supabase sync |
| XP-58IIH Printer | Bluetooth SPP | N/A | Print receipts |

---

### 1.1 Scales Protocol (Dniprovеs ВПД405Е-Т)

**Hardware Chain:**
```
Scales (RS-232) → USR-W610 (RS232→WiFi AP) → TCP Socket → Android App
```

**USR-W610 Configuration (AP Mode):**
- Mode: **Access Point (AP)** - creates own WiFi network
- SSID: `ZAGOT-SCALES` (or custom)
- Password: Set secure password
- IP Address: `10.10.100.254` (fixed, default)
- TCP Mode: Server
- TCP Port: `8899`
- Max connections: 24 (only 1 needed)

**Likely Protocol Options (test in order):**

| Protocol | Baud | Parity | Data Format |
|----------|------|--------|-------------|
| Mettler Toledo | 4800 | Even | `S S   12.34 kg\r\n` |
| Dniprovеs Custom | 4800 | Even | Binary BCD (6 bytes weight) |
| CAS | 9600 | Odd | Binary with checksum |

**Mettler Toledo MT-SICS Commands (most likely):**
```
Request stable weight:  S\r\n   → Response: S S    12.34 kg\r\n
Request immediate:      SI\r\n  → Response: S D    12.34 kg\r\n
Tare:                   T\r\n   → Response: T A\r\n (acknowledged)
Zero:                   Z\r\n   → Response: Z A\r\n

Status codes: S=stable, D=dynamic, +/- for overload/underload
```

**Dniprovеs ВТД-РС Protocol (if custom):**
```
Commands (prefix with 0x00 0x00):
0x01 - Tare
0x03 - Request weight

Response: 6 bytes BCD weight (W1W2W3W4W5W6), LSB first
Example: [0x05, 0x04, 0x03, 0x02, 0x01, 0x00] = 12345.0g = 12.345 kg
```

**Action Required:** Contact Dniprovеs (+38 097 691-01-96) for official protocol documentation for ВПД405Е-Т model.

### 1.2 Printer Protocol (Xprinter XP-58IIH)

**Connection:** Bluetooth SPP (Serial Port Profile)

**Protocol:** Epson ESC/POS compatible

**Critical for Ukrainian Text:**
```
1. Initialize:           0x1B 0x40
2. Cancel Chinese mode:  0x1C 0x2E  ← CRITICAL! Without this, Cyrillic shows as Chinese
3. Select CP866:         0x1B 0x74 0x11
```

**Essential ESC/POS Commands:**

| Command | Hex | Description |
|---------|-----|-------------|
| Initialize | `1B 40` | Reset printer |
| Align left | `1B 61 00` | Left alignment |
| Align center | `1B 61 01` | Center alignment |
| Align right | `1B 61 02` | Right alignment |
| Bold on | `1B 45 01` | Enable bold |
| Bold off | `1B 45 00` | Disable bold |
| Double height | `1B 21 10` | 2x height text |
| Normal size | `1B 21 00` | Normal text |
| Feed N lines | `1B 64 N` | Feed paper |
| Full cut | `1D 56 00` | Cut paper |

**Paper Width:** 58mm = 384 dots @ 203 DPI = 32 characters (normal font)

---

## Part 2: Architecture Design

### 2.1 Package Structure

```
android/app/src/main/kotlin/com/zagot/zagotplus/
├── hardware/
│   ├── HardwareModule.kt                 # Hilt DI module
│   ├── scales/
│   │   ├── ScalesService.kt              # Interface
│   │   ├── ScalesState.kt                # Connection states
│   │   ├── WeightReading.kt              # Domain model
│   │   ├── ScalesError.kt                # Error types
│   │   ├── TcpScalesService.kt           # Real TCP implementation
│   │   ├── MockScalesService.kt          # Debug/testing
│   │   ├── ScalesPreferences.kt          # DataStore config
│   │   └── protocol/
│   │       ├── ScalesProtocol.kt         # Protocol interface
│   │       ├── MettlerToledoProtocol.kt  # MT-SICS parser
│   │       ├── DniprovesProtocol.kt      # Custom BCD parser
│   │       └── GenericAsciiProtocol.kt   # Fallback regex
│   └── printer/
│       ├── PrinterService.kt             # Interface
│       ├── PrinterState.kt               # Connection states
│       ├── PrinterError.kt               # Error types
│       ├── BluetoothPrinterService.kt    # Real BT implementation
│       ├── MockPrinterService.kt         # Debug/testing
│       ├── PrinterPreferences.kt         # DataStore config
│       └── escpos/
│           ├── EscPosEncoder.kt          # Command builder
│           ├── EscPosCommands.kt         # Constants
│           └── CharsetHelper.kt          # CP866 encoding
├── data/
│   └── preferences/
│       └── HardwarePreferences.kt        # Combined hardware prefs
└── ui/
    └── screens/
        ├── settings/
        │   ├── HardwareSettingsSection.kt    # Settings UI component
        │   ├── ScalesConfigDialog.kt         # Scales setup dialog
        │   └── PrinterConfigDialog.kt        # Printer setup dialog
        └── debug/
            └── HardwareDebugScreen.kt        # Testing screen
```

### 2.2 Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              SCALES FLOW                                     │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐      ┌──────────────────┐      ┌──────────────────────────┐
│ PurchaseEntry    │      │ PurchaseEntry    │      │     ScalesService        │
│    Screen        │◄────▶│   ViewModel      │◄────▶│     (Interface)          │
│                  │      │                  │      │                          │
│ - WeightDisplay  │      │ - scaleWeight    │      │ - connectionState: Flow  │
│ - ManualToggle   │      │ - isManualMode   │      │ - weightReadings: Flow   │
│ - StableIndicator│      │ - effectiveWeight│      │ - connect()              │
└──────────────────┘      └──────────────────┘      │ - disconnect()           │
                                                    │ - tare()                 │
                                                    └───────────┬──────────────┘
                                                                │
                          ┌─────────────────────────────────────┼──────────────┐
                          │                                     │              │
                   ┌──────▼──────┐                      ┌───────▼────────┐     │
                   │ MockScales  │                      │ TcpScalesImpl  │     │
                   │  Service    │                      │                │     │
                   │ (Debug)     │                      │ Socket Client  │     │
                   └─────────────┘                      │ Protocol Parser│     │
                                                       └───────┬────────┘     │
                                                               │              │
                                                       ┌───────▼────────┐     │
                                                       │   USR-W610     │     │
                                                       │   TCP Server   │     │
                                                       └───────┬────────┘     │
                                                               │              │
                                                       ┌───────▼────────┐     │
                                                       │   ВПД405Е-Т    │     │
                                                       │   (RS-232)     │     │
                                                       └────────────────┘     │
                                                                              │
                                                       ScalesPreferences ◄────┘
                                                       - IP address
                                                       - Port
                                                       - Protocol type
                                                       - Auto-reconnect

┌─────────────────────────────────────────────────────────────────────────────┐
│                              PRINTER FLOW                                    │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐      ┌──────────────────┐      ┌──────────────────────────┐
│ PurchaseSummary  │      │ PurchaseEntry    │      │    PrinterService        │
│    Overlay       │─────▶│   ViewModel      │─────▶│     (Interface)          │
│                  │      │                  │      │                          │
│ [Print Receipt]  │      │ - finalize()     │      │ - connectionState: Flow  │
│                  │      │ - printReceipt() │      │ - scan(): Flow<Devices>  │
└──────────────────┘      └──────────────────┘      │ - connect(address)       │
                                                    │ - print(bytes)           │
                                                    └───────────┬──────────────┘
                                                                │
                   ┌────────────────────────────────────────────┤
                   │                                            │
            ┌──────▼──────┐                             ┌───────▼────────┐
            │ MockPrinter │                             │ BtPrinterImpl  │
            │  Service    │                             │                │
            │ (Logcat)    │                             │ BluetoothSocket│
            └─────────────┘                             │ SPP Profile    │
                                                        └───────┬────────┘
                                                                │
            ┌───────────────────────────────────────────────────┤
            │                                                   │
     ┌──────▼──────┐      ┌─────────────────┐           ┌───────▼────────┐
     │ ReceiptBuilder│───▶│  EscPosEncoder  │──────────▶│  XP-58IIH      │
     │              │     │                 │           │  (Bluetooth)   │
     │ - header()   │     │ - init()        │           └────────────────┘
     │ - item()     │     │ - cyrillic()    │
     │ - total()    │     │ - text(CP866)   │
     │ - footer()   │     │ - cut()         │
     └──────────────┘     └─────────────────┘
```

---

## Part 3: Interface Definitions

### 3.1 ScalesService Interface

```kotlin
// hardware/scales/ScalesService.kt
package com.zagot.zagotplus.hardware.scales

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal
import java.time.Instant

interface ScalesService {
    /** Current connection state */
    val connectionState: StateFlow<ScalesConnectionState>
    
    /** Stream of weight readings (emits continuously when connected) */
    val weightReadings: SharedFlow<WeightReading>
    
    /** Stream of errors */
    val errors: SharedFlow<ScalesError>
    
    /** Attempt to connect to configured scales */
    suspend fun connect()
    
    /** Disconnect from scales */
    suspend fun disconnect()
    
    /** Send tare command */
    suspend fun tare(): Result<Unit>
    
    /** Request single weight reading (for scales that don't auto-send) */
    suspend fun requestWeight(): Result<WeightReading>
    
    /** Check if scales are configured */
    fun isConfigured(): Boolean
}

data class WeightReading(
    val weightKg: BigDecimal,
    val isStable: Boolean,
    val timestamp: Instant,
    val raw: String  // Raw protocol data for debugging
)

sealed class ScalesConnectionState {
    object Disconnected : ScalesConnectionState()
    object Connecting : ScalesConnectionState()
    data class Connected(val deviceInfo: String) : ScalesConnectionState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ScalesConnectionState()
    data class Error(val error: ScalesError) : ScalesConnectionState()
}

sealed class ScalesError {
    object NotConfigured : ScalesError()
    object ConnectionFailed : ScalesError()
    object ConnectionLost : ScalesError()
    object Timeout : ScalesError()
    object Overload : ScalesError()
    object Underload : ScalesError()
    object UnstableReading : ScalesError()
    data class ParseError(val raw: String, val message: String) : ScalesError()
    data class NetworkError(val message: String) : ScalesError()
    data class Unknown(val message: String) : ScalesError()
    
    fun toDisplayMessage(): String = when (this) {
        NotConfigured -> "Ваги не налаштовані"
        ConnectionFailed -> "Не вдалося підключитися"
        ConnectionLost -> "З'єднання втрачено"
        Timeout -> "Час очікування вичерпано"
        Overload -> "Перевантаження ваг"
        Underload -> "Вага нижче нуля"
        UnstableReading -> "Нестабільне зважування"
        is ParseError -> "Помилка даних: $message"
        is NetworkError -> "Мережева помилка: $message"
        is Unknown -> message
    }
}
```

### 3.2 PrinterService Interface

```kotlin
// hardware/printer/PrinterService.kt
package com.zagot.zagotplus.hardware.printer

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface PrinterService {
    /** Current connection state */
    val connectionState: StateFlow<PrinterConnectionState>
    
    /** Available Bluetooth devices during scan */
    val availableDevices: StateFlow<List<BluetoothDeviceInfo>>
    
    /** Stream of errors */
    val errors: SharedFlow<PrinterError>
    
    /** Start scanning for Bluetooth printers */
    suspend fun scan()
    
    /** Stop scanning */
    fun stopScan()
    
    /** Connect to printer by MAC address */
    suspend fun connect(address: String)
    
    /** Disconnect from printer */
    suspend fun disconnect()
    
    /** Send raw bytes to printer */
    suspend fun print(data: ByteArray): Result<Unit>
    
    /** Check if printer is configured */
    fun isConfigured(): Boolean
    
    /** Check if printer is ready to print */
    fun isReady(): Boolean
}

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,  // MAC address
    val isPaired: Boolean,
    val isConnected: Boolean = false
)

sealed class PrinterConnectionState {
    object Disconnected : PrinterConnectionState()
    object Scanning : PrinterConnectionState()
    object Connecting : PrinterConnectionState()
    data class Connected(val device: BluetoothDeviceInfo) : PrinterConnectionState()
    data class Error(val error: PrinterError) : PrinterConnectionState()
}

sealed class PrinterError {
    object BluetoothDisabled : PrinterError()
    object BluetoothNotSupported : PrinterError()
    object PermissionDenied : PrinterError()
    object DeviceNotFound : PrinterError()
    object ConnectionFailed : PrinterError()
    object ConnectionLost : PrinterError()
    object PrintFailed : PrinterError()
    object Timeout : PrinterError()
    data class Unknown(val message: String) : PrinterError()
    
    fun toDisplayMessage(): String = when (this) {
        BluetoothDisabled -> "Bluetooth вимкнено"
        BluetoothNotSupported -> "Bluetooth не підтримується"
        PermissionDenied -> "Немає дозволу на Bluetooth"
        DeviceNotFound -> "Принтер не знайдено"
        ConnectionFailed -> "Не вдалося підключитися"
        ConnectionLost -> "З'єднання втрачено"
        PrintFailed -> "Помилка друку"
        Timeout -> "Час очікування вичерпано"
        is Unknown -> message
    }
}
```

### 3.3 Protocol Parser Interface

```kotlin
// hardware/scales/protocol/ScalesProtocol.kt
package com.zagot.zagotplus.hardware.scales.protocol

import com.zagot.zagotplus.hardware.scales.WeightReading

enum class Parity { NONE, ODD, EVEN }

interface ScalesProtocol {
    val name: String
    val baudRate: Int
    val dataBits: Int
    val stopBits: Int
    val parity: Parity
    
    /**
     * Parse raw bytes into a WeightReading.
     * Returns null if data is incomplete or invalid.
     */
    fun parseReading(data: ByteArray): WeightReading?
    
    /**
     * Build tare command bytes.
     */
    fun buildTareCommand(): ByteArray
    
    /**
     * Build weight request command bytes.
     * Returns null if scales auto-send weight (no request needed).
     */
    fun buildRequestCommand(): ByteArray?
    
    /**
     * Check if buffer contains a complete message.
     * Used for framing in stream parsing.
     */
    fun isCompleteMessage(buffer: ByteArray): Boolean
}
```

---

## Part 4: Implementation Details

### 4.1 ESC/POS Encoder

```kotlin
// hardware/printer/escpos/EscPosEncoder.kt
package com.zagot.zagotplus.hardware.printer.escpos

import java.nio.charset.Charset

class EscPosEncoder {
    private val buffer = mutableListOf<Byte>()
    
    /** Initialize printer */
    fun init(): EscPosEncoder {
        buffer.addAll(byteArrayOf(0x1B, 0x40).toList())  // ESC @
        return this
    }
    
    /** Cancel Chinese character mode (CRITICAL for Cyrillic!) */
    fun cancelChineseMode(): EscPosEncoder {
        buffer.addAll(byteArrayOf(0x1C, 0x2E).toList())  // FS .
        return this
    }
    
    /** Select CP866 codepage for Cyrillic */
    fun selectCyrillic(): EscPosEncoder {
        buffer.addAll(byteArrayOf(0x1B, 0x74, 0x11).toList())  // ESC t 17
        return this
    }
    
    /** Standard initialization for Ukrainian receipts */
    fun initUkrainian(): EscPosEncoder {
        return init().cancelChineseMode().selectCyrillic()
    }
    
    fun alignLeft(): EscPosEncoder {
        buffer.addAll(byteArrayOf(0x1B, 0x61, 0x00).toList())
        return this
    }
    
    fun alignCenter(): EscPosEncoder {
        buffer.addAll(byteArrayOf(0x1B, 0x61, 0x01).toList())
        return this
    }
    
    fun alignRight(): EscPosEncoder {
        buffer.addAll(byteArrayOf(0x1B, 0x61, 0x02).toList())
        return this
    }
    
    fun boldOn(): EscPosEncoder {
        buffer.addAll(byteArrayOf(0x1B, 0x45, 0x01).toList())
        return this
    }
    
    fun boldOff(): EscPosEncoder {
        buffer.addAll(byteArrayOf(0x1B, 0x45, 0x00).toList())
        return this
    }
    
    fun doubleHeight(): EscPosEncoder {
        buffer.addAll(byteArrayOf(0x1B, 0x21, 0x10).toList())
        return this
    }
    
    fun doubleWidth(): EscPosEncoder {
        buffer.addAll(byteArrayOf(0x1B, 0x21, 0x20).toList())
        return this
    }
    
    fun doubleSize(): EscPosEncoder {
        buffer.addAll(byteArrayOf(0x1B, 0x21, 0x30).toList())
        return this
    }
    
    fun normalSize(): EscPosEncoder {
        buffer.addAll(byteArrayOf(0x1B, 0x21, 0x00).toList())
        return this
    }
    
    /** Print text encoded as CP866 */
    fun text(text: String): EscPosEncoder {
        val cp866 = Charset.forName("CP866")
        buffer.addAll(text.toByteArray(cp866).toList())
        return this
    }
    
    fun newLine(): EscPosEncoder {
        buffer.add(0x0A)
        return this
    }
    
    fun feedLines(n: Int): EscPosEncoder {
        buffer.addAll(byteArrayOf(0x1B, 0x64, n.toByte()).toList())
        return this
    }
    
    fun cut(): EscPosEncoder {
        feedLines(3)
        buffer.addAll(byteArrayOf(0x1D, 0x56, 0x00).toList())  // Full cut
        return this
    }
    
    fun partialCut(): EscPosEncoder {
        feedLines(3)
        buffer.addAll(byteArrayOf(0x1D, 0x56, 0x01).toList())
        return this
    }
    
    /** Print separator line */
    fun separator(char: Char = '-', width: Int = 32): EscPosEncoder {
        text(char.toString().repeat(width))
        newLine()
        return this
    }
    
    /** Print two-column row (left text, right text) */
    fun twoColumn(left: String, right: String, width: Int = 32): EscPosEncoder {
        val padding = width - left.length - right.length
        if (padding > 0) {
            text(left + " ".repeat(padding) + right)
        } else {
            text(left.take(width - right.length - 1) + " " + right)
        }
        newLine()
        return this
    }
    
    fun build(): ByteArray = buffer.toByteArray()
    
    fun clear(): EscPosEncoder {
        buffer.clear()
        return this
    }
}
```

### 4.2 Receipt Builder

```kotlin
// hardware/printer/receipt/PurchaseReceiptBuilder.kt
package com.zagot.zagotplus.hardware.printer.receipt

import com.zagot.zagotplus.hardware.printer.escpos.EscPosEncoder
import com.zagot.zagotplus.ui.screens.purchase.PurchasePosition
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PurchaseReceiptBuilder(
    private val businessName: String = "ФОП",
    private val businessAddress: String? = null
) {
    private val items = mutableListOf<ReceiptItem>()
    private var receiptNumber: String = ""
    private var locationName: String = ""
    private var date: LocalDateTime = LocalDateTime.now()
    private var notes: String? = null
    
    data class ReceiptItem(
        val name: String,
        val weightKg: BigDecimal,
        val pricePerKg: BigDecimal
    ) {
        val total: BigDecimal get() = weightKg.multiply(pricePerKg)
            .setScale(2, java.math.RoundingMode.HALF_UP)
    }
    
    fun receiptNumber(number: String) = apply { this.receiptNumber = number }
    fun locationName(name: String) = apply { this.locationName = name }
    fun date(date: LocalDateTime) = apply { this.date = date }
    fun notes(notes: String?) = apply { this.notes = notes }
    
    fun addItem(name: String, weightKg: BigDecimal, pricePerKg: BigDecimal) = apply {
        items.add(ReceiptItem(name, weightKg, pricePerKg))
    }
    
    fun addPositions(positions: List<PurchasePosition>) = apply {
        positions.forEach { pos ->
            addItem(pos.product.name, pos.weightKg, pos.pricePerKg)
        }
    }
    
    fun build(): ByteArray {
        val encoder = EscPosEncoder().initUkrainian()
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        
        // Header
        encoder
            .alignCenter()
            .boldOn()
            .doubleHeight()
            .text(businessName)
            .newLine()
            .normalSize()
            .boldOff()
        
        businessAddress?.let {
            encoder.text(it).newLine()
        }
        
        if (locationName.isNotBlank()) {
            encoder.text(locationName).newLine()
        }
        
        encoder.separator('=')
        
        // Receipt info
        encoder.alignLeft()
        if (receiptNumber.isNotBlank()) {
            encoder.twoColumn("Чек №:", receiptNumber)
        }
        encoder.twoColumn("Дата:", date.format(dateFormatter))
        encoder.separator()
        
        // Items
        items.forEach { item ->
            // Product name (may wrap)
            encoder.boldOn().text(item.name).boldOff().newLine()
            
            // Weight × Price = Total
            val weightStr = "${item.weightKg.stripTrailingZeros().toPlainString()} кг"
            val priceStr = "× ${item.pricePerKg.toPlainString()}"
            val totalStr = "= ${item.total.toPlainString()}"
            
            encoder.text("  $weightStr $priceStr $totalStr").newLine()
        }
        
        encoder.separator()
        
        // Totals
        val totalWeight = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.weightKg) }
        val totalAmount = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.total) }
        
        encoder.twoColumn("Вага:", "${totalWeight.stripTrailingZeros().toPlainString()} кг")
        encoder.boldOn()
        encoder.twoColumn("РАЗОМ:", "${totalAmount.toPlainString()} грн")
        encoder.boldOff()
        
        // Notes
        notes?.takeIf { it.isNotBlank() }?.let {
            encoder.separator()
            encoder.text("Примітка: $it").newLine()
        }
        
        // Footer
        encoder
            .separator('=')
            .alignCenter()
            .text("Дякуємо за співпрацю!")
            .newLine()
            .cut()
        
        return encoder.build()
    }
}
```

### 4.3 Mettler Toledo Protocol Parser

```kotlin
// hardware/scales/protocol/MettlerToledoProtocol.kt
package com.zagot.zagotplus.hardware.scales.protocol

import com.zagot.zagotplus.hardware.scales.WeightReading
import java.math.BigDecimal
import java.time.Instant

/**
 * Parser for Mettler Toledo MT-SICS protocol.
 * Common format: "S S    12.34 kg\r\n" (stable) or "S D    12.34 kg\r\n" (dynamic)
 */
class MettlerToledoProtocol : ScalesProtocol {
    override val name = "Mettler Toledo (MT-SICS)"
    override val baudRate = 4800
    override val dataBits = 8
    override val stopBits = 1
    override val parity = Parity.EVEN
    
    // Pattern: S [S|D] [+|-]?[space]*[digits].[digits] [kg|g]
    private val weightPattern = Regex("""S\s+([SD])\s+([+-]?\s*\d+\.?\d*)\s*(kg|g)""", RegexOption.IGNORE_CASE)
    
    override fun parseReading(data: ByteArray): WeightReading? {
        val text = data.toString(Charsets.US_ASCII).trim()
        
        // Check for error responses
        if (text.startsWith("ES") || text.startsWith("EL")) {
            return null  // Syntax error or logical error
        }
        
        val match = weightPattern.find(text) ?: return null
        
        val (status, valueStr, unit) = match.destructured
        val cleanValue = valueStr.replace("\\s".toRegex(), "")
        
        val value = cleanValue.toBigDecimalOrNull() ?: return null
        
        val weightKg = when (unit.lowercase()) {
            "kg" -> value
            "g" -> value.divide(BigDecimal(1000))
            else -> return null
        }
        
        return WeightReading(
            weightKg = weightKg.setScale(3, java.math.RoundingMode.HALF_UP),
            isStable = status.uppercase() == "S",
            timestamp = Instant.now(),
            raw = text
        )
    }
    
    override fun buildTareCommand(): ByteArray = "T\r\n".toByteArray(Charsets.US_ASCII)
    
    override fun buildRequestCommand(): ByteArray = "S\r\n".toByteArray(Charsets.US_ASCII)
    
    override fun isCompleteMessage(buffer: ByteArray): Boolean {
        val text = buffer.toString(Charsets.US_ASCII)
        return text.endsWith("\r\n") || text.endsWith("\n")
    }
}
```

### 4.4 Mock Services for Development

```kotlin
// hardware/scales/MockScalesService.kt
package com.zagot.zagotplus.hardware.scales

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Mock scales service for development and testing.
 * Simulates weight readings with configurable behavior.
 */
@Singleton
class MockScalesService @Inject constructor() : ScalesService {
    
    private val _connectionState = MutableStateFlow<ScalesConnectionState>(
        ScalesConnectionState.Disconnected
    )
    override val connectionState: StateFlow<ScalesConnectionState> = _connectionState.asStateFlow()
    
    private val _weightReadings = MutableSharedFlow<WeightReading>(replay = 1)
    override val weightReadings: SharedFlow<WeightReading> = _weightReadings.asSharedFlow()
    
    private val _errors = MutableSharedFlow<ScalesError>()
    override val errors: SharedFlow<ScalesError> = _errors.asSharedFlow()
    
    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Simulation parameters
    var simulatedWeight: BigDecimal = BigDecimal("10.00")
    var simulateInstability: Boolean = false
    var simulateDisconnect: Boolean = false
    
    override suspend fun connect() {
        _connectionState.value = ScalesConnectionState.Connecting
        delay(500) // Simulate connection delay
        
        if (simulateDisconnect) {
            _connectionState.value = ScalesConnectionState.Error(ScalesError.ConnectionFailed)
            return
        }
        
        _connectionState.value = ScalesConnectionState.Connected("Mock Scales (Debug)")
        startSimulation()
    }
    
    override suspend fun disconnect() {
        simulationJob?.cancel()
        simulationJob = null
        _connectionState.value = ScalesConnectionState.Disconnected
    }
    
    override suspend fun tare(): Result<Unit> {
        simulatedWeight = BigDecimal.ZERO
        return Result.success(Unit)
    }
    
    override suspend fun requestWeight(): Result<WeightReading> {
        val reading = WeightReading(
            weightKg = simulatedWeight,
            isStable = !simulateInstability,
            timestamp = Instant.now(),
            raw = "MOCK: ${simulatedWeight}kg"
        )
        return Result.success(reading)
    }
    
    override fun isConfigured(): Boolean = true
    
    private fun startSimulation() {
        simulationJob = scope.launch {
            while (isActive) {
                // Add small random variation
                val variation = if (simulateInstability) {
                    BigDecimal(Random.nextDouble(-0.5, 0.5))
                } else {
                    BigDecimal(Random.nextDouble(-0.01, 0.01))
                }
                
                val weight = simulatedWeight.add(variation)
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                    .coerceAtLeast(BigDecimal.ZERO)
                
                val reading = WeightReading(
                    weightKg = weight,
                    isStable = !simulateInstability && Random.nextFloat() > 0.1,
                    timestamp = Instant.now(),
                    raw = "MOCK: ${weight}kg"
                )
                
                _weightReadings.emit(reading)
                delay(200) // 5 readings per second
            }
        }
    }
    
    /** For testing: set exact weight */
    suspend fun setWeight(kg: BigDecimal, stable: Boolean = true) {
        simulatedWeight = kg
        simulateInstability = !stable
        _weightReadings.emit(
            WeightReading(
                weightKg = kg,
                isStable = stable,
                timestamp = Instant.now(),
                raw = "MOCK_SET: ${kg}kg"
            )
        )
    }
}
```

---

## Part 5: Integration Points

### 5.1 PurchaseEntryViewModel Modifications

**Current state (lines 59-111 in PurchaseEntryViewModel.kt):**
```kotlin
// Phase 6 stub: Scale weight from Bluetooth/TCP connection
// Currently always null (scale not implemented yet)
val scaleWeight: BigDecimal? = null,
val isManualWeightMode: Boolean = false,
```

**Required changes:**

```kotlin
@HiltViewModel
class PurchaseEntryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val purchaseBatchRepository: PurchaseBatchRepository,
    private val devicePreferences: DevicePreferences,
    private val productOrderPreferences: ProductOrderPreferences,
    private val scalesService: ScalesService,      // NEW: Inject scales
    private val printerService: PrinterService,    // NEW: Inject printer
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    init {
        loadProductsAndBatch()
        observeScales()  // NEW
    }
    
    // NEW: Observe scales weight readings
    private fun observeScales() {
        viewModelScope.launch {
            scalesService.connectionState.collect { state ->
                _uiState.update { it.copy(
                    isScaleConnected = state is ScalesConnectionState.Connected
                )}
            }
        }
        
        viewModelScope.launch {
            scalesService.weightReadings.collect { reading ->
                if (!_uiState.value.isManualWeightMode) {
                    _uiState.update { it.copy(
                        scaleWeight = reading.weightKg,
                        isScaleStable = reading.isStable
                    )}
                }
            }
        }
    }
    
    // MODIFY finalize() - add receipt printing after line 366
    fun finalize() {
        // ... existing save logic ...
        
        // NEW: Print receipt
        viewModelScope.launch {
            if (printerService.isReady()) {
                val receipt = PurchaseReceiptBuilder()
                    .receiptNumber(batchLocalId.take(8).uppercase())
                    .locationName(/* get from location */)
                    .date(LocalDateTime.now())
                    .addPositions(state.positions)
                    .notes(state.notes.ifBlank { null })
                    .build()
                
                printerService.print(receipt).onFailure { e ->
                    // Log error but don't fail the purchase
                    Log.e("Purchase", "Receipt print failed", e)
                }
            }
        }
        
        // ... existing summary transition ...
    }
}
```

### 5.2 PurchaseEntryUiState Additions

```kotlin
data class PurchaseEntryUiState(
    // ... existing fields ...
    
    // Scales state
    val scaleWeight: BigDecimal? = null,
    val isManualWeightMode: Boolean = false,
    val isScaleStable: Boolean = false,      // NEW
    val scalesConnectionState: ScalesConnectionState = ScalesConnectionState.Disconnected,  // NEW
    
    // Printer state
    val isPrinterConnected: Boolean = false,  // NEW
    val isPrinting: Boolean = false,          // NEW
) {
    val isScaleConnected: Boolean
        get() = scalesConnectionState is ScalesConnectionState.Connected
    
    // ... existing computed properties ...
}
```

### 5.3 SettingsScreen Additions

Add new section after "Безпека" section (around line 384):

```kotlin
HorizontalDivider()

// Hardware section - NEW
SettingsSection(title = "Обладнання", icon = Icons.Filled.Scale) {
    // Scales configuration
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showScalesConfig = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ваги",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = when (uiState.scalesConnectionState) {
                    is ScalesConnectionState.Connected -> "Підключено"
                    is ScalesConnectionState.Connecting -> "Підключення..."
                    is ScalesConnectionState.Error -> "Помилка"
                    else -> uiState.scalesConfig?.let { "${it.ipAddress}:${it.port}" } ?: "Не налаштовано"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Налаштувати ваги"
        )
    }
    
    // Printer configuration
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPrinterConfig = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Принтер",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = when (uiState.printerConnectionState) {
                    is PrinterConnectionState.Connected -> 
                        (uiState.printerConnectionState as PrinterConnectionState.Connected).device.name
                    is PrinterConnectionState.Connecting -> "Підключення..."
                    is PrinterConnectionState.Scanning -> "Пошук..."
                    else -> uiState.printerConfig?.deviceName ?: "Не налаштовано"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Налаштувати принтер"
        )
    }
}
```

### 5.4 DevicePreferences Extensions

Add to `DevicePreferences.kt`:

```kotlin
companion object {
    // ... existing constants ...
    
    // Hardware settings keys
    private const val KEY_SCALES_IP = "scales_ip"
    private const val KEY_SCALES_PORT = "scales_port"
    private const val KEY_SCALES_PROTOCOL = "scales_protocol"
    private const val KEY_SCALES_AUTO_CONNECT = "scales_auto_connect"
    private const val KEY_SCALES_WIFI_SSID = "scales_wifi_ssid"
    
    private const val KEY_PRINTER_ADDRESS = "printer_address"
    private const val KEY_PRINTER_NAME = "printer_name"
    private const val KEY_PRINTER_AUTO_CONNECT = "printer_auto_connect"
    
    // Default values for AP mode (no router)
    const val DEFAULT_SCALES_IP = "10.10.100.254"
    const val DEFAULT_SCALES_PORT = 8899
    const val DEFAULT_SCALES_WIFI_SSID = "ZAGOT-SCALES"
}

// Scales configuration
fun getScalesConfig(): ScalesConfig? {
    val ip = prefs.getString(KEY_SCALES_IP, null) ?: return null
    val port = prefs.getInt(KEY_SCALES_PORT, DEFAULT_SCALES_PORT)
    val protocol = prefs.getString(KEY_SCALES_PROTOCOL, "auto") ?: "auto"
    val autoConnect = prefs.getBoolean(KEY_SCALES_AUTO_CONNECT, true)
    val wifiSsid = prefs.getString(KEY_SCALES_WIFI_SSID, DEFAULT_SCALES_WIFI_SSID)
    return ScalesConfig(ip, port, protocol, autoConnect, wifiSsid)
}

fun setScalesConfig(config: ScalesConfig) {
    prefs.edit()
        .putString(KEY_SCALES_IP, config.ipAddress)
        .putInt(KEY_SCALES_PORT, config.port)
        .putString(KEY_SCALES_PROTOCOL, config.protocol)
        .putBoolean(KEY_SCALES_AUTO_CONNECT, config.autoConnect)
        .putString(KEY_SCALES_WIFI_SSID, config.wifiSsid)
        .apply()
}

/** Initialize with default AP mode settings */
fun initializeDefaultScalesConfig() {
    if (prefs.getString(KEY_SCALES_IP, null) == null) {
        setScalesConfig(ScalesConfig(
            ipAddress = DEFAULT_SCALES_IP,
            port = DEFAULT_SCALES_PORT,
            protocol = "auto",
            autoConnect = true,
            wifiSsid = DEFAULT_SCALES_WIFI_SSID
        ))
    }
}

// Printer configuration
fun getPrinterConfig(): PrinterConfig? {
    val address = prefs.getString(KEY_PRINTER_ADDRESS, null) ?: return null
    val name = prefs.getString(KEY_PRINTER_NAME, "Printer")
    val autoConnect = prefs.getBoolean(KEY_PRINTER_AUTO_CONNECT, true)
    return PrinterConfig(address, name ?: "Printer", autoConnect)
}

fun setPrinterConfig(config: PrinterConfig) {
    prefs.edit()
        .putString(KEY_PRINTER_ADDRESS, config.address)
        .putString(KEY_PRINTER_NAME, config.name)
        .putBoolean(KEY_PRINTER_AUTO_CONNECT, config.autoConnect)
        .apply()
}

data class ScalesConfig(
    val ipAddress: String = DEFAULT_SCALES_IP,
    val port: Int = DEFAULT_SCALES_PORT,
    val protocol: String = "auto",  // "auto", "mettler", "dniprovesy"
    val autoConnect: Boolean = true,
    val wifiSsid: String? = DEFAULT_SCALES_WIFI_SSID  // For user reference
)

data class PrinterConfig(
    val address: String,  // Bluetooth MAC
    val name: String,
    val autoConnect: Boolean = true
)
```

---

## Part 6: Android Manifest & Permissions

Add to `AndroidManifest.xml`:

```xml
<!-- Bluetooth permissions (for printer) -->
<uses-permission android:name="android.permission.BLUETOOTH" 
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" 
    android:maxSdkVersion="30" />

<!-- Android 12+ Bluetooth permissions -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" 
    android:usesPermissionFlags="neverForLocation" />

<!-- Location (required for Bluetooth scanning on older Android) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Network (for scales via TCP) -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

<!-- Optional: Bluetooth feature -->
<uses-feature android:name="android.hardware.bluetooth" android:required="false" />
<uses-feature android:name="android.hardware.wifi" android:required="false" />
```

### WiFi Connection Helper (Optional Enhancement)

Since the tablet must connect to USR-W610's WiFi (`ZAGOT-SCALES`), you can optionally add a helper to check/prompt connection:

```kotlin
// hardware/wifi/ScalesWifiHelper.kt
class ScalesWifiHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val devicePreferences: DevicePreferences
) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    /** Check if connected to scales WiFi network */
    fun isConnectedToScalesWifi(): Boolean {
        val config = devicePreferences.getScalesConfig() ?: return false
        val expectedSsid = config.wifiSsid ?: return false
        
        val wifiInfo = wifiManager.connectionInfo
        val currentSsid = wifiInfo.ssid?.removeSurrounding("\"")
        
        return currentSsid == expectedSsid
    }
    
    /** Get current WiFi SSID */
    fun getCurrentWifiSsid(): String? {
        val wifiInfo = wifiManager.connectionInfo
        return wifiInfo.ssid?.removeSurrounding("\"")
    }
    
    /** Check if mobile data is available for sync */
    fun hasMobileDataConnectivity(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}
```

This can show warnings like:
- "Підключіться до WiFi 'ZAGOT-SCALES' для роботи з вагами"
- "Увімкніть мобільні дані для синхронізації"

---

## Part 7: Dependencies

No new dependencies required. The project already has:
- Ktor client for networking (can use plain sockets)
- Coroutines for async operations
- Hilt for DI
- DataStore preferences

For Bluetooth, use Android's built-in `BluetoothAdapter` and `BluetoothSocket`.

---

## Part 8: Implementation Timeline

### Phase A: Pre-Hardware (Can Start Now)

| Task | Time | Priority |
|------|------|----------|
| Create `hardware/` package structure | 30 min | 1 |
| Implement `ScalesService` interface | 30 min | 1 |
| Implement `PrinterService` interface | 30 min | 1 |
| Implement `EscPosEncoder` | 1 hr | 1 |
| Implement `PurchaseReceiptBuilder` | 1 hr | 1 |
| Implement `MettlerToledoProtocol` | 1 hr | 1 |
| Implement `MockScalesService` | 1 hr | 2 |
| Implement `MockPrinterService` | 30 min | 2 |
| Add `HardwareModule` (Hilt DI) | 30 min | 1 |
| Add preferences to `DevicePreferences` | 30 min | 1 |
| Add Settings UI for hardware | 2 hr | 2 |
| Modify `PurchaseEntryViewModel` | 2 hr | 1 |
| Create `HardwareDebugScreen` | 1 hr | 3 |

**Total: ~12 hours**

### Phase B: Post-Hardware (When Equipment Arrives)

| Task | Time | Priority |
|------|------|----------|
| Configure USR-W610 | 1 hr | 1 |
| Sniff actual protocol with terminal | 1 hr | 1 |
| Implement `TcpScalesService` | 2 hr | 1 |
| Adjust protocol parser if needed | 1 hr | 1 |
| Implement `BluetoothPrinterService` | 2 hr | 1 |
| Test Bluetooth pairing | 30 min | 1 |
| Test Ukrainian charset encoding | 30 min | 1 |
| End-to-end testing | 2 hr | 1 |

**Total: ~10 hours**

---

## Part 9: Testing Checklist

### Pre-Hardware Testing (Mock Mode)

- [ ] Mock scales service emits weight readings
- [ ] Weight display updates in PurchaseEntryScreen
- [ ] Manual mode toggle works
- [ ] Stable/unstable indicator shows correctly
- [ ] Receipt builder generates correct ESC/POS bytes
- [ ] Ukrainian text encodes correctly to CP866
- [ ] Settings UI shows hardware configuration
- [ ] Preferences persist across app restarts
- [ ] Default scales config uses `10.10.100.254:8899`

### Post-Hardware Testing (USR-W610 AP Mode)

**WiFi Setup:**
- [ ] USR-W610 broadcasts `ZAGOT-SCALES` SSID
- [ ] Tablet connects to `ZAGOT-SCALES` WiFi
- [ ] Tablet gets IP in `10.10.100.x` range
- [ ] Mobile data still works while on scales WiFi
- [ ] Supabase sync works over mobile data

**Scales Communication:**
- [ ] App connects to `10.10.100.254:8899`
- [ ] Weight readings appear in app
- [ ] Weight readings parse correctly (kg value)
- [ ] Stable/unstable status detected
- [ ] Tare command works
- [ ] Auto-reconnect works after WiFi reconnect
- [ ] Error shown when WiFi disconnected

**Printer (Bluetooth):**
- [ ] Bluetooth printer discovered in scan
- [ ] Bluetooth pairing succeeds
- [ ] Test receipt prints correctly
- [ ] Ukrainian characters display correctly (not Chinese!)
- [ ] Receipt format matches requirements
- [ ] Print works even without scales WiFi

**End-to-End Flow:**
- [ ] Select product → weight from scales → confirm → print receipt
- [ ] Manual weight override works when needed
- [ ] Purchase saves to local DB
- [ ] Purchase syncs to Supabase via mobile data
- [ ] Multiple purchases in sequence work correctly

---

## Part 10: USR-W610 Setup Guide (AP Mode - No Router)

Since there's no WiFi router in the kiosk, USR-W610 operates as its own Access Point.

### Initial Setup (One-Time)

1. **Power on USR-W610** and connect scales via RS-232 cable

2. **Connect your phone/laptop to USR-W610 default WiFi:**
   - SSID: `USR-W610-xxxx` (check device label)
   - No password (default)

3. **Open browser**: `http://10.10.100.254`

4. **Login**: admin / admin (default)

5. **Configure WiFi (AP Mode):**
   - Mode: **AP** (Access Point)
   - SSID: `ZAGOT-SCALES` (or your preferred name)
   - Security: WPA2-PSK
   - Password: Set strong password (write it down!)
   - Channel: Auto or fixed (1, 6, or 11 recommended)

6. **Configure Serial Port:**
   - Baud Rate: **4800** (or 9600 - test both)
   - Data Bits: **8**
   - Parity: **Even**
   - Stop Bits: **1**
   - Flow Control: None

7. **Configure Network (Socket A):**
   - Work Mode: **TCP Server**
   - Local Port: **8899**

8. **Save Configuration** and reboot device

9. **Reconnect to new SSID** (`ZAGOT-SCALES`) with your new password

10. **Test connection:**
    - Install TCP terminal app on phone (e.g., "TCP Client" from Play Store)
    - Connect to `10.10.100.254:8899`
    - You should see weight data appearing

### Android Tablet Configuration

1. **Connect to scales WiFi:**
   - Settings → WiFi → `ZAGOT-SCALES`
   - Enter password

2. **Keep mobile data enabled:**
   - Settings → Mobile Data → ON
   - This allows Supabase sync while connected to scales WiFi

3. **In Zagot+ app:**
   - Settings → Обладнання → Ваги
   - IP: `10.10.100.254`
   - Port: `8899`
   - Test connection

### Network Behavior Notes

- **Android handles dual connectivity:** WiFi for local (scales) + Mobile data for internet (sync)
- **If mobile data unavailable:** App works offline, syncs when data returns
- **WiFi priority:** Android may try to use WiFi for internet - if issues occur, can set "Use mobile data when WiFi has no internet" in WiFi settings

---

## Part 11: Troubleshooting Guide

### Scales Issues

| Symptom | Possible Cause | Solution |
|---------|----------------|----------|
| Can't find WiFi | W610 not powered | Check power LED |
| Can't find WiFi | Wrong SSID | Look for `USR-W610-xxxx` or `ZAGOT-SCALES` |
| Can't connect WiFi | Wrong password | Reset W610 to defaults (hold button 5s) |
| No TCP connection | Wrong IP | Always use `10.10.100.254` in AP mode |
| No TCP connection | Wrong port | Verify port 8899 in W610 config |
| No TCP connection | Firewall | Unlikely in AP mode, but check tablet settings |
| Connection drops | WiFi interference | Change W610 channel (1, 6, or 11) |
| Connection drops | Distance | Move tablet closer to W610 |
| Garbled data | Wrong baud rate | Try 4800 vs 9600 in W610 serial config |
| Garbled data | Wrong parity | Try Even vs None |
| No weight data | Wrong protocol | Contact Dniprovеs for docs |
| Unstable readings | Scale issue | Check scale placement, recalibrate |

### Network/Sync Issues

| Symptom | Possible Cause | Solution |
|---------|----------------|----------|
| No Supabase sync | Mobile data off | Enable mobile data |
| No Supabase sync | No signal | Move to area with coverage |
| Slow sync | WiFi priority | Android settings: "Use mobile data when WiFi has no internet" |
| Sync works only when disconnecting scales | Network routing | Same as above |

### Printer Issues

| Symptom | Possible Cause | Solution |
|---------|----------------|----------|
| Not found | BT disabled | Enable Bluetooth |
| Not found | Permissions | Grant location permission |
| Won't pair | Wrong mode | Put printer in pairing mode |
| Chinese chars | Chinese mode | Send `0x1C 0x2E` first |
| Wrong chars | Wrong codepage | Send `0x1B 0x74 0x11` |
| Paper jam | Mechanical | Clear paper path |
| Light print | Low battery/paper | Replace consumables |

### USR-W610 Factory Reset

If configuration gets messed up:
1. Power on the device
2. Hold reset button for **5+ seconds**
3. Wait for reboot
4. Connect to `USR-W610-xxxx` (no password)
5. Browse to `http://10.10.100.254`
6. Reconfigure from scratch

---

## Appendix A: Receipt Format Sample

```
================================
         ФОП ПЕТРЕНКО           
      вул. Центральна, 5        
         Точка №1               
================================
Чек №:              ABC12345   
Дата:          18.01.2026 14:30
--------------------------------
Горіх волоський
  5.2 кг × 85.00 = 442.00

Насіння соняшника
  12.0 кг × 42.00 = 504.00

Арахіс смажений
  3.5 кг × 65.00 = 227.50
--------------------------------
Вага:                  20.7 кг
РАЗОМ:              1173.50 грн
================================
    Дякуємо за співпрацю!      
================================
[CUT]
```

---

## Appendix B: File Checklist

Files to create:

```
□ hardware/HardwareModule.kt
□ hardware/scales/ScalesService.kt
□ hardware/scales/ScalesState.kt  
□ hardware/scales/WeightReading.kt
□ hardware/scales/ScalesError.kt
□ hardware/scales/TcpScalesService.kt
□ hardware/scales/MockScalesService.kt
□ hardware/scales/protocol/ScalesProtocol.kt
□ hardware/scales/protocol/MettlerToledoProtocol.kt
□ hardware/scales/protocol/DniprovesProtocol.kt
□ hardware/printer/PrinterService.kt
□ hardware/printer/PrinterState.kt
□ hardware/printer/PrinterError.kt
□ hardware/printer/BluetoothPrinterService.kt
□ hardware/printer/MockPrinterService.kt
□ hardware/printer/escpos/EscPosEncoder.kt
□ hardware/printer/escpos/EscPosCommands.kt
□ hardware/printer/receipt/PurchaseReceiptBuilder.kt
□ ui/screens/settings/HardwareSettingsSection.kt
□ ui/screens/settings/ScalesConfigDialog.kt
□ ui/screens/settings/PrinterConfigDialog.kt
□ ui/screens/debug/HardwareDebugScreen.kt
```

Files to modify:

```
□ AndroidManifest.xml (permissions)
□ data/preferences/DevicePreferences.kt (hardware prefs)
□ ui/screens/purchase/PurchaseEntryViewModel.kt (scales + printer)
□ ui/screens/purchase/PurchaseEntryScreen.kt (weight display)
□ ui/screens/settings/SettingsScreen.kt (hardware section)
□ ui/screens/settings/SettingsViewModel.kt (hardware state)
□ ui/navigation/NavGraph.kt (debug screen route)
```

---

*Document Version: 1.1*
*Created: 2026-01-18*
*Last Updated: 2026-01-18*
*Change: Updated for kiosk environment - USR-W610 AP mode (no router)*
