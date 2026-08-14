package com.dynamo.powerplant.sim

/**
 * The combination key switch, wired exactly as the one on a Model T: a single
 * rotary with five positions in this physical order.
 *
 *     BAT - DIM - OFF - ON - MAG
 *
 * BAT and MAG are the two ignition sources. DIM and ON are lighting positions
 * that feed the panel lamps off the battery and give no spark at all. OFF is
 * dead.
 *
 * The consequence of that layout is the whole point: you start on BAT, and to
 * get across to MAG you must sweep the key through DIM, OFF and ON, three
 * positions with no ignition. Dawdle and the engine stumbles or dies.
 *
 * The battery gives a fat spark at cranking speed but the trembler coil runs
 * out of dwell as revolutions rise. The magneto gives nothing at rest and
 * strengthens with speed. That is why the engine starts on one and runs on the
 * other.
 */
enum class IgnitionMode(
    val label: String,
    /** Does this position feed the plugs? */
    val ignites: Boolean,
    /** Panel lamp brightness drawn from the battery, 0..1. */
    val panelLamps: Double,
    /** Battery current drawn by the lamps, per second of charge. */
    val lampDrain: Double
) {
    BAT("BAT", true, 0.0, 0.0),
    DIM("DIM", false, 0.38, 0.0035),
    OFF("OFF", false, 0.0, 0.0),
    ON("ON", false, 1.0, 0.0092),
    MAG("MAG", true, 0.0, 0.0);

    /** One notch clockwise, towards MAG. Cannot jump positions. */
    fun clockwise() = entries[(ordinal + 1).coerceAtMost(entries.size - 1)]

    /** One notch anticlockwise, towards BAT. */
    fun anticlockwise() = entries[(ordinal - 1).coerceAtLeast(0)]
}

/** Every hand control on the board. Nothing here is automatic. */
class Controls {
    // --- the five mains ---
    var ignition: IgnitionMode = IgnitionMode.OFF
    /** 0..1 throttle. There is no governor; this is the only speed control. */
    var throttle: Double = 0.0
    /** 0..1 spark lever, maps to -5 deg (retard) .. +38 deg BTDC (advance). */
    var sparkLever: Double = 0.5
    /** 0..1 mixture needle, 0 = lean (17.5:1), 1 = rich (9.5:1) as supplied. */
    var mixture: Double = 0.5
    /** 0..1 field rheostat on the exciter. */
    var excitation: Double = 0.0

    // --- starting gear ---
    var compressionRelease: Boolean = false
    var primerCharges: Int = 0

    // --- auxiliaries ---
    /** 0..1 cooling water gate valve. */
    var waterValve: Double = 0.0
    /** 0..1 mechanical lubricator drip rate. */
    var oilerRate: Double = 0.0

    // --- switchboard ---
    var mainBreakerClosed: Boolean = false
    var fieldSwitchClosed: Boolean = true
    /**
     * Main Street and the Ice House are already alive on Willow Creek's supply
     * when you take over the shift; the mill and the railway are yours to pick up.
     */
    val feederClosed = booleanArrayOf(true, false, true, false)

    fun sparkAdvanceDeg(): Double = -5.0 + sparkLever * 43.0
    fun airFuelRatio(): Double = 17.5 - mixture * 8.0
}
