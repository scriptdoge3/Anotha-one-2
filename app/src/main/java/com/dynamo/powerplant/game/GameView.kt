package com.dynamo.powerplant.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.dynamo.powerplant.audio.EngineAudio
import com.dynamo.powerplant.sim.IgnitionMode
import com.dynamo.powerplant.sim.Plant
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
    private var layout: Layout? = null
    private var renderer: PanelRenderer? = null
    private val audio = EngineAudio()

    private var thread: Thread? = null
    @Volatile private var running = false
    private var lastNanos = 0L

    private var scale = 1f
    private var offX = 0f
    private var offY = 0f

    /** Which deck is showing. The instrument board above it is always in view. */
    var tab: Tab = Tab.CONTROL
        private set

    private var keyLastMove = 0L
    private val pressed = HashSet<String>()

    /** Which control each finger grabbed, and what it was holding when it did. */
    private class Grab(val id: String, val startX: Float, val startY: Float, val startValue: Double)
    private val grabs = HashMap<Int, Grab>()

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ------------------------------------------------------------------ lifecycle

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val vh = Layout.virtualHeight(width, height)
        val l = Layout(Layout.VIRTUAL_W, vh)
        layout = l
        renderer?.release()
        renderer = PanelRenderer(l)
        scale = width / Layout.VIRTUAL_W
        offX = 0f
        offY = (height - vh * scale) / 2f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    fun resume() {
        if (running) return
        running = true
        lastNanos = System.nanoTime()
        audio.start()
        thread = Thread(this, "dynamo-room").also { it.start() }
    }

    fun pause() {
        running = false
        audio.stop()
        thread?.join(800)
        thread = null
    }

    // ------------------------------------------------------------------ loop

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            var dt = (now - lastNanos) / 1_000_000_000.0
            lastNanos = now
            if (dt > 0.20) dt = 0.20

            plant.step(dt)
            audio.update(plant, dt)

            val h = holder
            if (h.surface.isValid) {
                var c: Canvas? = null
                try {
                    c = h.lockCanvas()
                    if (c != null) {
                        c.drawColor(android.graphics.Color.BLACK)
                        c.save()
                        c.translate(offX, offY)
                        c.scale(scale, scale)
                        renderer?.draw(c, plant, System.currentTimeMillis(), pressed, tab)
                        c.restore()
                    }
                } finally {
                    if (c != null) h.unlockCanvasAndPost(c)
                }
            }

            val spent = (System.nanoTime() - now) / 1_000_000L
            val wait = 16L - spent
            if (wait > 0) {
                try { Thread.sleep(wait) } catch (e: InterruptedException) { return }
            }
        }
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
            p.reset()
            tab = Tab.CONTROL
            renderer?.release()
            return
        }

        // --- the tab bar is live whichever deck is showing ---
        if (l.tabBar.contains(x, y)) {
            for ((i, t) in Tab.entries.withIndex()) {
                if (l.tabRect(i).contains(x, y) && t != tab) {
                    tab = t
                    renderer?.release()
                    audio.tick()
                }
            }
            return
        }

        if (tab == Tab.CONTROL) onDownControl(l, id, x, y) else onDownElectrical(l, id, x, y)
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

    private fun onDownElectrical(l: Layout, id: Int, x: Float, y: Float) {
        val p = plant
        if (l.mainBreaker.contains(x, y)) { p.toggleBreaker(); audio.clunk(); return }
        if (l.emgTxBreaker.contains(x, y)) {
            p.ctl.emgTxBreakerClosed = !p.ctl.emgTxBreakerClosed; audio.clunk(); return
        }
        if (l.batteryBreaker.contains(x, y)) {
            p.ctl.batteryBreakerClosed = !p.ctl.batteryBreakerClosed; audio.clunk(); return
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
        }
    }

    private fun onUp(id: Int) {
        val g = grabs.remove(id) ?: return
        when (g.id) {
            "starter" -> plant.engine.starterEngaged = false
            "primer" -> pressed.remove("primer")
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
