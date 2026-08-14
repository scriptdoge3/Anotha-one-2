package com.dynamo.powerplant.ui

import android.graphics.RectF

/**
 * The board is laid out in a virtual space 1080 wide and as tall as the phone's
 * aspect ratio calls for, so it fills the screen edge to edge on anything from
 * a 16:9 handset to a 21:9 one without letterboxing.
 */
class Layout(val w: Float, val h: Float) {

    companion object {
        const val VIRTUAL_W = 1080f
        fun virtualHeight(viewW: Int, viewH: Int): Float =
            (VIRTUAL_W * viewH / viewW.toFloat()).coerceIn(1780f, 2680f)
    }

    val header = RectF(0f, 0f, w, h * 0.056f)
    val annunciator = RectF(0f, h - h * 0.058f, w, h)

    private val contentTop = header.bottom
    private val contentH = annunciator.top - contentTop

    val gaugeBoard = RectF(0f, contentTop, w, contentTop + contentH * 0.395f)
    val engineDeck = RectF(0f, gaugeBoard.bottom, w, gaugeBoard.bottom + contentH * 0.350f)
    val switchBoard = RectF(0f, engineDeck.bottom, w, annunciator.top)

    // ---------------------------------------------------------------- gauges
    private val gb = gaugeBoard
    val bigR = minOf(w * 0.200f, gb.height() * 0.230f)
    val synchroscope = Pt(w * 0.268f, gb.top + gb.height() * 0.035f + bigR)
    val freqDial = Pt(w * 0.732f, gb.top + gb.height() * 0.035f + bigR)

    val lampR = minOf(w * 0.036f, gb.height() * 0.042f)
    val lampY = gb.top + gb.height() * 0.625f
    val lamps = listOf(
        Pt(w * 0.330f, lampY), Pt(w * 0.5f, lampY), Pt(w * 0.670f, lampY)
    )

    val smallR = minOf(w * 0.104f, gb.height() * 0.125f)
    private val smallY = gb.bottom - smallR * 1.15f - gb.height() * 0.030f
    val genVolts = Pt(w * 0.152f, smallY)
    val busVolts = Pt(w * 0.384f, smallY)
    val wattmeter = Pt(w * 0.616f, smallY)
    val ammeter = Pt(w * 0.848f, smallY)

    // ---------------------------------------------------------------- engine deck
    private val ed = engineDeck
    val engR = minOf(w * 0.088f, ed.height() * 0.145f)
    private val engGaugeY = ed.top + ed.height() * 0.030f + engR
    val tachometer = Pt(w * 0.170f, engGaugeY)
    val oilGauge = Pt(w * 0.500f, engGaugeY)
    val tempGauge = Pt(w * 0.830f, engGaugeY)

    private val ctlTop = engGaugeY + engR * 1.15f + ed.height() * 0.045f
    private val ctlH = ed.height() * 0.355f

    /** The five mains, side by side, in the order you use them. */
    val keySwitchR = minOf(w * 0.090f, ctlH * 0.40f)
    val keySwitch = Pt(w * 0.160f, ctlTop + ctlH * 0.46f)
    val throttleLever = RectF(w * 0.268f, ctlTop, w * 0.384f, ctlTop + ctlH)
    val sparkLever = RectF(w * 0.408f, ctlTop, w * 0.524f, ctlTop + ctlH)
    val mixtureKnobR = minOf(w * 0.076f, ctlH * 0.33f)
    val mixtureKnob = Pt(w * 0.658f, ctlTop + ctlH * 0.44f)
    val excitationKnobR = minOf(w * 0.076f, ctlH * 0.33f)
    val excitationKnob = Pt(w * 0.868f, ctlTop + ctlH * 0.44f)

    /** Starting gear and the auxiliaries along the bottom of the engine deck. */
    private val auxTop = ctlTop + ctlH + ed.height() * 0.045f
    private val auxH = ed.bottom - auxTop - ed.height() * 0.030f
    val compRelease = RectF(w * 0.028f, auxTop, w * 0.178f, auxTop + auxH)
    val primerButton = Pt(w * 0.262f, auxTop + auxH * 0.42f)
    val primerR = minOf(w * 0.050f, auxH * 0.28f)
    val crankHandle = Pt(w * 0.435f, auxTop + auxH * 0.42f)
    val crankR = minOf(w * 0.076f, auxH * 0.38f)
    val starterButton = Pt(w * 0.608f, auxTop + auxH * 0.42f)
    val starterR = minOf(w * 0.050f, auxH * 0.28f)
    val waterWheelR = minOf(w * 0.056f, auxH * 0.31f)
    val waterWheel = Pt(w * 0.762f, auxTop + auxH * 0.42f)
    val oilerWheelR = minOf(w * 0.056f, auxH * 0.31f)
    val oilerWheel = Pt(w * 0.922f, auxTop + auxH * 0.42f)

    // ---------------------------------------------------------------- switchboard
    private val sb = switchBoard
    val boardPlate = RectF(w * 0.020f, sb.top + sb.height() * 0.045f, w * 0.980f, sb.bottom - sb.height() * 0.045f)
    val mainBreaker = RectF(
        boardPlate.left + w * 0.022f, boardPlate.top + sb.height() * 0.085f,
        boardPlate.left + w * 0.300f, boardPlate.bottom - sb.height() * 0.075f
    )
    val fieldSwitch = RectF(
        mainBreaker.right + w * 0.022f, mainBreaker.top,
        mainBreaker.right + w * 0.150f, mainBreaker.bottom
    )

    /** Four feeder knife switches out to the town. */
    val feeders: List<RectF> = run {
        val left = fieldSwitch.right + w * 0.026f
        val right = boardPlate.right - w * 0.020f
        val each = (right - left) / 4f
        (0 until 4).map {
            RectF(left + each * it + each * 0.06f, mainBreaker.top, left + each * (it + 1) - each * 0.06f, mainBreaker.bottom)
        }
    }

    // ---------------------------------------------------------------- annunciator
    val alarmR = minOf(w * 0.026f, annunciator.height() * 0.30f)

    fun alarmPos(i: Int, n: Int): Pt {
        val each = w / n
        return Pt(each * i + each * 0.5f, annunciator.top + annunciator.height() * 0.38f)
    }
}

data class Pt(val x: Float, val y: Float) {
    fun near(px: Float, py: Float, r: Float): Boolean {
        val dx = px - x
        val dy = py - y
        return dx * dx + dy * dy <= r * r
    }
}
