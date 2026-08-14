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
     * A vertical column gauge, the way an exhaust pyrometer bank was read: six
     * of them side by side against one scale, so an unbalanced engine shows up
     * as a ragged skyline rather than a number you have to think about.
     */
    fun columnGauge(c: Canvas, r: RectF, value: Float, color: Int, ambient: Float) {
        val w = r.width()
        c.drawRoundRect(r, w * 0.18f, w * 0.18f, Theme.solid(Theme.dim(0xFF0A0C0E.toInt(), ambient)))
        val h = r.height() * value.coerceIn(0f, 1f)
        if (h > 1f) {
            val fill = RectF(r.left + 2f, r.bottom - h, r.right - 2f, r.bottom - 2f)
            c.drawRoundRect(fill, w * 0.15f, w * 0.15f, Theme.solid(color))
            // a highlight down the left of the column, like glass
            c.drawLine(
                fill.left + w * 0.16f, fill.top + 3f, fill.left + w * 0.16f, fill.bottom - 3f,
                Theme.line(Theme.withAlpha(Theme.LAMP_WHITE, (60 * ambient).toInt()), w * 0.10f)
            )
        }
        c.drawRoundRect(r, w * 0.18f, w * 0.18f, Theme.line(Theme.withAlpha(Theme.NICKEL, 80), 1.8f))
    }

    /**
     * One sight feed off the lubricator: a little glass with a drop hanging in
     * it. The drop falls faster the more feed is set, and the glass runs dark
     * when the liner behind it has gone dry.
     */
    fun sightFeed(c: Canvas, cx: Float, cy: Float, r: Float, feed: Float, film: Float, ambient: Float) {
        // A tall narrow glass with a brass cap top and bottom, the way a
        // force-feed lubricator's sight feeds are built.
        val glass = RectF(cx - r * 0.40f, cy - r * 1.05f, cx + r * 0.40f, cy + r * 0.95f)
        c.drawRoundRect(glass, r * 0.34f, r * 0.34f, Theme.solid(Theme.dim(0xFF0C0F12.toInt(), ambient)))

        val col = when {
            film < 0.35f -> Theme.DANGER
            feed > 0.86f -> Theme.ACCENT_COOL
            else -> Theme.ACCENT
        }
        // the oil standing in the bottom of the glass
        val pool = RectF(glass.left + 2f, glass.bottom - r * 0.34f, glass.right - 2f, glass.bottom - 2f)
        c.drawRoundRect(pool, r * 0.16f, r * 0.16f, Theme.solid(Theme.dim(col, ambient)))
        // the drop on its way down: high in the glass on a slow feed, low on a
        // fast one, so the whole bank can be read at a glance.
        if (feed > 0.02f) {
            val dy = glass.top + r * 0.30f + (1f - feed.coerceIn(0f, 1f)) * r * 1.05f
            c.drawCircle(cx, dy, r * 0.17f, Theme.solid(Theme.dim(col, ambient)))
            c.drawCircle(cx - r * 0.05f, dy - r * 0.05f, r * 0.06f,
                Theme.solid(Theme.withAlpha(Theme.LAMP_WHITE, (120 * ambient).toInt())))
        }
        c.drawRoundRect(glass, r * 0.34f, r * 0.34f, Theme.line(Theme.withAlpha(Theme.NICKEL, 120), 1.8f))
        // brass caps
        for (y in listOf(glass.top, glass.bottom)) {
            c.drawRoundRect(
                RectF(cx - r * 0.52f, y - r * 0.11f, cx + r * 0.52f, y + r * 0.11f), 2f, 2f,
                Theme.solid(Theme.dim(Theme.NICKEL, ambient))
            )
        }
        // the knurled needle valve on its side, with an index line
        val vx = cx + r * 0.86f
        c.drawCircle(vx, cy + r * 0.30f, r * 0.30f, Theme.solid(Theme.dim(Theme.NICKEL_DARK, ambient)))
        c.drawCircle(vx, cy + r * 0.30f, r * 0.30f, Theme.line(0x66000000, 1.6f))
        val a = Math.toRadians(-100.0 + 300.0 * feed)
        c.drawLine(
            vx, cy + r * 0.30f,
            vx + (Math.cos(a) * r * 0.27).toFloat(), cy + r * 0.30f + (Math.sin(a) * r * 0.27).toFloat(),
            Theme.line(Theme.dim(Theme.MARK, ambient), r * 0.09f)
        )
    }

    /** A gauge glass on the side of a tank, with the level standing in it. */
    fun gaugeGlass(c: Canvas, r: RectF, value: Float, title: String, reading: String, ambient: Float) {
        val w = r.width()
        c.drawRoundRect(r, w * 0.30f, w * 0.30f, Theme.solid(Theme.dim(0xFF0A0C0E.toInt(), ambient)))
        val v = value.coerceIn(0f, 1f)
        val h = r.height() * v
        val low = v < 0.18f
        if (h > 1f) {
            val fill = RectF(r.left + 3f, r.bottom - h, r.right - 3f, r.bottom - 3f)
            c.drawRoundRect(
                fill, w * 0.25f, w * 0.25f,
                Theme.solid(Theme.dim(if (low) Theme.DANGER else Theme.ACCENT_COOL, ambient))
            )
            // the meniscus
            c.drawLine(
                fill.left, fill.top, fill.right, fill.top,
                Theme.line(Theme.withAlpha(Theme.LAMP_WHITE, (110 * ambient).toInt()), 2.2f)
            )
        }
        // graduations up the side
        for (i in 1..3) {
            val y = r.bottom - r.height() * i / 4f
            c.drawLine(r.right - w * 0.34f, y, r.right - 3f, y, Theme.line(Theme.withAlpha(Theme.NICKEL, 70), 1.6f))
        }
        c.drawRoundRect(r, w * 0.30f, w * 0.30f, Theme.line(Theme.withAlpha(Theme.NICKEL, 120), 2.2f))
        Theme.engrave(
            c, title, r.centerX(), r.top - r.height() * 0.055f, w * 0.26f,
            Theme.dim(Theme.NICKEL_LIT, ambient)
        )
        Theme.engrave(
            c, reading, r.centerX(), r.bottom + r.height() * 0.135f, w * 0.26f,
            Theme.dim(if (low) Theme.DANGER else Theme.MARK_SOFT, ambient)
        )
    }

    /**
     * One relay's target window. Behind the little glass sits a painted flag
     * that drops when the relay operates and stays dropped until somebody puts
     * it back — which is what the operator taps to reset.
     *
     * While the relay is picking up but has not yet operated, the disc travel
     * shows as a filling arc, so a relay you are about to lose is visible before
     * you lose it.
     */
    fun relayTarget(
        c: Canvas, r: RectF, device: String, name: String,
        dropped: Boolean, pickedUp: Boolean, travel: Float, flash: Float, ambient: Float
    ) {
        Theme.panelPlate(c, r, 5f, 0.8f, pinstripe = false)
        val unit = minOf(r.width(), r.height())

        Theme.engrave(
            c, name, r.centerX(), r.top + r.height() * 0.245f,
            Theme.fitSize(name, unit * 0.30f, r.width() * 0.88f),
            Theme.dim(Theme.NICKEL_LIT, ambient)
        )

        // the window itself
        val win = RectF(
            r.left + r.width() * 0.14f, r.top + r.height() * 0.335f,
            r.right - r.width() * 0.14f, r.bottom - r.height() * 0.115f
        )
        c.drawRoundRect(win, 4f, 4f, Theme.solid(Theme.dim(0xFF0A0C0E.toInt(), ambient)))

        if (dropped) {
            // the flag, painted the same red as every target ever made
            c.drawRoundRect(win, 4f, 4f, Theme.solid(Theme.dim(Theme.DANGER, ambient)))
            Theme.engrave(
                c, device, win.centerX(), win.centerY() + win.height() * 0.30f,
                win.height() * 0.72f, Theme.withAlpha(0xFF120504.toInt(), (255 * flash).toInt())
            )
        } else {
            // the disc creeping round while the relay is timing out
            if (travel > 0.01f) {
                val fill = RectF(win.left + 2f, win.bottom - (win.height() - 4f) * travel.coerceIn(0f, 1f), win.right - 2f, win.bottom - 2f)
                c.drawRoundRect(fill, 3f, 3f, Theme.solid(Theme.dim(if (pickedUp) Theme.ACCENT else Theme.STEEL_DARK, ambient)))
            }
            Theme.engrave(
                c, device, win.centerX(), win.centerY() + win.height() * 0.28f,
                win.height() * 0.62f,
                Theme.dim(if (pickedUp) Theme.MARK else Theme.NICKEL_DARK, ambient)
            )
        }
        c.drawRoundRect(win, 4f, 4f, Theme.line(Theme.withAlpha(Theme.NICKEL, 130), 2f))
    }


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
