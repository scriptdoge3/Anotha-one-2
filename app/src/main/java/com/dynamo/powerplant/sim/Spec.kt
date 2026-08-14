package com.dynamo.powerplant.sim

/**
 * Nameplate data for the Fairfield-Wheelock horizontal gasoline engine direct
 * coupled to a 12-pole, 2300 volt, 60 cycle alternator rated 500 kilowatts.
 *
 * 12 poles at 600 rpm gives exactly 60 cycles, so frequency in hertz is simply
 * rpm / 10 throughout the simulation.
 */
object Spec {
    const val POLES = 12
    const val RATED_RPM = 600.0
    const val RATED_HZ = 60.0
    const val RATED_VOLTS = 2300.0
    const val RATED_KW = 500.0

    /** Combined flywheel and rotor inertia, kg*m^2. */
    const val INERTIA = 2700.0

    /** Peak indicated torque at wide-open throttle under ideal conditions, N*m. */
    const val PEAK_TORQUE = 8600.0

    /** Six cylinders, four stroke: three power strokes per crankshaft revolution. */
    const val CYLINDERS = 6
    const val FIRINGS_PER_REV = 3.0

    /** Synchronous reactance in per-unit. Sets the pull-out power and tie stiffness. */
    const val XS_PU = 0.95

    /** Torque the starting motor can put on the shaft at rest, N*m. */
    const val STARTER_TORQUE = 14_000.0
    const val STARTER_STALL_RPM = 320.0

    /** Mechanical damage thresholds. */
    const val OVERSPEED_RPM = 790.0
    const val BURST_RPM = 940.0
    const val SEIZE_BEARING_C = 205.0
    const val SEIZE_JACKET_C = 132.0

    /** Full load stator current, amperes, three phase. */
    val RATED_AMPS: Double get() = RATED_KW * 1000.0 / (RATED_VOLTS * 1.7320508)

    fun rpmToHz(rpm: Double) = rpm / 10.0
    fun hzToRpm(hz: Double) = hz * 10.0

    fun ratedTorque(): Double {
        val omega = RATED_RPM * Math.PI / 30.0
        return RATED_KW * 1000.0 / omega
    }
}
