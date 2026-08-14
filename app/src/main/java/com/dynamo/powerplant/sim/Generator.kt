package com.dynamo.powerplant.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The alternator and its field.
 *
 * The field is fed from the emergency line, along with the ignition, so the two
 * things the set cannot run without hang off the same switchboard. Lose that
 * line and the machine loses its excitation with everything else.
 *
 * Off the bus, terminal volts are simply the open-circuit EMF and the machine
 * costs the engine almost nothing. On the bus, the classic power-angle model
 * applies: real power out is set by the rotor angle (and therefore by the
 * throttle), while excitation sets the internal EMF and therefore the vars.
 */
class Generator {

    companion object {
        /**
         * Damper cage strength, watts per radian per second of slip. Sized for
         * about 40 percent damping on the natural swing of this machine so it
         * pulls into step instead of hunting all night.
         */
        const val DAMPING_W_PER_RAD = 239_000.0
    }

    /** Rotor angle relative to the bus reference, radians. Only meaningful when tied on. */
    var delta: Double = 0.0

    /** Field build-up lags the rheostat; the exciter has real inductance. */
    var fieldFlux: Double = 0.0

    var realPowerW: Double = 0.0
    var reactivePowerVar: Double = 0.0
    var terminalVolts: Double = 0.0
    var lineAmps: Double = 0.0

    /** Set for one step when the machine slips a pole. */
    var poleSlipThisStep: Boolean = false

    /** Saturation curve: rheostat 0.60 gives rated volts, 1.00 gives 1.25 pu. */
    fun fieldSaturation(x: Double): Double {
        val v = clamp(x, 0.0, 1.0)
        return 3.3333 * v / (1.0 + 1.6667 * v)
    }

    /** Open-circuit EMF in volts for this field setting and speed. */
    fun emf(rpm: Double): Double {
        val residual = 0.028
        val speed = rpm / Spec.RATED_RPM
        return (fieldSaturation(fieldFlux) + residual) * speed * Spec.RATED_VOLTS
    }

    /**
     * @param supplyPu volts on the emergency line feeding the field, 0 if the
     *   excitation switch is out or the line is dead. The rheostat can only ask
     *   for what the line is actually holding up.
     */
    fun stepField(dt: Double, ctl: Controls, rpm: Double, supplyPu: Double) {
        val target = clamp(ctl.excitation, 0.0, 1.0) * clamp(supplyPu, 0.0, 1.0)
        // The field winding has real inductance, so it takes a moment either way.
        fieldFlux += (target - fieldFlux) * clamp(dt * 1.15, 0.0, 1.0)
    }

    /**
     * Electrical torque opposing the engine, N*m. Zero when the breaker is open.
     * Also updates the metered quantities.
     */
    fun stepElectrical(dt: Double, ctl: Controls, rpm: Double, bus: Grid): Double {
        poleSlipThisStep = false
        val omega = rpm * PI / 30.0
        val e = emf(rpm)

        if (!ctl.mainBreakerClosed) {
            delta = 0.0
            realPowerW = 0.0
            reactivePowerVar = 0.0
            terminalVolts = e
            lineAmps = 0.0
            // Windage plus the exciter's own draw.
            return 0.6 * fieldFlux * rpm * 0.02
        }

        val v = bus.volts
        val xs = Spec.XS_PU * (Spec.RATED_VOLTS * Spec.RATED_VOLTS) / (Spec.RATED_KW * 1000.0)

        // Swing: the rotor angle advances at the difference between machine and bus.
        val slipRad = (rpm - Spec.hzToRpm(bus.hz)) * PI / 30.0
        delta += slipRad * dt

        if (abs(delta) > PI) {
            // Past 180 degrees the machine has slipped a pole. Violent, and it is over.
            poleSlipThisStep = true
            delta -= Math.copySign(2 * PI, delta)
        }

        var p = if (xs > 0) e * v / xs * sin(delta) else 0.0
        val q = if (xs > 0) (e * v * cos(delta) - v * v) / xs else 0.0

        // Damper windings. Any slip against the bus induces currents in the rotor
        // cage that oppose it, which is what stops a synchronous machine hunting.
        val dampW = DAMPING_W_PER_RAD * slipRad
        p += dampW

        realPowerW = p
        reactivePowerVar = q
        terminalVolts = v
        val s = Math.hypot(p, q)
        lineAmps = if (v > 1.0) s / (v * 1.7320508) else 0.0

        return if (omega > 1.0) p / omega else 0.0
    }

    /** Pull-out power for the present field: the most this machine can hold. */
    fun pullOutW(rpm: Double, busVolts: Double): Double {
        val xs = Spec.XS_PU * (Spec.RATED_VOLTS * Spec.RATED_VOLTS) / (Spec.RATED_KW * 1000.0)
        return emf(rpm) * busVolts / xs
    }

    fun reset() {
        delta = 0.0; fieldFlux = 0.0
        realPowerW = 0.0; reactivePowerVar = 0.0
        terminalVolts = 0.0; lineAmps = 0.0; poleSlipThisStep = false
    }
}
