package com.dynamo.powerplant.ui

import android.graphics.RectF

enum class Tab(val label: String) { CONTROL("CONTROL"), ENGINE("ENGINE"), ELECTRICAL("ELECTRICAL") }

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
        val each = w / Tab.entries.size
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
    fun panelHeader(r: RectF): Float = minOf(r.height() * 0.145f, h * 0.0245f)

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

    // ---------------------------------------------------------------- engine deck
    // Six cylinders across the top, each with its exhaust pyrometer, its sight
    // feed and its igniter cut-out. The tanks and their valves underneath.
    private val ed = deck
    private val edGap = ed.height() * 0.016f
    private val edTop = ed.top + ed.height() * 0.020f
    private val edH = ed.height() - ed.height() * 0.040f - edGap

    val cylinderPanel = RectF(w * 0.018f, edTop, w * 0.982f, edTop + edH * 0.560f)
    val tankPanel = RectF(w * 0.018f, cylinderPanel.bottom + edGap, w * 0.982f, cylinderPanel.bottom + edGap + edH * 0.440f)

    private val cp = cylinderPanel
    private val cpBody = cp.top + panelHeader(cp)
    private val cpH = cp.bottom - cpBody

    /** One column per cylinder, sharing the width of the panel. */
    fun cylinderColumn(i: Int): RectF {
        // A gutter down the left for the pyrometer scale and the row captions.
        val left = cp.left + w * 0.078f
        val right = cp.right - w * 0.012f
        val each = (right - left) / 6f
        return RectF(left + each * i + each * 0.04f, cpBody, left + each * (i + 1) - each * 0.04f, cp.bottom)
    }

    /** The exhaust pyrometer bar for one cylinder, read against a common scale. */
    fun pyrometer(i: Int): RectF {
        val c = cylinderColumn(i)
        return RectF(c.left + c.width() * 0.26f, cpBody + cpH * 0.045f, c.right - c.width() * 0.26f, cpBody + cpH * 0.430f)
    }

    fun sightFeedAt(i: Int): Pt {
        val c = cylinderColumn(i)
        return Pt(c.centerX(), cpBody + cpH * 0.595f)
    }
    val sightFeedR = minOf(w * 0.043f, cpH * 0.115f)

    fun igniterSwitch(i: Int): RectF {
        val c = cylinderColumn(i)
        return RectF(c.left + c.width() * 0.14f, cpBody + cpH * 0.790f, c.right - c.width() * 0.14f, cpBody + cpH * 0.960f)
    }

    // --- the tanks ---
    private val tp = tankPanel
    private val tpBody = tp.top + panelHeader(tp)
    private val tpH = tp.bottom - tpBody

    /** Three gauge glasses: fuel day tank, jacket water header, oil sump. */
    fun tankGlass(i: Int): RectF {
        val each = w * 0.115f
        val left = tp.left + w * 0.038f + each * i * 1.42f
        return RectF(left, tpBody + tpH * 0.180f, left + each * 0.42f, tpBody + tpH * 0.760f)
    }

    val fuelCock = RectF(w * 0.520f, tpBody + tpH * 0.075f, w * 0.725f, tpBody + tpH * 0.360f)
    val fuelTransfer = RectF(w * 0.750f, tpBody + tpH * 0.075f, w * 0.955f, tpBody + tpH * 0.360f)
    val mainTankBar = RectF(w * 0.520f, tpBody + tpH * 0.450f, w * 0.955f, tpBody + tpH * 0.545f)
    val oilReplenish = Pt(w * 0.610f, tpBody + tpH * 0.755f)
    val oilReplenishR = minOf(w * 0.048f, tpH * 0.130f)
    val makeUpWheel = Pt(w * 0.855f, tpBody + tpH * 0.740f)
    val makeUpWheelR = minOf(w * 0.052f, tpH * 0.145f)

    // ---------------------------------------------------------------- electrical deck
    /** The mimic diagram takes the upper part of the switchboard. */
    val mimic = RectF(
        w * 0.020f, deck.top + deck.height() * 0.020f,
        w * 0.980f, deck.top + deck.height() * 0.400f
    )

    /** The relay panel: one target window per relay, under the mimic. */
    val relayPlate = RectF(
        w * 0.020f, mimic.bottom + deck.height() * 0.020f,
        w * 0.980f, mimic.bottom + deck.height() * 0.148f
    )

    fun relayWindow(i: Int, n: Int): RectF {
        val left = relayPlate.left + w * 0.016f
        val right = relayPlate.right - w * 0.016f
        val each = (right - left) / n
        val top = relayPlate.top + relayPlate.height() * 0.300f
        return RectF(left + each * i + each * 0.06f, top, left + each * (i + 1) - each * 0.06f, relayPlate.bottom - relayPlate.height() * 0.090f)
    }

    val boardPlate = RectF(
        w * 0.020f, relayPlate.bottom + deck.height() * 0.018f,
        w * 0.980f, deck.bottom - deck.height() * 0.014f
    )
    val mainBreaker = RectF(
        boardPlate.left + w * 0.018f, boardPlate.top + boardPlate.height() * 0.055f,
        boardPlate.left + w * 0.200f, boardPlate.bottom - boardPlate.height() * 0.045f
    )

    /**
     * The board carries two rows of knife switches to the right of the unit
     * breaker: the main bus above, the emergency line below.
     */
    private val rowsLeft = mainBreaker.right + w * 0.016f
    private val rowsRight = boardPlate.right - w * 0.012f
    private val rowsTop = boardPlate.top + boardPlate.height() * 0.055f
    private val rowsH = boardPlate.height() * 0.900f
    private val rowGap = boardPlate.height() * 0.030f

    val mainRowLabel = RectF(rowsLeft, rowsTop, rowsRight, rowsTop + rowsH * 0.068f)
    private val mainRow = RectF(rowsLeft, mainRowLabel.bottom, rowsRight, rowsTop + rowsH * 0.480f)
    val emgRowLabel = RectF(rowsLeft, mainRow.bottom + rowGap, rowsRight, mainRow.bottom + rowGap + rowsH * 0.068f)
    private val emgRow = RectF(rowsLeft, emgRowLabel.bottom, rowsRight, rowsTop + rowsH)

    private fun cells(r: RectF, n: Int): List<RectF> {
        val each = r.width() / n
        return (0 until n).map {
            RectF(r.left + each * it + each * 0.05f, r.top, r.left + each * (it + 1) - each * 0.05f, r.bottom)
        }
    }

    /**
     * The main bus row: its own breaker, the starting transformer breaker, and
     * then the five regular loads.
     */
    private val mainCells = cells(mainRow, 7)

    /** Generator terminals to the station transformer. */
    val stationTxBreaker: RectF = mainCells[0]

    /** The grid tap, up on the system section of the high tension bar. */
    val startingTxBreaker: RectF = mainCells[1]

    /** Control supply, circulating pump, oil pump, fuel pump, house lights. */
    val mainSwitches: List<RectF> = mainCells.subList(2, 7)

    private val emgCells = cells(emgRow, 7)

    /** Generator to the emergency transformer, the road the charge takes. */
    val emgTxBreaker: RectF = emgCells[0]

    /** Battery out to the emergency line. */
    val batteryBreaker: RectF = emgCells[1]

    /** The field switch, with its discharge resistor. */
    val fieldSwitch: RectF = emgCells[2]

    /** The four emergency line switches on the board. */
    val auxSwitches: List<RectF> = emgCells.subList(3, 7)

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
