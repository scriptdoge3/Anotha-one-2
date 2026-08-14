package com.dynamo.powerplant

import com.dynamo.powerplant.sim.Controls
import com.dynamo.powerplant.sim.Engine
import com.dynamo.powerplant.sim.Failure
import com.dynamo.powerplant.sim.IgnitionMode
import com.dynamo.powerplant.sim.Plant
import com.dynamo.powerplant.sim.Spec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Drives the plant the way a competent operator would, and checks the physics behaves. */
class SimTest {

    private var lastHz = 60.0

    private fun run(p: Plant, seconds: Double, dt: Double = 1.0 / 60.0, each: (Plant) -> Unit = {}) {
        var t = 0.0
        while (t < seconds && !p.ended) {
            each(p)
            p.step(dt)
            t += dt
        }
    }

    /**
     * A competent operator: needle valve on best power, spark lever where the
     * charge wants it, oiler ahead of demand, water gate holding about 78 C.
     */
    private fun trim(p: Plant) {
        val coldFactor = 0.50 * ((50.0 - p.engine.jacketTempC) / 42.0).coerceIn(0.0, 1.0)
        val wantSupplied = 12.5 / (1.0 + coldFactor)
        p.ctl.mixture = ((17.5 - wantSupplied) / 8.0).coerceIn(0.0, 1.0)
        val opt = p.engine.optimalAdvanceDeg(p.rpm, 12.5)
        p.ctl.sparkLever = ((opt + 5.0) / 43.0).coerceIn(0.0, 1.0)
        p.ctl.oilerRate = (0.30 + p.rpm / 600.0 * 0.55 + (1.0 - p.engine.oilFilm) * 1.5).coerceIn(0.0, 1.0)
        p.ctl.waterValve = (p.ctl.waterValve + (p.engine.jacketTempC - 78.0) * 0.004).coerceIn(0.0, 1.0)
    }

    /** Hand on the throttle, damped, the way you have to work a governor-less engine. */
    private fun holdSpeed(p: Plant, targetHz: Double, dt: Double = 1.0 / 60.0) {
        val err = targetHz - p.hz
        val rate = (p.hz - lastHz) / dt
        lastHz = p.hz
        p.ctl.throttle = (p.ctl.throttle + (err * 0.075 - rate * 0.30) * dt).coerceIn(0.0, 1.0)
    }

    private fun tend(p: Plant, targetHz: Double = 60.0, dt: Double = 1.0 / 60.0) {
        trim(p)
        holdSpeed(p, targetHz, dt)
    }

    /** Bring the volts up to match whatever the bus is doing. */
    private fun matchVolts(p: Plant, dt: Double = 1.0 / 60.0) {
        p.ctl.excitation = (p.ctl.excitation + (p.grid.busVolts - p.genVolts) * 0.0035 * dt).coerceIn(0.0, 1.0)
    }

    /** Turn the key one notch at a time, taking the given seconds per position. */
    private fun sweepKeyTo(p: Plant, target: IgnitionMode, secondsPerNotch: Double) {
        var guard = 0
        while (p.ctl.ignition != target && guard++ < 12) {
            p.ctl.ignition =
                if (target.ordinal > p.ctl.ignition.ordinal) p.ctl.ignition.clockwise()
                else p.ctl.ignition.anticlockwise()
            run(p, secondsPerNotch)
        }
    }

    /** The full cold-start ritual. Returns true if the engine is running at speed. */
    private fun startEngine(p: Plant): Boolean {
        lastHz = 0.0
        p.ctl.waterValve = 0.30
        p.ctl.oilerRate = 0.55
        p.ctl.mixture = 0.88         // rich, the iron is stone cold
        p.ctl.sparkLever = 0.12      // well retarded so the crank cannot kick
        p.ctl.throttle = 0.35
        p.prime()
        p.ctl.compressionRelease = true
        p.ctl.ignition = IgnitionMode.EMG
        p.engine.starterEngaged = true
        run(p, 3.5)
        p.ctl.compressionRelease = false     // drop the release and let it fire
        run(p, 5.0)
        p.engine.starterEngaged = false

        run(p, 50.0) { tend(it) }
        sweepKeyTo(p, IgnitionMode.GEN, 0.25)   // a brisk sweep across the dead notch
        run(p, 15.0) { tend(it) }
        return p.running && !p.ended
    }

    // ------------------------------------------------------------------ ignition

    @Test
    fun selectorPositionsAreInOrder() {
        val order = IgnitionMode.entries.map { it.label }
        assertEquals(listOf("GRID", "GEN", "OFF", "EMG"), order)
        for (m in listOf(IgnitionMode.GRID, IgnitionMode.GEN, IgnitionMode.EMG)) {
            assertTrue("${m.label} must feed the plugs", m.ignites)
        }
        assertTrue("OFF is dead", !IgnitionMode.OFF.ignites)
        // The selector cannot jump: EMG to GEN passes through OFF.
        var k = IgnitionMode.EMG
        var notches = 0
        while (k != IgnitionMode.GEN) { k = k.anticlockwise(); notches++ }
        assertEquals(2, notches)
    }

    @Test
    fun theExciterGivesNothingAtRestButFiresWhenTurning() {
        val p = Plant(1)
        p.ctl.ignition = IgnitionMode.GEN
        assertTrue("the exciter must be dead at rest", p.engine.sparkEnergy(p.ctl, 0.0) < 0.02)
        assertTrue("still weak at cranking speed", p.engine.sparkEnergy(p.ctl, 90.0) < 0.35)
        assertTrue("strong once up to speed", p.engine.sparkEnergy(p.ctl, 600.0) > 0.9)
    }

    @Test
    fun stationServiceIsSteadyWhileTheBusIsHealthy() {
        val p = Plant(1)
        p.ctl.ignition = IgnitionMode.GRID
        p.engine.busSupplyPu = 1.0
        assertTrue("GRID must fire even at rest", p.engine.sparkEnergy(p.ctl, 0.0) > 0.9)
        assertTrue("and at working speed", p.engine.sparkEnergy(p.ctl, 600.0) > 0.9)
    }

    @Test
    fun aSaggingBusWeakensTheStationServiceSpark() {
        val engine = Engine()
        val ctl = Controls()
        ctl.ignition = IgnitionMode.GRID

        var previous = 1.1
        for (pu in listOf(1.00, 0.90, 0.82, 0.75, 0.68, 0.62)) {
            engine.busSupplyPu = pu
            val e = engine.sparkEnergy(ctl, 600.0)
            assertTrue("spark must not strengthen as the bus falls: $pu gave $e", e < previous + 1e-9)
            previous = e
        }
        engine.busSupplyPu = 0.62
        assertTrue("at the bottom there is nothing left", engine.sparkEnergy(ctl, 600.0) < 0.02)
    }

    @Test
    fun anEngineOnStationServiceDiesWhenTheBusGoes() {
        // Run the engine directly so the bus supply can be taken away outright.
        val engine = Engine()
        val ctl = Controls()
        ctl.ignition = IgnitionMode.GRID
        ctl.throttle = 0.5
        ctl.mixture = 0.625
        ctl.sparkLever = 0.72
        ctl.oilerRate = 0.8
        engine.jacketTempC = 78.0
        engine.busSupplyPu = 1.0

        repeat(240) { engine.step(1.0 / 60.0, ctl, 600.0) }
        assertTrue("should be firing on station service, was ${engine.firingSuccess}", engine.firingSuccess > 0.7)

        engine.busSupplyPu = 0.0
        repeat(120) { engine.step(1.0 / 60.0, ctl, 600.0) }
        assertTrue("with the bus gone there is no ignition left", engine.firingSuccess < 0.05)
    }

    @Test
    fun theBatteryIsStrongAtCrankingAndFadesAtSpeed() {
        val p = Plant(2)
        p.ctl.ignition = IgnitionMode.EMG
        assertTrue("the battery must fire at cranking speed", p.engine.sparkEnergy(p.ctl, 90.0) > 0.9)
        assertTrue(
            "and must fade at working speed",
            p.engine.sparkEnergy(p.ctl, 600.0) < p.engine.sparkEnergy(p.ctl, 90.0) * 0.85
        )
    }

    @Test
    fun theOffPositionGivesNoSparkAtAll() {
        val p = Plant(2)
        p.ctl.ignition = IgnitionMode.OFF
        assertEquals("OFF must give no spark", 0.0, p.engine.sparkEnergy(p.ctl, 600.0), 1e-9)
        assertEquals(0.0, p.engine.sparkEnergy(p.ctl, 90.0), 1e-9)
    }

    @Test
    fun dawdlingAcrossTheDeadPositionsKillsTheEngine() {
        // Both machines start on EMG and must cross OFF to reach GEN.
        val fast = Plant(21)
        assertTrue(startEngine(fast))
        sweepKeyTo(fast, IgnitionMode.EMG, 0.2)
        run(fast, 6.0) { trim(it) }
        assertTrue("should still be alive back on EMG", fast.running)
        sweepKeyTo(fast, IgnitionMode.GEN, 0.25)
        run(fast, 6.0) { trim(it) }
        assertTrue("a brisk sweep to GEN should keep it running, rpm ${fast.rpm}", fast.running)

        val slow = Plant(21)
        assertTrue(startEngine(slow))
        sweepKeyTo(slow, IgnitionMode.EMG, 0.2)
        run(slow, 6.0) { trim(it) }
        slow.ctl.throttle = 0.25
        sweepKeyTo(slow, IgnitionMode.GEN, 30.0)  // dawdling across the dead notch
        assertTrue(
            "dawdling must cost real speed: ${slow.rpm} against ${fast.rpm}",
            slow.rpm < fast.rpm - 120.0
        )

        // Dawdle long enough and it coasts below the speed the exciter needs, so
        // landing on GEN cannot relight it and the only way back is the battery.
        val stalled = Plant(21)
        assertTrue(startEngine(stalled))
        sweepKeyTo(stalled, IgnitionMode.EMG, 0.2)
        run(stalled, 6.0) { trim(it) }
        stalled.ctl.throttle = 0.0
        sweepKeyTo(stalled, IgnitionMode.GEN, 70.0)
        run(stalled, 20.0)
        assertTrue("the engine must be dead, rpm ${stalled.rpm}", !stalled.running)
    }

    // ------------------------------------------------------------------ starting

    @Test
    fun engineStartsAndReachesWorkingSpeed() {
        val p = Plant(3)
        assertTrue("engine should be running after the start sequence", startEngine(p))
        assertTrue("should be turning usefully, was ${p.rpm}", p.rpm > 400.0)
    }

    @Test
    fun advancedSparkOnTheCrankKicksBack() {
        val p = Plant(31)
        p.ctl.mixture = 0.88
        p.ctl.throttle = 0.3
        p.ctl.sparkLever = 1.0          // fully advanced, the classic mistake
        p.ctl.ignition = IgnitionMode.EMG
        p.prime()
        var guard = 0
        while (!p.ended && guard++ < 4000) {
            p.crank(1.0)
            p.step(1.0 / 60.0)
        }
        assertEquals("cranking on full advance must break your wrist", Failure.KICKBACK, p.failure)
    }

    @Test
    fun handCrankingNeedsTheCompressionRelease() {
        val withRelease = Plant(32)
        withRelease.ctl.compressionRelease = true
        repeat(900) { withRelease.crank(1.0); withRelease.step(1.0 / 60.0) }

        val without = Plant(32)
        without.ctl.compressionRelease = false
        repeat(900) { without.crank(1.0); without.step(1.0 / 60.0) }

        assertTrue(
            "the release must make the engine far easier to turn: ${without.rpm} vs ${withRelease.rpm}",
            withRelease.rpm > without.rpm * 1.25
        )
        assertTrue("hand cranking should reach magneto speed", withRelease.rpm > 150.0)
    }

    // ------------------------------------------------------------------ running

    @Test
    fun throttleSetsFrequencyOffTheBus() {
        val p = Plant(4)
        assertTrue(startEngine(p))
        run(p, 60.0) { tend(it) }
        assertTrue("should settle near 60 Hz, was ${p.hz}", abs(p.hz - 60.0) < 1.0)
    }

    @Test
    fun excitationSetsTerminalVolts() {
        val p = Plant(5)
        assertTrue(startEngine(p))
        run(p, 60.0) { pl ->
            tend(pl)
            pl.ctl.excitation = (pl.ctl.excitation + (Spec.RATED_VOLTS - pl.genVolts) * 0.0035 / 60.0).coerceIn(0.0, 1.0)
        }
        assertTrue("should hold rated volts, was ${p.genVolts}", abs(p.genVolts - Spec.RATED_VOLTS) < 160.0)
        assertTrue("field rheostat should land mid scale, was ${p.ctl.excitation}", p.ctl.excitation in 0.35..0.90)
    }

    // ------------------------------------------------------------------ paralleling

    /** Match cycles and volts, then close the moment the lamps go dark. */
    private fun synchronise(p: Plant): Boolean {
        // Trim to a whisker above the bus so the scope creeps slowly forward.
        run(p, 50.0) { pl ->
            tend(pl, pl.grid.busHz + 0.10)
            matchVolts(pl)
        }
        val dt = 1.0 / 240.0
        var guard = 0
        while (guard++ < 60000 && !p.ended) {
            tend(p, p.grid.busHz + 0.10, dt)
            matchVolts(p, dt)
            p.step(dt)
            if (abs(p.syncPhase) < 0.06 && abs(p.slipHz) < 0.30) {
                p.toggleBreaker()
                return p.ctl.mainBreakerClosed
            }
        }
        return false
    }

    @Test
    fun cleanSynchroniseCarriesLoadWithNoDamage() {
        val p = Plant(6)
        assertTrue(startEngine(p))
        assertTrue("breaker should have closed", synchronise(p))
        assertTrue("a clean close must not hurt anything", p.couplingDamage < 0.01)

        // Pick up the rest of the town and hold the cycles while doing it.
        p.toggleFeeder(1)
        p.toggleFeeder(3)
        run(p, 60.0) { tend(it) }
        assertTrue("should be exporting power, was ${p.outputKw} kW", p.outputKw > 15.0)
        assertTrue("cycles must stay in hand, bus at ${p.grid.busHz}", abs(p.grid.busHz - 60.0) < 1.2)
        assertEquals(Failure.NONE, p.failure)
    }

    @Test
    fun closingOutOfPhaseWrecksTheShaft() {
        val p = Plant(7)
        assertTrue(startEngine(p))
        run(p, 50.0) { pl ->
            tend(pl, pl.grid.busHz + 0.15)
            matchVolts(pl)
        }
        var guard = 0
        while (abs(p.syncPhase) < 3.0 && guard++ < 60000 && !p.ended) p.step(1.0 / 240.0)
        p.toggleBreaker()
        assertEquals("closing at 180 degrees must wreck the machine", Failure.OUT_OF_PHASE, p.failure)
    }

    @Test
    fun throttleMovesRealPowerAndExcitationMovesVars() {
        val p = Plant(13)
        assertTrue(startEngine(p))
        assertTrue(synchronise(p))
        run(p, 20.0) { trim(it) }

        val kwBefore = p.outputKw
        p.ctl.throttle = (p.ctl.throttle + 0.18).coerceAtMost(1.0)
        run(p, 12.0) { trim(it) }
        assertTrue("opening the throttle must push out watts: $kwBefore -> ${p.outputKw}", p.outputKw > kwBefore + 6.0)

        val kwAfterThrottle = p.outputKw
        val kvarBefore = p.outputKvar
        p.ctl.excitation = (p.ctl.excitation + 0.20).coerceAtMost(1.0)
        run(p, 12.0) { trim(it) }
        assertTrue("more field must push out vars: $kvarBefore -> ${p.outputKvar}", p.outputKvar > kvarBefore + 8.0)
        assertTrue(
            "field should barely touch the watts: $kwAfterThrottle -> ${p.outputKw}",
            abs(p.outputKw - kwAfterThrottle) < 22.0
        )
    }

    @Test
    fun throwingOffLoadAtFullThrottleRunsTheEngineAway() {
        val p = Plant(8)
        assertTrue(startEngine(p))
        p.ctl.throttle = 1.0
        run(p, 25.0) { pl ->
            pl.ctl.waterValve = 1.0
            pl.ctl.oilerRate = 1.0
        }
        assertTrue("engine must run away past the overspeed mark, reached ${p.rpm}", p.rpm > Spec.OVERSPEED_RPM)
    }

    // ------------------------------------------------------------------ neglect

    @Test
    fun runningTheOilerDryBurnsOutTheBearings() {
        val p = Plant(9)
        assertTrue(startEngine(p))
        run(p, 400.0) { pl ->
            tend(pl)
            pl.ctl.oilerRate = 0.0          // the lubricator is left shut
            pl.ctl.waterValve = 0.7
        }
        assertTrue(
            "starving the bearings must end the shift, ended with ${p.failure}",
            p.failure == Failure.BEARING_SEIZED || p.failure == Failure.THROWN_ROD
        )
    }

    @Test
    fun shuttingTheWaterOffCooksTheEngine() {
        val p = Plant(10)
        assertTrue(startEngine(p))
        run(p, 500.0) { pl ->
            tend(pl)
            pl.ctl.waterValve = 0.0         // the water gate is left shut
            pl.ctl.oilerRate = 0.8
        }
        assertTrue(
            "no cooling water must destroy the engine, ended with ${p.failure}",
            p.failure == Failure.OVERHEAT_SEIZED || p.failure == Failure.PISTON_HOLED
        )
    }

    // ------------------------------------------------------------------ the town

    @Test
    fun townDemandStepsEveryNinetySeconds() {
        val p = Plant(11)
        val first = p.grid.demandW
        run(p, 89.0)
        assertEquals("demand must hold for the full 90 seconds", 0, p.grid.demandStep)
        run(p, 8.0)
        assertEquals(1, p.grid.demandStep)
        assertTrue("the figure must actually move", abs(p.grid.demandW - first) > 2000.0)
    }

    @Test
    fun synchronisingLampsGoDarkOnlyAtCoincidence() {
        val p = Plant(12)
        assertTrue(startEngine(p))
        run(p, 50.0) { pl ->
            tend(pl, pl.grid.busHz + 0.15)
            matchVolts(pl)
        }
        var minB = 1.0
        var maxB = 0.0
        var guard = 0
        while (guard++ < 40000 && !p.ended) {
            p.step(1.0 / 240.0)
            minB = minOf(minB, p.lampBrightness())
            maxB = maxOf(maxB, p.lampBrightness())
        }
        assertTrue("lamps must go dark at coincidence, min was $minB", minB < 0.12)
        assertTrue("lamps must burn bright at opposition, max was $maxB", maxB > 0.7)
    }
}
