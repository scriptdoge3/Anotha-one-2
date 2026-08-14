package com.dynamo.powerplant.sim

import kotlin.math.abs
import kotlin.math.max

/**
 * One protective relay on the switchboard.
 *
 * A relay sits watching one quantity. When that quantity goes outside what the
 * relay is set for it starts to *pick up*; if the condition holds for the
 * relay's time setting the relay operates, trips the unit breaker, and drops a
 * mechanical target behind a little window on the panel.
 *
 * The target is the whole point. It stays dropped after the relay has reset, so
 * an operator walking in cold can see which relay put the machine off the bars.
 * And the breaker is interlocked against it: you cannot close again until you
 * have gone to the board and reset the target by hand, which means you cannot
 * close again without first noticing what happened.
 */
class Relay(
    /** The ANSI device number a 1920s board would have had painted on it. */
    val device: String,
    val name: String,
    val shortName: String,
    /** Seconds the condition must hold before the relay operates. */
    val delayS: Double,
    val detail: String
) {
    /** How far into its time the relay has travelled, 0..1. */
    var travel: Double = 0.0
        internal set

    /** True while the watched quantity is outside the setting. */
    var pickedUp: Boolean = false
        internal set

    /** The dropped target. Stays until the operator resets it by hand. */
    var target: Boolean = false
        internal set

    /** Set for one step when the relay actually operates. */
    var operatedThisStep: Boolean = false
        internal set

    fun reset() {
        travel = 0.0; pickedUp = false; target = false; operatedThisStep = false
    }
}

/**
 * The relay panel: everything that can put the unit off the bars without you
 * touching the handle.
 *
 * Nothing here is a shift-ender. A relay operation is a trip, and a trip is
 * recoverable — you find out why, reset the target, re-synchronise and carry on.
 * What it costs you is the kilowatt-hours you were not exporting while you sorted
 * it out, which the dispatcher notices.
 */
class Protection {

    companion object {
        /** Motoring: the bus driving your engine round instead of the other way. */
        const val REVERSE_POWER_KW = -12.0
        const val REVERSE_POWER_S = 6.0

        /** Stator current the machine will stand continuously, per unit. */
        const val OVERCURRENT_PU = 1.15
        /** Inverse time: the further over, the faster it goes. */
        const val OVERCURRENT_BASE_S = 22.0

        /** Field current below which the machine is losing hold of the bus. */
        const val LOSS_OF_FIELD_PU = 0.22
        const val LOSS_OF_FIELD_S = 5.0

        const val FREQUENCY_S = 4.0

        /** Differential is instantaneous: it is looking for a fault in the machine. */
        const val DIFFERENTIAL_PU = 2.6
    }

    val reversePower = Relay(
        "32", "REVERSE POWER", "REV PWR", REVERSE_POWER_S,
        "The bus was driving the machine as a motor. Either the engine died under you or you shut the throttle too far while tied on."
    )
    val overcurrent = Relay(
        "51", "OVERCURRENT", "O/C", OVERCURRENT_BASE_S,
        "Stator current above what the windings will stand. Too many kilowatts, or too many amperes of field pushed out as reactive."
    )
    val underFrequency = Relay(
        "81", "OVER / UNDER FREQUENCY", "FREQ", FREQUENCY_S,
        "The system frequency went outside the limits the machine is allowed to run tied to. Not your doing, but you cannot stay on the bars through it."
    )
    val lossOfField = Relay(
        "40", "LOSS OF FIELD", "FIELD", LOSS_OF_FIELD_S,
        "Excitation collapsed while carrying load. The machine was drawing its magnetising current out of the system and heading for a pole slip."
    )
    val differential = Relay(
        "87", "DIFFERENTIAL", "DIFF", 0.0,
        "A fault inside the machine or its leads. The differential relay does not wait."
    )

    val relays = listOf(reversePower, overcurrent, underFrequency, lossOfField, differential)

    /** Set for one step when any relay operates, carrying the one that did. */
    var trippedBy: Relay? = null
        private set

    /** True while any target is dropped, which is what blocks the breaker. */
    val anyTarget: Boolean get() = relays.any { it.target }

    fun droppedTargets(): List<Relay> = relays.filter { it.target }

    /**
     * Watch everything for one step. Returns true if a relay operated, in which
     * case the caller trips the unit breaker.
     *
     * The relays only watch while the unit is on the bars. Off the bars there is
     * no current in the leads and nothing for them to measure — which is also why
     * you can leave the machine running unexcited off the bus all night without
     * the loss of field relay caring.
     */
    fun step(dt: Double, tied: Boolean, outputKw: Double, ampsPu: Double, fieldPu: Double, hz: Double): Boolean {
        trippedBy = null
        for (r in relays) r.operatedThisStep = false

        if (!tied) {
            for (r in relays) {
                r.pickedUp = false
                r.travel = max(0.0, r.travel - dt * 0.6)
            }
            return false
        }

        // 32 — reverse power. A definite time relay: it either sees motoring or
        // it does not, and it waits the same six seconds either way, because a
        // swing through zero on a load change is not a fault.
        advance(reversePower, dt, outputKw < REVERSE_POWER_KW, 1.0)

        // 51 — overcurrent, inverse time. Twenty per cent over takes the best
        // part of a minute; double takes a couple of seconds.
        val over = ampsPu / OVERCURRENT_PU
        advance(overcurrent, dt, over > 1.0, if (over > 1.0) (over - 1.0) * 5.0 + 0.35 else 0.0)

        // 81 — the system frequency, and nothing you can do about it.
        advance(underFrequency, dt, hz < Grid.TRIP_LOW_HZ || hz > Grid.TRIP_HIGH_HZ, 1.0)

        // 40 — loss of field. Only counts as loss of field if the machine is
        // actually carrying something; an idling machine with no field is just
        // an idling machine.
        advance(lossOfField, dt, fieldPu < LOSS_OF_FIELD_PU && abs(outputKw) > 40.0, 1.0)

        // 87 — differential, instantaneous.
        if (ampsPu > DIFFERENTIAL_PU) {
            differential.pickedUp = true
            operate(differential)
            return true
        }
        differential.pickedUp = false

        for (r in relays) {
            if (r.travel >= 1.0 && !r.operatedThisStep) {
                operate(r)
                return true
            }
        }
        return false
    }

    /**
     * Wind the relay disc forward while the condition holds, and let it run back
     * when it does not. A real induction disc does exactly this, which is why a
     * condition that comes and goes still eventually operates the relay.
     */
    private fun advance(r: Relay, dt: Double, condition: Boolean, rate: Double) {
        r.pickedUp = condition
        r.travel = if (condition && r.delayS > 0.0) {
            (r.travel + dt / r.delayS * max(rate, 0.05)).coerceAtMost(1.2)
        } else {
            max(0.0, r.travel - dt / max(r.delayS, 0.5) * 0.55)
        }
    }

    private fun operate(r: Relay) {
        r.travel = 0.0
        r.target = true
        r.operatedThisStep = true
        trippedBy = r
    }

    /** Reset one dropped target by hand. Returns true if there was one to reset. */
    fun resetTarget(r: Relay): Boolean {
        if (!r.target) return false
        r.target = false
        r.travel = 0.0
        return true
    }

    fun reset() {
        for (r in relays) r.reset()
        trippedBy = null
    }
}
