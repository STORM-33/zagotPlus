package com.zagot.zagotplus.hardware.scales

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.random.Random

/**
 * Mock scales service for development and testing.
 * Simulates weight readings with configurable behavior.
 *
 * Use in debug builds when real scales are not available.
 * Provided via HardwareModule based on BuildConfig.DEBUG.
 */
class MockScalesService : ScalesService {

    private val _connectionState = MutableStateFlow<ScalesConnectionState>(
        ScalesConnectionState.Disconnected
    )
    override val connectionState: StateFlow<ScalesConnectionState> = _connectionState.asStateFlow()

    private val _weightReadings = MutableSharedFlow<WeightReading>(replay = 1)
    override val weightReadings: SharedFlow<WeightReading> = _weightReadings.asSharedFlow()

    private val _errors = MutableSharedFlow<ScalesError>()
    override val errors: SharedFlow<ScalesError> = _errors.asSharedFlow()

    private val _debugLog = MutableSharedFlow<String>(replay = 50, extraBufferCapacity = 100)
    override val debugLog: SharedFlow<String> = _debugLog.asSharedFlow()

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Simulation parameters (can be modified for testing)
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

    override suspend fun sendRawCommand(command: String): Result<Unit> {
        _debugLog.emit("[TX-RAW] \"$command\" (mock)")
        return Result.success(Unit)
    }

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
                    .setScale(2, RoundingMode.HALF_UP)
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

    /** For testing: simulate connection loss */
    suspend fun simulateConnectionLoss() {
        simulationJob?.cancel()
        _connectionState.value = ScalesConnectionState.Error(ScalesError.ConnectionLost)
        _errors.emit(ScalesError.ConnectionLost)
    }
}
