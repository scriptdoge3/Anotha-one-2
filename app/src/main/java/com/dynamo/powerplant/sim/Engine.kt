package com.dynamo.powerplant.sim

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * The gasoline engine: combustion, ignition, cooling water jacket, and the
 * mechanical lubricator. Produces a mean shaft torque; the Plant integrates it.
 *
 * Deliberately free of any governor. The player is the governor.
 */
class Engine(private val rnd: Random = Random(0xC0FFEE)) {

    // --- thermal / lube state ---
    var jacketTempC: Double = 12.0      // starts stone cold on a winter morning
    var bearingTempC: Double = 12.0
    var oilFilm: Double = 1.0           // 1 = fully wetted, 0 = metal to metal
    var oilInSump: Double = 1.0         // 0..1 reservoir remaining
    var oilPressureKpa: Double = 0.0

    // --- ignition / fuelling state ---
    var batteryCharge: Double = 1.0     // 0..1
    var plugFouling: Double = 0.0       // 0..1, cuts spark energy
    var floodLevel: Double = 0.0        // 0..1, over-priming
    var firingSuccess: Double = 0.0     // smoothed fraction of charges that light

    // --- wear / damage accumulators ---
    var knockIndex: Double = 0.0        // instantaneous detonation intensity 0..1
    var knockDamage: Double = 0.0       // 0..1 cumulative, 1 = holed piston
    var bearingWear: Double = 0.0       // 0..1 cumulative, 1 = thrown rod

    // --- starter ---
    var starterEngaged: Boolean = false
    var starterCranking: Boolean = false
    var starterHeat: Double = 0.0

    // --- observable one-shot events, consumed by audio/visuals ---
    var firedThisStep: Boolean = false
    var misfiredThisStep: Boolean = false
    var backfiredThisStep: Boolean = false
    var kickbackThisStep: Boolean = false

    var running: Boolean = false
        private set

    private var firingPhase: Double = 0.0
    private var revsSincePrime: Double = 0.0

    val ambientC = 8.0

    /** Spark energy 0..1 available at the plug for the given key position and speed. */
    fun sparkEnergy(ctl: Controls, rpm: Double): Double {
        val raw = when (ctl.ignition) {
            IgnitionMode.MAG -> magnetoOutput(rpm)
            IgnitionMode.BAT -> batteryOutput(rpm)
            else -> 0.0     // DIM, OFF and ON are lighting positions, no spark
        }
        return clamp(raw * (1.0 - 0.85 * plugFouling), 0.0, 1.0)
    }

    /** The flywheel magneto: dead at rest, strengthening with speed. */
    fun magnetoOutput(rpm: Double): Double {
        val t = clamp((rpm - 55.0) / 165.0, 0.0, 1.0)
        return clamp(t * t * (3 - 2 * t) * 1.05, 0.0, 1.0)
    }

    /** The coil box off the battery: fat at cranking speed, fading as revs rise. */
    fun batteryOutput(rpm: Double): Double {
        val speedFade = clamp(1.0 - max(0.0, rpm - 430.0) / 620.0, 0.0, 1.0)
        return clamp(batteryCharge * speedFade, 0.0, 1.0)
    }

    /**
     * What actually ends up in the cylinder, as opposed to what the needle valve
     * supplies. On cold iron a good part of the gasoline condenses on the port
     * walls and never burns, so the charge arrives leaner than it was metered.
     * That is why a cold engine needs the mixture rich and the primer used.
     */
    fun inCylinderAfr(suppliedAfr: Double, jacket: Double, primeActive: Boolean, flood: Double): Double {
        val coldFactor = 0.50 * clamp((50.0 - jacket) / 42.0, 0.0, 1.0)
        var afr = suppliedAfr * (1.0 + coldFactor)
        if (primeActive) afr *= 0.78
        afr *= (1.0 - flood * 0.42)
        return clamp(afr, 4.0, 26.0)
    }

    /** Ideal spark advance in degrees BTDC for this speed and charge. */
    fun optimalAdvanceDeg(rpm: Double, afr: Double): Double {
        val base = 6.0 + rpm * 0.0335
        // A lean charge burns slowly and wants more lead; a rich one burns fast.
        return base + (afr - 12.5) * 0.55
    }

    /** Combustion efficiency 0..1 for the charge actually present in the cylinder. */
    fun mixtureEfficiency(inCylAfr: Double): Double =
        clamp(exp(-sq((inCylAfr - 12.5) / 2.80)), 0.0, 1.0)

    fun sparkEfficiency(advanceDeg: Double, optimalDeg: Double): Double {
        val err = advanceDeg - optimalDeg
        return clamp(1.0 - sq(err / 21.0), 0.0, 1.0)
    }

    /** Friction and pumping torque, N*m. Compression release removes the pumping work. */
    fun dragTorque(rpm: Double, ctl: Controls): Double {
        val comp = if (ctl.compressionRelease) 0.12 else 1.0
        val pumping = 170.0 * comp
        val viscous = 0.28 * rpm + 0.00045 * rpm * rpm      // bearings plus windage
        val dryPenalty = (1.0 - oilFilm) * (170.0 + 0.55 * rpm)
        return pumping + viscous + dryPenalty
    }

    /**
     * How much of the man on the crank handle actually reaches the flywheel.
     * With the compression release shut he stalls against the compression stroke
     * and gets nowhere until the flywheel already has some speed in it.
     */
    fun crankEffectiveness(rpm: Double, ctl: Controls): Double =
        if (ctl.compressionRelease) 1.0 else clamp(rpm / 110.0, 0.22, 1.0)

    /**
     * Advance the engine one step and return net mean shaft torque in N*m
     * (positive drives the flywheel).
     */
    fun step(dt: Double, ctl: Controls, rpm: Double): Double {
        firedThisStep = false
        misfiredThisStep = false
        backfiredThisStep = false
        kickbackThisStep = false

        val afr = ctl.airFuelRatio()
        val advance = ctl.sparkAdvanceDeg()
        val energy = sparkEnergy(ctl, rpm)

        // ---- charge preparation -------------------------------------------------
        // Idle bleed means the throttle is never fully shut off.
        val airflow = 0.055 + 0.945 * Math.pow(clamp(ctl.throttle, 0.0, 1.0), 1.25)
        val primeActive = ctl.primerCharges > 0
        val effectiveAfr = inCylinderAfr(afr, jacketTempC, primeActive, floodLevel)

        val mixEff = mixtureEfficiency(effectiveAfr)
        val sparkEff = sparkEfficiency(advance, optimalAdvanceDeg(rpm, effectiveAfr))
        // Cold iron soaks up heat that should have gone into the piston, so an
        // engine run with the water gate wide open is down on power all night.
        val coldEff = clamp(0.70 + jacketTempC / 250.0, 0.0, 1.0)

        // ---- will the charge actually light? -----------------------------------
        val lightable = energy > 0.16 && mixEff > 0.12 && !ctl.compressionRelease && floodLevel < 0.85
        val ignitionQuality = if (lightable) clamp(energy * 1.35, 0.0, 1.0) * clamp(mixEff * 1.6, 0.0, 1.0) else 0.0

        // Count discrete firing events for sound and for the exhaust beat.
        firingPhase += rpm / 60.0 * Spec.FIRINGS_PER_REV * dt
        var events = 0
        while (firingPhase >= 1.0) {
            firingPhase -= 1.0
            events++
        }
        if (events > 0) {
            val lit = rnd.nextDouble() < ignitionQuality
            if (lit) firedThisStep = true else misfiredThisStep = true
            // Unburnt charge in a hot exhaust pipe eventually goes off with a bang.
            if (!lit && lightable && jacketTempC > 60.0 && rnd.nextDouble() < 0.05) backfiredThisStep = true
            // A squirt of raw gasoline is good for about a dozen charges.
            revsSincePrime += events.toDouble()
            if (revsSincePrime >= 12.0) {
                revsSincePrime = 0.0
                if (ctl.primerCharges > 0) ctl.primerCharges--
            }
        }

        firingSuccess += (ignitionQuality - firingSuccess) * min(1.0, dt * 6.0)

        // ---- indicated torque ---------------------------------------------------
        val speedCurve = torqueCurve(rpm)
        var torque = Spec.PEAK_TORQUE * airflow * mixEff * sparkEff * speedCurve * firingSuccess * coldEff
        torque *= clamp(1.0 - knockDamage * 0.75, 0.15, 1.0)
        // A boiling jacket loses charge density and starts to lose power.
        if (jacketTempC > 100.0) torque *= clamp(1.0 - (jacketTempC - 100.0) / 55.0, 0.25, 1.0)

        // ---- kickback -----------------------------------------------------------
        // Firing well before top dead centre at cranking speed drives the crank backwards.
        if (firedThisStep && rpm < 165.0 && advance > 14.0 && !ctl.compressionRelease) {
            val severity = clamp((advance - 14.0) / 24.0, 0.0, 1.0) * clamp(1.0 - rpm / 165.0, 0.0, 1.0)
            if (severity > 0.18) {
                kickbackThisStep = true
                torque = -Spec.PEAK_TORQUE * 0.85 * severity
            }
        }

        // ---- starting motor -----------------------------------------------------
        // Wired through the BAT position only, exactly like the switch it hangs off.
        starterCranking = starterEngaged &&
            ctl.ignition == IgnitionMode.BAT &&
            batteryCharge > 0.04 &&
            rpm < Spec.STARTER_STALL_RPM
        if (starterCranking) {
            val avail = clamp(batteryCharge * (1.0 - starterHeat * 0.75), 0.0, 1.0)
            torque += Spec.STARTER_TORQUE * avail * clamp(1.0 - rpm / Spec.STARTER_STALL_RPM, 0.0, 1.0)
            batteryCharge = max(0.0, batteryCharge - dt * 0.032)
            starterHeat = min(1.0, starterHeat + dt * 0.075)
        } else {
            starterHeat = max(0.0, starterHeat - dt * 0.045)
        }

        // ---- battery housekeeping ----------------------------------------------
        if (ctl.ignition == IgnitionMode.BAT) {
            batteryCharge = max(0.0, batteryCharge - dt * 0.0022 * (1.0 + rpm / 600.0))
        }
        // The panel lamps on DIM and ON come straight off the cells too.
        if (ctl.ignition.lampDrain > 0.0) {
            batteryCharge = max(0.0, batteryCharge - dt * ctl.ignition.lampDrain)
        }
        if (rpm > 340.0) {
            batteryCharge = min(1.0, batteryCharge + dt * 0.010 * clamp((rpm - 340.0) / 260.0, 0.0, 1.0))
        }

        // ---- flooding -----------------------------------------------------------
        if (ctl.primerCharges > 3) floodLevel = min(1.0, floodLevel + dt * 0.30 * (ctl.primerCharges - 3))
        // Cranking on a wide throttle with no spark clears a flooded cylinder.
        val clearing = if (energy < 0.16 && ctl.throttle > 0.8 && rpm > 40.0) 0.35 else 0.06
        floodLevel = max(0.0, floodLevel - dt * clearing * (1.0 + rpm / 300.0))

        stepThermal(dt, ctl, rpm, effectiveAfr, advance, torque)
        stepLubrication(dt, ctl, rpm, torque)
        stepKnock(dt, ctl, rpm, effectiveAfr, advance)

        running = rpm > 150.0 && firingSuccess > 0.22

        return torque - dragTorque(rpm, ctl)
    }

    /**
     * A big slow-turning engine pulls hard from very low revolutions; the curve is
     * a broad plateau, not a peak. It keeps making torque well past rated speed,
     * which is exactly why throwing the load off is so dangerous.
     */
    private fun torqueCurve(rpm: Double): Double {
        if (rpm < 30.0) return 0.0
        val ramp = clamp((rpm - 45.0) / 105.0, 0.0, 1.0)
        val x = rpm / Spec.RATED_RPM
        val bell = 1.18 * exp(-sq((x - 1.0) / 1.15))
        return clamp(ramp * bell, 0.0, 1.25)
    }

    private fun stepThermal(dt: Double, ctl: Controls, rpm: Double, afr: Double, advance: Double, torque: Double) {
        // Heat into the iron follows the fuel burned, as a fraction of full load.
        val burnPu = max(0.0, torque) / Spec.PEAK_TORQUE * (rpm / Spec.RATED_RPM)
        // A lean charge and a retarded spark both dump extra heat into the jacket.
        val leanHeat = clamp((afr - 13.5) / 5.0, 0.0, 1.0) * 1.45
        val retardHeat = clamp((optimalAdvanceDeg(rpm, afr) - advance) / 26.0, 0.0, 1.0) * 1.15
        val heatIn = burnPu * 3.2 * (1.0 + leanHeat + retardHeat) + 0.04

        // Water jacket. With the gate shut there is only convection to the engine room,
        // and a hard-working engine will cook itself in a couple of minutes.
        val flow = clamp(ctl.waterValve, 0.0, 1.0)
        val cooling = (0.05 + 1.30 * flow) * (jacketTempC - ambientC) * 0.0535
        jacketTempC += (heatIn - cooling) * dt
        jacketTempC = max(ambientC, jacketTempC)

        // Bearings pick up jacket heat plus whatever the oil film fails to carry away.
        val frictionHeat = (1.0 - oilFilm) * (2.4 + rpm * 0.022) + rpm * 0.0016
        val bearingCool = (bearingTempC - jacketTempC * 0.55 - ambientC * 0.45) * 0.055
        bearingTempC += (frictionHeat - bearingCool) * dt
        bearingTempC = max(ambientC, bearingTempC)

        // A rich charge lays carbon on the plugs, and so does a lubricator run
        // wide open, which puts more oil up the bore than the rings can scrape off.
        val fouling = clamp((11.4 - afr) / 3.0, 0.0, 1.0) * 0.030 +
            clamp((ctl.oilerRate - 0.88) / 0.12, 0.0, 1.0) * 0.020
        // A hot engine on a sensible mixture burns the deposit off again.
        val cleaning = if (afr > 12.2 && jacketTempC > 70.0 && rpm > 350.0) 0.012 else 0.0
        plugFouling = clamp(plugFouling + (fouling - cleaning) * dt, 0.0, 1.0)
    }

    private fun stepLubrication(dt: Double, ctl: Controls, rpm: Double, torque: Double) {
        if (ctl.oilerRate > 0.0 && oilInSump > 0.0) {
            oilInSump = max(0.0, oilInSump - dt * ctl.oilerRate * 0.0043)
        }
        val supply = if (oilInSump > 0.0) ctl.oilerRate else 0.0
        // Demand climbs with speed and with the load being carried.
        val demand = clamp(rpm / 600.0 * 0.55 + abs(torque) / Spec.PEAK_TORQUE * 0.42, 0.0, 1.6)
        val deficit = demand - supply
        val rate = if (deficit > 0) deficit * 0.30 else deficit * 0.55
        oilFilm = clamp(oilFilm - rate * dt, 0.0, 1.0)

        oilPressureKpa = clamp(oilFilm * (28.0 + rpm * 0.30), 0.0, 240.0)

        if (oilFilm < 0.35 && rpm > 60.0) {
            bearingWear = min(1.0, bearingWear + dt * (0.35 - oilFilm) * 0.30)
        }
        if (bearingTempC > 150.0) {
            bearingWear = min(1.0, bearingWear + dt * (bearingTempC - 150.0) * 0.0055)
        }
    }

    private fun stepKnock(dt: Double, ctl: Controls, rpm: Double, afr: Double, advance: Double) {
        val over = max(0.0, advance - optimalAdvanceDeg(rpm, afr))
        val lean = clamp((afr - 14.2) / 4.0, 0.0, 1.0)
        val hot = clamp((jacketTempC - 96.0) / 34.0, 0.0, 1.0)
        val load = clamp(ctl.throttle, 0.0, 1.0)
        knockIndex = clamp((over / 15.0) * 0.55 + lean * 0.42 + hot * 0.55, 0.0, 1.0) * load * clamp(rpm / 300.0, 0.0, 1.0)
        if (knockIndex > 0.30) {
            knockDamage = min(1.0, knockDamage + dt * (knockIndex - 0.30) * 0.115)
        }
    }

    fun reset() {
        jacketTempC = 12.0; bearingTempC = 12.0
        oilFilm = 1.0; oilInSump = 1.0; oilPressureKpa = 0.0
        batteryCharge = 1.0; plugFouling = 0.0; floodLevel = 0.0; firingSuccess = 0.0
        knockIndex = 0.0; knockDamage = 0.0; bearingWear = 0.0
        starterEngaged = false; starterCranking = false; starterHeat = 0.0
        firingPhase = 0.0; revsSincePrime = 0.0; running = false
    }
}

internal fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))
internal fun sq(v: Double) = v * v
