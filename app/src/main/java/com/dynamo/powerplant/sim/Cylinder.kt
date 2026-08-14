package com.dynamo.powerplant.sim

import kotlin.math.max
import kotlin.math.min

/**
 * One cylinder of the six.
 *
 * Everything the engine as a whole used to have, each pot now has its own copy
 * of: its own igniter with its own deposit on it, its own feed off the
 * lubricator, its own oil film on the liner, its own exhaust temperature. That
 * is what makes a rough engine diagnosable — the pyrometer tells you *which*
 * one, and the beat tells you it is one and not all six.
 *
 * The firing order is 1-5-3-6-2-4, which is what a six-cylinder engine of this
 * period would have run, and it is what sets the exhaust beat.
 */
class Cylinder(val number: Int, val trim: Double = 1.0) {

    /** Deposit on the igniter points, 0..1. Cuts the spark it will pass. */
    var fouling: Double = 0.0

    /** Oil on the liner, 0..1. Its own, because its own feed supplies it. */
    var oilFilm: Double = 1.0

    /** Exhaust temperature at the port, degrees centigrade. */
    var exhaustC: Double = 12.0

    /** Smoothed fraction of this cylinder's charges that are lighting, 0..1. */
    var firingSuccess: Double = 0.0

    /** Bore and liner wear from running dry, 0..1. One is a scored liner. */
    var wear: Double = 0.0

    /** Set for one step when this cylinder fires, and when it does not. */
    var firedThisStep: Boolean = false
    var misfiredThisStep: Boolean = false

    /** True when the igniter is shorted out at the cut-out switch. */
    var cutOut: Boolean = false

    /** True while this pot is contributing nothing worth having. */
    val dead: Boolean get() = cutOut || firingSuccess < 0.15

    fun reset() {
        fouling = 0.0
        oilFilm = 1.0
        exhaustC = 12.0
        firingSuccess = 0.0
        wear = 0.0
        firedThisStep = false
        misfiredThisStep = false
        cutOut = false
    }

    /**
     * The exhaust runs hot in proportion to what this pot is burning, and hotter
     * still on a lean charge or a retarded spark. A cylinder that has quit goes
     * cold, and that is how you find it on the pyrometer.
     */
    fun stepExhaust(dt: Double, burn: Double, leanHeat: Double, retardHeat: Double, ambientC: Double) {
        val target = ambientC + burn * 520.0 * trim * (1.0 + leanHeat * 0.55 + retardHeat * 0.40)
        // A heavy iron manifold takes a good half minute to follow.
        exhaustC += (target - exhaustC) * min(1.0, dt * 0.11)
        exhaustC = max(ambientC, exhaustC)
    }

    companion object {
        /** 1-5-3-6-2-4, as the crank is thrown. */
        val FIRING_ORDER = intArrayOf(0, 4, 2, 5, 1, 3)
    }
}
