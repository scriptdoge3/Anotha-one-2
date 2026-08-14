package com.dynamo.powerplant.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.ConcurrentHashMap
import com.dynamo.powerplant.audio.EngineAudio
import com.dynamo.powerplant.sim.IgnitionMode
import com.dynamo.powerplant.sim.Plant
import com.dynamo.powerplant.sim.Spec
import com.dynamo.powerplant.ui.Layout
import com.dynamo.powerplant.ui.PanelRenderer
import com.dynamo.powerplant.ui.Tab
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The board itself: a surface, a game loop running the plant in real time, and
 * multi-touch so you can hold the throttle with one thumb and throw the breaker
 * with the other.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    val plant = Plant()
    @Volatile private var layout: Layout? = null
    @Volatile private var renderer: PanelRenderer? = null
    private val audio = EngineAudio()

    private var thread: Thread? = null
    @Volatile private var running = false
    /** The loop only turns when the activity is up and the surface exists. */
    @Volatile private var resumed = false
    @Volatile private var hasSurface = false
    private var lastNanos = 0L

    // Written by surfaceChanged on the UI thread, read by the loop on the game
    // thread and by the touch handler back on the UI thread.
    @Volatile private var scale = 1f
    @Volatile private var offX = 0f
    @Volatile private var offY = 0f

    /** Which deck is showing. The instrument board above it is always in view. */
    @Volatile var tab: Tab = Tab.CONTROL
        private set

    /**
     * Whether the loop is meant to be turning. It turns only while the activity
     * is resumed and the surface exists, and both of those go away without
     * warning on a phone.
     */
    val loopRunning: Boolean get() = running

    /**
     * Touches arrive on the UI thread and the plant is stepped on the game
     * thread, so anything that restructures the world rather than just nudging a
     * control is queued here and applied by the loop between frames.
     */
    @Volatile private var pendingReset = false

    private var keyLastMove = 0L
    /** Read by the renderer on the game thread while the UI thread writes it. */
    private val pressed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Which control each finger grabbed, and what it was holding when it did. */
    private class Grab(val id: String, val startX: Float, val startY: Float, val startValue: Double)
    private val grabs = HashMap<Int, Grab>()

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ------------------------------------------------------------------ lifecycle

    override fun surfaceCreated(holder: SurfaceHolder) {
        hasSurface = true
        startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val vh = Layout.virtualHeight(width, height)
        val l = Layout(Layout.VIRTUAL_W, vh)
        val old = renderer
        // Publish the new one before releasing the old, so the game loop never
        // holds a reference to a renderer whose bitmap is being recycled.
        layout = l
        renderer = PanelRenderer(l)
        old?.release()
        scale = width / Layout.VIRTUAL_W
        offX = 0f
        offY = (height - vh * scale) / 2f
    }

    /**
     * The contract is that this must not return while the loop is still touching
     * the surface, so it stops the thread and waits for it. Leaving the loop
     * running against a destroyed surface is the other way this used to die: the
     * next `lockCanvas` throws, on the game thread, where nothing catches it.
     */
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        hasSurface = false
        stopLoop()
    }

    fun resume() {
        resumed = true
        audio.start()
        startLoop()
    }

    fun pause() {
        resumed = false
        stopLoop()
        audio.stop()
    }

    /**
     * Both of these are called only from the UI thread — surface callbacks and
     * the activity lifecycle — and are synchronised against each other so a
     * pause racing a surface teardown cannot end up with two loops running.
     */
    @Synchronized
    private fun startLoop() {
        if (running || !resumed || !hasSurface) return
        running = true
        lastNanos = System.nanoTime()
        thread = Thread(this, "dynamo-room").also { it.start() }
    }

    @Synchronized
    private fun stopLoop() {
        running = false
        val t = thread ?: return
        thread = null
        if (t !== Thread.currentThread()) {
            try {
                t.join(2000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    // ------------------------------------------------------------------ loop

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            var dt = (now - lastNanos) / 1_000_000_000.0
            lastNanos = now
            if (dt > 0.20) dt = 0.20

            advance(dt)

            // Taking and giving back the canvas is guarded, because the surface
            // can be torn down by the system between the check and the call and
            // there is nothing to be done about that but skip the frame. The
            // drawing itself is not guarded: a fault in there is a real bug and
            // should be heard about rather than swallowed.
            val h = holder
            val c: Canvas? = try {
                if (hasSurface && h.surface.isValid) h.lockCanvas() else null
            } catch (e: IllegalStateException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
            if (c != null) {
                try {
                    c.drawColor(android.graphics.Color.BLACK)
                    c.save()
                    c.translate(offX, offY)
                    c.scale(scale, scale)
                    renderer?.draw(c, plant, System.currentTimeMillis(), pressed, tab)
                    c.restore()
                } finally {
                    try {
                        h.unlockCanvasAndPost(c)
                    } catch (e: IllegalStateException) {
                        // the surface went out from under us mid-frame
                    } catch (e: IllegalArgumentException) {
                    }
                }
            }

            val spent = (System.nanoTime() - now) / 1_000_000L
            val wait = 16L - spent
            if (wait > 0) {
                try { Thread.sleep(wait) } catch (e: InterruptedException) { return }
            }
        }
    }

    /**
     * One frame's worth of world: apply anything the UI thread queued, then step
     * the plant and hand the audio the events it threw off. Called by the loop
     * every frame, and by the tests in its place.
     */
    fun advance(dt: Double) {
        if (pendingReset) {
            pendingReset = false
            plant.reset()
            tab = Tab.CONTROL
        }
        plant.step(dt)
        audio.update(plant, dt)
    }

    // ------------------------------------------------------------------ input

    private fun vx(e: MotionEvent, i: Int) = (e.getX(i) - offX) / scale
    private fun vy(e: MotionEvent, i: Int) = (e.getY(i) - offY) / scale

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val l = layout ?: return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                onDown(l, e.getPointerId(i), vx(e, i), vy(e, i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    onMove(l, e.getPointerId(i), vx(e, i), vy(e, i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val i = e.actionIndex
                onUp(e.getPointerId(i))
            }
        }
        return true
    }

    private fun onDown(l: Layout, id: Int, x: Float, y: Float) {
        val p = plant

        if (p.ended) {
            // Hand the reset to the game thread rather than tearing the plant
            // down underneath it mid-step.
            pendingReset = true
            grabs.clear()
            pressed.clear()
            return
        }

        // --- the tab bar is live whichever deck is showing ---
        if (l.tabBar.contains(x, y)) {
            for ((i, t) in Tab.entries.withIndex()) {
                if (l.tabRect(i).contains(x, y) && t != tab) {
                    tab = t
                    // No release here: the renderer rebuilds its own cached
                    // steelwork when it notices the deck has changed, and
                    // recycling that bitmap from this thread is what used to
                    // crash the game every so often on a tab tap.
                    grabs.clear()
                    audio.tick()
                }
            }
            return
        }

        when (tab) {
            Tab.CONTROL -> onDownControl(l, id, x, y)
            Tab.ENGINE -> onDownEngine(l, id, x, y)
            Tab.ELECTRICAL -> onDownElectrical(l, id, x, y)
        }
    }

    private fun onDownControl(l: Layout, id: Int, x: Float, y: Float) {
        val p = plant

        // --- levers and wheels take a grab so the drag is relative ---
        if (l.throttleLever.contains(x, y)) { grabs[id] = Grab("throttle", x, y, p.ctl.throttle); return }
        if (l.sparkLever.contains(x, y)) { grabs[id] = Grab("spark", x, y, p.ctl.sparkLever); return }
        if (l.mixtureKnob.near(x, y, l.mixtureKnobR * 1.5f)) { grabs[id] = Grab("mixture", x, y, p.ctl.mixture); return }
        if (l.excitationKnob.near(x, y, l.excitationKnobR * 1.5f)) { grabs[id] = Grab("excitation", x, y, p.ctl.excitation); return }
        if (l.waterWheel.near(x, y, l.waterWheelR * 1.5f)) { grabs[id] = Grab("water", x, y, p.ctl.waterValve); return }
        if (l.oilerWheel.near(x, y, l.oilerWheelR * 1.5f)) { grabs[id] = Grab("oiler", x, y, p.ctl.oilerRate); return }
        if (l.keySwitch.near(x, y, l.keySwitchR * 1.45f)) { grabs[id] = Grab("key", x, y, 0.0); return }
        // --- momentary and latching switches ---
        if (l.starterButton.near(x, y, l.starterR * 1.6f)) {
            p.engine.starterEngaged = true
            grabs[id] = Grab("starter", x, y, 0.0)
            return
        }
        if (l.primerButton.near(x, y, l.primerR * 1.6f)) {
            p.prime()
            pressed.add("primer")
            grabs[id] = Grab("primer", x, y, 0.0)
            return
        }
        if (l.compRelease.contains(x, y)) {
            p.ctl.compressionRelease = !p.ctl.compressionRelease
            return
        }
    }

    private fun onDownEngine(l: Layout, id: Int, x: Float, y: Float) {
        val p = plant

        // --- the six cylinders ---
        for (i in 0 until Spec.CYLINDERS) {
            val sf = l.sightFeedAt(i)
            if (sf.near(x, y, l.sightFeedR * 1.9f)) {
                grabs[id] = Grab("feed$i", x, y, p.ctl.sightFeed[i]); return
            }
            if (l.igniterSwitch(i).contains(x, y)) {
                p.ctl.igniterCutOut[i] = !p.ctl.igniterCutOut[i]
                audio.clunk(); return
            }
        }

        // --- the tanks ---
        if (l.fuelCock.contains(x, y)) { p.ctl.fuelCock = !p.ctl.fuelCock; audio.clunk(); return }
        if (l.fuelTransfer.contains(x, y)) { p.ctl.fuelTransfer = !p.ctl.fuelTransfer; audio.clunk(); return }
        if (l.makeUpWheel.near(x, y, l.makeUpWheelR * 1.5f)) {
            grabs[id] = Grab("makeup", x, y, p.ctl.makeUpValve); return
        }
        if (l.oilReplenish.near(x, y, l.oilReplenishR * 1.6f)) {
            p.ctl.oilReplenish = true
            pressed.add("oilfill")
            grabs[id] = Grab("oilfill", x, y, 0.0)
            return
        }
    }

    private fun onDownElectrical(l: Layout, id: Int, x: Float, y: Float) {
        val p = plant
        if (l.mainBreaker.contains(x, y)) {
            p.toggleBreaker()
            if (p.events.interlocked) audio.tick() else audio.clunk()
            return
        }
        // --- the relay panel: tap a dropped target to put it back ---
        val n = p.protection.relays.size
        for (i in 0 until n) {
            if (l.relayWindow(i, n).contains(x, y)) { p.resetTarget(i); audio.tick(); return }
        }
        if (l.stationTxBreaker.contains(x, y)) {
            p.ctl.stationTxBreakerClosed = !p.ctl.stationTxBreakerClosed; audio.clunk(); return
        }
        if (l.startingTxBreaker.contains(x, y)) {
            p.ctl.startingTxBreakerClosed = !p.ctl.startingTxBreakerClosed; audio.clunk(); return
        }
        if (l.fieldSwitch.contains(x, y)) { p.toggleFieldSwitch(); audio.clunk(); return }
        if (l.emgTxBreaker.contains(x, y)) {
            p.ctl.emgTxBreakerClosed = !p.ctl.emgTxBreakerClosed; audio.clunk(); return
        }
        if (l.batteryBreaker.contains(x, y)) {
            p.ctl.batteryBreakerClosed = !p.ctl.batteryBreakerClosed; audio.clunk(); return
        }
        for (i in l.mainSwitches.indices) {
            if (l.mainSwitches[i].contains(x, y)) { p.toggleMain(i); audio.clunk(); return }
        }
        for (i in l.auxSwitches.indices) {
            if (l.auxSwitches[i].contains(x, y)) { p.toggleAux(i); audio.clunk(); return }
        }
    }

    private fun onMove(l: Layout, id: Int, x: Float, y: Float) {
        val g = grabs[id] ?: return
        val p = plant
        when (g.id) {
            "throttle" -> p.ctl.throttle = leverValue(l.throttleLever.top, l.throttleLever.bottom, g, y)
            "spark" -> p.ctl.sparkLever = leverValue(l.sparkLever.top, l.sparkLever.bottom, g, y)
            "mixture" -> p.ctl.mixture = wheelValue(g, y, l.mixtureKnobR)
            "excitation" -> p.ctl.excitation = wheelValue(g, y, l.excitationKnobR)
            "water" -> p.ctl.waterValve = wheelValue(g, y, l.waterWheelR)
            "oiler" -> p.ctl.oilerRate = wheelValue(g, y, l.oilerWheelR)
            "key" -> moveKey(l, x, y)
            "makeup" -> p.ctl.makeUpValve = wheelValue(g, y, l.makeUpWheelR)
            else -> if (g.id.startsWith("feed")) {
                val i = g.id.removePrefix("feed").toInt()
                p.ctl.sightFeed[i] = wheelValue(g, y, l.sightFeedR)
            }
        }
    }

    private fun onUp(id: Int) {
        val g = grabs.remove(id) ?: return
        when (g.id) {
            "starter" -> plant.engine.starterEngaged = false
            "primer" -> pressed.remove("primer")
            "oilfill" -> { plant.ctl.oilReplenish = false; pressed.remove("oilfill") }
        }
    }

    private fun leverValue(top: Float, bottom: Float, g: Grab, y: Float): Double {
        val span = bottom - top
        val dy = y - g.startY
        return (g.startValue - dy / span).coerceIn(0.0, 1.0)
    }

    /** Wheels turn on a vertical drag: up opens, down shuts. */
    private fun wheelValue(g: Grab, y: Float, r: Float): Double {
        val dy = y - g.startY
        return (g.startValue - dy / (r * 5.2f)).coerceIn(0.0, 1.0)
    }

    /**
     * The key is a rotary, so the finger sweeps it round. It moves one notch at a
     * time and cannot be flicked straight from BAT to MAG, which is the whole
     * reason the changeover has to be done smartly.
     */
    private fun moveKey(l: Layout, x: Float, y: Float) {
        val now = System.currentTimeMillis()
        if (now - keyLastMove < 55L) return
        val a = Math.toDegrees(atan2((y - l.keySwitch.y).toDouble(), (x - l.keySwitch.x).toDouble()))
        val spread = 200.0
        val start = 180.0 - (spread - 180.0) / 2.0
        // Measure round from the BAT end. Anything outside the arc belongs to
        // whichever end it is nearer, so dragging past a stop does not wrap the
        // key round to the opposite position.
        var rel = (a - start) % 360.0
        if (rel < 0) rel += 360.0
        val t = when {
            rel <= spread -> rel / spread
            rel < spread + (360.0 - spread) / 2.0 -> 1.0
            else -> 0.0
        }
        val want = Math.round(t * (IgnitionMode.entries.size - 1)).toInt()
        val cur = plant.ctl.ignition.ordinal
        if (want == cur) return
        plant.ctl.ignition = if (want > cur) plant.ctl.ignition.clockwise() else plant.ctl.ignition.anticlockwise()
        keyLastMove = now
        audio.tick()
    }

}
