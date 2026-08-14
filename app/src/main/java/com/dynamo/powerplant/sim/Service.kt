package com.dynamo.powerplant.sim

import kotlin.math.max
import kotlin.math.min

/** One load hanging off an internal bus, with its own switch and fuse. */
class ServiceLoad(
    val name: String,
    val shortName: String,
    /** What it draws when running, in kilowatts. */
    val kw: Double,
    /** Below this fraction of normal volts the load drops out. */
    val dropoutPu: Double
) {
    var fuseBlown: Boolean = false
    var fuseHeat: Double = 0.0
    var running: Boolean = false
}

/**
 * One of the plant's two internal buses, and the whole point of the switchboard.
 *
 * The **main bus** carries the regular running gear: the switchgear control
 * supply, the circulating pump, the oil pump and the house lighting. It hangs
 * off the generator terminals through the station transformer, so it is dead
 * until the machine is turning and excited.
 *
 * The **emergency line** carries the four things the set cannot run without:
 * the ignition, the excitation, the emergency pump and the emergency lights.
 * What feeds it is chosen on the supply selector, and each source can carry a
 * different amount. The battery in particular cannot run everything at once, so
 * on emergency supply you have to decide what matters.
 */
class Service(val loads: List<ServiceLoad>) {

    companion object {
        /** What each source can deliver to the emergency line, in kilowatts. */
        const val CAPACITY_GRID_KW = 60.0
        const val CAPACITY_MAIN_KW = 40.0
        const val CAPACITY_BATTERY_KW = 7.0

        /** What the station transformer can deliver to the main bus. */
        const val CAPACITY_STATION_KW = 45.0

        // --- positions on the main bus ---
        const val CONTROL = 0
        const val CIRC_PUMP = 1
        const val OIL_PUMP = 2
        const val LIGHTS = 3

        // --- positions on the emergency line ---
        const val IGNITION = 0
        const val EXCITATION = 1
        const val EMG_PUMP = 2
        const val EMG_LIGHTS = 3

        /** The regular controls and pumps, all on the main bus. */
        fun mainBus() = Service(
            listOf(
                ServiceLoad("CONTROL SUPPLY", "CTRL", 1.20, 0.45),
                ServiceLoad("CIRCULATING PUMP", "CIRC", 14.00, 0.55),
                ServiceLoad("OIL PUMP", "OIL", 5.50, 0.50),
                ServiceLoad("HOUSE LIGHTS", "LIGHT", 3.00, 0.25)
            )
        )

        /** Everything the set cannot run without, all on the battery's line. */
        fun emergencyLine() = Service(
            listOf(
                ServiceLoad("IGNITION", "IGN", 0.60, 0.30),
                ServiceLoad("EXCITATION", "EXC", 2.20, 0.40),
                ServiceLoad("EMERGENCY PUMP", "E.PUMP", 4.00, 0.55),
                ServiceLoad("EMERGENCY LIGHTS", "E.LT", 1.20, 0.25)
            )
        )
    }

    /** Volts on this bus, per unit. */
    var volts: Double = 0.0
        private set
    var demandKw: Double = 0.0
        private set
    var capacityKw: Double = 0.0
        private set
    var overloaded: Boolean = false
        private set
    var fuseBlewThisStep: Boolean = false

    /**
     * Work out what this bus is doing this instant.
     *
     * Voltage and capacity are separate things: a source can be at full volts and
     * still not have the kilowatts to carry everything switched onto it.
     *
     * @param closed the switch positions on the board, one per load
     * @param sourceVolts 0..1, the terminal volts of the selected source
     * @param sourceCapacityKw what that source can carry, in kilowatts
     */
    fun step(dt: Double, closed: BooleanArray, sourceVolts: Double, sourceCapacityKw: Double) {
        fuseBlewThisStep = false
        capacityKw = if (sourceVolts > 0.05) sourceCapacityKw else 0.0

        // Everything switched in and not fused out asks for its share.
        var asked = 0.0
        for ((i, l) in loads.withIndex()) {
            if (closed[i] && !l.fuseBlown) asked += l.kw
        }
        demandKw = asked

        // The bus sags when more is asked of it than the source can carry.
        // With no source there is no current, so nothing is overloaded and no fuse
        // can blow; the bus is simply dead.
        val ratio = if (capacityKw > 0.01) asked / capacityKw else 0.0
        overloaded = ratio > 1.0
        val sag = if (ratio > 1.0) min(0.85, (ratio - 1.0) * 1.60) else 0.0
        volts = clamp(sourceVolts * (1.0 - sag), 0.0, 1.15)

        // Each load runs only if the bus is holding up well enough for it.
        for ((i, l) in loads.withIndex()) {
            l.running = closed[i] && !l.fuseBlown && volts >= l.dropoutPu
            // A badly overloaded bus eventually takes a fuse out.
            if (closed[i] && !l.fuseBlown && ratio > 1.30) {
                l.fuseHeat = min(2.0, l.fuseHeat + (ratio - 1.30) * dt * 0.55)
                if (l.fuseHeat >= 1.0) {
                    l.fuseBlown = true
                    l.fuseHeat = 0.0
                    l.running = false
                    fuseBlewThisStep = true
                }
            } else {
                l.fuseHeat = max(0.0, l.fuseHeat - dt * 0.35)
            }
        }
    }

    fun isRunning(index: Int): Boolean = loads[index].running

    fun reset() {
        volts = 0.0
        demandKw = 0.0
        capacityKw = 0.0
        overloaded = false
        fuseBlewThisStep = false
        for (l in loads) { l.fuseBlown = false; l.fuseHeat = 0.0; l.running = false }
    }
}
