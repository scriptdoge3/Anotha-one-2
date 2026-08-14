package com.dynamo.powerplant

import android.graphics.Bitmap
import android.graphics.Canvas
import com.dynamo.powerplant.sim.IgnitionMode
import com.dynamo.powerplant.sim.Plant
import com.dynamo.powerplant.ui.Layout
import com.dynamo.powerplant.ui.PanelRenderer
import com.dynamo.powerplant.ui.Tab
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * Renders the board to PNG files so the panel can actually be looked at without
 * a device. Output lands in app/build/screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderTest {

    private val outDir = File("build/screens").apply { mkdirs() }

    private var lastHz = 0.0

    private fun trim(p: Plant) {
        val cf = 0.50 * ((50.0 - p.engine.jacketTempC) / 42.0).coerceIn(0.0, 1.0)
        p.ctl.mixture = ((17.5 - 12.5 / (1.0 + cf)) / 8.0).coerceIn(0.0, 1.0)
        p.ctl.sparkLever = ((p.engine.optimalAdvanceDeg(p.rpm, 12.5) + 5.0) / 43.0).coerceIn(0.0, 1.0)
        p.ctl.oilerRate = (0.30 + p.rpm / 600.0 * 0.55 + (1.0 - p.engine.oilFilm) * 1.5).coerceIn(0.0, 1.0)
        p.ctl.waterValve = (p.ctl.waterValve + (p.engine.jacketTempC - 78.0) * 0.004).coerceIn(0.0, 1.0)
    }

    private fun hold(p: Plant, target: Double, dt: Double) {
        val err = target - p.hz
        val rate = (p.hz - lastHz) / dt
        lastHz = p.hz
        p.ctl.throttle = (p.ctl.throttle + (err * 0.075 - rate * 0.30) * dt).coerceIn(0.0, 1.0)
    }

    /** Turn the selector one notch at a time in whichever direction is needed. */
    private fun sweepTo(p: Plant, target: IgnitionMode, secondsPerNotch: Double = 0.25) {
        var guard = 0
        while (p.ctl.ignition != target && guard++ < 12) {
            p.ctl.ignition =
                if (target.ordinal > p.ctl.ignition.ordinal) p.ctl.ignition.clockwise()
                else p.ctl.ignition.anticlockwise()
            run(p, secondsPerNotch)
        }
    }

    private fun run(p: Plant, seconds: Double, each: (Plant) -> Unit = {}) {
        val dt = 1.0 / 60.0
        var t = 0.0
        while (t < seconds && !p.ended) { each(p); p.step(dt); t += dt }
    }

    private fun shoot(name: String, p: Plant, tab: Tab = Tab.CONTROL) {
        val l = Layout(Layout.VIRTUAL_W, 2340f)
        val r = PanelRenderer(l)
        val bmp = Bitmap.createBitmap(l.w.toInt(), l.h.toInt(), Bitmap.Config.ARGB_8888)
        r.draw(Canvas(bmp), p, 1000L, emptySet(), tab)
        val f = File(outDir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        r.release()
        bmp.recycle()
        assertTrue("wrote $f", f.length() > 4000)
        println("wrote ${f.absolutePath} (${f.length()} bytes)")
    }

    @Test
    fun coldBoardAtShiftStart() {
        val p = Plant(101)
        p.step(0.1)
        shoot("01-cold-start", p)
    }

    @Test
    fun crankingOnTheStarter() {
        val p = Plant(102)
        p.ctl.mixture = 0.88
        p.ctl.sparkLever = 0.12
        p.ctl.throttle = 0.35
        p.ctl.waterValve = 0.15
        p.ctl.oilerRate = 0.5
        p.prime()
        p.ctl.compressionRelease = true
        p.ctl.ignition = IgnitionMode.EMG
        p.engine.starterEngaged = true
        run(p, 2.5)
        shoot("02-cranking", p)
    }

    @Test
    fun runningAndReadyToSynchronise() {
        val p = Plant(103)
        lastHz = 0.0
        p.ctl.waterValve = 0.30; p.ctl.oilerRate = 0.55
        p.ctl.mixture = 0.88; p.ctl.sparkLever = 0.12; p.ctl.throttle = 0.35
        p.prime(); p.ctl.compressionRelease = true
        p.ctl.ignition = IgnitionMode.EMG; p.engine.starterEngaged = true
        run(p, 3.5); p.ctl.compressionRelease = false; run(p, 5.0)
        p.engine.starterEngaged = false
        p.ctl.excitation = 0.60          // the main bus hangs off the station transformer
        run(p, 50.0) { trim(it); hold(it, 60.0, 1.0 / 60.0) }
        sweepTo(p, IgnitionMode.GEN)
        run(p, 20.0) {
            trim(it); hold(it, it.grid.hz + 0.10, 1.0 / 60.0)
            it.ctl.excitation = (it.ctl.excitation + (it.grid.volts - it.genVolts) * 0.0035 / 60.0).coerceIn(0.0, 1.0)
        }
        // stop where the lamps are bright, so the sync gear is clearly doing something
        var guard = 0
        while (guard++ < 40000 && !p.ended && p.lampBrightness() < 0.85) p.step(1.0 / 240.0)
        shoot("03-synchronising", p)
    }

    @Test
    fun onTheBusCarryingTheTown() {
        val p = Plant(104)
        lastHz = 0.0
        p.ctl.waterValve = 0.30; p.ctl.oilerRate = 0.55
        p.ctl.mixture = 0.88; p.ctl.sparkLever = 0.12; p.ctl.throttle = 0.35
        p.prime(); p.ctl.compressionRelease = true
        p.ctl.ignition = IgnitionMode.EMG; p.engine.starterEngaged = true
        run(p, 3.5); p.ctl.compressionRelease = false; run(p, 5.0)
        p.engine.starterEngaged = false
        p.ctl.excitation = 0.60          // the main bus hangs off the station transformer
        run(p, 50.0) { trim(it); hold(it, 60.0, 1.0 / 60.0) }
        sweepTo(p, IgnitionMode.GEN)
        run(p, 50.0) {
            trim(it); hold(it, it.grid.hz + 0.10, 1.0 / 60.0)
            it.ctl.excitation = (it.ctl.excitation + (it.grid.volts - it.genVolts) * 0.0035 / 60.0).coerceIn(0.0, 1.0)
        }
        var guard = 0
        while (guard++ < 60000 && !p.ended) {
            trim(p); hold(p, p.grid.hz + 0.10, 1.0 / 240.0)
            p.ctl.excitation = (p.ctl.excitation + (p.grid.volts - p.genVolts) * 0.0035 / 240.0).coerceIn(0.0, 1.0)
            p.step(1.0 / 240.0)
            if (abs(p.syncPhase) < 0.06 && abs(p.slipHz) < 0.30) { p.toggleBreaker(); break }
        }
        run(p, 90.0) { pl ->
            trim(pl)
            val err = pl.grid.dispatchKw() - pl.outputKw
            pl.ctl.throttle = (pl.ctl.throttle + err * 0.0012).coerceIn(0.0, 1.0)
        }
        shoot("04-on-the-bus", p)
        shoot("06-electrical-tied", p, tab = Tab.ELECTRICAL)
    }

    @Test
    fun theElectricalDeckBeforeAnythingIsRunning() {
        val p = Plant(106)
        p.ctl.ignition = IgnitionMode.GRID
        run(p, 3.0)
        shoot("07-electrical-cold", p, tab = Tab.ELECTRICAL)
    }

    @Test
    fun theShiftEndsBadly() {
        val p = Plant(105)
        lastHz = 0.0
        p.ctl.waterValve = 0.30; p.ctl.oilerRate = 0.55
        p.ctl.mixture = 0.88; p.ctl.sparkLever = 0.12; p.ctl.throttle = 0.35
        p.prime(); p.ctl.compressionRelease = true
        p.ctl.ignition = IgnitionMode.EMG; p.engine.starterEngaged = true
        run(p, 3.5); p.ctl.compressionRelease = false; run(p, 5.0)
        p.engine.starterEngaged = false
        p.ctl.excitation = 0.60          // the main bus hangs off the station transformer
        run(p, 50.0) { trim(it); hold(it, 60.0, 1.0 / 60.0) }
        // walk away from the water gate
        p.ctl.throttle = 0.9
        run(p, 600.0) { it.ctl.waterValve = 0.0; it.ctl.oilerRate = 0.8 }
        assertTrue("expected a wreck, got ${p.failure}", p.ended)
        shoot("05-wrecked", p)
    }
}
