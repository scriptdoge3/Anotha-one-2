package com.dynamo.powerplant

import com.dynamo.powerplant.sim.Controls
import com.dynamo.powerplant.sim.Engine
import com.dynamo.powerplant.sim.Failure
import com.dynamo.powerplant.sim.IgnitionMode
import com.dynamo.powerplant.sim.Plant
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

        // Bring the field up as soon as it will stand it: the main bus hangs off
        // the station transformer, and until it is alive there is no circulating
        // pump, no oil pump and nothing for the tie to carry.
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
    fun theMainBusTieGivesNothingUntilTheMachineIsExcited() {
        val p = Plant(1)
        p.ctl.ignition = IgnitionMode.GEN
        // Stone cold: no volts on the terminals, so no station transformer, so
        // no main bus and nothing for the tie to carry.
        run(p, 1.0)
        assertTrue("the tie must be dead at rest", p.engine.sourceStrength(p.ctl, 0.0) < 0.02)

        // Turning and excited, it is as good a supply as the machine is.
        assertTrue(startEngine(p))
        assertTrue("the main bus should be alive", p.mainBus.volts > 0.9)
        assertTrue("and the tie with it", p.engine.sourceStrength(p.ctl, p.rpm) > 0.9)
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
        assertTrue("and the main bus should be dead with it", slow.mainBus.volts < 0.05)

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
        // The water pump alone is 14 kW off the cells, so emergency supply is a
        // clock: get it lit and get across to the machine.
        val p = Plant(78)
        p.ctl.ignition = IgnitionMode.EMG
        p.engine.starterEngaged = true
        run(p, 60.0)
        assertTrue("the cells must be well down, was ${p.engine.batteryCharge}", p.engine.batteryCharge < 0.25)
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
    fun theMachineMustBeExcitedToCharge() {
        val p = Plant(92)
        // Stone cold and nothing turning: the generator terminals are dead, so
        // the emergency transformer has nothing to work on however the board is
        // set.
        p.ctl.ignition = IgnitionMode.GRID
        p.engine.batteryCharge = 0.50
        run(p, 4.0)
        assertTrue("dead terminals cannot charge", !p.engine.batteryChargingNow)
        val held = p.engine.batteryCharge
        run(p, 10.0)
        assertTrue("and the cells must not rise", p.engine.batteryCharge <= held + 1e-6)
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
}
