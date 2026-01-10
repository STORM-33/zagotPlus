# Module: Hardware Integration

Updated: 2026-01-10

## Overview

Zagot+ integrates with two hardware devices:
1. **Scales**: Dniprovesy VPD405E-T via USR-W610 (TCP/IP)
2. **Printer**: Xprinter XP-58IIH (Bluetooth, ESC/POS)

## Scales Integration

### Hardware Setup

**Scales**: Dniprovesy VPD405E-T (60kg capacity, RS-232 output)
**Bridge**: USR-W610 (RS-232 to WiFi converter)

**Connection**:
1. Scales RS-232 → USR-W610 RS-232 input
2. USR-W610 creates WiFi network or joins existing WiFi
3. Android tablet connects to same WiFi
4. App connects to USR-W610 via TCP socket

### USR-W610 Configuration

- **Mode**: TCP Server
- **Port**: 8234 (default) or custom
- **Baud rate**: 9600 (match scales)
- **Data bits**: 8
- **Stop bits**: 1
- **Parity**: None
- **IP**: Static or DHCP (find via USR-W610 config tool)

### Protocol

Scales sends weight data continuously over RS-232, USR-W610 forwards as TCP stream.

**Format**:
```
ST,GS,+  12.34kg\r\n
```

**Fields**:
- `ST` - Header (always "ST")
- `GS` - Status: `GS` = stable, `US` = unstable
- `+/-` - Sign
- `12.34` - Weight value
- `kg` - Unit (always kg)
- `\r\n` - Line terminator

**Examples**:
```
ST,GS,+  12.34kg\r\n   // Stable, 12.34 kg
ST,US,+   0.05kg\r\n   // Unstable, 0.05 kg
ST,GS,-   2.10kg\r\n   // Stable, negative (tare?)
```

### Parsing

```kotlin
// ScalesProtocol.kt
data class ScalesReading(
    val weight: BigDecimal,
    val isStable: Boolean,
    val timestamp: Instant = Instant.now()
)

object ScalesProtocol {
    private val PATTERN = Regex("""ST,(\w+),([+-])\s*(\d+\.?\d*)\s*kg""")

    fun parse(line: String): ScalesReading? {
        val match = PATTERN.matchEntire(line.trim()) ?: return null
        val (status, sign, value) = match.destructured

        return ScalesReading(
            weight = BigDecimal(value).let { if (sign == "-") it.negate() else it },
            isStable = status == "GS"
        )
    }
}
```

### Connection Management

```kotlin
// ScalesManager.kt
@Singleton
class ScalesManager @Inject constructor() {
    private var socket: Socket? = null
    private val _readings = MutableSharedFlow<ScalesReading>()
    val readings: SharedFlow<ScalesReading> = _readings.asSharedFlow()

    suspend fun connect(host: String, port: Int = 8234) {
        withContext(Dispatchers.IO) {
            socket = Socket(host, port).apply {
                soTimeout = 5000 // 5 second read timeout
            }
            startReading()
        }
    }

    private suspend fun startReading() {
        withContext(Dispatchers.IO) {
            val reader = socket?.getInputStream()?.bufferedReader() ?: return@withContext

            while (socket?.isConnected == true) {
                try {
                    val line = reader.readLine() ?: break
                    ScalesProtocol.parse(line)?.let { reading ->
                        _readings.emit(reading)
                    }
                } catch (e: IOException) {
                    Log.e("Scales", "Read error: ${e.message}")
                    break
                }
            }
        }
    }

    fun disconnect() {
        socket?.close()
        socket = null
    }
}
```

### UI Integration

```kotlin
// In PurchaseScreen
val scalesManager = remember { hiltViewModel<PurchaseViewModel>().scalesManager }

LaunchedEffect(Unit) {
    scalesManager.readings
        .filter { it.isStable } // Only accept stable readings
        .collect { reading ->
            viewModel.updateWeight(reading.weight)
        }
}
```

### Error Handling

- **Connection failed**: Show manual weight entry option
- **Unstable readings**: Display weight but disable "Confirm" button
- **Timeout**: Reconnect automatically after 5 seconds
- **Network lost**: Fall back to manual entry

---

## Printer Integration

### Hardware Setup

**Printer**: Xprinter XP-58IIH
- 58mm thermal printer
- Bluetooth 2.0 + EDR
- ESC/POS command set
- 203 DPI resolution

**Connection**:
1. Pair printer with Android device (Settings → Bluetooth)
2. App discovers paired Bluetooth devices
3. Connect via Bluetooth SPP (Serial Port Profile)
4. Send ESC/POS commands as byte array

### Bluetooth Connection

```kotlin
// PrinterManager.kt
@Singleton
class PrinterManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var socket: BluetoothSocket? = null
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    suspend fun connect(deviceAddress: String) {
        withContext(Dispatchers.IO) {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            val device = adapter.getRemoteDevice(deviceAddress)

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID).apply {
                connect()
            }
        }
    }

    suspend fun print(data: ByteArray) {
        withContext(Dispatchers.IO) {
            socket?.outputStream?.write(data)
            socket?.outputStream?.flush()
        }
    }

    fun disconnect() {
        socket?.close()
        socket = null
    }
}
```

### ESC/POS Commands

```kotlin
// ReceiptFormatter.kt
object EscPos {
    // Control commands
    val INIT = byteArrayOf(0x1B, 0x40) // ESC @
    val LF = byteArrayOf(0x0A) // Line feed
    val CUT = byteArrayOf(0x1D, 0x56, 0x00) // GS V 0 (full cut)
    val PARTIAL_CUT = byteArrayOf(0x1D, 0x56, 0x01) // GS V 1

    // Alignment
    val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
    val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2

    // Text style
    val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
    val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0
    val DOUBLE_HEIGHT = byteArrayOf(0x1B, 0x21, 0x10) // ESC ! 16
    val NORMAL_SIZE = byteArrayOf(0x1B, 0x21, 0x00) // ESC ! 0

    // Utility
    fun text(str: String): ByteArray = str.toByteArray(Charsets.UTF_8)
    fun feed(lines: Int = 1): ByteArray = ByteArray(lines) { 0x0A }
}
```

### Receipt Template (Purchase)

```kotlin
// ReceiptTemplate.kt
class ReceiptTemplate {
    fun buildPurchaseReceipt(
        receiptNumber: String,
        dateTime: String,
        items: List<ReceiptItem>,
        total: BigDecimal
    ): ByteArray = buildList<ByteArray> {
        // Initialize
        add(EscPos.INIT)

        // Header (centered, bold)
        add(EscPos.ALIGN_CENTER)
        add(EscPos.BOLD_ON)
        add(EscPos.text("ФОП Петренко"))
        add(EscPos.LF)
        add(EscPos.BOLD_OFF)
        add(EscPos.text("вул. Центральна, 5"))
        add(EscPos.feed(2))

        // Receipt info (left-aligned)
        add(EscPos.ALIGN_LEFT)
        add(EscPos.text("Чек №: $receiptNumber"))
        add(EscPos.LF)
        add(EscPos.text("Дата: $dateTime"))
        add(EscPos.feed(2))

        // Items
        items.forEach { item ->
            // Product name and weight
            add(EscPos.text("${item.name.padEnd(20)} ${item.weight} кг"))
            add(EscPos.LF)

            // Price per kg and total
            val priceStr = "  ${item.pricePerKg.format()} грн/кг"
            val totalStr = item.total.format()
            val spacing = 32 - priceStr.length - totalStr.length
            add(EscPos.text("$priceStr${" ".repeat(spacing)}$totalStr"))
            add(EscPos.LF)
        }

        add(EscPos.feed(1))

        // Total (bold)
        add(EscPos.BOLD_ON)
        val totalLabel = "РАЗОМ:"
        val totalValue = "${total.format()} грн"
        val spacing = 32 - totalLabel.length - totalValue.length
        add(EscPos.text("$totalLabel${" ".repeat(spacing)}$totalValue"))
        add(EscPos.BOLD_OFF)
        add(EscPos.feed(2))

        // Payment method
        add(EscPos.text("Оплата: готівка"))
        add(EscPos.feed(2))

        // Footer (centered)
        add(EscPos.ALIGN_CENTER)
        add(EscPos.text("Дякуємо за співпрацю!"))
        add(EscPos.feed(3))

        // Cut paper
        add(EscPos.PARTIAL_CUT)
    }.reduce { acc, bytes -> acc + bytes }

    private fun BigDecimal.format(): String =
        "%,.2f".format(Locale.US, this).replace(",", " ")
}

data class ReceiptItem(
    val name: String,
    val weight: BigDecimal,
    val pricePerKg: BigDecimal,
    val total: BigDecimal
)
```

### Permissions

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />

<uses-feature android:name="android.hardware.bluetooth" android:required="true" />
```

### Error Handling

- **Printer not paired**: Show pairing instructions
- **Connection failed**: Retry up to 3 times, then offer manual fallback
- **Out of paper**: Detect via status command, warn user
- **Print failed**: Log error, offer reprint option

### Testing

- Use ESC/POS emulator (e.g., BlueThermal library examples)
- Test receipt layout with different data (long names, large numbers)
- Test error scenarios (disconnect mid-print, low battery)

---

## Settings Storage

Store hardware config in SharedPreferences:

```kotlin
// HardwarePreferences.kt
@Singleton
class HardwarePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("hardware", Context.MODE_PRIVATE)

    var scalesIp: String?
        get() = prefs.getString("scales_ip", null)
        set(value) = prefs.edit().putString("scales_ip", value).apply()

    var scalesPort: Int
        get() = prefs.getInt("scales_port", 8234)
        set(value) = prefs.edit().putInt("scales_port", value).apply()

    var printerAddress: String?
        get() = prefs.getString("printer_address", null)
        set(value) = prefs.edit().putString("printer_address", value).apply()
}
```

## Fallback Modes

- **Scales unavailable**: Manual weight entry with number pad
- **Printer unavailable**: Skip printing, show "Receipt not printed" warning
- **Both unavailable**: App still functional for data entry
