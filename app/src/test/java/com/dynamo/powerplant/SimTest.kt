package com.dynamo.powerplant

import com.dynamo.powerplant.sim.Auxiliaries
import com.dynamo.powerplant.sim.Controls
import com.dynamo.powerplant.sim.Engine
import com.dynamo.powerplant.sim.Failure
import com.dynamo.powerplant.sim.IgnitionMode
import com.dynamo.powerplant.sim.Plant
import com.dynamo.powerplant.sim.Protection
import com.dynamo.powerplant.sim.Service
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
        p.ctl.excitation = (p.ctl.excitation + (p.grid.volts - p.genVolts) * 0.0035 * dt).coerceIn(0.0, 1.0)
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
        p.ctl.sparkLever = 0.30
        p.ctl.throttle = 0.35
        p.prime()
        p.ctl.compressionRelease = true
        p.ctl.ignition = IgnitionMode.EMG
        p.engine.starterEngaged = true
        run(p, 4.0)                          // spin it up light on the starter
        p.ctl.compressionRelease = false     // shut the relief cock and let it fire
        run(p, 6.0)
        p.engine.starterEngaged = false

        // Open the starting transformer before touching the field: with it in,
        // the terminals are already tied to the system through it, and exciting
        // the machine parallels the set through a transformer that cannot hold
        // it. Then bring the field up — the main bus hangs off the station
        // transformer and there is no circulating pump until it is alive.
        p.ctl.startingTxBreakerClosed = false
        p.ctl.excitation = 0.60
        run(p, 55.0) { tend(it) }
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
    fun theGenTapGivesNothingUntilSomethingHoldsTheTerminalsUp() {
        val p = Plant(1)
        p.ctl.ignition = IgnitionMode.GEN
        // With the starting transformer in, the system holds the bar up and the
        // tap works before the engine has turned at all.
        run(p, 1.0)
        assertTrue("back-fed, the tap is good", p.engine.sourceStrength(p.ctl, 0.0) > 0.9)

        // Islanded and stone cold there is nothing behind it.
        p.ctl.startingTxBreakerClosed = false
        run(p, 1.0)
        assertTrue("the tap must be dead at rest", p.engine.sourceStrength(p.ctl, 0.0) < 0.02)

        // Turning and excited, it is as good a supply as the machine is.
        assertTrue(startEngine(p))
        assertTrue("the tap should be alive", p.engine.sourceStrength(p.ctl, p.rpm) > 0.9)

        // And it comes off the emergency transformer, not the main bus. Open
        // that breaker and the tap is gone at once, while the main bus carries
        // on as if nothing had happened — the two transformers are independent
        // all the way back to the bar.
        p.ctl.emgTxBreakerClosed = false
        run(p, 0.5) { tend(it) }
        assertTrue("the emergency transformer is out", p.emergencyTransformerPu() < 0.02)
        assertTrue("so the tap gives nothing", p.engine.sourceStrength(p.ctl, p.rpm) < 0.02)
        assertTrue("but the main bus must be untouched, was ${p.mainBus.volts}", p.mainBus.volts > 0.9)
    }

    @Test
    fun stationServiceIsSteadyWhileTheGridIsHealthy() {
        val p = Plant(1)
        p.ctl.ignition = IgnitionMode.GRID
        p.engine.busSupplyPu = 1.0
        assertTrue("GRID must be good even at rest", p.engine.sourceStrength(p.ctl, 0.0) > 0.9)
        assertTrue("and at working speed", p.engine.sourceStrength(p.ctl, 600.0) > 0.9)
    }

    @Test
    fun aSaggingBusWeakensTheStationServiceSpark() {
        val engine = Engine()
        val ctl = Controls()
        ctl.ignition = IgnitionMode.GRID

        var previous = 1.1
        for (pu in listOf(1.00, 0.90, 0.82, 0.75, 0.68, 0.62)) {
            engine.busSupplyPu = pu
            val e = engine.sourceStrength(ctl, 600.0)
            assertTrue("supply must not strengthen as the grid falls: $pu gave $e", e < previous + 1e-9)
            previous = e
        }
        engine.busSupplyPu = 0.62
        assertTrue("at the bottom there is nothing left", engine.sourceStrength(ctl, 600.0) < 0.02)
    }

    @Test
    fun anEngineOnStationServiceDiesWhenTheGridGoes() {
        // Drive the engine and the internal bus directly, so the grid can be
        // taken away outright without waiting for the whole system to collapse.
        val engine = Engine()
        val service = Service.emergencyLine()
        val ctl = Controls()
        ctl.ignition = IgnitionMode.GRID
        ctl.throttle = 0.5
        ctl.mixture = 0.625
        ctl.sparkLever = 0.72
        ctl.oilerRate = 0.8
        engine.jacketTempC = 78.0
        engine.busSupplyPu = 1.0

        fun tick() {
            service.step(1.0 / 60.0, ctl.auxClosed, engine.sourceStrength(ctl, 600.0), Service.CAPACITY_GRID_KW)
            engine.serviceVolts = service.volts
            engine.ignitionLive = service.isRunning(Service.IGNITION)
            engine.coolantFlowPu = if (service.isRunning(Service.EMG_PUMP)) 0.42 else 0.0
            engine.step(1.0 / 60.0, ctl, 600.0)
        }

        repeat(240) { tick() }
        assertTrue("should be firing on station service, was ${engine.firingSuccess}", engine.firingSuccess > 0.7)

        engine.busSupplyPu = 0.0
        repeat(120) { tick() }
        assertTrue("with the grid gone there is no ignition left", engine.firingSuccess < 0.05)
    }

    @Test
    fun theBatteryCoilBoxFadesAsTheRevolutionsRise() {
        val p = Plant(2)
        p.ctl.ignition = IgnitionMode.EMG
        // The cells hold their volts whatever the engine is doing...
        assertTrue("full cells are full volts", p.engine.sourceStrength(p.ctl, 90.0) > 0.95)
        assertTrue("at any speed", p.engine.sourceStrength(p.ctl, 600.0) > 0.95)
        // ...but the coil box runs out of dwell, so the spark falls away.
        assertTrue("coil good at cranking speed", p.engine.coilDwellFade(90.0) > 0.95)
        assertTrue("coil poor at working speed", p.engine.coilDwellFade(600.0) < 0.80)
    }

    @Test
    fun flatCellsLoseTheirVoltsAltogether() {
        val p = Plant(2)
        p.ctl.ignition = IgnitionMode.EMG
        p.engine.batteryCharge = 0.60
        assertTrue("half down is still good volts", p.engine.sourceStrength(p.ctl, 0.0) > 0.9)
        p.engine.batteryCharge = 0.05
        assertTrue("nearly flat falls off a cliff", p.engine.sourceStrength(p.ctl, 0.0) < 0.35)
    }

    @Test
    fun theOffPositionGivesNothingAtAll() {
        val p = Plant(2)
        p.ctl.ignition = IgnitionMode.OFF
        run(p, 1.0)
        assertEquals("OFF must leave the internal bus dead", 0.0, p.service.volts, 1e-9)
        assertEquals("and no spark", 0.0, p.engine.sparkEnergy(p.ctl, 600.0), 1e-9)
    }

    @Test
    fun dawdlingAcrossTheDeadPositionKillsTheFire() {
        // Both machines start on EMG and must cross OFF to reach GEN.
        val fast = Plant(21)
        assertTrue(startEngine(fast))
        sweepKeyTo(fast, IgnitionMode.EMG, 0.2)
        run(fast, 6.0) { tend(it) }
        assertTrue("should still be alive back on EMG", fast.running)
        sweepKeyTo(fast, IgnitionMode.GEN, 0.25)
        run(fast, 6.0) { tend(it) }
        assertTrue("a brisk sweep to GEN should keep it running, rpm ${fast.rpm}", fast.running)

        // The same machine, left sitting on the dead notch.
        val slow = Plant(21)
        assertTrue(startEngine(slow))
        sweepKeyTo(slow, IgnitionMode.EMG, 0.2)
        run(slow, 6.0) { tend(it) }
        val before = slow.rpm
        slow.ctl.ignition = IgnitionMode.OFF
        run(slow, 40.0)
        assertTrue("with no ignition the fire must go out", slow.engine.firingSuccess < 0.05)
        assertTrue(
            "and it must cost real speed: $before -> ${slow.rpm}",
            slow.rpm < before - 120.0
        )

        // And GEN cannot save it. The field is a load on the line, so a long
        // spell on the dead notch leaves the machine unexcited, the station
        // transformer with nothing to work on, and the main bus dead. Only the
        // battery can put the fire back.
        slow.ctl.ignition = IgnitionMode.GEN
        run(slow, 10.0) { tend(it) }
        assertTrue("a de-excited machine cannot carry its own line", !slow.running)
        assertTrue("and the emergency transformer with it", slow.emergencyTransformerPu() < 0.05)

        slow.ctl.ignition = IgnitionMode.EMG
        run(slow, 8.0) { tend(it) }
        assertTrue("but the cells will relight it", slow.engine.firingSuccess > 0.5)
    }

    // ------------------------------------------------------------------ starting

    @Test
    fun engineStartsAndReachesWorkingSpeed() {
        val p = Plant(3)
        assertTrue("engine should be running after the start sequence", startEngine(p))
        assertTrue("should be turning usefully, was ${p.rpm}", p.rpm > 400.0)
    }

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
            tend(pl, pl.grid.hz + 0.10)
            matchVolts(pl)
        }
        val dt = 1.0 / 240.0
        var guard = 0
        while (guard++ < 60000 && !p.ended) {
            tend(p, p.grid.hz + 0.10, dt)
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

        // Wind the throttle up to meet the dispatcher's order.
        run(p, 60.0) { pl ->
            trim(pl)
            val err = pl.grid.dispatchKw() - pl.outputKw
            pl.ctl.throttle = (pl.ctl.throttle + err * 0.0012 / 60.0 * 60.0).coerceIn(0.0, 1.0)
        }
        assertTrue("should be exporting power, was ${p.outputKw} kW", p.outputKw > 60.0)
        assertTrue(
            "should be near the order of ${p.grid.dispatchKw()} kW, was ${p.outputKw}",
            abs(p.outputKw - p.grid.dispatchKw()) < 70.0
        )
        assertEquals(Failure.NONE, p.failure)
    }

    @Test
    fun closingOutOfPhaseWrecksTheShaft() {
        val p = Plant(7)
        assertTrue(startEngine(p))
        run(p, 50.0) { pl ->
            tend(pl, pl.grid.hz + 0.15)
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
        run(p, 20.0) { trim(it) }
        assertTrue("opening the throttle must push out watts: $kwBefore -> ${p.outputKw}", p.outputKw > kwBefore + 25.0)

        val kwAfterThrottle = p.outputKw
        val kvarBefore = p.outputKvar
        p.ctl.excitation = (p.ctl.excitation + 0.20).coerceAtMost(1.0)
        run(p, 30.0) { trim(it) }
        assertTrue("more field must push out vars: $kvarBefore -> ${p.outputKvar}", p.outputKvar > kvarBefore + 25.0)
        assertTrue(
            "field should barely touch the watts: $kwAfterThrottle -> ${p.outputKw}",
            abs(p.outputKw - kwAfterThrottle) < 60.0
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
    fun theSetStartsOnTheStarterFromTheBoardAsHandedOver() {
        // Everything left where the day man had it: selector to the battery and
        // hold the starting motor, and nothing else.
        val p = Plant(77)
        p.ctl.ignition = IgnitionMode.EMG
        p.engine.starterEngaged = true
        // Hold the starter until it catches, then let go, as anyone would.
        var held = 0.0
        while (held < 25.0 && !p.running) { p.step(1.0 / 60.0); held += 1.0 / 60.0 }
        p.engine.starterEngaged = false
        assertTrue("the set must catch on the starter, gave up after ${held}s", p.running)
        run(p, 8.0)
        assertTrue("and must keep running once the starter is released, rpm ${p.rpm}", p.running)
    }

    @Test
    fun holdingTheStarterOnTheBatteryFlattensIt() {
        // Islanded, the cells are all there is, and emergency supply is a clock:
        // get it lit and get across to the machine.
        val p = Plant(78)
        p.ctl.startingTxBreakerClosed = false
        p.ctl.ignition = IgnitionMode.EMG
        p.engine.starterEngaged = true
        run(p, 60.0)
        assertTrue("the cells must be well down, was ${p.engine.batteryCharge}", p.engine.batteryCharge < 0.25)
    }

    @Test
    fun theBackFeedTakesTheClockOffTheStarter() {
        // With the starting transformer in, the charging set is working the
        // whole time you are cranking, so the cells hold up far better.
        val islanded = Plant(79)
        islanded.ctl.startingTxBreakerClosed = false
        islanded.ctl.ignition = IgnitionMode.EMG
        islanded.engine.starterEngaged = true
        run(islanded, 40.0)

        val backFed = Plant(79)
        backFed.ctl.ignition = IgnitionMode.EMG
        backFed.engine.starterEngaged = true
        run(backFed, 40.0)

        assertTrue(
            "back-feeding must spare the cells: ${backFed.engine.batteryCharge} vs ${islanded.engine.batteryCharge}",
            backFed.engine.batteryCharge > islanded.engine.batteryCharge + 0.05
        )
    }

    @Test
    fun theEmergencyTransformerIsTheRoadBackToTheCells() {
        val p = Plant(91)
        assertTrue(startEngine(p))
        // Bring the field up so the machine actually makes volts. The emergency
        // transformer hangs straight off the generator terminals, so an
        // unexcited machine has nothing to give it.
        p.ctl.excitation = 0.60
        run(p, 8.0) { tend(it) }

        p.engine.batteryCharge = 0.50
        run(p, 5.0) { tend(it) }
        assertTrue("the emergency transformer should be charging", p.engine.batteryChargingNow)
        val rising = p.engine.batteryCharge
        run(p, 20.0) { tend(it) }
        assertTrue("and the cells should be coming up", p.engine.batteryCharge > rising)

        // Open the emergency transformer breaker and that road is cut.
        p.ctl.emgTxBreakerClosed = false
        run(p, 5.0) { tend(it) }
        assertTrue("with the breaker open nothing can charge", !p.engine.batteryChargingNow)
        val held = p.engine.batteryCharge
        run(p, 20.0) { tend(it) }
        assertTrue("and the cells must not rise", p.engine.batteryCharge <= held + 1e-6)
    }

    @Test
    fun theTerminalsMustBeLiveToCharge() {
        val p = Plant(92)
        // Islanded and stone cold: nothing on the terminals, so the emergency
        // transformer has nothing to work on however the board is set.
        p.ctl.startingTxBreakerClosed = false
        p.ctl.ignition = IgnitionMode.EMG
        p.engine.batteryCharge = 0.50
        run(p, 4.0)
        assertTrue("dead terminals cannot charge", !p.engine.batteryChargingNow)
        val held = p.engine.batteryCharge
        run(p, 10.0)
        assertTrue("and the cells must not rise", p.engine.batteryCharge <= held + 1e-6)

        // Close the starting transformer and the system back-feeds the bar, so
        // the charging set comes alive with the engine still stone cold.
        p.ctl.startingTxBreakerClosed = true
        run(p, 4.0)
        assertTrue("the back-feed must reach the charging set", p.engine.batteryChargingNow)
        run(p, 30.0)
        assertTrue("and put the cells back up", p.engine.batteryCharge > held + 0.005)
    }

    @Test
    fun theStartingTransformerBringsADeadStationAlive() {
        val p = Plant(93)
        // Nothing turning, nothing excited, and the whole station alive anyway:
        // the system is holding the terminals up through the starting transformer.
        assertTrue("the terminals must be live from the system", p.generatorBusPu() > 0.9)
        run(p, 3.0)
        assertTrue("and the main bus with them", p.mainBus.volts > 0.9)
        assertTrue("so the circulating pump can run before the engine does",
            p.mainBus.isRunning(Service.CIRC_PUMP))
        assertTrue("and the oil pump", p.mainBus.isRunning(Service.OIL_PUMP))

        // Open its breaker and the station goes dark, because nothing else is
        // holding anything up yet.
        p.ctl.startingTxBreakerClosed = false
        run(p, 3.0)
        assertTrue("islanded and cold, the bar is dead", p.generatorBusPu() < 0.05)
        assertTrue("and the main bus with it", p.mainBus.volts < 0.05)
    }

    @Test
    fun excitingAgainstTheStartingTransformerThrowsItOff() {
        val p = Plant(94)
        // Start it but leave the starting transformer in, which is the mistake.
        p.ctl.waterValve = 0.30; p.ctl.oilerRate = 0.55
        p.ctl.mixture = 0.88; p.ctl.sparkLever = 0.30; p.ctl.throttle = 0.35
        p.prime(); p.ctl.compressionRelease = true
        p.ctl.ignition = IgnitionMode.EMG; p.engine.starterEngaged = true
        run(p, 4.0); p.ctl.compressionRelease = false; run(p, 6.0)
        p.engine.starterEngaged = false
        run(p, 40.0) { tend(it) }
        assertTrue("the engine should be turning", p.rpm > 300.0)
        assertTrue("and the starting transformer still in", p.ctl.startingTxBreakerClosed)

        // Now bring the field up against it.
        p.ctl.excitation = 0.60
        run(p, 40.0) { tend(it) }
        assertTrue("the starting transformer must throw itself off", !p.ctl.startingTxBreakerClosed)
        assertEquals("once", 1, p.startingTxTrips)
        assertEquals("but it is not a wreck", Failure.NONE, p.failure)
        assertTrue("and the machine is fine", p.running)
    }

    @Test
    fun theBatteryBreakerTakesTheCellsOffTheLine() {
        val p = Plant(93)
        p.ctl.ignition = IgnitionMode.EMG
        run(p, 1.0)
        assertTrue("on emergency supply the line should be alive", p.service.volts > 0.8)

        p.ctl.batteryBreakerClosed = false
        run(p, 1.0)
        assertTrue("with the battery breaker open the line is dead", p.service.volts < 0.05)
        assertEquals("and there is no spark", 0.0, p.engine.sparkEnergy(p.ctl, 0.0), 1e-9)

        // Nor will the starting motor turn: it hangs off the same breaker.
        p.engine.starterEngaged = true
        run(p, 2.0)
        assertTrue("and the starter must not turn", !p.engine.starterCranking && p.rpm < 5.0)
    }

    @Test
    fun theFieldHangsOffTheEmergencyLine() {
        val p = Plant(94)
        assertTrue(startEngine(p))
        p.ctl.excitation = 0.60
        run(p, 8.0) { tend(it) }
        assertTrue("the machine should be making volts", p.genVolts > Spec.RATED_VOLTS * 0.5)

        // Pull the excitation switch out on the board and the field collapses,
        // rheostat or no rheostat.
        p.toggleAux(Service.EXCITATION)
        run(p, 6.0) { tend(it) }
        assertTrue("the field must collapse, flux was ${p.gen.fieldFlux}", p.gen.fieldFlux < 0.05)
        assertTrue("and the volts with it", p.genVolts < Spec.RATED_VOLTS * 0.10)
    }

    @Test
    fun theDispatcherGivesANewOrderEveryNinetySeconds() {
        val p = Plant(11)
        val first = p.grid.dispatchW
        run(p, 89.0)
        assertEquals("the order must hold for the full 90 seconds", 0, p.grid.dispatchStep)
        run(p, 8.0)
        assertEquals(1, p.grid.dispatchStep)
        assertTrue("the figure must actually move", abs(p.grid.dispatchW - first) > 2000.0)
    }

    @Test
    fun theGridIsStiffAndSetsTheFrequencyItself() {
        val p = Plant(12)
        // Nothing the unit does should shift a whole interconnection.
        val readings = ArrayList<Double>()
        run(p, 120.0) { readings.add(it.grid.hz) }
        assertTrue("the system must stay near 60 cycles", readings.all { abs(it - 60.0) < 1.4 })
        assertTrue("but it must not be perfectly still", readings.maxOrNull()!! - readings.minOrNull()!! > 0.05)
    }

    // ------------------------------------------------------------------ station service

    @Test
    fun theIgnitionIsFedFromTheEmergencyLine() {
        val p = Plant(51)
        p.ctl.ignition = IgnitionMode.GRID
        run(p, 1.0)
        assertTrue("ignition should be alive on the line", p.engine.sparkEnergy(p.ctl, 0.0) > 0.8)

        // Pull the ignition switch out on the board and the plugs go dead.
        p.toggleAux(Service.IGNITION)
        run(p, 1.0)
        assertEquals("no spark with the ignition switched out", 0.0, p.engine.sparkEnergy(p.ctl, 0.0), 1e-9)
    }

    @Test
    fun theBatteryCannotCarryTheWholeBoard() {
        val p = Plant(52)
        p.ctl.ignition = IgnitionMode.EMG
        // Everything switched in at once is more than the cells will carry.
        p.ctl.auxClosed[Service.EMG_LIGHTS] = true
        run(p, 2.0)
        assertTrue("the emergency line should be overloaded", p.service.overloaded)
        assertTrue("and its volts should have sagged, was ${p.service.volts}", p.service.volts < 0.90)

        // Shed the lights and it comes back up.
        p.ctl.auxClosed[Service.EMG_LIGHTS] = false
        run(p, 2.0)
        assertTrue("shedding load must restore the line", !p.service.overloaded)
        assertTrue("volts back up, was ${p.service.volts}", p.service.volts > 0.9)
    }

    @Test
    fun losingThePumpsCooksTheEngineEvenWithTheGateOpen() {
        val p = Plant(53)
        assertTrue(startEngine(p))
        p.ctl.waterValve = 1.0            // gate wide open, but both pumps are out
        run(p, 400.0) { pl ->
            trim(pl)
            holdSpeed(pl, 60.0)
            pl.ctl.waterValve = 1.0
            pl.ctl.mainClosed[Service.CIRC_PUMP] = false
            pl.ctl.auxClosed[Service.EMG_PUMP] = false
        }
        assertTrue(
            "with no circulating pump the engine must cook, ended with ${p.failure}",
            p.failure == Failure.OVERHEAT_SEIZED || p.failure == Failure.PISTON_HOLED
        )
    }

    @Test
    fun synchronisingLampsGoDarkOnlyAtCoincidence() {
        val p = Plant(14)
        assertTrue(startEngine(p))
        run(p, 50.0) { pl ->
            tend(pl, pl.grid.hz + 0.15)
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

    // ------------------------------------------------------------------ protection

    /** Get on the bars and settle at roughly the order. */
    private fun onLoad(p: Plant, seconds: Double = 45.0): Boolean {
        if (!startEngine(p)) return false
        if (!synchronise(p)) return false
        run(p, seconds) { pl ->
            trim(pl)
            val err = pl.grid.dispatchKw() - pl.outputKw
            pl.ctl.throttle = (pl.ctl.throttle + err * 0.0012).coerceIn(0.0, 1.0)
        }
        return p.ctl.mainBreakerClosed && !p.ended
    }

    @Test
    fun reversePowerRelayTripsTheUnitAndDropsItsTarget() {
        val p = Plant(201)
        assertTrue(onLoad(p))
        assertTrue("no target should be dropped yet", !p.protection.anyTarget)

        // Shut the throttle while tied on. The bus keeps the machine turning and
        // starts driving it as a motor, which is exactly what the 32 is for.
        run(p, 4.0) { it.ctl.throttle = 0.0; trim(it) }
        assertTrue("the machine should be motoring, was ${p.outputKw} kW", p.outputKw < Protection.REVERSE_POWER_KW)
        assertTrue("and the relay should be timing out", p.protection.reversePower.pickedUp)

        run(p, 12.0) { it.ctl.throttle = 0.0; trim(it) }
        assertTrue("the reverse power relay must operate", p.protection.reversePower.target)
        assertTrue("and it must trip the unit off the bars", !p.ctl.mainBreakerClosed)
        assertEquals("a relay trip is not a wreck", Failure.NONE, p.failure)
        assertEquals(1, p.relayTrips)
    }

    @Test
    fun theBreakerIsInterlockedUntilTheTargetIsReset() {
        val p = Plant(202)
        assertTrue(onLoad(p))
        run(p, 32.0) { it.ctl.throttle = 0.0; trim(it) }
        assertTrue("expected a trip", p.protection.anyTarget)

        // The closing mechanism is held out while the target is down, however
        // well you have re-matched the machine.
        p.ctl.throttle = 0.42
        run(p, 20.0) { tend(it, it.grid.hz + 0.05); matchVolts(it) }
        p.toggleBreaker()
        assertTrue("the interlock must hold the breaker out", !p.ctl.mainBreakerClosed)
        assertTrue("and say so", p.events.interlocked)

        // Reset the target by hand and it will close again.
        val which = p.protection.relays.indexOfFirst { it.target }
        p.resetTarget(which)
        assertTrue("the target must clear", !p.protection.anyTarget)
        assertTrue("and then it will parallel again", synchronise(p))
    }

    @Test
    fun theOvercurrentRelayIsInverseTime() {
        fun timeToOperate(ampsPu: Double): Double {
            val pr = Protection()
            var t = 0.0
            while (t < 400.0 && !pr.overcurrent.target) {
                pr.step(1.0 / 60.0, true, 300.0, ampsPu, 0.6, 60.0)
                t += 1.0 / 60.0
            }
            return t
        }
        val gentle = timeToOperate(1.25)
        val hard = timeToOperate(1.90)
        assertTrue("a small overload must take a while, took $gentle s", gentle > 8.0)
        assertTrue("a big one must be much quicker, took $hard s", hard < gentle * 0.5)
        // Inside the setting it must never operate at all.
        assertTrue("full load is not an overload", timeToOperate(1.00) > 399.0)
    }

    @Test
    fun theDifferentialRelayDoesNotWait() {
        val pr = Protection()
        pr.step(1.0 / 60.0, true, 300.0, Protection.DIFFERENTIAL_PU + 0.1, 0.6, 60.0)
        assertTrue("87 is instantaneous", pr.differential.target)
    }

    @Test
    fun theFrequencyRelayTakesYouOffWhenTheSystemGoes() {
        val pr = Protection()
        var t = 0.0
        while (t < 10.0 && !pr.underFrequency.target) {
            pr.step(1.0 / 60.0, true, 300.0, 0.8, 0.6, 56.9)
            t += 1.0 / 60.0
        }
        assertTrue("81 must operate below the limit", pr.underFrequency.target)
        assertTrue("and take about its time setting, took $t s", t > 3.0 && t < 6.0)

        // Off the bars it watches nothing, because there is nothing to watch.
        val idle = Protection()
        repeat(1200) { idle.step(1.0 / 60.0, false, 0.0, 0.0, 0.0, 56.0) }
        assertTrue("off the bars the relays must stay put", !idle.anyTarget)
    }

    @Test
    fun lossOfFieldOnlyCountsWhenTheMachineIsCarryingSomething() {
        val loaded = Protection()
        repeat(600) { loaded.step(1.0 / 60.0, true, 300.0, 0.9, 0.05, 60.0) }
        assertTrue("40 must operate on load", loaded.lossOfField.target)

        val floating = Protection()
        repeat(600) { floating.step(1.0 / 60.0, true, 2.0, 0.02, 0.05, 60.0) }
        assertTrue("an idling machine with no field is not a fault", !floating.lossOfField.target)
    }

    // ------------------------------------------------------------------ cylinders

    @Test
    fun aCutOutCylinderCostsAboutASixthOfThePower() {
        val p = Plant(210)
        assertTrue(startEngine(p))
        run(p, 20.0) { tend(it) }
        val before = p.engine.cylinders.sumOf { it.firingSuccess }

        p.ctl.igniterCutOut[3] = true
        run(p, 20.0) { tend(it) }
        val after = p.engine.cylinders.sumOf { it.firingSuccess }

        assertTrue("the cut pot must stop firing", p.engine.cylinders[3].firingSuccess < 0.05)
        assertTrue("the other five must carry on", p.engine.cylinders.count { it.firingSuccess > 0.5 } == 5)
        val lost = (before - after) / before
        assertTrue("about a sixth of the fire should be gone, lost $lost", lost > 0.10 && lost < 0.28)
    }

    @Test
    fun theExhaustPyrometerFindsTheDeadCylinder() {
        val p = Plant(211)
        assertTrue(startEngine(p))
        run(p, 40.0) { tend(it) }
        p.ctl.igniterCutOut[1] = true
        run(p, 60.0) { tend(it) }

        val cold = p.engine.cylinders[1].exhaustC
        val others = p.engine.cylinders.filterIndexed { i, _ -> i != 1 }.map { it.exhaustC }
        assertTrue("the cut pot must go cold, read $cold", cold < others.min() * 0.5)
        assertTrue("and the rest must stay hot, coldest was ${others.min()}", others.min() > 120.0)
    }

    @Test
    fun aShutSightFeedScoresItsOwnLinerAndNoOther() {
        val p = Plant(212)
        assertTrue(startEngine(p))
        // Shut one feed right off and open the rest wide, then work it.
        run(p, 260.0) { pl ->
            tend(pl)
            pl.ctl.oilerRate = 1.0
            for (i in pl.ctl.sightFeed.indices) pl.ctl.sightFeed[i] = if (i == 4) 0.0 else 0.85
        }
        val starved = p.engine.cylinders[4]
        assertTrue("the shut pot must lose its film, was ${starved.oilFilm}", starved.oilFilm < 0.2)
        assertTrue("and start to pick up, wear ${starved.wear}", starved.wear > 0.05)
        val healthy = p.engine.cylinders.filterIndexed { i, _ -> i != 4 }
        assertTrue("the fed pots must be fine", healthy.all { it.oilFilm > 0.75 })
        assertTrue("and unmarked", healthy.all { it.wear < 0.001 })
    }

    @Test
    fun runningOnePotDryEventuallyScoresTheLiner() {
        val p = Plant(213)
        assertTrue(startEngine(p))
        run(p, 1200.0) { pl ->
            tend(pl)
            pl.ctl.oilerRate = 0.9
            for (i in pl.ctl.sightFeed.indices) pl.ctl.sightFeed[i] = if (i == 0) 0.0 else 0.8
        }
        assertTrue(
            "expected a scored liner or a thrown rod, got ${p.failure}",
            p.failure == Failure.LINER_SCORED || p.failure == Failure.THROWN_ROD
        )
    }

    // ------------------------------------------------------------------ the tanks

    @Test
    fun shuttingTheFuelCockStopsTheEngine() {
        val p = Plant(220)
        assertTrue(startEngine(p))
        p.ctl.fuelCock = false
        run(p, 12.0) { tend(it) }
        assertTrue("no fuel, no fire", p.engine.firingSuccess < 0.05)
        // A flywheel this size takes a minute and a half to run down on its own.
        run(p, 55.0) { tend(it) }
        assertTrue("and it must be coasting down hard, rpm ${p.rpm}", p.rpm < 350.0)
    }

    @Test
    fun theDayTankRunsDryAndTheTransferPumpFillsItAgain() {
        val p = Plant(221)
        assertTrue(startEngine(p))
        p.aux.dayTankL = 26.0
        run(p, 40.0) { pl -> tend(pl); pl.ctl.throttle = 0.85 }
        assertTrue("the gravity tank should be starving it", p.aux.fuelStarved)
        assertTrue("which shows as a weak fire, was ${p.engine.firingSuccess}", p.engine.firingSuccess < 0.6)

        // The transfer pump is a main bus load, so it needs the main bus alive.
        val before = p.aux.dayTankL
        p.ctl.fuelTransfer = true
        run(p, 60.0) { tend(it) }
        assertTrue("the pump must lift fuel, ${p.aux.dayTankL} vs $before", p.aux.dayTankL > before + 20.0)
        assertTrue("and it must come out of the main tank", p.aux.mainTankL < Auxiliaries.MAIN_TANK_CAP_L)
        assertTrue("the fire should come back", p.engine.firingSuccess > 0.75)
    }

    @Test
    fun overfillingTheDayTankSpillsOutOfTheOverflow() {
        val p = Plant(222)
        assertTrue(startEngine(p))
        p.aux.dayTankL = Auxiliaries.DAY_TANK_CAP_L - 2.0
        p.ctl.fuelTransfer = true
        run(p, 60.0) { tend(it) }
        assertTrue("it must go on the floor, spilled ${p.aux.spilledL}", p.aux.spilledL > 5.0)
        assertTrue("and the tank cannot hold more than it holds", p.aux.dayTankL <= Auxiliaries.DAY_TANK_CAP_L + 1e-6)
    }

    @Test
    fun anEmptyHeaderMakesTheGateValveMeaningless() {
        val p = Plant(223)
        assertTrue(startEngine(p))
        p.aux.headerL = 8.0
        p.ctl.waterValve = 1.0
        run(p, 4.0) { pl -> trim(pl); pl.ctl.waterValve = 1.0 }
        assertTrue("nothing to circulate", p.engine.coolantFlowPu < 0.2)

        // The make-up valve off the town main is what puts it back.
        run(p, 90.0) { pl -> trim(pl); pl.ctl.waterValve = 1.0; pl.ctl.makeUpValve = 1.0 }
        assertTrue("the header must fill, was ${p.aux.headerL}", p.aux.headerL > 60.0)
        assertTrue("and the pumps come good again", p.engine.coolantFlowPu > 0.8)
    }

    @Test
    fun theJacketBoilsWaterAwayAndTheOutletReadsHotterThanTheIron() {
        val p = Plant(224)
        assertTrue(startEngine(p))
        val before = p.aux.headerL
        run(p, 300.0) { pl -> tend(pl); pl.ctl.throttle = 0.75 }
        assertTrue("a hot jacket must lose water, ${p.aux.headerL} from $before", p.aux.headerL < before - 1.0)
        assertTrue(
            "the outlet must read above the iron: ${p.aux.jacketOutletC} vs ${p.engine.jacketTempC}",
            p.aux.jacketOutletC > p.engine.jacketTempC
        )
    }

    @Test
    fun theSumpRunsDownAndTheHandPumpPutsItBack() {
        val p = Plant(225)
        assertTrue(startEngine(p))
        val before = p.aux.sumpL
        run(p, 400.0) { pl -> tend(pl); pl.ctl.oilerRate = 1.0 }
        assertTrue("the oiler must use oil, ${p.aux.sumpL} from $before", p.aux.sumpL < before - 1.0)

        val low = p.aux.sumpL
        run(p, 30.0) { pl -> tend(pl); pl.ctl.oilReplenish = true }
        assertTrue("the hand pump must put it back, ${p.aux.sumpL} from $low", p.aux.sumpL > low + 3.0)
    }

    // ------------------------------------------------------------------ new switchgear

    @Test
    fun theStationTransformerBreakerDropsTheMainBusAndNothingElse() {
        val p = Plant(230)
        assertTrue(startEngine(p))
        assertTrue("the main bus should be alive", p.mainBus.volts > 0.9)

        p.ctl.stationTxBreakerClosed = false
        run(p, 3.0) { tend(it) }
        assertTrue("the main bus must go dead", p.mainBus.volts < 0.05)
        assertTrue("and take the circulating pump with it", !p.mainBus.isRunning(Service.CIRC_PUMP))
        // The emergency line has its own transformer and does not care.
        assertTrue("the emergency line must hold up", p.service.volts > 0.8)
        assertTrue("and the engine keeps running", p.running)
    }

    @Test
    fun theStartingTransformerBreakerLocksOutGridSupply() {
        val p = Plant(231)
        p.ctl.ignition = IgnitionMode.GRID
        run(p, 1.0)
        assertTrue("GRID should be good to start on", p.service.volts > 0.8)

        p.ctl.startingTxBreakerClosed = false
        run(p, 1.0)
        assertTrue("with the tap locked out there is nothing there", p.service.volts < 0.05)
        assertEquals("and no spark", 0.0, p.engine.sparkEnergy(p.ctl, 0.0), 1e-9)
    }

    @Test
    fun openingTheFieldSwitchCollapsesTheFieldThroughItsResistor() {
        val p = Plant(232)
        assertTrue(startEngine(p))
        p.ctl.excitation = 0.60
        run(p, 8.0) { tend(it) }
        assertTrue("the machine should be excited", p.gen.fieldFlux > 0.4)

        p.toggleFieldSwitch()
        run(p, 6.0) { tend(it) }
        assertTrue("the field must go", p.gen.fieldFlux < 0.05)
        assertTrue("opening it is the safe way and costs nothing", p.fieldInsulation < 1e-9)
    }

    @Test
    fun throwingTheFieldSwitchInHotDamagesTheInsulation() {
        val p = Plant(233)
        assertTrue(startEngine(p))
        p.ctl.excitation = 0.75
        run(p, 6.0) { tend(it) }

        // Out is free; in with the rheostat still up is not.
        p.toggleFieldSwitch()
        run(p, 2.0) { tend(it) }
        val clean = p.fieldInsulation
        p.toggleFieldSwitch()
        assertTrue("throwing it in hot must mark the winding", p.fieldInsulation > clean)
        assertTrue("and it should have said so", p.events.fieldSurge)

        // Do it the right way and it costs nothing further.
        val marked = p.fieldInsulation
        p.ctl.excitation = 0.0
        run(p, 2.0) { tend(it) }
        p.toggleFieldSwitch()
        p.toggleFieldSwitch()
        assertEquals("rheostat at the bottom first is free", marked, p.fieldInsulation, 1e-9)
    }

    @Test
    fun enoughHotSwitchingOfTheFieldFlashesItOver() {
        val p = Plant(234)
        assertTrue(startEngine(p))
        p.ctl.excitation = 0.95
        run(p, 6.0) { tend(it) }
        var guard = 0
        while (!p.ended && guard++ < 40) {
            p.toggleFieldSwitch()   // out
            p.toggleFieldSwitch()   // and straight back in, hot
            run(p, 0.5) { tend(it) }
        }
        assertEquals("it must let go eventually", Failure.FIELD_FLASHOVER, p.failure)
    }

}
