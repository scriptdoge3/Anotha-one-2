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
 * The look of a small-town dynamo room about 1924: black enamelled iron,
 * a slate switchboard, brass bezels gone slightly green, ivory instrument
 * faces and warm carbon-filament lamps.
 */
object Theme {

    // --- iron and slate ---
    const val IRON_DARK = 0xFF16140F.toInt()
    const val IRON = 0xFF262218.toInt()
    const val IRON_LIT = 0xFF3A3425.toInt()
    const val SLATE = 0xFF2B2E31.toInt()
    const val SLATE_LIGHT = 0xFF3C4045.toInt()
    const val SLATE_VEIN = 0xFF565B60.toInt()

    // --- brass and steel ---
    const val BRASS = 0xFFC9A227.toInt()
    const val BRASS_LIT = 0xFFF0D67A.toInt()
    const val BRASS_DARK = 0xFF6E5514.toInt()
    const val BRASS_GREEN = 0xFF8C8A3E.toInt()
    const val STEEL = 0xFF8C8578.toInt()
    const val STEEL_DARK = 0xFF4A453C.toInt()

    // --- instrument faces ---
    const val IVORY = 0xFFE7DCC0.toInt()
    const val IVORY_SHADE = 0xFFCFC2A2.toInt()
    const val INK = 0xFF1B1710.toInt()
    const val INK_SOFT = 0xFF4A4133.toInt()
    const val NEEDLE = 0xFF17130D.toInt()
    const val DANGER = 0xFFA8342A.toInt()
    const val DANGER_SOFT = 0xFF7E2A22.toInt()

    // --- lamps ---
    const val LAMP_OFF = 0xFF3A2E20.toInt()
    const val LAMP_AMBER = 0xFFFFC15E.toInt()
    const val LAMP_RED = 0xFFFF6A4A.toInt()
    const val LAMP_GREEN = 0xFF9BE07A.toInt()
    const val LAMP_WHITE = 0xFFFFF2D0.toInt()

    // --- timber ---
    const val MAHOGANY = 0xFF4A2A16.toInt()
    const val MAHOGANY_LIT = 0xFF6B3E20.toInt()

    val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    val serifBold: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = serifBold
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
        stroke.color = color
        stroke.strokeWidth = width
        stroke.strokeCap = Paint.Cap.BUTT
        return stroke
    }

    fun label(size: Float, color: Int = INK, bold: Boolean = true, align: Paint.Align = Paint.Align.CENTER): Paint {
        text.shader = null
        text.textSize = size
        text.color = color
        text.typeface = if (bold) serifBold else serif
        text.textAlign = align
        text.letterSpacing = 0.06f
        return text
    }

    /** Shrink the type until it fits the space it has been given. */
    fun fitSize(s: String, size: Float, maxWidth: Float, bold: Boolean = true): Float {
        var sz = size
        while (sz > 4f && label(sz, INK, bold).measureText(s) > maxWidth) sz *= 0.94f
        return sz
    }

    /** Engraved lettering: a light lower edge under dark text, as if cut into metal. */
    fun engrave(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
        c.drawText(s, x, y + size * 0.055f, label(size, 0x55FFFFFF, true, align))
        c.drawText(s, x, y, label(size, color, true, align))
    }

    /** A slotted steel screw head, the kind holding every panel down. */
    fun screw(c: Canvas, x: Float, y: Float, r: Float, angleDeg: Float = 24f) {
        c.drawCircle(x, y, r, solid(STEEL_DARK))
        c.drawCircle(x - r * 0.12f, y - r * 0.12f, r * 0.86f, solid(STEEL))
        val a = Math.toRadians(angleDeg.toDouble())
        val dx = (cos(a) * r * 0.72).toFloat()
        val dy = (sin(a) * r * 0.72).toFloat()
        c.drawLine(x - dx, y - dy, x + dx, y + dy, line(0xFF2A261F.toInt(), r * 0.30f))
    }

    /** A hammered rivet head. */
    fun rivet(c: Canvas, x: Float, y: Float, r: Float) {
        c.drawCircle(x, y, r, solid(0xFF15130E.toInt()))
        fill.alpha = 255
        fill.shader = RadialGradient(
            x - r * 0.35f, y - r * 0.35f, r * 1.5f,
            intArrayOf(0xFF9A9284.toInt(), STEEL_DARK), null, Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r * 0.88f, fill)
        fill.shader = null
    }

    /** Cast-iron plate with a rolled edge and a soft top-lit gradient. */
    fun ironPlate(c: Canvas, r: RectF, radius: Float = 10f, lift: Float = 1f) {
        fill.alpha = 255
        fill.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            intArrayOf(blend(IRON, Color.WHITE, 0.06f * lift), IRON, blend(IRON, Color.BLACK, 0.35f)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(r, radius, radius, fill)
        fill.shader = null
        c.drawRoundRect(r, radius, radius, line(0x33FFFFFF, 1.6f))
        c.drawRoundRect(RectF(r.left + 2, r.top + 2, r.right - 2, r.bottom - 2), radius, radius, line(0x55000000, 2f))
    }

    /** Quarried slate for the switchboard, with a little veining. */
    fun slatePanel(c: Canvas, r: RectF, seed: Int = 7) {
        fill.alpha = 255
        fill.shader = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            intArrayOf(SLATE_LIGHT, SLATE, blend(SLATE, Color.BLACK, 0.30f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(r, 8f, 8f, fill)
        fill.shader = null
        // veining
        val p = Path()
        var s = seed * 9781L
        fun rnd(): Float { s = (s * 6364136223846793005L + 1442695040888963407L); return ((s ushr 33).toInt() % 1000) / 1000f }
        val vein = line(SLATE_VEIN, 1.4f)
        vein.alpha = 46
        for (i in 0 until 7) {
            p.reset()
            var x = r.left + rnd() * r.width()
            var y = r.top
            p.moveTo(x, y)
            while (y < r.bottom) {
                y += r.height() / 6f
                x += (rnd() - 0.5f) * r.width() * 0.30f
                p.lineTo(x.coerceIn(r.left, r.right), y)
            }
            c.drawPath(p, vein)
        }
        vein.alpha = 255
        c.drawRoundRect(r, 8f, 8f, line(0x66000000, 3f))
    }

    /** Turned brass bezel around an instrument. */
    fun brassBezel(c: Canvas, cx: Float, cy: Float, rOuter: Float, ambient: Float = 1f) {
        fill.alpha = 255
        fill.shader = RadialGradient(
            cx - rOuter * 0.4f, cy - rOuter * 0.5f, rOuter * 1.9f,
            intArrayOf(
                blend(BRASS_LIT, Color.BLACK, 1f - ambient),
                blend(BRASS, Color.BLACK, 1f - ambient),
                blend(BRASS_DARK, Color.BLACK, 1f - ambient)
            ),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, rOuter, fill)
        fill.shader = null
        c.drawCircle(cx, cy, rOuter * 0.995f, line(0x44000000, 2f))
        c.drawCircle(cx, cy, rOuter * 0.90f, line(blend(BRASS_DARK, Color.BLACK, 1f - ambient), rOuter * 0.035f))
    }

    /** Ivory enamel instrument face, slightly domed by the glass over it. */
    fun dialFace(c: Canvas, cx: Float, cy: Float, r: Float, ambient: Float = 1f) {
        fill.alpha = 255
        fill.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.45f, r * 1.7f,
            intArrayOf(
                blend(0xFFFFF6E2.toInt(), Color.BLACK, 1f - ambient),
                blend(IVORY, Color.BLACK, 1f - ambient),
                blend(IVORY_SHADE, Color.BLACK, 1f - ambient)
            ),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, fill)
        fill.shader = null
    }

    /** The curved reflection on the cover glass. */
    fun dialGlass(c: Canvas, cx: Float, cy: Float, r: Float) {
        val p = Path()
        p.addCircle(cx, cy, r, Path.Direction.CW)
        c.save()
        c.clipPath(p)
        fill.alpha = 255
        fill.shader = LinearGradient(
            cx - r, cy - r, cx + r * 0.4f, cy + r,
            intArrayOf(0x2EFFFFFF, 0x0AFFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, fill)
        fill.shader = null
        c.restore()
        c.drawCircle(cx, cy, r, line(0x33000000, 2f))
    }

    /** A glowing lamp bulb behind a coloured lens. */
    fun lamp(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, brightness: Float) {
        val b = brightness.coerceIn(0f, 1f)
        // socket ring
        c.drawCircle(cx, cy, r * 1.26f, solid(blend(BRASS_DARK, IRON_DARK, 0.4f)))
        c.drawCircle(cx, cy, r * 1.26f, line(0x66000000, 2f))
        if (b > 0.02f) {
            fill.alpha = 255
        fill.shader = RadialGradient(
                cx, cy, r * 3.4f,
                intArrayOf(withAlpha(color, (150 * b).toInt()), withAlpha(color, 0)),
                null, Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, r * 3.4f, fill)
            fill.shader = null
        }
        val lens = blend(blend(0xFF1E1811.toInt(), color, 0.10f), color, b)
        c.drawCircle(cx, cy, r, solid(lens))
        if (b > 0.02f) {
            fill.alpha = 255
        fill.shader = RadialGradient(
                cx - r * 0.2f, cy - r * 0.25f, r * 1.15f,
                intArrayOf(withAlpha(LAMP_WHITE, (235 * b).toInt()), withAlpha(color, (120 * b).toInt()), withAlpha(color, 0)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, r, fill)
            fill.shader = null
        }
        c.drawCircle(cx, cy, r, line(0x77000000, 2.2f))
        // a hint of the filament
        if (b > 0.38f) {
            // the hairpin filament of a carbon lamp
            val fp = Path()
            fp.moveTo(cx - r * 0.24f, cy + r * 0.18f)
            fp.lineTo(cx - r * 0.10f, cy - r * 0.20f)
            fp.lineTo(cx + r * 0.10f, cy - r * 0.20f)
            fp.lineTo(cx + r * 0.24f, cy + r * 0.18f)
            c.drawPath(fp, line(withAlpha(LAMP_WHITE, (235 * b).toInt()), r * 0.075f))
        }
    }

    /** Little engraved brass nameplate. */
    fun namePlate(c: Canvas, r: RectF, s: String, size: Float, ambient: Float = 1f) {
        fill.alpha = 255
        fill.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            intArrayOf(
                blend(BRASS_LIT, Color.BLACK, 1f - ambient),
                blend(BRASS, Color.BLACK, 1f - ambient),
                blend(BRASS_DARK, Color.BLACK, 1f - ambient)
            ),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(r, 4f, 4f, fill)
        fill.shader = null
        c.drawRoundRect(r, 4f, 4f, line(0x55000000, 1.6f))
        screw(c, r.left + r.height() * 0.42f, r.centerY(), r.height() * 0.19f)
        screw(c, r.right - r.height() * 0.42f, r.centerY(), r.height() * 0.19f)
        val sz = fitSize(s, size, r.width() - r.height() * 1.30f)
        val p = label(sz, blend(0xFF241B05.toInt(), Color.BLACK, 1f - ambient))
        c.drawText(s, r.centerX(), r.centerY() + sz * 0.35f, p)
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

    /** Dim everything by the light actually available in the room. */
    fun dim(c: Int, ambient: Float): Int = blend(c, Color.BLACK, (1f - ambient).coerceIn(0f, 0.82f))
}
