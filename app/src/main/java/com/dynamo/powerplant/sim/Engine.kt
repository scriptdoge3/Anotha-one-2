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

    /** Volts on the grid, per unit, for the GRID position of the selector. */
    var busSupplyPu: Double = 1.0

    /** Volts on the main bus, per unit, for the GEN position of the selector. */
    var mainBusPu: Double = 0.0

    /**
     * Volts on the emergency line, per unit, and whether the ignition is
     * actually switched in and running. Both are set by the Plant from the
     * switchboard each step.
     */
    var serviceVolts: Double = 0.0
    var ignitionLive: Boolean = true

    /**
     * How much cooling water is being circulated, 0..1, before the gate valve
     * meters it. The big circulating pump on the main bus gives full flow; the
     * emergency pump on the battery's line gives enough to nurse the engine and
     * no more.
     */
    var coolantFlowPu: Double = 0.0

    /** The forced-feed oil pump on the main bus, behind the mechanical lubricator. */
    var oilPumpRunning: Boolean = false
    /** Generator terminal volts, per unit: what the emergency transformer sees. */
    var genTerminalPu: Double = 0.0
    /** Kilowatts the emergency line is drawing, for the battery drain. */
    var serviceDrawKw: Double = 0.0

    // --- starter ---
    var starterEngaged: Boolean = false
    var starterCranking: Boolean = false
    /** True while the cells are being put back, for the mimic diagram. */
    var batteryChargingNow: Boolean = false
    var starterHeat: Double = 0.0

    // --- observable one-shot events, consumed by audio/visuals ---
    var firedThisStep: Boolean = false
    var misfiredThisStep: Boolean = false
    var backfiredThisStep: Boolean = false

    var running: Boolean = false
        private set

    private var firingPhase: Double = 0.0
    private var revsSincePrime: Double = 0.0

    val ambientC = 8.0

    /**
     * Spark energy 0..1 at the plug. The coils are fed from the emergency line,
     * so the selector chooses the source and the switchboard decides whether the
     * ignition is switched in and whether the line is holding its volts.
     */
    fun sparkEnergy(ctl: Controls, rpm: Double): Double {
        if (!ignitionLive) return 0.0
        var e = serviceVolts
        // Fed from the cells it is a coil box, and the coil runs out of dwell as
        // the revolutions rise. Fed from the bus it is a proper transformer set.
        if (ctl.ignition == IgnitionMode.EMG) e *= coilDwellFade(rpm)
        return clamp(e * (1.0 - 0.85 * plugFouling), 0.0, 1.0)
    }

    /** How well the battery coil box keeps up as the engine speeds up. */
    fun coilDwellFade(rpm: Double): Double =
        clamp(1.0 - max(0.0, rpm - 430.0) / 620.0, 0.0, 1.0)

    /** What the selected source is worth right now, at the emergency line. */
    fun sourceStrength(ctl: Controls, rpm: Double): Double = when (ctl.ignition) {
        IgnitionMode.GRID -> gridSupply()
        IgnitionMode.GEN -> generatorSupply(rpm)
        // The cells only reach the line through the battery breaker.
        IgnitionMode.EMG -> if (ctl.batteryBreakerClosed) emergencySupply(rpm) else 0.0
        IgnitionMode.OFF -> 0.0
    }

    /**
     * Off the grid bus, down through the starting transformer. Full and steady
     * whatever the engine is doing, so long as the bus is healthy. It fades as
     * the bus volts sag, which is the trap: the ignition goes weak exactly when
     * the system is in trouble and you most need the engine.
     */
    fun gridSupply(): Double = clamp((busSupplyPu - 0.62) / 0.33, 0.0, 1.0)

    /**
     * The tie across to the main bus, which is the machine carrying its own
     * emergency line. Dead until the machine is excited and turning, which is
     * why this position cannot start the engine and is the right place to run
     * it — nothing outside the station can take it away.
     *
     * The circle closes on itself: the field is a load on the line the main bus
     * is holding up. That is why the changeover from the battery has to be made
     * briskly, before the field decays past the point where the machine can
     * carry itself.
     */
    fun generatorSupply(@Suppress("UNUSED_PARAMETER") rpm: Double): Double = clamp(mainBusPu, 0.0, 1.0)

    /**
     * Battery terminal volts, per unit. A lead cell holds close to its nominal
     * voltage almost all the way down and then falls off a cliff, so state of
     * charge is not the same thing as how good the supply is.
     */
    fun emergencySupply(rpm: Double): Double {
        val steady = 1.02 - 0.10 * (1.0 - batteryCharge)
        val cliff = 0.95 * max(0.0, 0.18 - batteryCharge) / 0.18
        return clamp(steady - cliff, 0.0, 1.0)
    }

    /**
     * What actually ends up in the cylinder, as opposed to what the needle valve
     * supplies. On cold iron a good part of the gasoline condenses on the port
     * walls and never burns, so the charge arrives leaner than it was metered.
     * That is why a cold engine needs the mixture rich and the primer used.
     */
    fun inCylinderAfr(suppliedAfr: Double, jacket: Double, primeActive: Boolean, flood: Double): Double {
        val coldFactor = 0.38 * clamp((50.0 - jacket) / 42.0, 0.0, 1.0)
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
        clamp(exp(-sq((inCylAfr - 12.5) / 3.10)), 0.0, 1.0)

    fun sparkEfficiency(advanceDeg: Double, optimalDeg: Double): Double {
        val err = advanceDeg - optimalDeg
        return clamp(1.0 - sq(err / 21.0), 0.0, 1.0)
    }

    /** Friction and pumping torque, N*m. Compression release removes the pumping work. */
    fun dragTorque(rpm: Double, ctl: Controls): Double {
        val comp = if (ctl.compressionRelease) 0.12 else 1.0
        val pumping = 560.0 * comp
        val viscous = 1.17 * rpm + 0.00188 * rpm * rpm      // bearings plus windage
        val dryPenalty = (1.0 - oilFilm) * (560.0 + 2.30 * rpm)
        return pumping + viscous + dryPenalty
    }

    /**
     * Advance the engine one step and return net mean shaft torque in N*m
     * (positive drives the flywheel).
     */
    fun step(dt: Double, ctl: Controls, rpm: Double): Double {
        firedThisStep = false
        misfiredThisStep = false
        backfiredThisStep = false

        val afr = ctl.airFuelRatio()
        val advance = ctl.sparkAdvanceDeg()
        val energy = sparkEnergy(ctl, rpm)

        // ---- charge preparation -------------------------------------------------
        // Idle bleed means the throttle is never fully shut off.
        val airflow = 0.085 + 0.915 * Math.pow(clamp(ctl.throttle, 0.0, 1.0), 1.25)
        val primeActive = ctl.primerCharges > 0
        val effectiveAfr = inCylinderAfr(afr, jacketTempC, primeActive, floodLevel)

        val mixEff = mixtureEfficiency(effectiveAfr)
        val sparkEff = sparkEfficiency(advance, optimalAdvanceDeg(rpm, effectiveAfr))
        // Cold iron soaks up heat that should have gone into the piston, so an
        // engine run with the water gate wide open is down on power all night.
        val coldEff = clamp(0.86 + jacketTempC / 450.0, 0.0, 1.0)

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

        // ---- starting motor -----------------------------------------------------
        // Wired straight off the battery, so EMG is the one position that will
        // turn the engine over, and only with the battery breaker made.
        starterCranking = starterEngaged &&
            ctl.ignition == IgnitionMode.EMG &&
            ctl.batteryBreakerClosed &&
            batteryCharge > 0.04 &&
            rpm < Spec.STARTER_STALL_RPM
        if (starterCranking) {
            val avail = clamp(batteryCharge * (1.0 - starterHeat * 0.75), 0.0, 1.0)
            torque += Spec.STARTER_TORQUE * avail * clamp(1.0 - rpm / Spec.STARTER_STALL_RPM, 0.0, 1.0)
            batteryCharge = max(0.0, batteryCharge - dt * 0.022)
            starterHeat = min(1.0, starterHeat + dt * 0.030)
        } else {
            starterHeat = max(0.0, starterHeat - dt * 0.055)
        }

        // ---- battery housekeeping ----------------------------------------------
        // Only the emergency position draws on the cells; the other two hold the
        // line up themselves and leave the battery alone.
        if (ctl.ignition == IgnitionMode.EMG && ctl.batteryBreakerClosed) {
            // Everything switched onto the line is coming out of the cells.
            batteryCharge = max(0.0, batteryCharge - dt * 0.0016 * (1.0 + serviceDrawKw * 0.60))
        }
        // Charge comes back the one way it can: off the generator terminals,
        // through the emergency transformer breaker and into the cells. No
        // excited machine or an open breaker and there is nothing putting back.
        val canCharge = ctl.emgTxBreakerClosed && genTerminalPu > 0.35
        batteryChargingNow = canCharge && batteryCharge < 0.999
        if (batteryChargingNow) {
            batteryCharge = min(1.0, batteryCharge + dt * 0.013 * clamp(genTerminalPu, 0.0, 1.0))
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

        // Water jacket. The gate meters the flow, but the pumps are electric
        // machines on the switchboard: lose both and the gate does nothing at
        // all, and on the emergency pump alone there is only enough to nurse it.
        val flow = clamp(ctl.waterValve, 0.0, 1.0) * clamp(coolantFlowPu, 0.0, 1.0)
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
        // The lubricator drips into a forced-feed pump on the main bus. Lose the
        // pump and what the sight glasses show still reaches the bearings, but
        // only what gravity will carry, so the feed has to be opened up to make
        // up for it.
        val pumpFactor = if (oilPumpRunning) 1.0 else 0.80
        val supply = if (oilInSump > 0.0) ctl.oilerRate * pumpFactor else 0.0
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
        busSupplyPu = 1.0; mainBusPu = 0.0
        serviceVolts = 0.0; ignitionLive = true
        coolantFlowPu = 0.0; oilPumpRunning = false
        genTerminalPu = 0.0; serviceDrawKw = 0.0
        knockIndex = 0.0; knockDamage = 0.0; bearingWear = 0.0
        starterEngaged = false; starterCranking = false; starterHeat = 0.0
        batteryChargingNow = false
        firingPhase = 0.0; revsSincePrime = 0.0; running = false
    }
}

internal fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))
internal fun sq(v: Double) = v * v
