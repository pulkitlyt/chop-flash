package com.example.chopflash

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.sqrt

/**
 * Detects two sharp "chops" (or shakes) in quick succession.
 *
 * Uses the raw accelerometer with a low-pass filter to estimate gravity, then looks at
 * the remaining (linear) acceleration. A chop is a spike above [threshold] m/s². After a
 * spike, the signal has to settle below half the threshold before another spike counts,
 * so one chop's rebound isn't counted twice.
 */
class ChopDetector(
    @Volatile var threshold: Float,
    private val onDoubleChop: () -> Unit,
) : SensorEventListener {

    private val gravity = FloatArray(3)
    private var gravityReady = false

    private var firstChopAt = 0L
    private var armed = true
    private var cooldownUntil = 0L

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val now = event.timestamp / 1_000_000L // ns -> ms

        val v = event.values
        if (!gravityReady) {
            gravity[0] = v[0]; gravity[1] = v[1]; gravity[2] = v[2]
            gravityReady = true
            return
        }
        for (i in 0..2) gravity[i] = ALPHA * gravity[i] + (1 - ALPHA) * v[i]
        val x = v[0] - gravity[0]
        val y = v[1] - gravity[1]
        val z = v[2] - gravity[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        if (now < cooldownUntil) return

        if (!armed) {
            if (magnitude < threshold * 0.5f) armed = true
            return
        }
        if (magnitude < threshold) return

        // A new chop peak.
        armed = false
        val sinceFirst = now - firstChopAt
        when {
            firstChopAt == 0L || sinceFirst > MAX_GAP_MS -> firstChopAt = now
            sinceFirst < MIN_GAP_MS -> Unit // still the same motion, ignore
            else -> {
                firstChopAt = 0L
                cooldownUntil = now + COOLDOWN_MS
                onDoubleChop()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun reset() {
        gravityReady = false
        firstChopAt = 0L
        armed = true
        cooldownUntil = 0L
    }

    companion object {
        private const val ALPHA = 0.8f
        private const val MIN_GAP_MS = 150L
        private const val MAX_GAP_MS = 800L
        private const val COOLDOWN_MS = 1200L

        /** Maps the 0..100 sensitivity slider to an acceleration threshold in m/s². */
        fun thresholdFor(sensitivity: Int): Float = 26f - sensitivity.coerceIn(0, 100) * 0.18f
    }
}
