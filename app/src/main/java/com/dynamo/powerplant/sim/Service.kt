package com.dynamo.powerplant.sim

import kotlin.math.max
import kotlin.math.min

/** One internal load hanging off the station service bus, with its own switch and fuse. */
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
 * The station service bus: the plant's own internal supply, and the whole point
 * of the switchboard. The ignition, the cooling water pump, the battery charger
 * and the house lighting all hang off it.
 *
 * What feeds it is chosen on the supply selector, and each source can carry a
 * different amount. The battery in particular cannot run everything at once, so
 * on emergency supply you have to decide what matters.
 */
class Service {

    companion object {
        /** What each source can deliver to the internal bus, in kilowatts. */
        const val CAPACITY_GRID_KW = 26.0
        const val CAPACITY_GEN_KW = 26.0
        const val CAPACITY_BATTERY_KW = 5.0

        const val IGNITION = 0
        const val PUMP = 1
        const val CHARGER = 2
        const val LIGHTS = 3
    }

    val loads = listOf(
        ServiceLoad("IGNITION", "IGN", 0.35, 0.30),
        ServiceLoad("WATER PUMP", "PUMP", 3.40, 0.55),
        ServiceLoad("BATTERY CHARGER", "CHGR", 2.10, 0.60),
        ServiceLoad("HOUSE LIGHTS", "LIGHT", 1.30, 0.25)
    )

    /** Volts on the internal bus, per unit. */
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
     * Work out what the internal bus is doing this instant.
     *
     * Voltage and capacity are separate things: a source can be at full volts and
     * still not have the kilowatts to carry everything switched onto it.
     *
     * @param sourceVolts 0..1, the terminal volts of the selected source
     * @param sourceCapacityKw what that source can carry, in kilowatts
     */
    fun step(dt: Double, ctl: Controls, sourceVolts: Double, sourceCapacityKw: Double) {
        fuseBlewThisStep = false
        capacityKw = if (sourceVolts > 0.05) sourceCapacityKw else 0.0

        // Everything switched in and not fused out asks for its share.
        var asked = 0.0
        for ((i, l) in loads.withIndex()) {
            if (ctl.auxClosed[i] && !l.fuseBlown) asked += l.kw
        }
        demandKw = asked

        // The bus sags when more is asked of it than the source can carry.
        // With no source there is no current, so nothing is overloaded and no fuse
        // can blow; the bus is simply dead.
        val ratio = if (capacityKw > 0.01) asked / capacityKw else 0.0
        overloaded = ratio > 1.0
        val sag = if (ratio > 1.0) min(0.85, (ratio - 1.0) * 0.75) else 0.0
        volts = clamp(sourceVolts * (1.0 - sag), 0.0, 1.15)

        // Each load runs only if the bus is holding up well enough for it.
        for ((i, l) in loads.withIndex()) {
            l.running = ctl.auxClosed[i] && !l.fuseBlown && volts >= l.dropoutPu
            // A badly overloaded bus eventually takes a fuse out.
            if (ctl.auxClosed[i] && !l.fuseBlown && ratio > 1.30) {
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
