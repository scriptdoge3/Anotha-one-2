package com.dynamo.powerplant

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.dynamo.powerplant.game.GameView
import com.dynamo.powerplant.sim.IgnitionMode
import com.dynamo.powerplant.ui.Layout
import com.dynamo.powerplant.ui.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the board through real touch events, so the control wiring is exercised
 * end to end without a device: every hand control must move the thing it is
 * connected to, and nothing may throw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlsTouchTest {

    private val screenW = 1080
    private val screenH = 2340

    private lateinit var view: GameView
    private lateinit var L: Layout
    private var scale = 1f
    private var offY = 0f

    @Before
    fun setUp() {
        view = GameView(ApplicationProvider.getApplicationContext())
        view.surfaceChanged(view.holder, 0, screenW, screenH)
        val vh = Layout.virtualHeight(screenW, screenH)
        L = Layout(Layout.VIRTUAL_W, vh)
        scale = screenW / Layout.VIRTUAL_W
        offY = (screenH - vh * scale) / 2f
    }

    // --- turning virtual board coordinates back into screen pixels ---
    private fun sx(x: Float) = x * scale
    private fun sy(y: Float) = y * scale + offY

    private fun send(action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, action, sx(x), sy(y), 0)
        view.onTouchEvent(e)
        e.recycle()
    }

    /** Switch decks the way the player does, by hitting the tab bar. */
    private fun showTab(t: Tab) {
        val r = L.tabRect(t.ordinal)
        send(MotionEvent.ACTION_DOWN, r.centerX(), r.centerY())
        send(MotionEvent.ACTION_UP, r.centerX(), r.centerY())
        assertEquals("should be showing $t", t, view.tab)
    }

    private fun tap(x: Float, y: Float) {
        send(MotionEvent.ACTION_DOWN, x, y)
        send(MotionEvent.ACTION_UP, x, y)
    }

    private fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float, steps: Int = 8) {
        send(MotionEvent.ACTION_DOWN, fromX, fromY)
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            send(MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * t, fromY + (toY - fromY) * t)
        }
        send(MotionEvent.ACTION_UP, toX, toY)
    }

    @Test
    fun throttleLeverFollowsTheFinger() {
        val p = view.plant
        p.ctl.throttle = 0.0
        val cx = L.throttleLever.centerX()
        drag(cx, L.throttleLever.bottom - 10f, cx, L.throttleLever.top + 10f)
        assertTrue("throttle should have opened, was ${p.ctl.throttle}", p.ctl.throttle > 0.75)

        drag(cx, L.throttleLever.top + 10f, cx, L.throttleLever.bottom - 10f)
        assertTrue("throttle should have shut again, was ${p.ctl.throttle}", p.ctl.throttle < 0.25)
    }

    @Test
    fun sparkLeverFollowsTheFinger() {
        val p = view.plant
        p.ctl.sparkLever = 0.0
        val cx = L.sparkLever.centerX()
        drag(cx, L.sparkLever.bottom - 10f, cx, L.sparkLever.top + 10f)
        assertTrue("spark should be advanced, was ${p.ctl.sparkLever}", p.ctl.sparkLever > 0.75)
    }

    @Test
    fun handwheelsTurnOnAVerticalDrag() {
        val p = view.plant
        p.ctl.mixture = 0.0
        drag(L.mixtureKnob.x, L.mixtureKnob.y, L.mixtureKnob.x, L.mixtureKnob.y - L.mixtureKnobR * 4f)
        assertTrue("mixture should have richened, was ${p.ctl.mixture}", p.ctl.mixture > 0.5)

        p.ctl.excitation = 0.0
        drag(L.excitationKnob.x, L.excitationKnob.y, L.excitationKnob.x, L.excitationKnob.y - L.excitationKnobR * 4f)
        assertTrue("field should have come up, was ${p.ctl.excitation}", p.ctl.excitation > 0.5)

        p.ctl.waterValve = 0.0
        drag(L.waterWheel.x, L.waterWheel.y, L.waterWheel.x, L.waterWheel.y - L.waterWheelR * 4f)
        assertTrue("water gate should have opened, was ${p.ctl.waterValve}", p.ctl.waterValve > 0.5)

        p.ctl.oilerRate = 0.0
        drag(L.oilerWheel.x, L.oilerWheel.y, L.oilerWheel.x, L.oilerWheel.y - L.oilerWheelR * 4f)
        assertTrue("oiler should be feeding, was ${p.ctl.oilerRate}", p.ctl.oilerRate > 0.5)
    }

    @Test
    fun theKeySweepsRoundOneNotchAtATime() {
        val p = view.plant
        p.ctl.ignition = IgnitionMode.OFF

        // The selector is a rotary: drag round it from the OFF mark towards GRID.
        val r = L.keySwitchR * 1.12f
        send(MotionEvent.ACTION_DOWN, L.keySwitch.x, L.keySwitch.y - r)
        var seen = mutableListOf(p.ctl.ignition)
        for (i in 0..24) {
            // sweep anticlockwise across the top of the escutcheon towards GRID
            val deg = -90.0 - i * 5.0
            val a = Math.toRadians(deg)
            send(
                MotionEvent.ACTION_MOVE,
                L.keySwitch.x + (Math.cos(a) * r).toFloat(),
                L.keySwitch.y + (Math.sin(a) * r).toFloat()
            )
            Thread.sleep(60)     // the switch will not be flicked faster than this
            if (seen.last() != p.ctl.ignition) seen.add(p.ctl.ignition)
        }
        send(MotionEvent.ACTION_UP, L.keySwitch.x - r, L.keySwitch.y)

        assertEquals("should have reached station service", IgnitionMode.GRID, p.ctl.ignition)
        assertEquals(
            "the selector must pass through every position in order, saw $seen",
            listOf(IgnitionMode.OFF, IgnitionMode.GEN, IgnitionMode.GRID), seen
        )
    }

    @Test
    fun theCrankTurnsTheEngineOver() {
        val p = view.plant
        p.ctl.compressionRelease = true
        val r = L.crankR * 0.8f
        send(MotionEvent.ACTION_DOWN, L.crankHandle.x + r, L.crankHandle.y)
        // Twelve turns of the handle, with the plant running on as it would in the
        // game loop so the torque is actually applied while the finger moves.
        for (i in 1..432) {
            val a = Math.toRadians(i * 10.0)
            send(
                MotionEvent.ACTION_MOVE,
                L.crankHandle.x + (Math.cos(a) * r).toFloat(),
                L.crankHandle.y + (Math.sin(a) * r).toFloat()
            )
            p.step(1.0 / 60.0)
        }
        send(MotionEvent.ACTION_UP, L.crankHandle.x + r, L.crankHandle.y)
        assertTrue("the engine should be turning over, was ${p.rpm}", p.rpm > 60.0)
    }

    @Test
    fun switchgearRespondsToTaps() {
        val p = view.plant
        showTab(Tab.ELECTRICAL)

        val fieldBefore = p.ctl.fieldSwitchClosed
        tap(L.fieldSwitch.centerX(), L.fieldSwitch.centerY())
        assertNotEquals("the field switch must throw", fieldBefore, p.ctl.fieldSwitchClosed)

        for (i in L.auxSwitches.indices) {
            val before = p.ctl.auxClosed[i]
            tap(L.auxSwitches[i].centerX(), L.auxSwitches[i].centerY())
            assertNotEquals("internal switch $i must throw", before, p.ctl.auxClosed[i])
        }

        showTab(Tab.CONTROL)
        val reliefBefore = p.ctl.compressionRelease
        tap(L.compRelease.centerX(), L.compRelease.centerY())
        assertNotEquals("the relief cock must move", reliefBefore, p.ctl.compressionRelease)
    }

    @Test
    fun eachDeckOnlyAnswersWhileItIsShowing() {
        val p = view.plant
        // On the control deck the breaker is not on the board at all.
        assertEquals(Tab.CONTROL, view.tab)
        val breakerBefore = p.ctl.mainBreakerClosed
        tap(L.mainBreaker.centerX(), L.mainBreaker.centerY())
        assertEquals("the switchboard must be out of reach", breakerBefore, p.ctl.mainBreakerClosed)

        // And on the electrical deck the throttle is not either.
        showTab(Tab.ELECTRICAL)
        p.ctl.throttle = 0.0
        val cx = L.throttleLever.centerX()
        drag(cx, L.throttleLever.bottom - 10f, cx, L.throttleLever.top + 10f)
        assertEquals("the engine controls must be out of reach", 0.0, p.ctl.throttle, 1e-9)
    }

    @Test
    fun throwingTheBreakerOnADeadMachineWrecksIt() {
        val p = view.plant
        showTab(Tab.ELECTRICAL)
        assertTrue(!p.ended)
        // Paralleling a stopped machine to a live 2300 volt bus is a short circuit
        // in all but name, and the board does not stop you doing it.
        tap(L.mainBreaker.centerX(), L.mainBreaker.centerY())
        assertTrue("closing onto the bus from rest must end the shift", p.ended)
    }

    @Test
    fun primerAndStarterAreMomentary() {
        val p = view.plant
        val before = p.ctl.primerCharges
        tap(L.primerButton.x, L.primerButton.y)
        assertEquals("a squirt of gasoline per press", before + 1, p.ctl.primerCharges)

        send(MotionEvent.ACTION_DOWN, L.starterButton.x, L.starterButton.y)
        assertTrue("starter engages while held", p.engine.starterEngaged)
        send(MotionEvent.ACTION_UP, L.starterButton.x, L.starterButton.y)
        assertTrue("starter drops out when released", !p.engine.starterEngaged)
    }

    @Test
    fun theThrottleCanBeHeldWhileTheBreakerIsThrown() {
        val p = view.plant
        p.ctl.throttle = 0.0
        val cx = L.throttleLever.centerX()
        val t = SystemClock.uptimeMillis()

        // finger one goes on the throttle
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, sx(cx), sy(L.throttleLever.bottom - 10f), 0)
        view.onTouchEvent(down); down.recycle()
        send(MotionEvent.ACTION_MOVE, cx, L.throttleLever.centerY())
        val heldThrottle = p.ctl.throttle
        assertTrue("throttle should be open, was $heldThrottle", heldThrottle > 0.2)

        // finger two works the spark lever without disturbing the first
        val sparkBefore = p.ctl.sparkLever
        val sx2 = L.sparkLever.centerX()
        val t2 = SystemClock.uptimeMillis()
        val d2 = MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, sx(sx2), sy(L.sparkLever.bottom - 10f), 0)
        view.onTouchEvent(d2); d2.recycle()
        send(MotionEvent.ACTION_MOVE, sx2, L.sparkLever.top + 10f)
        assertTrue("the spark lever must have moved", p.ctl.sparkLever > sparkBefore + 0.3)
        assertEquals("the throttle must not have moved", heldThrottle, p.ctl.throttle, 1e-9)
    }

    @Test
    fun aTouchAfterTheShiftEndsStartsAFreshOne() {
        val p = view.plant
        // wreck it: full throttle off the bus until the flywheel lets go
        p.ctl.ignition = IgnitionMode.EMG
        p.ctl.throttle = 1.0
        p.engine.starterEngaged = true
        var guard = 0
        while (!p.ended && guard++ < 200000) p.step(1.0 / 60.0)
        assertTrue("expected the shift to end", p.ended)

        tap(L.throttleLever.centerX(), L.throttleLever.centerY())
        assertTrue("the board should be reset for another shift", !p.ended)
        assertEquals(IgnitionMode.OFF, p.ctl.ignition)
        assertEquals(0.0, p.rpm, 1e-9)
    }
}
