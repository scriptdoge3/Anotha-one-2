package com.dynamo.powerplant

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.dynamo.powerplant.game.GameView
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
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * The tests that go looking for the crash rather than the physics.
 *
 * A game loop that throws once an hour is worse than one that is wrong all the
 * time, because you cannot see it coming. These beat on the board at random,
 * across every deck, for far longer than anyone would play, and check that
 * nothing throws and no number has quietly gone to NaN.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StressTest {

    private val screenW = 1080
    private val screenH = 2340

    /**
     * The background bitmap is built on the game thread and recycled from the UI
     * thread, so the two must be mutually exclusive. Without it the loop can
     * recycle the bitmap between the null check and the draw, and the game dies
     * with "cannot draw recycled bitmaps" — sometimes, which is the worst kind
     * of bug to be handed.
     */
    @Test
    fun drawAndReleaseAreMutuallyExclusive() {
        val draw = PanelRenderer::class.java.declaredMethods.first { it.name == "draw" }
        val release = PanelRenderer::class.java.declaredMethods.first { it.name == "release" }
        assertTrue("draw must be synchronised against release", Modifier.isSynchronized(draw.modifiers))
        assertTrue("release must be synchronised against draw", Modifier.isSynchronized(release.modifiers))
    }

    /**
     * A smoke test alongside it: hammer both from two threads at once. This is
     * weaker than the check above — the window is narrow enough that it will not
     * reproduce the fault reliably — but it does prove the two can be called
     * concurrently at all.
     */
    @Test
    fun releasingWhileDrawingDoesNotThrow() {
        val l = Layout(Layout.VIRTUAL_W, 2340f)
        val r = PanelRenderer(l)
        val p = Plant(1)
        p.step(0.5)
        val bmp = Bitmap.createBitmap(l.w.toInt(), l.h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val blew = AtomicReference<Throwable?>(null)

        val releaser = Thread {
            try {
                repeat(240) {
                    r.release()
                    Thread.sleep(0, 200_000)
                }
            } catch (t: Throwable) {
                blew.set(t)
            }
        }
        releaser.start()
        try {
            repeat(240) { r.draw(c, p, 1000L, emptySet(), Tab.entries[it % Tab.entries.size]) }
        } catch (t: Throwable) {
            blew.set(t)
        }
        releaser.join(5000)

        assertTrue("drawing while releasing threw ${blew.get()}", blew.get() == null)
        r.release()
        bmp.recycle()
    }

    /**
     * Thump every deck at random for a long while, drawing every frame, and see
     * whether anything falls over. This is the one that catches the index that
     * only goes out of range when a particular switch happens to be open.
     */
    @Test
    fun randomThumpingNeverThrows() {
        val view = GameView(ApplicationProvider.getApplicationContext())
        view.surfaceChanged(view.holder, 0, screenW, screenH)
        val vh = Layout.virtualHeight(screenW, screenH)
        val l = Layout(Layout.VIRTUAL_W, vh)
        val r = PanelRenderer(l)
        val scale = screenW / Layout.VIRTUAL_W
        val offY = (screenH - vh * scale) / 2f
        val bmp = Bitmap.createBitmap(l.w.toInt(), l.h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(20250814)

        fun send(action: Int, x: Float, y: Float) {
            val t = SystemClock.uptimeMillis()
            val e = MotionEvent.obtain(t, t, action, x * scale, y * scale + offY, 0)
            view.onTouchEvent(e)
            e.recycle()
        }

        val p = view.plant
        repeat(6000) { step ->
            // A finger somewhere on the board, dragged a little, then lifted.
            val x = rnd.nextFloat() * Layout.VIRTUAL_W
            val y = rnd.nextFloat() * vh
            send(MotionEvent.ACTION_DOWN, x, y)
            if (rnd.nextInt(3) == 0) {
                send(MotionEvent.ACTION_MOVE, x + (rnd.nextFloat() - 0.5f) * 300f, y + (rnd.nextFloat() - 0.5f) * 300f)
            }
            send(MotionEvent.ACTION_UP, x, y)

            // Step through the view, so a queued reset is carried out exactly
            // the way the loop would carry it out.
            view.advance(1.0 / 30.0)
            r.draw(c, p, 1000L + step * 33L, emptySet(), view.tab)

            assertTrue("rpm went bad at step $step: ${p.rpm}", p.rpm.isFinite())
            assertTrue("phase went bad at step $step", p.syncPhase.isFinite())
            assertTrue("output went bad at step $step", p.outputKw.isFinite())
            assertTrue("the emergency line went bad at step $step", p.service.volts.isFinite())
            assertTrue("the main bus went bad at step $step", p.mainBus.volts.isFinite())
            assertTrue("the jacket went bad at step $step", p.engine.jacketTempC.isFinite())
            for (cyl in p.engine.cylinders) {
                assertTrue("cylinder ${cyl.number} exhaust went bad", cyl.exhaustC.isFinite())
                assertTrue("cylinder ${cyl.number} film went bad", cyl.oilFilm.isFinite())
            }
            assertTrue("the day tank went bad at step $step", p.aux.dayTankL.isFinite())
            assertTrue("the header went bad at step $step", p.aux.headerL.isFinite())
        }
        r.release()
        bmp.recycle()
    }

    /**
     * The other way a game loop dies: a state nobody drew before. Walk the board
     * through the states that are awkward to reach by playing — wrecked, every
     * switch out, every fuse gone, every tank empty — and draw each of them on
     * every deck.
     */
    @Test
    fun awkwardStatesAllDraw() {
        val l = Layout(Layout.VIRTUAL_W, 2340f)
        val r = PanelRenderer(l)
        val bmp = Bitmap.createBitmap(l.w.toInt(), l.h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val states: List<Pair<String, (Plant) -> Unit>> = listOf(
            "untouched" to { _ -> },
            "everything open" to { p ->
                p.ctl.mainBreakerClosed = false
                p.ctl.stationTxBreakerClosed = false
                p.ctl.startingTxBreakerClosed = false
                p.ctl.emgTxBreakerClosed = false
                p.ctl.batteryBreakerClosed = false
                p.ctl.fieldSwitchClosed = false
                for (i in p.ctl.mainClosed.indices) p.ctl.mainClosed[i] = false
                for (i in p.ctl.auxClosed.indices) p.ctl.auxClosed[i] = false
            },
            "every fuse gone" to { p ->
                for (load in p.mainBus.loads) load.fuseBlown = true
                for (load in p.service.loads) load.fuseBlown = true
            },
            "every target dropped" to { p ->
                repeat(400) { p.protection.step(1.0 / 60.0, true, -400.0, 3.0, 0.0, 50.0) }
            },
            "every pot cut out" to { p ->
                for (i in p.ctl.igniterCutOut.indices) p.ctl.igniterCutOut[i] = true
            },
            "every tank empty" to { p ->
                p.aux.dayTankL = 0.0; p.aux.mainTankL = 0.0
                p.aux.headerL = 0.0; p.aux.sumpL = 0.0
            },
            "every tank overfull" to { p ->
                p.aux.dayTankL = 1e4; p.aux.headerL = 1e4; p.aux.sumpL = 1e4
            },
            "wrecked" to { p ->
                p.engine.knockDamage = 1.0
            },
            "flat out" to { p ->
                p.ctl.throttle = 1.0; p.ctl.excitation = 1.0
                p.ctl.mainBreakerClosed = true
            }
        )

        for ((name, setUp) in states) {
            for (tab in Tab.entries) {
                val p = Plant(7)
                p.step(0.5)
                setUp(p)
                repeat(30) { p.step(1.0 / 30.0) }
                r.draw(c, p, 1000L, setOf("primer", "oilfill"), tab)
                assertTrue("$name on $tab left rpm bad", p.rpm.isFinite())
            }
        }
        r.release()
        bmp.recycle()
    }

    /**
     * Backgrounding, rotating, locking the screen: the lifecycle churn that a
     * phone does to you unasked.
     *
     * The loop must turn only while the activity is resumed *and* the surface
     * exists. Leaving it turning after the surface has gone is what kills the
     * game on a screen lock — the next `lockCanvas` throws on the game thread,
     * where nothing catches it.
     *
     * This asserts the state machine rather than counting live threads: under
     * Robolectric the game thread exits on the first frame anyway, so a thread
     * count would pass whatever the code did.
     */
    @Test
    fun theLoopTurnsOnlyWithBothASurfaceAndAResumedActivity() {
        val view = GameView(ApplicationProvider.getApplicationContext())
        assertTrue("nothing before either arrives", !view.loopRunning)

        // A surface with no activity behind it is not enough.
        view.surfaceCreated(view.holder)
        view.surfaceChanged(view.holder, 0, screenW, screenH)
        assertTrue("a surface alone must not start it", !view.loopRunning)

        view.resume()
        assertTrue("surface plus resumed starts it", view.loopRunning)
        view.resume()
        assertTrue("and a second resume changes nothing", view.loopRunning)

        // The system takes the surface away without warning.
        view.surfaceDestroyed(view.holder)
        assertTrue("losing the surface must stop it", !view.loopRunning)

        // Still resumed, so getting the surface back starts it again.
        view.surfaceCreated(view.holder)
        assertTrue("and getting it back starts it again", view.loopRunning)

        view.pause()
        assertTrue("pausing must stop it", !view.loopRunning)
        view.surfaceCreated(view.holder)
        assertTrue("a surface while paused must not start it", !view.loopRunning)

        // And the whole churn again, in the other order, many times over.
        repeat(20) {
            view.resume()
            view.surfaceCreated(view.holder)
            assertTrue("should be turning", view.loopRunning)
            view.pause()
            view.surfaceDestroyed(view.holder)
            assertTrue("should be stopped", !view.loopRunning)
        }

        val stragglers = Thread.getAllStackTraces().keys.count { it.name == "dynamo-room" && it.isAlive }
        assertTrue("no loop may outlive the churn, saw $stragglers", stragglers == 0)
    }
}
