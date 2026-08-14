package com.dynamo.powerplant.ui

import android.graphics.RectF

enum class Tab(val label: String) { CONTROL("CONTROL"), ELECTRICAL("ELECTRICAL") }

/**
 * The board is laid out in a virtual space 1080 wide and as tall as the phone's
 * aspect ratio calls for, so it fills the screen edge to edge on anything from
 * a 16:9 handset to a 21:9 one without letterboxing.
 *
 * The instrument board across the top is always in view. Below it the panel
 * switches between the engine controls and the switchboard, which is the walk
 * an operator makes between the machine and the board.
 */
class Layout(val w: Float, val h: Float) {

    companion object {
        const val VIRTUAL_W = 1080f
        fun virtualHeight(viewW: Int, viewH: Int): Float =
            (VIRTUAL_W * viewH / viewW.toFloat()).coerceIn(1780f, 2680f)
    }

    val header = RectF(0f, 0f, w, h * 0.054f)
    val annunciator = RectF(0f, h - h * 0.056f, w, h)
    val tabBar = RectF(0f, annunciator.top - h * 0.050f, w, annunciator.top)

    private val contentTop = header.bottom
    private val contentH = tabBar.top - contentTop

    /** Always in view: the electrical instruments and the synchronising gear. */
    val gaugeBoard = RectF(0f, contentTop, w, contentTop + contentH * 0.395f)

    /** Whichever deck the tab bar has selected. */
    val deck = RectF(0f, gaugeBoard.bottom, w, tabBar.top)

    fun tabRect(i: Int): RectF {
        val each = w / 2f
        return RectF(each * i, tabBar.top, each * (i + 1), tabBar.bottom)
    }

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

    // ---------------------------------------------------------------- control deck
    private val cd = deck
    val engR = minOf(w * 0.098f, cd.height() * 0.125f)
    private val engGaugeY = cd.top + cd.height() * 0.028f + engR
    val tachometer = Pt(w * 0.170f, engGaugeY)
    val oilGauge = Pt(w * 0.500f, engGaugeY)
    val tempGauge = Pt(w * 0.830f, engGaugeY)

    private val ctlTop = engGaugeY + engR * 1.15f + cd.height() * 0.030f
    private val ctlH = cd.height() * 0.415f

    /** The five mains, side by side, in the order you use them. */
    val keySwitchR = minOf(w * 0.100f, ctlH * 0.42f)
    val keySwitch = Pt(w * 0.150f, ctlTop + ctlH * 0.46f)
    val throttleLever = RectF(w * 0.278f, ctlTop, w * 0.394f, ctlTop + ctlH)
    val sparkLever = RectF(w * 0.418f, ctlTop, w * 0.534f, ctlTop + ctlH)
    val mixtureKnobR = minOf(w * 0.084f, ctlH * 0.33f)
    val mixtureKnob = Pt(w * 0.662f, ctlTop + ctlH * 0.44f)
    val excitationKnobR = minOf(w * 0.084f, ctlH * 0.33f)
    val excitationKnob = Pt(w * 0.872f, ctlTop + ctlH * 0.44f)

    /** Starting gear and the auxiliaries along the bottom of the control deck. */
    private val auxTop = ctlTop + ctlH + cd.height() * 0.038f
    private val auxH = cd.bottom - auxTop - cd.height() * 0.030f
    val compRelease = RectF(w * 0.026f, auxTop + auxH * 0.02f, w * 0.208f, auxTop + auxH * 0.80f)
    val primerButton = Pt(w * 0.272f, auxTop + auxH * 0.40f)
    val primerR = minOf(w * 0.052f, auxH * 0.26f)
    val crankHandle = Pt(w * 0.442f, auxTop + auxH * 0.40f)
    val crankR = minOf(w * 0.080f, auxH * 0.34f)
    val starterButton = Pt(w * 0.612f, auxTop + auxH * 0.40f)
    val starterR = minOf(w * 0.052f, auxH * 0.26f)
    val waterWheelR = minOf(w * 0.058f, auxH * 0.28f)
    val waterWheel = Pt(w * 0.758f, auxTop + auxH * 0.40f)
    val oilerWheelR = minOf(w * 0.058f, auxH * 0.28f)
    val oilerWheel = Pt(w * 0.912f, auxTop + auxH * 0.40f)

    // ---------------------------------------------------------------- electrical deck
    /** The mimic diagram takes the upper part of the switchboard. */
    val mimic = RectF(
        w * 0.020f, deck.top + deck.height() * 0.028f,
        w * 0.980f, deck.top + deck.height() * 0.555f
    )

    val boardPlate = RectF(
        w * 0.020f, mimic.bottom + deck.height() * 0.035f,
        w * 0.980f, deck.bottom - deck.height() * 0.022f
    )
    val mainBreaker = RectF(
        boardPlate.left + w * 0.022f, boardPlate.top + boardPlate.height() * 0.070f,
        boardPlate.left + w * 0.290f, boardPlate.bottom - boardPlate.height() * 0.060f
    )
    val fieldSwitch = RectF(
        mainBreaker.right + w * 0.022f, mainBreaker.top,
        mainBreaker.right + w * 0.148f, mainBreaker.bottom
    )

    /** Four feeder knife switches out to the town. */
    val feeders: List<RectF> = run {
        val left = fieldSwitch.right + w * 0.024f
        val right = boardPlate.right - w * 0.018f
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
