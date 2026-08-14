package com.dynamo.powerplant.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The interconnection. This is a full transmission system, not a village
 * lighting circuit: your 120 kilowatt set is a rounding error on it, so you do
 * not set its frequency, you follow it.
 *
 * That makes the grid an input rather than something you push around. It
 * wanders slowly about 60 cycles, takes the occasional knock when something
 * large elsewhere trips, and the dispatcher hands you a new load order every
 * ninety seconds which you are expected to meet on the throttle.
 */
class Grid(private val rnd: Random = Random(0x1920)) {

    companion object {
        const val DISPATCH_PERIOD_S = 90.0
        /** Frequency limits before the system protection sheds you. */
        const val TRIP_LOW_HZ = 57.5
        const val TRIP_HIGH_HZ = 62.5
    }

    var hz: Double = 60.0
        private set
    var volts: Double = Spec.RATED_VOLTS
        private set

    /** The load order in watts, and how long until the next one. */
    var dispatchW: Double = 185_000.0
        private set
    var secondsToChange: Double = DISPATCH_PERIOD_S
        private set
    var dispatchStep: Int = 0
        private set
    var dispatchChangedThisStep: Boolean = false

    /** A disturbance somewhere out on the system, in hertz, decaying away. */
    var disturbanceHz: Double = 0.0
        private set
    var disturbanceActive: Boolean = false
        private set

    var energyExportedKwh: Double = 0.0
    var dispatchErrorKws: Double = 0.0
    var secondsOffOrder: Double = 0.0

    private var t = 0.0
    private var wanderPhase = rnd.nextDouble() * 6.283
    private var nextEventAt = 55.0 + rnd.nextDouble() * 70.0

    /** The dispatcher's programme for the shift, in watts. */
    private val schedule = doubleArrayOf(
        185_000.0, 258_000.0, 325_000.0, 290_000.0, 396_000.0, 452_000.0,
        368_000.0, 483_000.0, 425_000.0, 310_000.0, 495_000.0, 402_000.0,
        276_000.0, 200_000.0, 125_000.0
    )

    fun step(dt: Double, gen: Generator, ctl: Controls) {
        t += dt
        dispatchChangedThisStep = false

        // ---- the dispatcher's order --------------------------------------------
        secondsToChange -= dt
        if (secondsToChange <= 0.0) {
            secondsToChange += DISPATCH_PERIOD_S
            dispatchStep++
            val base = schedule[dispatchStep % schedule.size]
            dispatchW = base * (0.95 + rnd.nextDouble() * 0.10)
            dispatchChangedThisStep = true
        }

        // ---- system frequency ---------------------------------------------------
        // A slow wander, plus whatever the last disturbance left behind.
        wanderPhase += dt * 0.085
        val wander = sin(wanderPhase) * 0.055 + sin(wanderPhase * 2.7) * 0.030

        if (t > nextEventAt) {
            // Something large tripped out on the system.
            disturbanceHz = -(0.35 + rnd.nextDouble() * 0.45)
            if (rnd.nextDouble() < 0.3) disturbanceHz = -disturbanceHz
            nextEventAt = t + 70.0 + rnd.nextDouble() * 110.0
        }
        disturbanceHz *= Math.exp(-dt / 9.0)
        disturbanceActive = abs(disturbanceHz) > 0.08

        hz = 60.0 + wander + disturbanceHz
        volts = Spec.RATED_VOLTS * (1.0 + wander * 0.012 + disturbanceHz * 0.020)

        // ---- how well you are following the order ------------------------------
        val outW = if (ctl.mainBreakerClosed) gen.realPowerW else 0.0
        energyExportedKwh += max(0.0, outW) / 3_600_000.0 * dt
        if (ctl.mainBreakerClosed) {
            val err = abs(outW - dispatchW)
            dispatchErrorKws += err / 1000.0 * dt
            if (err > 45_000.0) secondsOffOrder += dt
        } else {
            dispatchErrorKws += dispatchW / 1000.0 * dt
            secondsOffOrder += dt
        }
    }

    /** True when the system protection would throw your unit off. */
    fun outsideLimits(): Boolean = hz < TRIP_LOW_HZ || hz > TRIP_HIGH_HZ

    fun dispatchKw(): Double = dispatchW / 1000.0

    fun reset() {
        hz = 60.0
        volts = Spec.RATED_VOLTS
        dispatchW = 185_000.0
        secondsToChange = DISPATCH_PERIOD_S
        dispatchStep = 0
        dispatchChangedThisStep = false
        disturbanceHz = 0.0
        disturbanceActive = false
        energyExportedKwh = 0.0
        dispatchErrorKws = 0.0
        secondsOffOrder = 0.0
        t = 0.0
        nextEventAt = 55.0 + rnd.nextDouble() * 70.0
    }
}
