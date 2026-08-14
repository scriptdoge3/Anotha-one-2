package com.dynamo.powerplant.sim

/**
 * The station service selector: one rotary carrying four positions in this
 * physical order.
 *
 *     GRID - GEN - OFF - EMG
 *
 * It decides where the ignition and the panel lamps are fed from, and each
 * source behaves differently:
 *
 *  GRID  station service tapped off the town bus. Full and steady at any speed,
 *        but it dies with the bus, so a collapse takes your ignition with it.
 *  GEN   the machine's own shaft-driven exciter. Nothing at rest, strengthening
 *        with speed. Self sufficient, and where you want to be once running.
 *  OFF   dead.
 *  EMG   the emergency battery. A fat spark at cranking speed that fades as the
 *        revolutions rise, and it is the only position that will turn the
 *        starting motor. It also flattens the cells.
 *
 * The layout is the point: EMG is where you start and GEN is where you run, and
 * getting between them means passing through OFF with no ignition at all.
 */
enum class IgnitionMode(
    val label: String,
    /** Does this position feed the plugs? */
    val ignites: Boolean,
    /** How brightly this source lights the panel lamps, 0..1. */
    val panelLamps: Double
) {
    GRID("GRID", true, 0.85),
    GEN("GEN", true, 1.00),
    OFF("OFF", false, 0.0),
    EMG("EMG", true, 0.45);

    /** One notch clockwise, towards EMG. Cannot jump positions. */
    fun clockwise() = entries[(ordinal + 1).coerceAtMost(entries.size - 1)]

    /** One notch anticlockwise, towards GRID. */
    fun anticlockwise() = entries[(ordinal - 1).coerceAtLeast(0)]
}

/** Every hand control on the board. Nothing here is automatic. */
class Controls {
    // --- the five mains ---
    var ignition: IgnitionMode = IgnitionMode.OFF
    /**
     * 0..1 throttle. There is no governor; this is the only speed control.
     *
     * The five mains are left where the day man had them when he shut down: a
     * fast idle, a rich needle and a retarded spark, which is how you leave a
     * cold engine that somebody else has to start. Bring the throttle back as
     * the jacket warms or it will run away.
     */
    var throttle: Double = 0.45
    /** 0..1 spark lever, maps to -5 deg (retard) .. +38 deg BTDC (advance). */
    var sparkLever: Double = 0.30
    /** 0..1 mixture needle, 0 = lean (17.5:1), 1 = rich (9.5:1) as supplied. */
    var mixture: Double = 0.80
    /** 0..1 field rheostat on the exciter. */
    var excitation: Double = 0.0

    // --- starting gear ---
    var compressionRelease: Boolean = false
    var primerCharges: Int = 0

    // --- auxiliaries ---
    /** 0..1 cooling water gate valve. */
    var waterValve: Double = 0.35
    /** 0..1 mechanical lubricator drip rate. */
    var oilerRate: Double = 0.45

    // --- switchboard: the plant's own internal supplies ---
    /** The unit breaker, between the main transformer and the grid. */
    var mainBreakerClosed: Boolean = false
    var fieldSwitchClosed: Boolean = true
    /**
     * The internal loads on the station service bus, in the order they sit on the
     * board: ignition, cooling water pump, battery charger, house lights. The
     * ignition and the pump are left in at handover; the charger and the lights
     * are your business, and the cells will not carry all four at once.
     */
    val auxClosed = booleanArrayOf(true, true, false, false)

    fun sparkAdvanceDeg(): Double = -5.0 + sparkLever * 43.0
    fun airFuelRatio(): Double = 17.5 - mixture * 8.0
}
