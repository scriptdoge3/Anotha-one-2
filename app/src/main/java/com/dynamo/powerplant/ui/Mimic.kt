package com.dynamo.powerplant.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.dynamo.powerplant.sim.IgnitionMode
import com.dynamo.powerplant.sim.Plant
import com.dynamo.powerplant.sim.Spec
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The mimic diagram let into the switchboard, drawn the way a real control room
 * panel is: the single line coloured by voltage level, black nameplates against
 * every piece of apparatus, and a lamp at each switching device showing green
 * when it is made and red when it is open.
 *
 * The station reads top to bottom:
 *
 *   grid  ->  unit breaker  ->  main transformer  ->  generator terminals
 *
 * and the emergency circuit comes straight off those terminals: through the
 * emergency transformer breaker, through the emergency transformer, into the
 * battery, out through the battery breaker, and onto the emergency line that
 * carries the ignition, the excitation, the pumps and the lights.
 */
object Mimic {

    // Where each piece of apparatus sits, as a fraction of the mimic rectangle.
    // High tension down the left, generator volts across the middle, and below
    // it the plant's two internal buses: the main bus on the left carrying the
    // regular gear, the emergency circuit running out to the right and back
    // down onto its own line.
    private const val BUS_Y = 0.090f
    private const val UNIT_X = 0.090f
    private const val UNIT_BKR_X = 0.265f
    private const val MAIN_TX_Y = 0.225f
    private const val GEN_BUS_Y = 0.360f
    private const val GEN_Y = 0.470f

    private const val STATION_TX_X = 0.215f
    private const val STATION_BKR_Y = 0.405f
    private const val STATION_TX_Y = 0.478f
    private const val START_BKR_Y = 0.170f
    private const val FIELD_X = 0.470f
    private const val MAIN_BUS_Y = 0.560f
    private const val MAIN_LOAD_Y = 0.680f
    private const val GEN_TAP_X = 0.705f

    private const val EMG_BKR_X = 0.520f
    private const val EMG_BKR_Y = 0.455f
    private const val CHAIN_Y = 0.575f
    private const val EMG_TX_X = 0.655f
    private const val BATT_X = 0.780f
    private const val BATT_BKR_X = 0.890f

    private const val START_X = 0.955f
    private const val START_TX_Y = 0.265f

    private const val LINE_Y = 0.815f
    private const val LOAD_Y = 0.925f

    fun draw(c: Canvas, r: RectF, p: Plant, ambient: Float, phase: Float) {
        fun x(f: Float) = r.left + r.width() * f
        fun y(f: Float) = r.top + r.height() * f

        Theme.boardPanel(c, r, seed = 3)
        val u = minOf(r.width() * 0.019f, r.height() * 0.050f)

        // ---- what is alive -----------------------------------------------------
        val gridLive = p.grid.volts > Spec.RATED_VOLTS * 0.45
        val genLive = p.gen.emf(p.rpm) > Spec.RATED_VOLTS * 0.15
        val tied = p.ctl.mainBreakerClosed
        val txOutLive = p.mainTransformerLive()
        val emgTxClosed = p.ctl.emgTxBreakerClosed
        val battBkrClosed = p.ctl.batteryBreakerClosed
        val sel = p.ctl.ignition
        val strength = p.engine.sourceStrength(p.ctl, p.rpm)
        val gridService = sel == IgnitionMode.GRID && strength > 0.02
        val genService = sel == IgnitionMode.GEN && strength > 0.02
        val battService = sel == IgnitionMode.EMG && strength > 0.02
        val lineLive = p.service.volts > 0.05
        val charging = p.engine.batteryChargingNow

        Theme.engrave(
            c, "STATION SINGLE LINE", r.centerX(), r.top + u * 1.15f,
            u * 0.92f, Theme.dim(Theme.MARK_SOFT, ambient)
        )

        // ================= high tension ========================================
        // The bar is sectionalised at the unit breaker. The short left section is
        // the machine's own: main transformer up from the generator terminals,
        // and nothing else on it. Everything to the right of the breaker is the
        // system, and that is what the starting transformer hangs on.
        val ubx = x(UNIT_BKR_X)
        val hv = level(Theme.HV_LIVE, Theme.HV_DEAD, gridLive, ambient)
        val hvOut = level(Theme.HV_LIVE, Theme.HV_DEAD, txOutLive, ambient)

        bar(c, x(0.040f), ubx - u * 0.95f, y(BUS_Y), u * 0.95f, hvOut, txOutLive, phase, 0.5f, ambient)
        Theme.miniPlate(c, x(0.150f), y(BUS_Y) - u * 1.7f, "UNIT H.T.", u * 0.60f, ambient)

        deviceH(c, ubx, y(BUS_Y), u, tied, hv, ambient, lampAbove = false)
        Theme.miniPlate(c, ubx + u * 4.7f, y(BUS_Y) + u * 2.4f, "UNIT BREAKER", u * 0.58f, ambient)

        bar(c, ubx + u * 0.95f, x(0.972f), y(BUS_Y), u * 0.95f, hv, gridLive, phase, 0.5f, ambient)
        Theme.miniPlate(c, x(0.780f), y(BUS_Y) - u * 1.7f, "GRID  2300 V", u * 0.62f, ambient)

        // the machine's section down through the main transformer
        val ux = x(UNIT_X)
        run(c, ux, y(BUS_Y), ux, y(MAIN_TX_Y) - u * 1.3f, u * 0.62f, hvOut, txOutLive, phase, 0.4f, ambient)
        transformer(c, ux, y(MAIN_TX_Y), u * 1.3f, hvOut, ambient)
        Theme.miniPlate(c, ux + u * 5.0f, y(MAIN_TX_Y), "MAIN TRANSFORMER", u * 0.58f, ambient)

        // ================= generator terminals =================================
        // Everything the plant runs on comes off this bar, one way or another.
        val ac = level(Theme.AC_LIVE, Theme.AC_DEAD, genLive, ambient)
        run(c, ux, y(MAIN_TX_Y) + u * 1.3f, ux, y(GEN_BUS_Y), u * 0.62f, ac, genLive, phase, -0.4f, ambient)
        bar(c, x(0.045f), x(0.900f), y(GEN_BUS_Y), u * 0.85f, ac, genLive, phase, 0.4f, ambient)
        Theme.miniPlate(c, x(0.700f), y(GEN_BUS_Y) - u * 1.6f, "GENERATOR TERMINALS  2300 V", u * 0.56f, ambient)

        run(c, ux, y(GEN_BUS_Y), ux, y(GEN_Y) - u * 1.5f, u * 0.62f, ac, genLive, phase, -0.4f, ambient)
        machine(c, ux, y(GEN_Y), u * 1.5f, ac, ambient)
        Theme.miniPlate(c, ux, y(GEN_Y) + u * 2.5f, "GENERATOR  500 kW", u * 0.58f, ambient)

        // ================= the main bus ========================================
        // The regular controls and pumps, fed off the generator terminals through
        // the station transformer. Dead until the machine is making volts.
        val stx = x(STATION_TX_X)
        val mainLive = p.mainBus.volts > 0.05
        val stnClosed = p.ctl.stationTxBreakerClosed
        val stationCol = level(Theme.AC_LIVE, Theme.AC_DEAD, mainLive, ambient)
        run(c, stx, y(GEN_BUS_Y), stx, y(STATION_BKR_Y) - u * 0.95f, u * 0.62f, ac, genLive, phase, 0.4f, ambient)
        device(c, stx, y(STATION_BKR_Y), u * 0.92f, stnClosed, ac, ambient)
        Theme.miniPlate(c, stx + u * 4.6f, y(STATION_BKR_Y), "STN TX BREAKER", u * 0.54f, ambient)
        run(c, stx, y(STATION_BKR_Y) + u * 0.95f, stx, y(STATION_TX_Y) - u * 1.0f, u * 0.62f, stationCol, mainLive, phase, 0.4f, ambient)
        transformer(c, stx, y(STATION_TX_Y), u * 1.0f, stationCol, ambient)
        Theme.miniPlate(c, stx + u * 4.6f, y(STATION_TX_Y), "STATION TX", u * 0.54f, ambient)
        run(c, stx, y(STATION_TX_Y) + u * 1.0f, stx, y(MAIN_BUS_Y), u * 0.62f, stationCol, mainLive, phase, 0.4f, ambient)

        bar(c, x(0.160f), x(0.415f), y(MAIN_BUS_Y), u * 0.85f, stationCol, mainLive, phase, 0.4f, ambient)
        Theme.miniPlate(c, x(0.400f), y(MAIN_BUS_Y) - u * 1.5f, "MAIN BUS", u * 0.60f, ambient)

        for (i in p.mainBus.loads.indices) {
            val l = p.mainBus.loads[i]
            val lx = x(0.185f + i * 0.068f)
            val col = level(Theme.AC_LIVE, Theme.AC_DEAD, l.running, ambient)
            run(c, lx, y(MAIN_BUS_Y), lx, y(MAIN_LOAD_Y) - u * 1.9f, u * 0.55f, col, l.running, phase, 0.4f, ambient)
            device(c, lx, y(MAIN_LOAD_Y) - u * 1.25f, u * 0.66f, p.ctl.mainClosed[i] && !l.fuseBlown, col, ambient)
            fuseSymbol(c, lx, y(MAIN_LOAD_Y) - u * 0.02f, u * 0.56f, l.fuseBlown, col, ambient)
            Theme.miniPlate(c, lx, y(MAIN_LOAD_Y) + u * 1.25f, l.shortName, u * 0.52f, ambient)
        }

        // ================= emergency circuit ===================================
        // Straight off the generator terminals, through its own breaker and its
        // own transformer, into the battery, and out through the battery breaker
        // onto the emergency line.
        val ebx = x(EMG_BKR_X)
        run(c, ebx, y(GEN_BUS_Y), ebx, y(EMG_BKR_Y) - u * 1.0f, u * 0.62f, ac, genLive, phase, 0.4f, ambient)
        device(c, ebx, y(EMG_BKR_Y), u, emgTxClosed, ac, ambient)
        Theme.miniPlate(c, ebx + u * 5.0f, y(EMG_BKR_Y), "EMG TX BREAKER", u * 0.56f, ambient)

        // the charging road: live only with the breaker made and the machine excited
        val feeding = emgTxClosed && genLive
        val feedCol = level(Theme.AC_LIVE, Theme.AC_DEAD, feeding, ambient)
        val etx = x(EMG_TX_X)
        run(c, ebx, y(EMG_BKR_Y) + u * 1.0f, ebx, y(CHAIN_Y), u * 0.62f, feedCol, feeding, phase, 0.4f, ambient)
        run(c, ebx, y(CHAIN_Y), etx - u * 1.15f, y(CHAIN_Y), u * 0.62f, feedCol, feeding, phase, 0.4f, ambient)
        transformer(c, etx, y(CHAIN_Y), u * 1.15f, feedCol, ambient)
        Theme.miniPlate(c, etx, y(CHAIN_Y) + u * 2.2f, "EMERGENCY TX", u * 0.56f, ambient)

        // from here on it is the battery circuit, and the panel draws it green
        val bx = x(BATT_X)
        val dc = level(Theme.DC_LIVE, Theme.DC_DEAD, charging || battService, ambient)
        val chargeCol = level(Theme.DC_LIVE, Theme.DC_DEAD, charging, ambient)
        val txOut = level(Theme.DC_LIVE, Theme.DC_DEAD, feeding, ambient)
        run(c, etx + u * 1.15f, y(CHAIN_Y), x(GEN_TAP_X), y(CHAIN_Y), u * 0.62f, txOut, feeding, phase, 0.4f, ambient)
        run(c, x(GEN_TAP_X), y(CHAIN_Y), bx - u * 1.4f, y(CHAIN_Y), u * 0.62f, chargeCol, charging, phase, 0.4f, ambient)
        battery(c, bx, y(CHAIN_Y), u * 1.4f, p.engine.batteryCharge.toFloat(), dc, battService, charging, ambient)
        Theme.miniPlate(c, bx, y(CHAIN_Y) - u * 2.2f, "BATTERY", u * 0.60f, ambient)

        // The emergency transformer's own tap onto the line. The battery floats
        // across this same output, which is why one road charges the cells and
        // the other carries the line.
        val gtx = x(GEN_TAP_X)
        val genTap = level(Theme.DC_LIVE, Theme.DC_DEAD, genService, ambient)
        run(c, gtx, y(CHAIN_Y), gtx, y(LINE_Y) - u * 2.15f, u * 0.62f, genTap, genService, phase, 0.4f, ambient)
        tap(c, gtx, y(LINE_Y) - u * 1.35f, u * 0.78f, genService, genTap, ambient)
        run(c, gtx, y(LINE_Y) - u * 0.57f, gtx, y(LINE_Y), u * 0.62f, genTap, genService, phase, 0.4f, ambient)

        // The battery's own output. It is only alive when the cells are actually
        // the thing holding the line up; while they are charging the current is
        // coming the other way, in from the transformer.
        val bbx = x(BATT_BKR_X)
        val outCol = level(Theme.DC_LIVE, Theme.DC_DEAD, battService, ambient)
        run(c, bx + u * 1.4f, y(CHAIN_Y), bbx - u * 0.95f, y(CHAIN_Y), u * 0.62f, outCol, battService, phase, 0.4f, ambient)
        deviceH(c, bbx, y(CHAIN_Y), u, battBkrClosed, outCol, ambient)
        Theme.miniPlate(c, bbx, y(CHAIN_Y) - u * 3.3f, "BATTERY BREAKER", u * 0.56f, ambient)

        // down onto the line, through the selector's own emergency contact
        run(c, bbx, y(CHAIN_Y) + u * 0.95f, bbx, y(LINE_Y) - u * 2.15f, u * 0.62f, outCol, battService, phase, 0.4f, ambient)
        tap(c, bbx, y(LINE_Y) - u * 1.35f, u * 0.78f, battService, outCol, ambient)
        run(c, bbx, y(LINE_Y) - u * 0.57f, bbx, y(LINE_Y), u * 0.62f, outCol, battService, phase, 0.4f, ambient)

        // ================= the emergency line ==================================
        val lineCol = level(Theme.DC_LIVE, Theme.DC_DEAD, lineLive, ambient)
        bar(c, x(0.040f), x(0.972f), y(LINE_Y), u * 0.85f, lineCol, lineLive, phase, 0.4f, ambient)
        Theme.miniPlate(c, x(0.640f), y(LINE_Y) + u * 1.7f, "EMERGENCY LINE", u * 0.60f, ambient)

        // the grid's tap, down through the starting transformer
        val sx = x(START_X)
        val startClosed = p.ctl.startingTxBreakerClosed
        run(c, sx, y(BUS_Y), sx, y(START_BKR_Y) - u * 0.95f, u * 0.62f, hv, gridLive, phase, 0.4f, ambient)
        device(c, sx, y(START_BKR_Y), u * 0.92f, startClosed, hv, ambient)
        Theme.miniPlate(c, sx - u * 5.4f, y(START_BKR_Y), "START TX BKR", u * 0.54f, ambient)
        val startTapLive = gridLive && startClosed
        val startFeed = level(Theme.HV_LIVE, Theme.HV_DEAD, startTapLive, ambient)
        run(c, sx, y(START_BKR_Y) + u * 0.95f, sx, y(START_TX_Y) - u * 1.15f, u * 0.62f, startFeed, startTapLive, phase, 0.4f, ambient)
        val startCol = level(Theme.AC_LIVE, Theme.AC_DEAD, gridService, ambient)
        transformer(c, sx, y(START_TX_Y), u * 1.15f, startCol, ambient)
        Theme.miniPlate(c, sx - u * 5.2f, y(START_TX_Y), "STARTING TX", u * 0.56f, ambient)
        run(c, sx, y(START_TX_Y) + u * 1.15f, sx, y(LINE_Y) - u * 2.15f, u * 0.62f, startCol, gridService, phase, 0.4f, ambient)
        tap(c, sx, y(LINE_Y) - u * 1.35f, u * 0.78f, gridService, startCol, ambient)
        run(c, sx, y(LINE_Y) - u * 0.57f, sx, y(LINE_Y), u * 0.62f, startCol, gridService, phase, 0.4f, ambient)

        // The field circuit hangs off the line through its own switch, in series
        // with the excitation load switch on the board. The discharge resistor
        // beside it is what takes the current when you open it.
        val fx = x(FIELD_X)
        val fieldClosed = p.ctl.fieldSwitchClosed
        val fieldAlive = fieldClosed && p.gen.fieldFlux > 0.03
        val fieldCol = level(Theme.DC_LIVE, Theme.DC_DEAD, fieldAlive, ambient)
        run(c, fx, y(LINE_Y), fx, y(LOAD_Y) - u * 1.9f, u * 0.55f, fieldCol, fieldAlive, phase, 0.4f, ambient)
        device(c, fx, y(LOAD_Y) - u * 1.25f, u * 0.66f, fieldClosed, fieldCol, ambient)
        resistor(c, fx + u * 1.9f, y(LOAD_Y) - u * 1.25f, u * 0.60f, fieldCol, ambient)
        Theme.miniPlate(c, fx + u * 0.9f, y(LOAD_Y) + u * 1.25f, "FIELD SW", u * 0.52f, ambient)

        // ================= the loads on the emergency line =====================
        for (i in p.service.loads.indices) {
            val l = p.service.loads[i]
            val lx = x(0.065f + i * 0.092f)
            val col = level(Theme.DC_LIVE, Theme.DC_DEAD, l.running, ambient)
            run(c, lx, y(LINE_Y), lx, y(LOAD_Y) - u * 1.9f, u * 0.55f, col, l.running, phase, 0.4f, ambient)
            device(c, lx, y(LOAD_Y) - u * 1.25f, u * 0.66f, p.ctl.auxClosed[i] && !l.fuseBlown, col, ambient)
            fuseSymbol(c, lx, y(LOAD_Y) - u * 0.02f, u * 0.56f, l.fuseBlown, col, ambient)
            Theme.miniPlate(c, lx, y(LOAD_Y) + u * 1.25f, l.shortName, u * 0.52f, ambient)
        }
    }

    // ------------------------------------------------------------------ pieces

    /** The colour a run of conductor takes at this voltage level. */
    private fun level(live: Int, dead: Int, energised: Boolean, ambient: Float): Int =
        Theme.dim(if (energised) live else dead, ambient)

    /** A heavy horizontal bus bar. */
    private fun bar(
        c: Canvas, x0: Float, x1: Float, y: Float, w: Float, color: Int,
        live: Boolean, phase: Float, speed: Float, ambient: Float
    ) {
        c.drawLine(x0, y, x1, y, Theme.line(Theme.dim(0xFF07090B.toInt(), ambient), w * 2.0f))
        c.drawLine(x0, y, x1, y, Theme.line(color, w))
        if (live) flow(c, x0, y, x1, y, w, phase, speed, ambient)
    }

    /** A run of conductor between two points. */
    private fun run(
        c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, color: Int,
        live: Boolean, phase: Float, speed: Float, ambient: Float
    ) {
        c.drawLine(x0, y0, x1, y1, Theme.line(Theme.dim(0xFF07090B.toInt(), ambient), w * 2.1f))
        c.drawLine(x0, y0, x1, y1, Theme.line(color, w))
        if (live) flow(c, x0, y0, x1, y1, w, phase, speed, ambient)
    }

    /** Beads of current sliding along a live conductor. */
    private fun flow(
        c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float,
        phase: Float, speed: Float, ambient: Float
    ) {
        val len = hypot(x1 - x0, y1 - y0)
        if (len < 1f) return
        val step = w * 7.0f
        val n = (len / step).toInt().coerceAtMost(40)
        if (n <= 0) return
        val dx = (x1 - x0) / len
        val dy = (y1 - y0) / len
        val off = ((phase * speed * step * 7f) % step + step) % step
        val paint = Theme.solid(Theme.withAlpha(Theme.LAMP_WHITE, (120 * ambient).toInt()))
        for (i in 0..n) {
            val d = off + i * step
            if (d < 0f || d > len) continue
            c.drawCircle(x0 + dx * d, y0 + dy * d, w * 0.40f, paint)
        }
    }

    /**
     * A switching device: the square contact box that sits in the line, with the
     * indicator lamp beside it that every mimic panel carries.
     */
    private fun device(c: Canvas, cx: Float, cy: Float, u: Float, closed: Boolean, color: Int, ambient: Float) {
        val h = u * 0.95f
        val w = u * 0.72f
        val box = RectF(cx - w, cy - h, cx + w, cy + h)
        c.drawRect(box, Theme.solid(Theme.dim(0xFF0B0E11.toInt(), ambient)))
        c.drawRect(box, Theme.line(color, u * 0.26f))
        if (closed) {
            // the contacts made, so the line runs straight through
            c.drawLine(cx, box.top, cx, box.bottom, Theme.line(color, u * 0.42f))
        } else {
            // drawn out of its jaws
            c.drawLine(cx, box.top, cx + w * 0.72f, box.bottom, Theme.line(color, u * 0.34f))
        }
        Theme.lamp(
            c, cx - u * 2.15f, cy, u * 0.52f,
            if (closed) Theme.LAMP_GREEN else Theme.LAMP_RED, if (closed) 0.95f else 0.85f
        )
    }

    /** The same device, lying in a horizontal run instead of a vertical one. */
    private fun deviceH(
        c: Canvas, cx: Float, cy: Float, u: Float, closed: Boolean, color: Int, ambient: Float,
        lampAbove: Boolean = true
    ) {
        val w = u * 0.95f
        val h = u * 0.72f
        val box = RectF(cx - w, cy - h, cx + w, cy + h)
        c.drawRect(box, Theme.solid(Theme.dim(0xFF0B0E11.toInt(), ambient)))
        c.drawRect(box, Theme.line(color, u * 0.26f))
        if (closed) {
            c.drawLine(box.left, cy, box.right, cy, Theme.line(color, u * 0.42f))
        } else {
            c.drawLine(box.left, cy, box.right, cy - h * 0.72f, Theme.line(color, u * 0.34f))
        }
        // clear of whatever the run does on its way past
        Theme.lamp(
            c, cx, cy + (if (lampAbove) -u * 1.95f else u * 1.60f), u * 0.52f,
            if (closed) Theme.LAMP_GREEN else Theme.LAMP_RED, if (closed) 0.95f else 0.85f
        )
    }

    /** A selector contact into the emergency line, with its own lamp. */
    private fun tap(c: Canvas, cx: Float, cy: Float, r: Float, closed: Boolean, color: Int, ambient: Float) {
        c.drawCircle(cx, cy - r, r * 0.34f, Theme.solid(color))
        c.drawCircle(cx, cy + r, r * 0.34f, Theme.solid(color))
        if (closed) c.drawLine(cx, cy - r, cx, cy + r, Theme.line(color, r * 0.46f))
        else c.drawLine(cx, cy - r, cx + r * 1.20f, cy + r * 0.35f, Theme.line(color, r * 0.42f))
        Theme.lamp(
            c, cx - r * 2.5f, cy, r * 0.46f,
            if (closed) Theme.LAMP_GREEN else Theme.LAMP_RED, if (closed) 0.95f else 0.80f
        )
    }

    /** Two interlinked coils: the single-line symbol for a transformer. */
    private fun transformer(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, ambient: Float) {
        val p = Theme.line(color, r * 0.22f)
        c.drawCircle(cx, cy - r * 0.42f, r * 0.62f, Theme.solid(Theme.dim(0xFF0B0E11.toInt(), ambient)))
        c.drawCircle(cx, cy + r * 0.42f, r * 0.62f, Theme.solid(Theme.dim(0xFF0B0E11.toInt(), ambient)))
        c.drawCircle(cx, cy - r * 0.42f, r * 0.62f, p)
        c.drawCircle(cx, cy + r * 0.42f, r * 0.62f, p)
    }

    /** The generator: a circle with a sine wave through it. */
    private fun machine(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, ambient: Float) {
        c.drawCircle(cx, cy, r, Theme.solid(Theme.dim(0xFF0B0E11.toInt(), ambient)))
        c.drawCircle(cx, cy, r, Theme.line(color, r * 0.15f))
        val p = Path()
        val n = 24
        for (i in 0..n) {
            val t = i.toFloat() / n
            val px = cx - r * 0.58f + r * 1.16f * t
            val py = cy - sin(t * 2.0 * Math.PI).toFloat() * r * 0.34f
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        c.drawPath(p, Theme.line(color, r * 0.13f))
    }

    /** A battery: long and short plates, with its state of charge beneath. */
    private fun battery(
        c: Canvas, cx: Float, cy: Float, r: Float, charge: Float, color: Int,
        discharging: Boolean, charging: Boolean, ambient: Float
    ) {
        var y = cy - r * 0.66f
        for (i in 0 until 3) {
            c.drawLine(cx - r * 0.85f, y, cx + r * 0.85f, y, Theme.line(color, r * 0.19f))
            y += r * 0.33f
            c.drawLine(cx - r * 0.40f, y, cx + r * 0.40f, y, Theme.line(color, r * 0.19f))
            y += r * 0.33f
        }
        val barW = r * 1.7f
        val bar = RectF(cx - barW / 2, cy + r * 0.86f, cx + barW / 2, cy + r * 1.16f)
        c.drawRoundRect(bar, bar.height() / 2, bar.height() / 2, Theme.solid(Theme.dim(0xFF0B0E11.toInt(), ambient)))
        val fill = RectF(bar.left + 2, bar.top + 2, bar.left + 2 + (bar.width() - 4) * charge.coerceIn(0f, 1f), bar.bottom - 2)
        c.drawRoundRect(
            fill, bar.height() / 2, bar.height() / 2,
            Theme.solid(
                Theme.dim(
                    when {
                        charge < 0.2f -> Theme.DANGER
                        charging -> Theme.DC_LIVE
                        discharging -> Theme.ACCENT
                        else -> Theme.NICKEL
                    }, ambient
                )
            )
        )
        c.drawRoundRect(bar, bar.height() / 2, bar.height() / 2, Theme.line(Theme.withAlpha(Theme.NICKEL, 90), 1.4f))
    }

    /** The discharge resistor beside the field switch: a zig-zag on a stub. */
    private fun resistor(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, ambient: Float) {
        val p = Path()
        p.moveTo(cx, cy - r * 1.3f)
        var y = cy - r * 0.9f
        var side = 1f
        while (y < cy + r * 0.9f) {
            p.lineTo(cx + side * r * 0.55f, y)
            y += r * 0.45f
            side = -side
        }
        p.lineTo(cx, cy + r * 1.3f)
        c.drawPath(p, Theme.line(color, r * 0.34f))
    }

    /** The single-line symbol for a fuse. */
    private fun fuseSymbol(c: Canvas, cx: Float, cy: Float, r: Float, blown: Boolean, color: Int, ambient: Float) {
        val col = if (blown) Theme.dim(Theme.DANGER, ambient) else color
        val box = RectF(cx - r * 0.60f, cy - r * 0.95f, cx + r * 0.60f, cy + r * 0.95f)
        c.drawRect(box, Theme.solid(Theme.dim(0xFF0B0E11.toInt(), ambient)))
        c.drawRect(box, Theme.line(col, r * 0.22f))
        if (blown) c.drawLine(box.left, box.top, box.right, box.bottom, Theme.line(col, r * 0.22f))
        else c.drawLine(cx, box.top, cx, box.bottom, Theme.line(col, r * 0.20f))
    }
}
