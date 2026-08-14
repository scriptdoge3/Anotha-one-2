package com.dynamo.powerplant.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.dynamo.powerplant.sim.IgnitionMode
import com.dynamo.powerplant.sim.Plant
import com.dynamo.powerplant.sim.Spec
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The mimic diagram: the single line of the station drawn on the switchboard so
 * you can see at a glance what is connected to what. Boards of the period had
 * exactly this, laid out in brass strip let into the slate.
 *
 * Live conductors are drawn bright with current running along them; dead ones
 * are dark. Contacts show as a break in the line when they are open.
 */
object Mimic {

    /** Where each piece of apparatus sits, as a fraction of the mimic rectangle. */
    private const val BUS_Y = 0.155f
    private const val GEN_X = 0.150f
    private const val MAIN_TX_Y = 0.360f
    private const val BREAKER_Y = 0.540f
    private const val GEN_Y = 0.700f
    private const val START_TX_X = 0.400f
    private const val START_TX_Y = 0.420f
    private const val SERVICE_Y = 0.800f
    private const val BATT_X = 0.860f
    private const val BATT_Y = 0.900f
    private const val LOAD_Y = 0.940f

    fun draw(c: Canvas, r: RectF, p: Plant, ambient: Float, phase: Float) {
        fun x(f: Float) = r.left + r.width() * f
        fun y(f: Float) = r.top + r.height() * f

        Theme.boardPanel(c, r, seed = 3)
        val unit = minOf(r.width() * 0.020f, r.height() * 0.055f)

        Theme.engrave(
            c, "STATION SINGLE LINE", r.left + r.width() * 0.5f, r.top + unit * 1.30f,
            unit * 0.95f, Theme.dim(Theme.MARK_SOFT, ambient)
        )

        // ---- what is alive -----------------------------------------------------
        val gridLive = p.grid.volts > Spec.RATED_VOLTS * 0.45
        val genLive = p.gen.emf(p.rpm) > Spec.RATED_VOLTS * 0.15
        val tied = p.ctl.mainBreakerClosed
        val sel = p.ctl.ignition

        val strength = p.engine.sourceStrength(p.ctl, p.rpm)
        val gridService = sel == IgnitionMode.GRID && strength > 0.02
        val genService = sel == IgnitionMode.GEN && strength > 0.02
        val battService = sel == IgnitionMode.EMG && strength > 0.02
        val serviceLive = p.service.volts > 0.05
        val charging = p.engine.batteryChargingNow

        // ---- the transmission line ---------------------------------------------
        busBar(c, x(0.055f), x(0.945f), y(BUS_Y), unit, gridLive, ambient, phase, 0.55f)
        label(c, "GRID  2300 V", x(0.300f), y(BUS_Y) - unit * 1.05f, unit * 0.80f, ambient, Paint.Align.LEFT)

        val inX = x(0.075f)
        conductor(c, inX, y(0.045f), inX, y(BUS_Y), unit, gridLive, ambient, phase, 0.35f)
        box(c, RectF(inX - unit * 3.4f, y(0.010f) - unit * 0.95f, inX + unit * 3.4f, y(0.010f) + unit * 0.95f),
            "INTERCONNECTION", unit * 0.68f, gridLive, ambient)

        // ---- the unit: generator, main transformer, unit breaker ---------------
        val gx = x(GEN_X)
        conductor(c, gx, y(BUS_Y), gx, y(MAIN_TX_Y) - unit * 1.5f, unit, gridLive, ambient, phase, 0.35f)
        transformer(c, gx, y(MAIN_TX_Y), unit * 1.5f, tied && genLive, ambient)
        label(c, "MAIN", gx + unit * 2.4f, y(MAIN_TX_Y) + unit * 0.35f, unit * 0.72f, ambient, Paint.Align.LEFT)

        conductor(c, gx, y(MAIN_TX_Y) + unit * 1.5f, gx, y(BREAKER_Y) - unit * 1.1f, unit, tied && genLive, ambient, phase, 0.35f)
        contact(c, gx, y(BREAKER_Y), unit * 1.1f, tied, ambient, vertical = true)
        label(c, "UNIT BKR", gx + unit * 2.0f, y(BREAKER_Y) + unit * 0.35f, unit * 0.70f, ambient, Paint.Align.LEFT)

        conductor(c, gx, y(BREAKER_Y) + unit * 1.1f, gx, y(GEN_Y) - unit * 1.7f, unit, tied && genLive, ambient, phase, 0.35f)
        machine(c, gx, y(GEN_Y), unit * 1.7f, genLive, ambient)

        // ---- station service: three sources into one internal bus --------------
        val serviceLeft = x(0.255f)
        val serviceRight = x(0.930f)
        busBar(c, serviceLeft, serviceRight, y(SERVICE_Y), unit * 0.8f, serviceLive, ambient, phase, 0.40f)
        val hdr = if (p.service.overloaded) "STATION SERVICE  OVERLOAD" else
            "STATION SERVICE  %.1f / %.1f kW".format(p.service.demandKw, p.service.capacityKw)
        Theme.engrave(
            c, hdr, serviceLeft, y(SERVICE_Y) - unit * 1.05f, unit * 0.72f,
            Theme.dim(if (p.service.overloaded) Theme.DANGER else Theme.MARK_SOFT, ambient),
            Paint.Align.LEFT
        )

        // GEN tap, off the machine terminals
        val genTapX = x(0.275f)
        conductor(c, gx, y(GEN_Y), genTapX, y(GEN_Y), unit * 0.8f, genService, ambient, phase, 0.30f)
        conductor(c, genTapX, y(GEN_Y), genTapX, y(SERVICE_Y) + unit * 1.0f, unit * 0.8f, genService, ambient, phase, 0.30f)
        contact(c, genTapX, y(SERVICE_Y) + unit * 0.5f, unit * 0.85f, genService, ambient, vertical = true)

        // GRID tap, down through the starting transformer
        val sx = x(START_TX_X)
        conductor(c, sx, y(BUS_Y), sx, y(START_TX_Y) - unit * 1.3f, unit * 0.8f, gridLive, ambient, phase, 0.30f)
        transformer(c, sx, y(START_TX_Y), unit * 1.3f, gridService, ambient)
        label(c, "STARTING", sx + unit * 2.1f, y(START_TX_Y) + unit * 0.30f, unit * 0.70f, ambient, Paint.Align.LEFT)
        conductor(c, sx, y(START_TX_Y) + unit * 1.3f, sx, y(SERVICE_Y) - unit * 1.0f, unit * 0.8f, gridService, ambient, phase, 0.30f)
        contact(c, sx, y(SERVICE_Y) - unit * 0.5f, unit * 0.85f, gridService, ambient, vertical = true)

        // EMG tap, the battery, which the charging set also feeds back into
        val bx = x(BATT_X)
        conductor(c, bx, y(SERVICE_Y), bx, y(BATT_Y) - unit * 1.2f, unit * 0.8f, battService || charging, ambient,
            phase, if (charging && !battService) -0.30f else 0.30f)
        contact(c, bx, y(SERVICE_Y) + unit * 1.1f, unit * 0.85f, true, ambient, vertical = true)
        battery(c, bx, y(BATT_Y), unit * 1.2f, p.engine.batteryCharge.toFloat(), battService, charging, ambient)

        // ---- the internal loads hanging off the service bus --------------------
        for (i in p.service.loads.indices) {
            val l = p.service.loads[i]
            val lx = x(0.335f + i * 0.098f)
            val switched = p.ctl.auxClosed[i] && !l.fuseBlown
            conductor(c, lx, y(SERVICE_Y), lx, y(LOAD_Y) - unit * 1.6f, unit * 0.7f, l.running, ambient, phase, 0.30f)
            contact(c, lx, y(LOAD_Y) - unit * 2.2f, unit * 0.75f, switched, ambient, vertical = true)
            fuseSymbol(c, lx, y(LOAD_Y) - unit * 0.6f, unit * 0.75f, l.fuseBlown, l.running, ambient)
            label(c, l.shortName, lx, y(LOAD_Y) + unit * 1.15f, unit * 0.64f, ambient, Paint.Align.CENTER)
        }
    }

    // ------------------------------------------------------------------ pieces

    private fun label(
        c: Canvas, s: String, x: Float, y: Float, size: Float, ambient: Float, align: Paint.Align
    ) = Theme.engrave(c, s, x, y, size, Theme.dim(Theme.MARK_SOFT, ambient), align)

    /** A heavy horizontal bus bar. */
    private fun busBar(
        c: Canvas, x0: Float, x1: Float, y: Float, unit: Float, live: Boolean,
        ambient: Float, phase: Float, speed: Float
    ) {
        val w = unit * 0.85f
        c.drawLine(x0, y, x1, y, Theme.line(Theme.dim(0xFF0C0F12.toInt(), ambient), w * 1.9f))
        c.drawLine(x0, y, x1, y, Theme.line(Theme.dim(if (live) Theme.ACCENT else Theme.STEEL_DARK, ambient), w))
        if (live) flow(c, x0, y, x1, y, w, phase, speed, ambient)
    }

    /** A run of conductor between two points. */
    private fun conductor(
        c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, unit: Float, live: Boolean,
        ambient: Float, phase: Float, speed: Float
    ) {
        val w = unit * 0.55f
        c.drawLine(x0, y0, x1, y1, Theme.line(Theme.dim(0xFF0C0F12.toInt(), ambient), w * 2.1f))
        c.drawLine(x0, y0, x1, y1, Theme.line(Theme.dim(if (live) Theme.ACCENT else Theme.STEEL_DARK, ambient), w))
        if (live) flow(c, x0, y0, x1, y1, w, phase, speed, ambient)
    }

    /** Beads of current sliding along a live conductor. */
    private fun flow(
        c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float,
        phase: Float, speed: Float, ambient: Float
    ) {
        val len = hypot(x1 - x0, y1 - y0)
        if (len < 1f) return
        val step = w * 5.5f
        val n = (len / step).toInt().coerceAtMost(40)
        if (n <= 0) return
        val dx = (x1 - x0) / len
        val dy = (y1 - y0) / len
        val off = ((phase * speed * step * 8f) % step + step) % step
        val paint = Theme.solid(Theme.withAlpha(Theme.LAMP_WHITE, (150 * ambient).toInt()))
        for (i in 0..n) {
            val d = off + i * step
            if (d > len) break
            c.drawCircle(x0 + dx * d, y0 + dy * d, w * 0.44f, paint)
        }
    }

    /** A pair of contacts, drawn closed or standing open. */
    private fun contact(c: Canvas, cx: Float, cy: Float, half: Float, closed: Boolean, ambient: Float, vertical: Boolean) {
        val col = Theme.dim(if (closed) Theme.ACCENT else Theme.STEEL, ambient)
        if (vertical) {
            c.drawCircle(cx, cy - half, half * 0.30f, Theme.solid(col))
            c.drawCircle(cx, cy + half, half * 0.30f, Theme.solid(col))
            if (closed) {
                c.drawLine(cx, cy - half, cx, cy + half, Theme.line(col, half * 0.42f))
            } else {
                // the blade swung clear
                c.drawLine(cx, cy - half, cx + half * 1.15f, cy + half * 0.30f, Theme.line(col, half * 0.42f))
            }
        }
    }

    /** Two interlinked coils, the usual single-line symbol for a transformer. */
    private fun transformer(c: Canvas, cx: Float, cy: Float, r: Float, live: Boolean, ambient: Float) {
        val col = Theme.dim(if (live) Theme.ACCENT else Theme.STEEL, ambient)
        val p = Theme.line(col, r * 0.20f)
        c.drawCircle(cx, cy - r * 0.42f, r * 0.62f, Theme.solid(Theme.dim(0xFF101418.toInt(), ambient)))
        c.drawCircle(cx, cy + r * 0.42f, r * 0.62f, Theme.solid(Theme.dim(0xFF101418.toInt(), ambient)))
        c.drawCircle(cx, cy - r * 0.42f, r * 0.62f, p)
        c.drawCircle(cx, cy + r * 0.42f, r * 0.62f, p)
    }

    /** The generator: a circle with a sine wave through it. */
    private fun machine(c: Canvas, cx: Float, cy: Float, r: Float, live: Boolean, ambient: Float) {
        val col = Theme.dim(if (live) Theme.ACCENT else Theme.STEEL, ambient)
        c.drawCircle(cx, cy, r, Theme.solid(Theme.dim(0xFF101418.toInt(), ambient)))
        c.drawCircle(cx, cy, r, Theme.line(col, r * 0.14f))
        val p = Path()
        val n = 24
        for (i in 0..n) {
            val t = i.toFloat() / n
            val px = cx - r * 0.58f + r * 1.16f * t
            val py = cy - sin(t * 2.0 * Math.PI).toFloat() * r * 0.34f
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        c.drawPath(p, Theme.line(col, r * 0.13f))
        Theme.engrave(c, "GEN", cx, cy + r * 1.75f, r * 0.52f, Theme.dim(Theme.MARK_SOFT, ambient))
    }

    /** A battery: long and short plates, with its state of charge alongside. */
    private fun battery(
        c: Canvas, cx: Float, cy: Float, r: Float, charge: Float,
        discharging: Boolean, charging: Boolean, ambient: Float
    ) {
        val col = Theme.dim(
            when {
                discharging -> Theme.ACCENT
                charging -> Theme.ACCENT_COOL
                else -> Theme.STEEL
            }, ambient
        )
        var y = cy - r * 0.75f
        for (i in 0 until 3) {
            c.drawLine(cx - r * 0.80f, y, cx + r * 0.80f, y, Theme.line(col, r * 0.17f))
            y += r * 0.36f
            c.drawLine(cx - r * 0.38f, y, cx + r * 0.38f, y, Theme.line(col, r * 0.17f))
            y += r * 0.36f
        }
        val barW = r * 1.8f
        val bar = RectF(cx - barW / 2, cy + r * 1.28f, cx + barW / 2, cy + r * 1.62f)
        c.drawRoundRect(bar, bar.height() / 2, bar.height() / 2, Theme.solid(Theme.dim(0xFF0E1114.toInt(), ambient)))
        val fillRect = RectF(bar.left + 2, bar.top + 2, bar.left + 2 + (bar.width() - 4) * charge.coerceIn(0f, 1f), bar.bottom - 2)
        c.drawRoundRect(
            fillRect, bar.height() / 2, bar.height() / 2,
            Theme.solid(Theme.dim(if (charge < 0.2f) Theme.DANGER else Theme.ACCENT_COOL, ambient))
        )
        Theme.engrave(c, "BATTERY", cx, cy + r * 2.30f, r * 0.50f, Theme.dim(Theme.MARK_SOFT, ambient))
    }

    /** The single-line symbol for a fuse. */
    private fun fuseSymbol(c: Canvas, cx: Float, cy: Float, r: Float, blown: Boolean, live: Boolean, ambient: Float) {
        val col = Theme.dim(if (blown) Theme.DANGER else if (live) Theme.ACCENT else Theme.STEEL, ambient)
        val box = RectF(cx - r * 0.52f, cy - r * 0.85f, cx + r * 0.52f, cy + r * 0.85f)
        c.drawRect(box, Theme.solid(Theme.dim(0xFF101418.toInt(), ambient)))
        c.drawRect(box, Theme.line(col, r * 0.20f))
        if (blown) {
            c.drawLine(box.left, box.top, box.right, box.bottom, Theme.line(col, r * 0.20f))
        } else {
            c.drawLine(cx, box.top, cx, box.bottom, Theme.line(col, r * 0.18f))
        }
    }

    /** A labelled box for apparatus off the edge of the station. */
    private fun box(c: Canvas, r: RectF, s: String, size: Float, live: Boolean, ambient: Float) {
        val col = Theme.dim(if (live) Theme.ACCENT else Theme.STEEL, ambient)
        c.drawRoundRect(r, 3f, 3f, Theme.solid(Theme.dim(0xFF101418.toInt(), ambient)))
        c.drawRoundRect(r, 3f, 3f, Theme.line(col, 2.2f))
        c.drawText(
            s, r.centerX(), r.centerY() + size * 0.36f,
            Theme.label(Theme.fitSize(s, size, r.width() * 0.90f), Theme.dim(Theme.MARK_SOFT, ambient))
        )
    }
}
