package com.dynamo.powerplant.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/** Levers, wheels, keys and knife switches: everything you actually put a hand on. */
object Widgets {

    /**
     * A quadrant lever in a slotted plate, the sort used for throttle and spark.
     * Value 0 sits at the bottom of the slot, 1 at the top.
     */
    fun quadrantLever(
        c: Canvas, r: RectF, value: Float, title: String, topMark: String, bottomMark: String,
        ambient: Float, handleColor: Int = Theme.NICKEL, notches: Int = 10
    ) {
        val cx = r.centerX()
        val slotTop = r.top + r.height() * 0.205f
        val slotBottom = r.bottom - r.height() * 0.185f
        val slotW = r.width() * 0.20f

        // backing plate
        val plate = RectF(cx - r.width() * 0.36f, r.top + r.height() * 0.085f, cx + r.width() * 0.36f, r.bottom - r.height() * 0.065f)
        Theme.panelPlate(c, plate, 6f, 1f, pinstripe = false)
        Theme.screw(c, plate.left + 9f, plate.top + 9f, 6f)
        Theme.screw(c, plate.right - 9f, plate.top + 9f, 6f, 62f)
        Theme.screw(c, plate.left + 9f, plate.bottom - 9f, 6f, 100f)
        Theme.screw(c, plate.right - 9f, plate.bottom - 9f, 6f, 14f)

        // the slot itself, cut through
        val slot = RectF(cx - slotW / 2, slotTop, cx + slotW / 2, slotBottom)
        c.drawRoundRect(slot, slotW / 2, slotW / 2, Theme.solid(0xFF0B0A07.toInt()))
        c.drawRoundRect(slot, slotW / 2, slotW / 2, Theme.line(0x44FFFFFF, 1.4f))

        // notches filed into the quadrant beside the slot
        for (i in 0..notches) {
            val y = slotBottom - (slotBottom - slotTop) * i / notches
            val long = i % 5 == 0
            val len = if (long) r.width() * 0.15f else r.width() * 0.085f
            c.drawLine(
                cx + slotW * 0.62f, y, cx + slotW * 0.62f + len, y,
                Theme.line(Theme.dim(if (long) Theme.STEEL else Theme.STEEL_DARK, ambient), if (long) 3.2f else 2f)
            )
        }

        val ty = slotBottom - (slotBottom - slotTop) * value
        // the lever arm running down to its pivot below the plate
        c.drawLine(cx, ty, cx, slotBottom + r.height() * 0.02f, Theme.line(Theme.dim(Theme.STEEL_DARK, ambient), slotW * 0.42f))

        // brass knob on the end
        val kr = slotW * 0.86f
        Theme.fill.alpha = 255
        Theme.fill.shader = LinearGradient(
            cx - kr, ty - kr, cx + kr, ty + kr,
            intArrayOf(
                Theme.dim(Theme.NICKEL_LIT, ambient),
                Theme.dim(handleColor, ambient),
                Theme.dim(Theme.NICKEL_DARK, ambient)
            ),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, ty, kr, Theme.fill)
        Theme.fill.shader = null
        c.drawCircle(cx, ty, kr, Theme.line(0x66000000, 2f))
        c.drawCircle(cx - kr * 0.28f, ty - kr * 0.30f, kr * 0.26f, Theme.solid(Theme.withAlpha(Color.WHITE, (90 * ambient).toInt())))

        Theme.engrave(c, topMark, cx, r.top + r.height() * 0.165f, r.width() * 0.135f, Theme.dim(Theme.NICKEL, ambient))
        Theme.engrave(c, bottomMark, cx, r.bottom - r.height() * 0.115f, r.width() * 0.135f, Theme.dim(Theme.NICKEL, ambient))
        Theme.engrave(c, title, cx, r.bottom + r.height() * 0.055f, r.width() * 0.150f, Theme.dim(Theme.NICKEL_LIT, ambient))
    }

    /**
     * A spoked handwheel, for the water gate and the lubricator. It turns through
     * three full revolutions between shut and wide open, so it reads like a valve.
     */
    fun handwheel(
        c: Canvas, cx: Float, cy: Float, r: Float, value: Float, title: String, ambient: Float,
        rimColor: Int = Theme.NICKEL
    ) {
        val turn = value * 3.0f * Math.PI.toFloat() * 2f * 0.28f
        // shadow under the wheel
        c.drawCircle(cx + r * 0.06f, cy + r * 0.08f, r, Theme.solid(0x44000000))
        // rim
        c.drawCircle(cx, cy, r, Theme.line(Theme.dim(rimColor, ambient), r * 0.20f))
        c.drawCircle(cx, cy, r * 1.10f, Theme.line(0x55000000, 2f))
        // spokes
        for (i in 0 until 5) {
            val a = turn + i * (2.0 * Math.PI / 5.0)
            c.drawLine(
                cx + (cos(a) * r * 0.18).toFloat(), cy + (sin(a) * r * 0.18).toFloat(),
                cx + (cos(a) * r * 0.92).toFloat(), cy + (sin(a) * r * 0.92).toFloat(),
                Theme.line(Theme.dim(rimColor, ambient), r * 0.13f)
            )
        }
        c.drawCircle(cx, cy, r * 0.26f, Theme.solid(Theme.dim(Theme.NICKEL_DARK, ambient)))
        c.drawCircle(cx, cy, r * 0.17f, Theme.solid(Theme.dim(Theme.STEEL_DARK, ambient)))

        // a little quadrant scale so you can see how far it is open
        val arc = RectF(cx - r * 1.26f, cy - r * 1.26f, cx + r * 1.26f, cy + r * 1.26f)
        c.drawArc(arc, 130f, 280f, false, Theme.line(Theme.dim(Theme.STEEL_DARK, ambient), r * 0.09f))
        c.drawArc(arc, 130f, 280f * value, false, Theme.line(Theme.dim(rimColor, ambient), r * 0.09f))
        Theme.engrave(c, title, cx, cy + r * 1.42f, r * 0.30f, Theme.dim(Theme.NICKEL_LIT, ambient))
    }

    /**
     * The combination key switch. Five positions on one rotary, in the order
     * they sit on the barrel: BAT, DIM, OFF, ON, MAG.
     */
    fun keySwitch(
        c: Canvas, cx: Float, cy: Float, r: Float, labels: List<String>, index: Int,
        liveIndexes: Set<Int>, ambient: Float
    ) {
        val n = labels.size
        val spread = 210f
        val start = 180f - (spread - 180f) / 2f   // sweeps across the top

        // Nickel escutcheon over a black engraved dial plate.
        bezelRing(c, cx, cy, r, ambient)
        Theme.dialFace(c, cx, cy, r * 0.86f, ambient)

        for (i in 0 until n) {
            val a = Math.toRadians((start + spread * i / (n - 1)).toDouble())
            val selected = i == index
            // the position mark out near the rim
            val markR = r * 0.755f
            c.drawCircle(
                cx + (cos(a) * markR).toFloat(), cy + (sin(a) * markR).toFloat(),
                r * 0.042f,
                Theme.solid(
                    Theme.dim(
                        when {
                            selected && i in liveIndexes -> Theme.ACCENT
                            selected -> Theme.MARK
                            i in liveIndexes -> Theme.NICKEL_DARK
                            else -> Theme.STEEL_DARK
                        }, ambient
                    )
                )
            )
            // the legend engraved on the plate itself, inside the bezel
            val tr = r * 0.575f
            Theme.engrave(
                c, labels[i],
                cx + (cos(a) * tr).toFloat(),
                cy + (sin(a) * tr).toFloat() + r * 0.050f,
                r * 0.135f,
                Theme.dim(if (selected) Theme.ACCENT else Theme.MARK_SOFT, ambient)
            )
        }

        // A short knurled knob with a single index line, pointing at the position.
        val a = Math.toRadians((start + spread * index / (n - 1)).toDouble())
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        val kr = r * 0.285f
        c.drawCircle(cx + r * 0.02f, cy + r * 0.03f, kr, Theme.solid(0x66000000))
        Theme.fill.alpha = 255
        Theme.fill.shader = LinearGradient(
            cx - kr, cy - kr, cx + kr, cy + kr,
            intArrayOf(Theme.dim(Theme.NICKEL_LIT, ambient), Theme.dim(Theme.NICKEL_DARK, ambient)),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, kr, Theme.fill)
        Theme.fill.shader = null
        c.drawCircle(cx, cy, kr, Theme.line(0x77000000, 2f))
        for (i in 0 until 20) {
            val ka = Math.toRadians(i * 18.0)
            c.drawLine(
                cx + (cos(ka) * kr * 0.86).toFloat(), cy + (sin(ka) * kr * 0.86).toFloat(),
                cx + (cos(ka) * kr).toFloat(), cy + (sin(ka) * kr).toFloat(),
                Theme.line(Theme.withAlpha(Color.BLACK, (60 * ambient).toInt()), kr * 0.075f)
            )
        }
        // The index line points at the chosen legend without covering it.
        c.drawLine(
            cx + ca * kr * 0.15f, cy + sa * kr * 0.15f,
            cx + ca * r * 0.40f, cy + sa * r * 0.40f,
            Theme.line(Theme.dim(Theme.ACCENT, ambient), r * 0.055f)
        )
        c.drawCircle(cx, cy, kr * 0.30f, Theme.solid(Theme.dim(Theme.STEEL_DARK, ambient)))

        Theme.engrave(c, "SUPPLY", cx, cy + r * 1.34f, r * 0.21f, Theme.dim(Theme.NICKEL_LIT, ambient))
    }

    /** The plain nickel ring around a switch, without the instrument glass lip. */
    private fun bezelRing(c: Canvas, cx: Float, cy: Float, r: Float, ambient: Float) {
        Theme.fill.alpha = 255
        Theme.fill.shader = RadialGradient(
            cx - r * 0.42f, cy - r * 0.52f, r * 1.85f,
            intArrayOf(
                Theme.dim(Theme.NICKEL_LIT, ambient),
                Theme.dim(Theme.NICKEL, ambient),
                Theme.dim(Theme.NICKEL_DARK, ambient)
            ),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, Theme.fill)
        Theme.fill.shader = null
        val kn = Theme.line(Theme.withAlpha(Color.BLACK, (46 * ambient).toInt()), r * 0.016f)
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0)
            c.drawLine(
                cx + (cos(a) * r * 0.93).toFloat(), cy + (sin(a) * r * 0.93).toFloat(),
                cx + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat(), kn
            )
        }
        c.drawCircle(cx, cy, r, Theme.line(0x55000000, 2f))
    }

    fun knifeSwitch(
        c: Canvas, r: RectF, closed: Float, title: String, sub: String, ambient: Float,
        blown: Boolean = false, live: Boolean = false
    ) {
        val cx = r.centerX()
        val w = r.width()

        Theme.engrave(c, title, cx, r.top + r.height() * 0.115f, Theme.fitSize(title, w * 0.20f, w * 0.98f),
            Theme.dim(Theme.NICKEL_LIT, ambient))

        // The blade is short enough that swinging it open stays inside its own
        // column on the board, which is how they were spaced in practice.
        val len = w * 0.66f
        val hingeY = r.top + r.height() * 0.30f
        val jawY = hingeY + len

        for (y in listOf(hingeY, jawY)) {
            c.drawCircle(cx, y, w * 0.125f, Theme.solid(Theme.dim(0xFFC9C0AE.toInt(), ambient)))
            c.drawCircle(cx, y, w * 0.125f, Theme.line(0x66000000, 1.8f))
            c.drawCircle(cx, y, w * 0.068f, Theme.solid(Theme.dim(Theme.NICKEL_DARK, ambient)))
        }

        // the jaws: two brass fingers the blade drops between
        val jawGlow = if (live) Theme.NICKEL_LIT else Theme.NICKEL
        for (dx in listOf(-w * 0.078f, w * 0.078f)) {
            c.drawLine(cx + dx, jawY - w * 0.155f, cx + dx, jawY + w * 0.055f,
                Theme.line(Theme.dim(jawGlow, ambient), w * 0.046f))
        }

        // the blade, hinged at the top and swinging up and out when opened
        val openDeg = 46.0
        val a = Math.toRadians(90.0 - openDeg * (1.0 - closed))
        val ex = cx - (cos(a) * len).toFloat()
        val ey = hingeY + (sin(a) * len).toFloat()
        c.drawLine(cx, hingeY, ex, ey, Theme.line(0x55000000, w * 0.125f))
        c.drawLine(cx, hingeY, ex, ey, Theme.line(Theme.dim(Theme.NICKEL, ambient), w * 0.088f))
        c.drawLine(cx, hingeY, ex, ey, Theme.line(Theme.withAlpha(Theme.NICKEL_LIT, (70 * ambient).toInt()), w * 0.026f))

        // insulated grip on the end of the blade
        c.drawCircle(ex, ey, w * 0.100f, Theme.solid(Theme.dim(Theme.BAKELITE, ambient)))
        c.drawCircle(ex, ey, w * 0.100f, Theme.line(0x77000000, 2f))
        c.drawCircle(ex - w * 0.030f, ey - w * 0.030f, w * 0.030f,
            Theme.solid(Theme.withAlpha(Theme.BAKELITE_LIT, (170 * ambient).toInt())))

        // cartridge fuse in its clips below the switch
        val fy = r.bottom - r.height() * 0.235f
        val fw = w * 0.30f
        val fh = w * 0.088f
        val fr = RectF(cx - fw, fy - fh, cx + fw, fy + fh)
        c.drawRoundRect(fr, fh * 0.45f, fh * 0.45f,
            Theme.solid(Theme.dim(if (blown) 0xFF3A1512.toInt() else 0xFF7E8489.toInt(), ambient)))
        c.drawRoundRect(fr, fh * 0.45f, fh * 0.45f, Theme.line(0x66000000, 1.8f))
        // brass ferrules in the spring clips at each end
        for (dx in listOf(-fw * 0.78f, fw * 0.78f)) {
            c.drawRoundRect(
                RectF(cx + dx - fw * 0.22f, fy - fh * 1.12f, cx + dx + fw * 0.22f, fy + fh * 1.12f),
                2f, 2f, Theme.solid(Theme.dim(Theme.NICKEL, ambient))
            )
        }
        if (blown) c.drawLine(fr.left + 5, fr.top + 2, fr.right - 5, fr.bottom - 2,
            Theme.line(Theme.dim(Theme.DANGER, ambient), 3f))

        if (sub.isNotEmpty()) {
            Theme.engrave(c, sub, cx, r.bottom - r.height() * 0.015f, Theme.fitSize(sub, w * 0.165f, w * 0.95f),
                Theme.dim(if (blown) Theme.DANGER else Theme.NICKEL_DARK, ambient))
        }
    }

    /**
     * The main oil switch: a heavy handle you throw across, with a mechanical
     * flag showing whether the contacts are actually made.
     */
    fun mainBreaker(c: Canvas, r: RectF, closed: Float, ambient: Float, live: Boolean) {
        Theme.panelPlate(c, r, 10f)
        val cx = r.centerX()
        val pivotY = r.bottom - r.height() * 0.24f

        // the arc the handle travels through
        val arcR = minOf(r.height() * 0.42f, r.width() * 0.35f)
        val arc = RectF(cx - arcR, pivotY - arcR, cx + arcR, pivotY + arcR)
        c.drawArc(arc, 200f, 140f, false, Theme.line(Theme.dim(Theme.STEEL_DARK, ambient), r.width() * 0.055f))
        Theme.engrave(c, "OFF", cx - arcR * 0.86f, pivotY - arcR * 0.62f, r.width() * 0.085f, Theme.dim(Theme.NICKEL, ambient))
        Theme.engrave(c, "ON", cx + arcR * 0.86f, pivotY - arcR * 0.62f, r.width() * 0.085f,
            Theme.dim(if (closed > 0.5f) Theme.LAMP_AMBER else Theme.NICKEL, ambient))

        val a = Math.toRadians(200.0 + 140.0 * closed)
        val ex = cx + (cos(a) * arcR * 1.02).toFloat()
        val ey = pivotY + (sin(a) * arcR * 1.02).toFloat()
        c.drawLine(cx, pivotY, ex, ey, Theme.line(0x66000000, r.width() * 0.085f))
        c.drawLine(cx, pivotY, ex, ey, Theme.line(Theme.dim(Theme.STEEL, ambient), r.width() * 0.060f))

        // ball handle
        val hr = r.width() * 0.085f
        Theme.fill.alpha = 255
        Theme.fill.shader = LinearGradient(
            ex - hr, ey - hr, ex + hr, ey + hr,
            intArrayOf(Theme.dim(Theme.BAKELITE_LIT, ambient), Theme.dim(Theme.BAKELITE, ambient)),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(ex, ey, hr, Theme.fill)
        Theme.fill.shader = null
        c.drawCircle(ex, ey, hr, Theme.line(0x77000000, 2.2f))

        c.drawCircle(cx, pivotY, r.width() * 0.055f, Theme.solid(Theme.dim(Theme.NICKEL_DARK, ambient)))
        c.drawCircle(cx, pivotY, r.width() * 0.032f, Theme.solid(Theme.dim(Theme.STEEL, ambient)))

        Theme.namePlate(
            c, RectF(cx - r.width() * 0.36f, r.top + r.height() * 0.055f, cx + r.width() * 0.36f, r.top + r.height() * 0.175f),
            "MAIN BREAKER", r.width() * 0.085f, ambient
        )
        // contact flag
        val flagR = RectF(cx - r.width() * 0.15f, r.bottom - r.height() * 0.155f, cx + r.width() * 0.15f, r.bottom - r.height() * 0.045f)
        val made = closed > 0.9f
        c.drawRoundRect(flagR, 4f, 4f, Theme.solid(Theme.dim(if (made) 0xFF1E4A22.toInt() else 0xFF4A1E1A.toInt(), ambient)))
        c.drawRoundRect(flagR, 4f, 4f, Theme.line(0x66000000, 2f))
        c.drawText(
            if (made) "CLOSED" else "OPEN", flagR.centerX(), flagR.centerY() + r.width() * 0.035f,
            Theme.label(r.width() * 0.085f, Theme.dim(if (made) Theme.LAMP_GREEN else Theme.LAMP_RED, ambient))
        )
        if (live && made) {
            c.drawCircle(cx, pivotY, r.width() * 0.13f, Theme.solid(Theme.withAlpha(Theme.LAMP_AMBER, 26)))
        }
    }

    /** A brass push button on a porcelain base. */
    fun pushButton(c: Canvas, cx: Float, cy: Float, r: Float, pressed: Boolean, title: String, ambient: Float, tint: Int = Theme.NICKEL) {
        c.drawCircle(cx, cy, r * 1.32f, Theme.solid(Theme.dim(0xFF1A1712.toInt(), ambient)))
        c.drawCircle(cx, cy, r * 1.32f, Theme.line(0x55000000, 2f))
        val d = if (pressed) r * 0.07f else 0f
        c.drawCircle(cx, cy + r * 0.10f, r, Theme.solid(0x66000000))
        Theme.fill.alpha = 255
        Theme.fill.shader = LinearGradient(
            cx - r, cy - r + d, cx + r, cy + r + d,
            intArrayOf(
                Theme.dim(Theme.blend(tint, Color.WHITE, 0.30f), ambient),
                Theme.dim(tint, ambient),
                Theme.dim(Theme.blend(tint, Color.BLACK, 0.52f), ambient)
            ),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy + d, r, Theme.fill)
        Theme.fill.shader = null
        c.drawCircle(cx, cy + d, r, Theme.line(0x77000000, 2f))
        Theme.engrave(c, title, cx, cy + r * 1.78f, r * 0.42f, Theme.dim(Theme.NICKEL_LIT, ambient))
    }

    /** A small two-position lever, for the compression release. */
    fun toggleLever(c: Canvas, r: RectF, on: Float, title: String, onMark: String, offMark: String, ambient: Float) {
        Theme.panelPlate(c, r, 6f, 1f, pinstripe = false)
        val cx = r.centerX()
        val unit = minOf(r.width(), r.height())
        val cy = r.top + r.height() * 0.62f
        val len = minOf(r.width() * 0.30f, r.height() * 0.26f)

        Theme.engrave(
            c, title, cx, r.top + r.height() * 0.17f,
            Theme.fitSize(title, unit * 0.20f, r.width() * 0.86f),
            Theme.dim(Theme.NICKEL_LIT, ambient)
        )

        // the quadrant the lever swings across
        val arc = RectF(cx - len * 1.30f, cy - len * 1.30f, cx + len * 1.30f, cy + len * 1.30f)
        c.drawArc(arc, 200f, 140f, false, Theme.line(Theme.dim(Theme.STEEL_DARK, ambient), unit * 0.055f))

        val a = Math.toRadians(-150.0 + 120.0 * on)
        val ex = cx + (cos(a) * len).toFloat()
        val ey = cy + (sin(a) * len).toFloat()
        c.drawLine(cx, cy, ex, ey, Theme.line(0x66000000, unit * 0.115f))
        c.drawLine(cx, cy, ex, ey, Theme.line(Theme.dim(Theme.NICKEL, ambient), unit * 0.080f))
        c.drawCircle(ex, ey, unit * 0.095f, Theme.solid(Theme.dim(Theme.BAKELITE, ambient)))
        c.drawCircle(ex, ey, unit * 0.095f, Theme.line(0x77000000, 2f))
        c.drawCircle(cx, cy, unit * 0.055f, Theme.solid(Theme.dim(Theme.STEEL_DARK, ambient)))

        val markSize = Theme.fitSize(offMark, unit * 0.150f, r.width() * 0.36f)
        val markY = cy + len * 1.05f
        Theme.engrave(
            c, offMark, r.left + r.width() * 0.23f, markY, markSize,
            Theme.dim(if (on < 0.5f) Theme.ACCENT else Theme.MARK_SOFT, ambient)
        )
        Theme.engrave(
            c, onMark, r.right - r.width() * 0.23f, markY, markSize,
            Theme.dim(if (on > 0.5f) Theme.ACCENT else Theme.MARK_SOFT, ambient)
        )
    }

    /**
     * The starting crank: a brass handle on the end of the shaft, drawn spinning
     * with the engine. You swipe round it to turn the engine over.
     */
    fun crank(c: Canvas, cx: Float, cy: Float, r: Float, angleRad: Float, effort: Float, ambient: Float) {
        c.drawCircle(cx, cy, r * 1.24f, Theme.solid(Theme.dim(0xFF1A1E22.toInt(), ambient)))
        c.drawCircle(cx, cy, r * 1.24f, Theme.line(Theme.dim(Theme.NICKEL, ambient), r * 0.085f))
        // ratchet teeth around the boss
        for (i in 0 until 16) {
            val a = angleRad + i * (2f * Math.PI.toFloat() / 16f)
            c.drawLine(
                cx + (cos(a) * r * 0.30f), cy + (sin(a) * r * 0.30f),
                cx + (cos(a) * r * 0.44f), cy + (sin(a) * r * 0.44f),
                Theme.line(Theme.dim(Theme.STEEL_DARK, ambient), r * 0.06f)
            )
        }
        val ca = cos(angleRad)
        val sa = sin(angleRad)
        // the throw of the crank
        c.drawLine(cx, cy, cx + ca * r * 0.80f, cy + sa * r * 0.80f,
            Theme.line(0x66000000, r * 0.26f))
        c.drawLine(cx, cy, cx + ca * r * 0.80f, cy + sa * r * 0.80f,
            Theme.line(Theme.dim(Theme.STEEL, ambient), r * 0.19f))
        // the grip
        val gx = cx + ca * r * 0.80f
        val gy = cy + sa * r * 0.80f
        c.drawCircle(gx, gy, r * 0.30f, Theme.solid(Theme.dim(Theme.BAKELITE, ambient)))
        c.drawCircle(gx, gy, r * 0.30f, Theme.line(0x77000000, 2f))
        c.drawCircle(gx - r * 0.09f, gy - r * 0.09f, r * 0.09f,
            Theme.solid(Theme.withAlpha(Theme.BAKELITE_LIT, (180 * ambient).toInt())))
        c.drawCircle(cx, cy, r * 0.24f, Theme.solid(Theme.dim(Theme.NICKEL, ambient)))
        c.drawCircle(cx, cy, r * 0.13f, Theme.solid(Theme.dim(Theme.STEEL_DARK, ambient)))
        if (effort > 0.02f) {
            c.drawCircle(cx, cy, r * 1.24f, Theme.line(Theme.withAlpha(Theme.LAMP_AMBER, (150 * effort).toInt()), 4f))
        }
        Theme.engrave(c, "CRANK", cx, cy + r * 1.52f, r * 0.30f, Theme.dim(Theme.NICKEL_LIT, ambient))
    }

    /** A horizontal bar meter for the things that had no dial: sump, battery. */
    fun barMeter(c: Canvas, r: RectF, value: Float, title: String, ambient: Float, warnBelow: Float = 0.2f) {
        c.drawRoundRect(r, r.height() * 0.3f, r.height() * 0.3f, Theme.solid(Theme.dim(0xFF14120E.toInt(), ambient)))
        val inner = RectF(r.left + 2, r.top + 2, r.left + 2 + (r.width() - 4) * value.coerceIn(0f, 1f), r.bottom - 2)
        val col = if (value < warnBelow) Theme.DANGER else Theme.NICKEL
        c.drawRoundRect(inner, r.height() * 0.3f, r.height() * 0.3f, Theme.solid(Theme.dim(col, ambient)))
        c.drawRoundRect(r, r.height() * 0.3f, r.height() * 0.3f, Theme.line(Theme.dim(Theme.NICKEL_DARK, ambient), 2f))
        Theme.engrave(c, title, r.centerX(), r.top - r.height() * 0.35f, r.height() * 0.82f, Theme.dim(Theme.NICKEL_LIT, ambient))
    }

    fun textCentered(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
        c.drawText(s, x, y, Theme.label(size, color, true, Paint.Align.CENTER))
    }
}
