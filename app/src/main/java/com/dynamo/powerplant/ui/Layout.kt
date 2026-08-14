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
    // Three bordered panels, stacked, each with its own engraved header.
    private val cd = deck
    private val panelGap = cd.height() * 0.016f
    private val panelsTop = cd.top + cd.height() * 0.020f
    private val panelsH = cd.height() - cd.height() * 0.040f - panelGap * 2f

    val enginePanel = RectF(w * 0.018f, panelsTop, w * 0.982f, panelsTop + panelsH * 0.500f)
    val oilWaterPanel = RectF(
        w * 0.018f, enginePanel.bottom + panelGap,
        w * 0.982f, enginePanel.bottom + panelGap + panelsH * 0.255f
    )
    val generatorPanel = RectF(
        w * 0.018f, oilWaterPanel.bottom + panelGap,
        w * 0.982f, oilWaterPanel.bottom + panelGap + panelsH * 0.245f
    )

    /** Height of the engraved header strip at the top of each panel. */
    fun panelHeader(r: RectF): Float = r.height() * 0.145f

    // --- ENGINE panel ---------------------------------------------------------
    private val ep = enginePanel
    private val epBody = ep.top + panelHeader(ep)
    private val epH = ep.bottom - epBody

    val engR = minOf(w * 0.078f, epH * 0.30f)
    val tachometer = Pt(ep.left + w * 0.098f, epBody + epH * 0.310f)

    val keySwitchR = minOf(w * 0.077f, epH * 0.285f)
    val keySwitch = Pt(w * 0.325f, epBody + epH * 0.310f)
    val throttleLever = RectF(w * 0.450f, epBody + epH * 0.030f, w * 0.560f, epBody + epH * 0.600f)
    val sparkLever = RectF(w * 0.585f, epBody + epH * 0.030f, w * 0.695f, epBody + epH * 0.600f)
    val mixtureKnobR = minOf(w * 0.072f, epH * 0.245f)
    val mixtureKnob = Pt(w * 0.855f, epBody + epH * 0.290f)

    /** Starting gear along the foot of the engine panel. */
    private val startTop = epBody + epH * 0.670f
    private val startH = ep.bottom - startTop - epH * 0.030f
    val compRelease = RectF(ep.left + w * 0.020f, startTop, ep.left + w * 0.215f, startTop + startH * 0.94f)
    val primerButton = Pt(w * 0.360f, startTop + startH * 0.42f)
    val primerR = minOf(w * 0.050f, startH * 0.30f)
    val starterButton = Pt(w * 0.560f, startTop + startH * 0.42f)
    val starterR = minOf(w * 0.062f, startH * 0.36f)

    // --- OIL AND WATER panel --------------------------------------------------
    private val op = oilWaterPanel
    private val opBody = op.top + panelHeader(op)
    private val opH = op.bottom - opBody

    val auxGaugeR = minOf(w * 0.070f, opH * 0.42f)
    val oilGauge = Pt(w * 0.130f, opBody + opH * 0.470f)
    val tempGauge = Pt(w * 0.375f, opBody + opH * 0.470f)
    val oilerWheelR = minOf(w * 0.058f, opH * 0.335f)
    val oilerWheel = Pt(w * 0.630f, opBody + opH * 0.400f)
    val waterWheelR = minOf(w * 0.058f, opH * 0.335f)
    val waterWheel = Pt(w * 0.855f, opBody + opH * 0.400f)

    // --- GENERATOR panel ------------------------------------------------------
    private val gp = generatorPanel
    private val gpBody = gp.top + panelHeader(gp)
    private val gpH = gp.bottom - gpBody

    val excitationKnobR = minOf(w * 0.070f, gpH * 0.395f)
    val excitationKnob = Pt(w * 0.150f, gpBody + gpH * 0.450f)
    /** Edgewise meters for the field, alongside the rheostat. */
    val fieldMeter = RectF(w * 0.310f, gpBody + gpH * 0.300f, w * 0.640f, gpBody + gpH * 0.430f)
    val varMeter = RectF(w * 0.310f, gpBody + gpH * 0.630f, w * 0.640f, gpBody + gpH * 0.760f)
    val powerFactorAt = Pt(w * 0.830f, gpBody + gpH * 0.480f)

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
        boardPlate.left + w * 0.020f, boardPlate.top + boardPlate.height() * 0.070f,
        boardPlate.left + w * 0.240f, boardPlate.bottom - boardPlate.height() * 0.060f
    )

    /**
     * The knife switches along the board: the emergency transformer breaker, the
     * battery breaker, and then the four loads on the emergency line.
     */
    private val switchRow: List<RectF> = run {
        val left = mainBreaker.right + w * 0.018f
        val right = boardPlate.right - w * 0.014f
        val each = (right - left) / 6f
        (0 until 6).map {
            RectF(left + each * it + each * 0.05f, mainBreaker.top, left + each * (it + 1) - each * 0.05f, mainBreaker.bottom)
        }
    }

    /** Generator to the emergency transformer, the road the charge takes. */
    val emgTxBreaker: RectF = switchRow[0]

    /** Battery out to the emergency line. */
    val batteryBreaker: RectF = switchRow[1]

    /** The four emergency line switches on the board. */
    val auxSwitches: List<RectF> = switchRow.subList(2, 6)

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
