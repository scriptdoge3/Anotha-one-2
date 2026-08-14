package com.dynamo.powerplant.sim

/**
 * Nameplate data for the Fairfield-Wheelock 2-cylinder horizontal gasoline engine
 * direct-coupled to a 12-pole, 2300 volt, 60 cycle alternating current generator.
 *
 * 12 poles at 600 rpm gives exactly 60 cycles, so frequency in hertz is simply
 * rpm / 10 throughout the simulation.
 */
object Spec {
    const val POLES = 12
    const val RATED_RPM = 600.0
    const val RATED_HZ = 60.0
    const val RATED_VOLTS = 2300.0
    const val RATED_KW = 120.0

    /** Combined flywheel + rotor inertia, kg*m^2. Big enough to thump, small enough to hurt. */
    const val INERTIA = 640.0

    /** Peak indicated torque at wide-open throttle under ideal conditions, N*m. */
    const val PEAK_TORQUE = 2050.0

    /** Torque the starting motor can put on the shaft at rest, N*m. */
    const val STARTER_TORQUE = 2600.0
    const val STARTER_STALL_RPM = 300.0

    /** Torque a strong pair of arms can put on the starting crank, N*m. */
    const val CRANK_TORQUE = 950.0

    /** Cylinders firing per crankshaft revolution (2 cylinders, 4 stroke). */
    const val FIRINGS_PER_REV = 1.0

    /** Synchronous reactance in per-unit. Sets the pull-out power and how stiff the tie is. */
    const val XS_PU = 0.95

    /** Mechanical damage thresholds. */
    const val OVERSPEED_RPM = 790.0
    const val BURST_RPM = 940.0
    const val SEIZE_BEARING_C = 205.0
    const val SEIZE_JACKET_C = 132.0

    fun rpmToHz(rpm: Double) = rpm / 10.0
    fun hzToRpm(hz: Double) = hz * 10.0

    fun ratedTorque(): Double {
        val omega = RATED_RPM * Math.PI / 30.0
        return RATED_KW * 1000.0 / omega
    }
}
