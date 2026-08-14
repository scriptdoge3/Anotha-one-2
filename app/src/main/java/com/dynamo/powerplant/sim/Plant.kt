package com.dynamo.powerplant.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

enum class Failure(val headline: String, val detail: String) {
    NONE("", ""),
    KICKBACK("THE CRANK KICKED", "The charge fired ahead of top dead centre while you were on the handle. Broken wrist. The shift is over."),
    BEARING_SEIZED("MAIN BEARING SEIZED", "The lubricator was not keeping up. The white metal ran and the shaft picked up."),
    THROWN_ROD("CONNECTING ROD THROWN", "The bottom end let go and came out through the crankcase."),
    PISTON_HOLED("PISTON BURNED THROUGH", "Detonation, hour after hour, until the crown gave way."),
    OVERHEAT_SEIZED("ENGINE SEIZED", "The jacket boiled dry and the piston welded itself into the bore."),
    FLYWHEEL_BURST("FLYWHEEL BURST", "Load thrown off at full throttle with nobody on the valve. The rim went through the roof."),
    OUT_OF_PHASE("SHAFT WRECKED CLOSING OUT OF PHASE", "You closed the breaker with the machines fighting each other. The coupling sheared and took the crankshaft with it."),
    POLE_SLIP("MACHINE FELL OUT OF STEP", "Field too weak for the load being carried. The rotor slipped a pole and the whole station shook."),
    GRID_TRIP("THROWN OFF THE SYSTEM", "The interconnection protection would not hold you any longer. The unit is off the bars and the dispatcher wants an explanation."),
    BATTERY_DEAD("NOTHING LEFT IN THE CELLS", "The battery is flat, the exciter will not fire at rest, and there is no way to turn the engine over."),
    SHIFT_COMPLETE("SHIFT COMPLETE", "Seven and a half hours on the boards. The day man is here to take over.")
}

class PlantEvents {
    var fired = false
    var misfired = false
    var backfired = false
    var kickback = false
    var breakerClosed = false
    var breakerOpened = false
    var roughClose = false
    var severeClose = false
    var knifeSwitch = false
    var fuseBlew = false
    var demandChanged = false
    var poleSlip = false

    fun clear() {
        fired = false; misfired = false; backfired = false; kickback = false
        breakerClosed = false; breakerOpened = false; roughClose = false; severeClose = false
        knifeSwitch = false; fuseBlew = false; demandChanged = false; poleSlip = false
    }
}

/**
 * Ties the engine, the alternator and the town bus together and integrates them
 * on one shaft. Also keeps the score and decides when you have wrecked something
 * badly enough to end the shift.
 */
class Plant(seed: Long = System.nanoTime()) {

    val rnd = Random(seed)
    val ctl = Controls()
    val engine = Engine(Random(seed xor 0x5EED))
    val gen = Generator()
    val grid = Grid(Random(seed xor 0xB055))
    val service = Service()
    val events = PlantEvents()

    var rpm: Double = 0.0
        private set

    /** Phase of the machine relative to the bus, radians, wrapped to -PI..PI. */
    var syncPhase: Double = 0.0
        private set

    var crankTorque: Double = 0.0
        private set

    var couplingDamage: Double = 0.0
        private set

    var failure: Failure = Failure.NONE
        private set

    var shiftSeconds: Double = 0.0
        private set

    var peakOutputKw: Double = 0.0
        private set

    var overspeedSeconds: Double = 0.0
        private set

    private var wasBreakerClosed = false
    private var gridTripSeconds = 0.0
    private var previousDemandStep = 0

    val running: Boolean get() = engine.running
    val hz: Double get() = Spec.rpmToHz(rpm)
    val genVolts: Double get() = if (ctl.mainBreakerClosed) grid.volts else gen.emf(rpm)
    val slipHz: Double get() = hz - grid.hz
    val outputKw: Double get() = gen.realPowerW / 1000.0
    val outputKvar: Double get() = gen.reactivePowerVar / 1000.0
    val ended: Boolean get() = failure != Failure.NONE

    /** Brightness 0..1 of the three synchronising lamps, dark-lamp connection. */
    fun lampBrightness(): Double {
        // The lamps hang across the open breaker contacts. Close it and they are
        // short circuited, so they go out and stay out.
        if (ctl.mainBreakerClosed) return 0.0
        if (!ctl.fieldSwitchClosed) return 0.0
        val vg = gen.emf(rpm)
        val vb = grid.volts
        // Voltage across the lamp is the vector difference of the two systems.
        val diff = Math.sqrt(vg * vg + vb * vb - 2 * vg * vb * Math.cos(syncPhase))
        return clamp(diff / (Spec.RATED_VOLTS * 1.35), 0.0, 1.0)
    }

    /** One swipe of the starting crank. */
    fun crank(strength: Double) {
        if (ended) return
        if (rpm < 340.0) crankTorque = max(crankTorque, Spec.CRANK_TORQUE * clamp(strength, 0.0, 1.0))
    }

    fun prime() {
        if (ended) return
        ctl.primerCharges = min(6, ctl.primerCharges + 1)
    }

    fun toggleBreaker() {
        if (ended) return
        if (ctl.mainBreakerClosed) openBreaker() else closeBreaker()
    }

    private fun closeBreaker() {
        // Wrap the phase error into -PI..PI.
        var phi = syncPhase
        while (phi > PI) phi -= 2 * PI
        while (phi < -PI) phi += 2 * PI

        val dV = abs(gen.emf(rpm) - grid.volts) / Spec.RATED_VOLTS
        var shock = abs(sin(phi / 2.0)) + dV * 0.5 + abs(slipHz) * 0.35

        // A dead machine simply cannot be paralleled.
        if (rpm < 200.0 || gen.emf(rpm) < Spec.RATED_VOLTS * 0.35) shock = max(shock, 0.62)

        ctl.mainBreakerClosed = true
        gen.delta = phi
        events.breakerClosed = true

        when {
            shock < 0.10 -> { /* clean close, nothing felt but the clunk */ }
            shock < 0.28 -> {
                events.roughClose = true
                couplingDamage = min(1.5, couplingDamage + shock * 0.30)
                bumpSpeed(-sign(sin(phi)) * shock * 120.0)
            }
            shock < 0.55 -> {
                events.severeClose = true
                couplingDamage = min(1.5, couplingDamage + shock * 0.95)
                bumpSpeed(-sign(sin(phi)) * shock * 260.0)
                ctl.mainBreakerClosed = false      // the overload trip does its job
                events.breakerOpened = true
            }
            else -> {
                events.severeClose = true
                couplingDamage = 1.5
                fail(Failure.OUT_OF_PHASE)
            }
        }
    }

    private fun openBreaker() {
        ctl.mainBreakerClosed = false
        gen.delta = 0.0
        events.breakerOpened = true
    }

    private fun bumpSpeed(deltaRpm: Double) {
        rpm = max(0.0, rpm + deltaRpm)
    }

    /** Throw one of the internal supply switches on the board. */
    fun toggleAux(i: Int) {
        if (ended) return
        val l = service.loads[i]
        if (l.fuseBlown && !ctl.auxClosed[i]) {
            // Replacing a cartridge fuse takes a moment but you can do it.
            l.fuseBlown = false
        }
        ctl.auxClosed[i] = !ctl.auxClosed[i]
        events.knifeSwitch = true
    }

    fun step(dtWall: Double) {
        events.clear()
        if (ended) return

        // Integrate on a fixed sub-step so the swing equation stays well behaved.
        val sub = 1.0 / 240.0
        var remaining = min(dtWall, 0.25)
        while (remaining > 0.0) {
            val dt = min(sub, remaining)
            remaining -= dt
            integrate(dt)
            if (ended) return
        }
        shiftSeconds += min(dtWall, 0.25)
        if (grid.dispatchStep != previousDemandStep) {
            previousDemandStep = grid.dispatchStep
            events.demandChanged = true
        }
        if (grid.dispatchStep >= 15) fail(Failure.SHIFT_COMPLETE)
    }

    private fun integrate(dt: Double) {
        val omega = rpm * PI / 30.0

        // Work out the internal supply before anything else, because the ignition,
        // the cooling water pump and the charging set all hang off it.
        engine.busSupplyPu = grid.volts / Spec.RATED_VOLTS
        val strength = engine.sourceStrength(ctl, rpm)
        val capacity = when (ctl.ignition) {
            IgnitionMode.GRID -> Service.CAPACITY_GRID_KW
            IgnitionMode.GEN -> Service.CAPACITY_GEN_KW
            IgnitionMode.EMG -> Service.CAPACITY_BATTERY_KW
            IgnitionMode.OFF -> 0.0
        }
        service.step(dt, ctl, strength, capacity)
        if (service.fuseBlewThisStep) events.fuseBlew = true
        engine.serviceVolts = service.volts
        engine.ignitionLive = service.isRunning(Service.IGNITION)
        engine.waterPumpRunning = service.isRunning(Service.PUMP)
        engine.chargerRunning = service.isRunning(Service.CHARGER)
        engine.serviceDrawKw = service.demandKw

        // ---- prime movers -------------------------------------------------------
        var torque = engine.step(dt, ctl, rpm)
        if (engine.firedThisStep) events.fired = true
        if (engine.misfiredThisStep) events.misfired = true
        if (engine.backfiredThisStep) events.backfired = true

        if (crankTorque > 1.0) {
            if (rpm < 340.0) torque += crankTorque * engine.crankEffectiveness(rpm, ctl)
            crankTorque *= Math.exp(-dt / 0.35)
            if (engine.kickbackThisStep) {
                events.kickback = true
                fail(Failure.KICKBACK)
                return
            }
        }

        // ---- electrical ---------------------------------------------------------
        gen.stepField(dt, ctl, rpm)
        val elecTorque = gen.stepElectrical(dt, ctl, rpm, grid)
        if (gen.poleSlipThisStep) {
            events.poleSlip = true
            fail(Failure.POLE_SLIP)
            return
        }

        // ---- the shaft ----------------------------------------------------------
        val net = torque - elecTorque
        val newOmega = max(0.0, omega + net / Spec.INERTIA * dt)
        rpm = newOmega * 30.0 / PI

        // ---- phase relative to the bus -----------------------------------------
        if (ctl.mainBreakerClosed) {
            syncPhase = gen.delta
        } else {
            syncPhase += 2 * PI * (hz - grid.hz) * dt
            while (syncPhase > PI) syncPhase -= 2 * PI
            while (syncPhase < -PI) syncPhase += 2 * PI
        }

        grid.step(dt, gen, ctl)

        peakOutputKw = max(peakOutputKw, outputKw)
        checkFailures(dt)

        if (wasBreakerClosed != ctl.mainBreakerClosed) wasBreakerClosed = ctl.mainBreakerClosed
    }

    private fun checkFailures(dt: Double) {
        if (rpm > Spec.BURST_RPM) { fail(Failure.FLYWHEEL_BURST); return }
        if (rpm > Spec.OVERSPEED_RPM) {
            overspeedSeconds += dt
            couplingDamage = min(1.5, couplingDamage + dt * 0.22)
            if (overspeedSeconds > 6.0) { fail(Failure.FLYWHEEL_BURST); return }
        } else {
            overspeedSeconds = max(0.0, overspeedSeconds - dt * 0.5)
        }
        if (engine.bearingTempC > Spec.SEIZE_BEARING_C) { fail(Failure.BEARING_SEIZED); return }
        if (engine.bearingWear >= 1.0) { fail(Failure.THROWN_ROD); return }
        if (engine.knockDamage >= 1.0) { fail(Failure.PISTON_HOLED); return }
        if (engine.jacketTempC > Spec.SEIZE_JACKET_C) { fail(Failure.OVERHEAT_SEIZED); return }
        if (couplingDamage >= 1.5) { fail(Failure.OUT_OF_PHASE); return }
        // The system protection will not carry a unit that is badly out of step.
        if (ctl.mainBreakerClosed && grid.outsideLimits()) {
            gridTripSeconds += dt
            if (gridTripSeconds > 4.0) { fail(Failure.GRID_TRIP); return }
        } else {
            gridTripSeconds = max(0.0, gridTripSeconds - dt * 0.5)
        }
        if (engine.batteryCharge <= 0.001 && rpm < 30.0 && !engine.running) {
            fail(Failure.BATTERY_DEAD); return
        }
    }

    private fun fail(f: Failure) {
        if (failure == Failure.NONE) failure = f
    }

    /** 0..100 for the end-of-shift card. */
    fun score(): Int {
        var s = grid.energyExportedKwh * 4.5
        s -= grid.dispatchErrorKws * 0.010
        s -= grid.secondsOffOrder * 0.35
        s -= couplingDamage * 25.0
        s -= engine.knockDamage * 30.0
        s -= engine.bearingWear * 30.0
        for (l in service.loads) if (l.fuseBlown) s -= 6.0
        if (failure != Failure.SHIFT_COMPLETE && failure != Failure.NONE) s -= 40.0
        return clamp(s, 0.0, 100.0).toInt()
    }

    /**
     * How brightly the panel lamps burn, which depends on whether the source the
     * selector is pointing at is actually alive.
     */
    fun panelLampLevel(): Double =
        if (service.isRunning(Service.LIGHTS)) clamp(service.volts, 0.0, 1.0) else 0.0

    /** Plant clock: the shift starts at six in the evening. */
    fun clockText(): String {
        val minutes = (18 * 60 + (shiftSeconds / Grid.DISPATCH_PERIOD_S * 30.0)).toInt() % (24 * 60)
        val h = minutes / 60
        val m = minutes % 60
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format("%d:%02d %s", h12, m, ampm)
    }

    fun reset() {
        rpm = 0.0; syncPhase = 0.0; crankTorque = 0.0; couplingDamage = 0.0
        failure = Failure.NONE; shiftSeconds = 0.0; peakOutputKw = 0.0; overspeedSeconds = 0.0
        wasBreakerClosed = false; previousDemandStep = 0
        engine.reset(); gen.reset(); grid.reset(); service.reset()
        gridTripSeconds = 0.0
        ctl.ignition = IgnitionMode.OFF
        ctl.throttle = 0.0; ctl.sparkLever = 0.5; ctl.mixture = 0.5; ctl.excitation = 0.0
        ctl.compressionRelease = false; ctl.primerCharges = 0
        ctl.waterValve = 0.0; ctl.oilerRate = 0.0
        ctl.mainBreakerClosed = false; ctl.fieldSwitchClosed = true
        ctl.auxClosed[0] = true; ctl.auxClosed[1] = true
        ctl.auxClosed[2] = false; ctl.auxClosed[3] = false
    }
}
