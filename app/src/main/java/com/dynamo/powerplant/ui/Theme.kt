package com.dynamo.powerplant.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/**
 * The look of a late twenties generating station: charcoal enamelled steel,
 * nickel-plated bezels, black instrument faces with white figures, and the
 * squared-off deco lettering of the period. Machined and sober rather than
 * varnished and ornamental.
 */
object Theme {

    // --- enamelled steel and phenolic board ---
    const val PANEL_DARK = 0xFF15181B.toInt()
    const val PANEL = 0xFF262C31.toInt()
    const val PANEL_LIT = 0xFF39424A.toInt()
    const val BOARD = 0xFF1B2024.toInt()
    const val BOARD_LIGHT = 0xFF2C343A.toInt()
    const val BOARD_VEIN = 0xFF3E474F.toInt()

    // --- plating ---
    const val NICKEL = 0xFF9AA4AC.toInt()
    const val NICKEL_LIT = 0xFFE2E9EE.toInt()
    const val NICKEL_DARK = 0xFF4E575F.toInt()
    const val STEEL = 0xFF848D95.toInt()
    const val STEEL_DARK = 0xFF3F464C.toInt()

    // --- instrument faces: black, with white figures ---
    const val FACE = 0xFF191D21.toInt()
    const val FACE_SHADE = 0xFF0E1114.toInt()
    const val MARK = 0xFFE9EEF2.toInt()
    const val MARK_SOFT = 0xFF919BA3.toInt()
    const val NEEDLE = 0xFFF4F7F9.toInt()

    // --- accents ---
    const val ACCENT = 0xFFFFA22B.toInt()          // deco amber, for the live things
    const val ACCENT_COOL = 0xFF4FB3A5.toInt()     // verdigris, for the field side
    const val DANGER = 0xFFDC4436.toInt()
    const val DANGER_SOFT = 0xFF8E2C23.toInt()

    // --- lamps ---
    const val LAMP_OFF = 0xFF1C1F22.toInt()
    const val LAMP_AMBER = 0xFFFFC15E.toInt()
    const val LAMP_RED = 0xFFFF6A4A.toInt()
    const val LAMP_GREEN = 0xFF86E27E.toInt()
    const val LAMP_WHITE = 0xFFFFF6E0.toInt()

    // --- mimic diagram, coloured by voltage level as a real board is ---
    const val HV_DEAD = 0xFF5C2622.toInt()
    const val HV_LIVE = 0xFFD8493A.toInt()
    const val AC_DEAD = 0xFF5C4019.toInt()
    const val AC_LIVE = 0xFFE8933A.toInt()
    const val DC_DEAD = 0xFF1F4A2B.toInt()
    const val DC_LIVE = 0xFF52C46E.toInt()

    // --- bakelite handles and grips ---
    const val BAKELITE = 0xFF25201E.toInt()
    const val BAKELITE_LIT = 0xFF463C37.toInt()

    val display: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    val displayLight: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = display
        textAlign = Paint.Align.CENTER
    }

    fun solid(color: Int): Paint {
        fill.shader = null
        fill.alpha = 255          // clear anything a previous call left behind
        fill.color = color
        return fill
    }

    fun line(color: Int, width: Float): Paint {
        stroke.shader = null
        stroke.alpha = 255
        stroke.color = color
        stroke.strokeWidth = width
        stroke.strokeCap = Paint.Cap.BUTT
        return stroke
    }

    fun label(size: Float, color: Int = MARK, bold: Boolean = true, align: Paint.Align = Paint.Align.CENTER): Paint {
        text.shader = null
        text.alpha = 255
        text.textSize = size
        text.color = color
        text.typeface = if (bold) display else displayLight
        text.textAlign = align
        text.letterSpacing = 0.10f      // deco lettering is set wide
        return text
    }

    /** Shrink the type until it fits the space it has been given. */
    fun fitSize(s: String, size: Float, maxWidth: Float, bold: Boolean = true): Float {
        var sz = size
        while (sz > 4f && label(sz, MARK, bold).measureText(s) > maxWidth) sz *= 0.94f
        return sz
    }

    /** Panel lettering: stencilled on, with just enough shadow to lift it off the steel. */
    fun engrave(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
        c.drawText(s, x + size * 0.045f, y + size * 0.055f, label(size, 0x77000000, true, align))
        c.drawText(s, x, y, label(size, color, true, align))
    }

    /** A flush slotted screw in the steelwork. */
    fun screw(c: Canvas, x: Float, y: Float, r: Float, angleDeg: Float = 24f) {
        c.drawCircle(x, y, r, solid(STEEL_DARK))
        c.drawCircle(x - r * 0.10f, y - r * 0.10f, r * 0.88f, solid(STEEL))
        val a = Math.toRadians(angleDeg.toDouble())
        val dx = (cos(a) * r * 0.74).toFloat()
        val dy = (sin(a) * r * 0.74).toFloat()
        c.drawLine(x - dx, y - dy, x + dx, y + dy, line(0xFF23282C.toInt(), r * 0.26f))
    }

    /** A machine screw head standing slightly proud. */
    fun rivet(c: Canvas, x: Float, y: Float, r: Float) {
        c.drawCircle(x, y, r, solid(0xFF101316.toInt()))
        fill.alpha = 255
        fill.shader = RadialGradient(
            x - r * 0.35f, y - r * 0.35f, r * 1.5f,
            intArrayOf(NICKEL_LIT, NICKEL_DARK), null, Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r * 0.86f, fill)
        fill.shader = null
    }

    /**
     * A panel of enamelled steel with a machined edge and a thin deco pinstripe
     * set in from it.
     */
    fun panelPlate(c: Canvas, r: RectF, radius: Float = 6f, lift: Float = 1f, pinstripe: Boolean = true) {
        fill.alpha = 255
        fill.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            intArrayOf(blend(PANEL, Color.WHITE, 0.09f * lift), PANEL, blend(PANEL, Color.BLACK, 0.30f)),
            floatArrayOf(0f, 0.40f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(r, radius, radius, fill)
        fill.shader = null
        c.drawRoundRect(r, radius, radius, line(0x2EFFFFFF, 1.4f))
        c.drawRoundRect(RectF(r.left + 1.5f, r.top + 1.5f, r.right - 1.5f, r.bottom - 1.5f), radius, radius, line(0x66000000, 2f))
        if (pinstripe) {
            val inset = minOf(r.width(), r.height()) * 0.030f + 5f
            c.drawRoundRect(
                RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset),
                radius * 0.6f, radius * 0.6f, line(withAlpha(NICKEL, 44), 1.6f)
            )
        }
    }

    /** The switchboard: black phenolic, with the faint swirl of the moulding. */
    fun boardPanel(c: Canvas, r: RectF, seed: Int = 7) {
        fill.alpha = 255
        fill.shader = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            intArrayOf(BOARD_LIGHT, BOARD, blend(BOARD, Color.BLACK, 0.35f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(r, 5f, 5f, fill)
        fill.shader = null
        val p = Path()
        var s = seed * 9781L
        fun rnd(): Float { s = (s * 6364136223846793005L + 1442695040888963407L); return ((s ushr 33).toInt() % 1000) / 1000f }
        val swirl = line(BOARD_VEIN, 1.2f)
        swirl.alpha = 30
        for (i in 0 until 6) {
            p.reset()
            var x = r.left + rnd() * r.width()
            var y = r.top
            p.moveTo(x, y)
            while (y < r.bottom) {
                y += r.height() / 5f
                x += (rnd() - 0.5f) * r.width() * 0.24f
                p.lineTo(x.coerceIn(r.left, r.right), y)
            }
            c.drawPath(p, swirl)
        }
        swirl.alpha = 255
        c.drawRoundRect(r, 5f, 5f, line(0x77000000, 2.5f))
        c.drawRoundRect(RectF(r.left + 1f, r.top + 1f, r.right - 1f, r.bottom - 1f), 5f, 5f, line(withAlpha(NICKEL, 34), 1.4f))
    }

    /** A nickel-plated instrument bezel, knurled on its outer edge. */
    fun bezel(c: Canvas, cx: Float, cy: Float, rOuter: Float, ambient: Float = 1f) {
        fill.alpha = 255
        fill.shader = RadialGradient(
            cx - rOuter * 0.42f, cy - rOuter * 0.52f, rOuter * 1.85f,
            intArrayOf(dim(NICKEL_LIT, ambient), dim(NICKEL, ambient), dim(NICKEL_DARK, ambient)),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, rOuter, fill)
        fill.shader = null
        // knurling around the rim
        val kn = line(withAlpha(Color.BLACK, (46 * ambient).toInt()), rOuter * 0.014f)
        for (i in 0 until 72) {
            val a = Math.toRadians(i * 5.0)
            c.drawLine(
                cx + (cos(a) * rOuter * 0.945).toFloat(), cy + (sin(a) * rOuter * 0.945).toFloat(),
                cx + (cos(a) * rOuter).toFloat(), cy + (sin(a) * rOuter).toFloat(), kn
            )
        }
        c.drawCircle(cx, cy, rOuter * 0.999f, line(0x55000000, 2f))
        // the dark lip the glass sits in
        c.drawCircle(cx, cy, rOuter * 0.905f, line(dim(0xFF14181B.toInt(), ambient), rOuter * 0.055f))
    }

    /** Matte black instrument face. */
    fun dialFace(c: Canvas, cx: Float, cy: Float, r: Float, ambient: Float = 1f) {
        fill.alpha = 255
        fill.shader = RadialGradient(
            cx - r * 0.28f, cy - r * 0.42f, r * 1.75f,
            intArrayOf(dim(blend(FACE, Color.WHITE, 0.10f), ambient), dim(FACE, ambient), dim(FACE_SHADE, ambient)),
            floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, fill)
        fill.shader = null
    }

    /** The cover glass. */
    fun dialGlass(c: Canvas, cx: Float, cy: Float, r: Float) {
        val p = Path()
        p.addCircle(cx, cy, r, Path.Direction.CW)
        c.save()
        c.clipPath(p)
        fill.alpha = 255
        fill.shader = LinearGradient(
            cx - r, cy - r, cx + r * 0.35f, cy + r,
            intArrayOf(0x22FFFFFF, 0x08FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.38f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, fill)
        fill.shader = null
        c.restore()
        c.drawCircle(cx, cy, r, line(0x44000000, 2f))
    }

    /** A jewelled indicator lamp in a nickel socket. */
    fun lamp(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, brightness: Float) {
        val b = brightness.coerceIn(0f, 1f)
        c.drawCircle(cx, cy, r * 1.28f, solid(NICKEL_DARK))
        c.drawCircle(cx, cy, r * 1.16f, solid(0xFF15181B.toInt()))
        if (b > 0.02f) {
            fill.alpha = 255
            fill.shader = RadialGradient(
                cx, cy, r * 3.2f,
                intArrayOf(withAlpha(color, (140 * b).toInt()), withAlpha(color, 0)),
                null, Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, r * 3.2f, fill)
            fill.shader = null
        }
        val lens = blend(blend(LAMP_OFF, color, 0.13f), color, b)
        c.drawCircle(cx, cy, r, solid(lens))
        if (b > 0.02f) {
            fill.alpha = 255
            fill.shader = RadialGradient(
                cx - r * 0.22f, cy - r * 0.26f, r * 1.15f,
                intArrayOf(withAlpha(LAMP_WHITE, (225 * b).toInt()), withAlpha(color, (110 * b).toInt()), withAlpha(color, 0)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, r, fill)
            fill.shader = null
        }
        c.drawCircle(cx, cy, r, line(0x88000000.toInt(), 2f))
    }

    /** An etched nickel nameplate with deco rules either side of the lettering. */
    fun namePlate(c: Canvas, r: RectF, s: String, size: Float, ambient: Float = 1f) {
        fill.alpha = 255
        fill.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            intArrayOf(dim(NICKEL_LIT, ambient), dim(NICKEL, ambient), dim(NICKEL_DARK, ambient)),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(r, 3f, 3f, fill)
        fill.shader = null
        c.drawRoundRect(r, 3f, 3f, line(0x66000000, 1.6f))
        val sz = fitSize(s, size, r.width() - r.height() * 1.40f)
        val p = label(sz, dim(0xFF10141A.toInt(), ambient))
        c.drawText(s, r.centerX(), r.centerY() + sz * 0.35f, p)
        // deco rules running out to the edges of the plate
        val w = p.measureText(s)
        val gap = w / 2 + r.height() * 0.30f
        val rule = line(withAlpha(0xFF10141A.toInt(), (140 * ambient).toInt()), r.height() * 0.05f)
        for (dir in intArrayOf(-1, 1)) {
            val x0 = r.centerX() + dir * gap
            val x1 = r.centerX() + dir * (r.width() / 2 - r.height() * 0.22f)
            if (dir * (x1 - x0) > 0) {
                c.drawLine(x0, r.centerY() - r.height() * 0.11f, x1, r.centerY() - r.height() * 0.11f, rule)
                c.drawLine(x0, r.centerY() + r.height() * 0.11f, x1, r.centerY() + r.height() * 0.11f, rule)
            }
        }
    }

    /**
     * The small black nameplates that label everything on a mimic panel, with
     * their lettering picked out in white.
     */
    fun miniPlate(c: Canvas, cx: Float, cy: Float, s: String, size: Float, ambient: Float) {
        val p = label(size, MARK)
        val w = p.measureText(s) + size * 1.10f
        val h = size * 1.75f
        val r = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        c.drawRoundRect(r, 2f, 2f, solid(dim(0xFF0B0D0F.toInt(), ambient)))
        c.drawRoundRect(r, 2f, 2f, line(withAlpha(NICKEL, (90 * ambient).toInt()), 1.3f))
        c.drawText(s, cx, cy + size * 0.36f, label(size, dim(MARK, ambient)))
    }

    /** A run of deco chevrons, for banding a header. */
    fun chevrons(c: Canvas, r: RectF, color: Int, count: Int = 5) {
        val step = r.width() / count
        val p = Path()
        val paint = line(color, r.height() * 0.16f)
        for (i in 0 until count) {
            val x = r.left + step * i
            p.reset()
            p.moveTo(x, r.bottom)
            p.lineTo(x + step * 0.5f, r.top)
            p.lineTo(x + step, r.bottom)
            c.drawPath(p, paint)
        }
    }

    fun blend(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
        )
    }

    fun withAlpha(c: Int, a: Int): Int = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    /**
     * Dim by the light actually available in the room. The palette is already
     * dark, so this fades towards a cold shadow rather than towards black, and
     * never far enough to make the board unreadable.
     */
    fun dim(c: Int, ambient: Float): Int =
        blend(c, 0xFF0A0D10.toInt(), ((1f - ambient) * 0.80f).coerceIn(0f, 0.62f))
}
