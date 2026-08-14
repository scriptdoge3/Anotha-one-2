package com.dynamo.powerplant.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class Feeder(
    val name: String,
    val shortName: String,
    /** Fraction of town demand carried by this circuit. */
    val share: Double,
    /** Fuse rating in amps at 2300 volts. */
    val fuseAmps: Double,
    /** How badly this circuit suffers from poor volts and cycles. */
    val sensitivity: Double
) {
    var fuseBlown: Boolean = false
    var amps: Double = 0.0
    var fuseHeat: Double = 0.0
    var damageSeconds: Double = 0.0
}

/**
 * The 2300 volt town bus, the little Willow Creek hydro station that shares it,
 * and the four feeders out of the switchboard.
 *
 * Town demand steps to a new figure every 90 seconds, on the dot.
 */
class Grid(private val rnd: Random = Random(0x1920)) {

    companion object {
        const val DEMAND_PERIOD_S = 90.0
        /** Willow Creek can carry this much and no more. */
        const val OTHER_CAPACITY_W = 56_000.0
        /** What Willow Creek's governor is set to carry at exactly 60 cycles. */
        const val OTHER_BASE_W = 14_000.0
        /** Its droop: watts it picks up for every cycle the bus falls. */
        const val OTHER_DROOP_W_PER_HZ = 22_000.0
        /** Watts of imbalance per hertz per second of bus acceleration. */
        const val BUS_INERTIA = 25_000.0
        const val RATED_Q_VAR = 90_000.0
    }

    val feeders = listOf(
        Feeder("MAIN STREET LIGHTING", "MAIN ST", 0.24, 14.0, 1.35),
        Feeder("MILL No. 2 MOTORS", "MILL 2", 0.36, 22.0, 1.15),
        Feeder("ICE HOUSE & CREAMERY", "ICE HSE", 0.17, 11.0, 0.85),
        Feeder("STREET RAILWAY", "RAILWAY", 0.23, 16.0, 0.95)
    )

    var busHz: Double = 60.0
    var busVolts: Double = Spec.RATED_VOLTS
    var demandW: Double = 34_000.0
        private set
    private var demandTargetW: Double = 34_000.0
    var secondsToChange: Double = DEMAND_PERIOD_S
        private set
    var demandStep: Int = 0
        private set

    /** Watts actually connected through closed, unblown feeders. */
    var connectedLoadW: Double = 0.0
    var otherStationW: Double = 0.0

    var lampsBurnedOut: Boolean = false
    var blackout: Boolean = false
    var brownoutSeconds: Double = 0.0
    var energyDeliveredKwh: Double = 0.0
    var unservedKwh: Double = 0.0

    /** Set for one step when the demand figure changes. */
    var demandChangedThisStep: Boolean = false

    /** The dispatcher's schedule for the shift, in watts. */
    private val schedule = doubleArrayOf(
        34_000.0,  // 18:00 quiet
        52_000.0,  // lamps lit at dusk
        68_000.0,
        61_000.0,
        84_000.0,  // mill night shift comes on
        97_000.0,
        88_000.0,
        112_000.0, // the cold snap
        104_000.0,
        73_000.0,
        119_000.0, // peak
        95_000.0,
        66_000.0,
        48_000.0,
        31_000.0
    )

    fun step(dt: Double, gen: Generator, ctl: Controls) {
        demandChangedThisStep = false
        secondsToChange -= dt
        if (secondsToChange <= 0.0) {
            secondsToChange += DEMAND_PERIOD_S
            demandStep++
            val base = schedule[demandStep % schedule.size]
            demandTargetW = base * (0.94 + rnd.nextDouble() * 0.12)
            demandChangedThisStep = true
        }
        // The change comes on over a few seconds, not instantly.
        demandW += (demandTargetW - demandW) * min(1.0, dt / 4.5)

        // ---- what is actually connected ----------------------------------------
        var connected = 0.0
        var shed = 0.0
        for ((i, f) in feeders.withIndex()) {
            val load = demandW * f.share
            if (ctl.feederClosed[i] && !f.fuseBlown) connected += load else shed += load
        }
        // Loads draw less when the volts are down; incandescent lamps especially.
        val vpu = busVolts / Spec.RATED_VOLTS
        connectedLoadW = connected * clamp(vpu * vpu * 0.55 + vpu * 0.45, 0.15, 1.35)

        // ---- Willow Creek's droop governor -------------------------------------
        // It carries a fixed base and leans in as the bus falls, but it is a small
        // station: once it is against its stops the cycles are yours to hold.
        val droop = clamp(
            OTHER_BASE_W + (60.0 - busHz) * OTHER_DROOP_W_PER_HZ,
            0.0, OTHER_CAPACITY_W
        )
        otherStationW += (droop - otherStationW) * min(1.0, dt * 1.4)

        // ---- bus frequency ------------------------------------------------------
        val genW = if (ctl.mainBreakerClosed) gen.realPowerW else 0.0
        val damping = (busHz - 60.0) * 1_800.0
        val net = otherStationW + genW - connectedLoadW - damping
        busHz += net / BUS_INERTIA * dt
        busHz = clamp(busHz, 0.0, 90.0)

        // ---- bus volts ----------------------------------------------------------
        val qSupport = if (ctl.mainBreakerClosed) gen.reactivePowerVar / RATED_Q_VAR else 0.0
        val overload = max(0.0, connectedLoadW / (OTHER_CAPACITY_W + Spec.RATED_KW * 1000.0) - 0.85)
        val target = Spec.RATED_VOLTS * clamp(1.0 + qSupport * 0.115 - overload * 0.55, 0.30, 1.45)
        busVolts += (target - busVolts) * min(1.0, dt * 2.2)

        // ---- consequences -------------------------------------------------------
        val v = busVolts / Spec.RATED_VOLTS
        if (v > 1.11) lampsBurnedOut = true
        for ((i, f) in feeders.withIndex()) {
            if (!ctl.feederClosed[i] || f.fuseBlown) { f.amps = 0.0; f.fuseHeat = max(0.0, f.fuseHeat - dt * 0.4); continue }
            val load = demandW * f.share
            f.amps = load / (busVolts.coerceAtLeast(1.0) * 1.7320508)
            val over = f.amps / f.fuseAmps
            f.fuseHeat = clamp(f.fuseHeat + (over - 1.0) * dt * 0.55, 0.0, 2.0)
            if (f.fuseHeat >= 1.0) { f.fuseBlown = true; f.fuseHeat = 0.0 }
            // Low volts or low cycles cooks motors and annoys everyone on the line.
            if (v < 0.90 || busHz < 58.4 || busHz > 61.6) {
                f.damageSeconds += dt * f.sensitivity
            }
        }

        val serving = connectedLoadW > 1_000.0
        if (serving && (v < 0.90 || busHz < 58.4)) brownoutSeconds += dt
        energyDeliveredKwh += max(0.0, genW) / 3_600_000.0 * dt
        unservedKwh += shed / 3_600_000.0 * dt

        if (busHz < 54.0 || busHz > 67.0 || v < 0.55) blackout = true
    }

    fun totalDemandAmpsAt(volts: Double): Double =
        demandW / (volts.coerceAtLeast(1.0) * 1.7320508)

    fun reset() {
        busHz = 60.0; busVolts = Spec.RATED_VOLTS
        demandW = 34_000.0; demandTargetW = 34_000.0
        secondsToChange = DEMAND_PERIOD_S; demandStep = 0
        connectedLoadW = 0.0; otherStationW = 30_000.0
        lampsBurnedOut = false; blackout = false; brownoutSeconds = 0.0
        energyDeliveredKwh = 0.0; unservedKwh = 0.0
        for (f in feeders) { f.fuseBlown = false; f.amps = 0.0; f.fuseHeat = 0.0; f.damageSeconds = 0.0 }
    }
}
