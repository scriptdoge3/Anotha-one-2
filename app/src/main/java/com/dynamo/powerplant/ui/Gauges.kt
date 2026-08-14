package com.dynamo.powerplant.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/** A band of the scale printed in red, for the ranges that will cost you the engine. */
class Band(val from: Double, val to: Double, val color: Int = Theme.DANGER)

/**
 * A switchboard instrument: ivory face, black lettering, brass bezel, and a
 * needle swinging through 250 degrees.
 */
class Dial(
    val cx: Float,
    val cy: Float,
    val r: Float,
    val title: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val majors: Int,
    val minorsPerMajor: Int = 5,
    val bands: List<Band> = emptyList(),
    val decimals: Int = 0,
    val sweepDeg: Float = 230f
) {
    // The gap in the scale sits at the bottom of the dial, where the lettering goes.
    private val startDeg = 270f - sweepDeg / 2f

    fun angleFor(value: Double): Float {
        val t = ((value - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
        return startDeg + t * sweepDeg
    }

    /** Everything that never moves: face, bands, ticks, numerals, lettering. */
    fun drawFace(c: Canvas, ambient: Float) {
        Theme.bezel(c, cx, cy, r * 1.10f, ambient)
        Theme.dialFace(c, cx, cy, r, ambient)

        val ink = Theme.dim(Theme.MARK, ambient)
        val inkSoft = Theme.dim(Theme.MARK_SOFT, ambient)

        // coloured bands just inside the rim
        for (b in bands) {
            val a0 = angleFor(b.from)
            val a1 = angleFor(b.to)
            val rr = RectF(cx - r * 0.975f, cy - r * 0.975f, cx + r * 0.975f, cy + r * 0.975f)
            val p = Theme.line(Theme.dim(b.color, ambient), r * 0.062f)
            c.drawArc(rr, a0, a1 - a0, false, p)
        }

        // ticks
        val total = majors * minorsPerMajor
        for (i in 0..total) {
            val t = i.toFloat() / total
            val a = Math.toRadians((startDeg + t * sweepDeg).toDouble())
            val major = i % minorsPerMajor == 0
            val r0 = if (major) r * 0.79f else r * 0.855f
            val r1 = r * 0.93f
            c.drawLine(
                cx + (cos(a) * r0).toFloat(), cy + (sin(a) * r0).toFloat(),
                cx + (cos(a) * r1).toFloat(), cy + (sin(a) * r1).toFloat(),
                Theme.line(if (major) ink else inkSoft, if (major) r * 0.036f else r * 0.018f)
            )
            if (major) {
                val v = min + (max - min) * t
                val s = if (decimals > 0) String.format("%.${decimals}f", v) else v.toInt().toString()
                val rt = r * 0.625f
                c.drawText(
                    s,
                    cx + (cos(a) * rt).toFloat(),
                    cy + (sin(a) * rt).toFloat() + r * 0.055f,
                    Theme.label(r * 0.140f, ink)
                )
            }
        }

        // arc through the numerals, as printed instruments have
        val rr = RectF(cx - r * 0.93f, cy - r * 0.93f, cx + r * 0.93f, cy + r * 0.93f)
        c.drawArc(rr, startDeg, sweepDeg, false, Theme.line(ink, r * 0.012f))

        // Lettering sits in the open bottom of the scale, as on a real instrument.
        c.drawText(title, cx, cy + r * 0.47f, Theme.label(Theme.fitSize(title, r * 0.175f, r * 0.85f), ink))
        c.drawText(unit, cx, cy + r * 0.64f, Theme.label(Theme.fitSize(unit, r * 0.125f, r * 1.00f), inkSoft))
    }

    /** The needle, its counterweight and the boss over the pivot. */
    fun drawNeedle(c: Canvas, value: Double, ambient: Float, color: Int = Theme.NEEDLE) {
        val a = Math.toRadians(angleFor(value).toDouble())
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        val tipX = cx + ca * r * 0.90f
        val tipY = cy + sa * r * 0.90f
        val tailX = cx - ca * r * 0.20f
        val tailY = cy - sa * r * 0.20f
        val w = r * 0.045f
        val px = -sa * w
        val py = ca * w

        val path = Path()
        path.moveTo(tipX, tipY)
        path.lineTo(cx + px, cy + py)
        path.lineTo(tailX + px * 1.5f, tailY + py * 1.5f)
        path.lineTo(tailX - px * 1.5f, tailY - py * 1.5f)
        path.lineTo(cx - px, cy - py)
        path.close()

        // needle shadow, thrown onto the face by the light above the board
        c.save()
        c.translate(r * 0.035f, r * 0.045f)
        c.drawPath(path, Theme.solid(0x33000000))
        c.restore()

        c.drawPath(path, Theme.solid(Theme.dim(color, ambient)))
        c.drawCircle(cx, cy, r * 0.10f, Theme.solid(Theme.dim(Theme.NICKEL_DARK, ambient)))
        c.drawCircle(cx, cy, r * 0.065f, Theme.solid(Theme.dim(Theme.NICKEL, ambient)))
    }

    fun contains(x: Float, y: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= (r * 1.15f) * (r * 1.15f)
    }
}

object Instruments {

    /**
     * The synchroscope. Its pointer shows the phase angle between the machine
     * and the bus and rotates at the difference in frequency: clockwise when the
     * machine is running fast, anticlockwise when it is slow. You close the
     * breaker as it creeps up to the mark at twelve o'clock.
     */
    fun drawSynchroscopeFace(c: Canvas, cx: Float, cy: Float, r: Float, ambient: Float) {
        Theme.bezel(c, cx, cy, r * 1.10f, ambient)
        Theme.dialFace(c, cx, cy, r, ambient)
        val ink = Theme.dim(Theme.MARK, ambient)
        val soft = Theme.dim(Theme.MARK_SOFT, ambient)

        // full circle of ticks
        for (i in 0 until 36) {
            val a = Math.toRadians(i * 10.0 - 90.0)
            val major = i % 3 == 0
            val r0 = if (major) r * 0.74f else r * 0.81f
            c.drawLine(
                cx + (cos(a) * r0).toFloat(), cy + (sin(a) * r0).toFloat(),
                cx + (cos(a) * r * 0.88f).toFloat(), cy + (sin(a) * r * 0.88f).toFloat(),
                Theme.line(if (major) ink else soft, if (major) r * 0.030f else r * 0.015f)
            )
        }

        // the synchronising mark at the top, in red
        val markRect = RectF(cx - r * 0.885f, cy - r * 0.885f, cx + r * 0.885f, cy + r * 0.885f)
        c.drawArc(markRect, -97f, 14f, false, Theme.line(Theme.dim(Theme.DANGER, ambient), r * 0.085f))
        val tri = Path()
        tri.moveTo(cx, cy - r * 0.62f)
        tri.lineTo(cx - r * 0.075f, cy - r * 0.76f)
        tri.lineTo(cx + r * 0.075f, cy - r * 0.76f)
        tri.close()
        c.drawPath(tri, Theme.solid(Theme.dim(Theme.DANGER, ambient)))

        c.drawText("SLOW", cx - r * 0.44f, cy - r * 0.02f, Theme.label(r * 0.140f, ink))
        c.drawText("FAST", cx + r * 0.44f, cy - r * 0.02f, Theme.label(r * 0.140f, ink))
        arrow(c, cx - r * 0.44f, cy + r * 0.15f, -1f, r * 0.14f, ink)
        arrow(c, cx + r * 0.44f, cy + r * 0.15f, 1f, r * 0.14f, ink)
        c.drawText("SYNCHROSCOPE", cx, cy + r * 0.52f, Theme.label(r * 0.115f, ink))
    }

    private fun arrow(c: Canvas, x: Float, y: Float, dir: Float, s: Float, color: Int) {
        val p = Path()
        p.moveTo(x + dir * s * 0.6f, y)
        p.lineTo(x - dir * s * 0.3f, y - s * 0.42f)
        p.lineTo(x - dir * s * 0.3f, y + s * 0.42f)
        p.close()
        c.drawPath(p, Theme.solid(color))
    }

    /** The pointer: a long thin blade with a counterweight, as on the real thing. */
    fun drawSynchroscopeNeedle(c: Canvas, cx: Float, cy: Float, r: Float, phaseRad: Double, ambient: Float) {
        val a = phaseRad - Math.PI / 2.0
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        val w = r * 0.040f
        val px = -sa * w
        val py = ca * w
        val p = Path()
        p.moveTo(cx + ca * r * 0.80f, cy + sa * r * 0.80f)
        p.lineTo(cx + px, cy + py)
        p.lineTo(cx - ca * r * 0.34f + px * 2.2f, cy - sa * r * 0.34f + py * 2.2f)
        p.lineTo(cx - ca * r * 0.34f - px * 2.2f, cy - sa * r * 0.34f - py * 2.2f)
        p.lineTo(cx - px, cy - py)
        p.close()
        c.save()
        c.translate(r * 0.03f, r * 0.04f)
        c.drawPath(p, Theme.solid(0x33000000))
        c.restore()
        c.drawPath(p, Theme.solid(Theme.dim(Theme.NEEDLE, ambient)))
        c.drawCircle(cx, cy, r * 0.095f, Theme.solid(Theme.dim(Theme.NICKEL_DARK, ambient)))
        c.drawCircle(cx, cy, r * 0.060f, Theme.solid(Theme.dim(Theme.NICKEL, ambient)))
    }

    /**
     * A double-scale dial: cycles on the outer ring, revolutions on the inner,
     * which is how one instrument served both purposes on a small board.
     */
    fun drawInnerScale(c: Canvas, d: Dial, ambient: Float, label: String, factor: Double) {
        val ink = Theme.dim(Theme.MARK_SOFT, ambient)
        for (i in 0..d.majors) {
            val t = i.toFloat() / d.majors
            val a = Math.toRadians((d.angleFor(d.min + (d.max - d.min) * t)).toDouble())
            val v = (d.min + (d.max - d.min) * t) * factor
            c.drawText(
                v.toInt().toString(),
                d.cx + (cos(a) * d.r * 0.31f).toFloat(),
                d.cy + (sin(a) * d.r * 0.31f).toFloat() + d.r * 0.055f,
                Theme.label(d.r * 0.125f, ink)
            )
        }
        c.drawText(label, d.cx, d.cy + d.r * 0.44f, Theme.label(d.r * 0.115f, ink))
    }
}
