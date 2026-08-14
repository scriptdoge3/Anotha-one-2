package com.dynamo.powerplant.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.dynamo.powerplant.sim.Grid
import com.dynamo.powerplant.sim.IgnitionMode
import com.dynamo.powerplant.sim.Plant
import com.dynamo.powerplant.sim.Spec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Draws the whole dynamo room board. Static ironwork is cached; only the needles move. */
class PanelRenderer(val L: Layout) {

    val freqDial = Dial(
        L.freqDial.x, L.freqDial.y, L.bigR, "CYCLES", "PER SECOND",
        50.0, 70.0, 4, 5,
        listOf(Band(50.0, 58.4), Band(61.6, 70.0)), decimals = 0
    )
    val genVoltsDial = Dial(
        L.genVolts.x, L.genVolts.y, L.smallR, "MACHINE", "VOLTS",
        0.0, 3000.0, 3, 5, listOf(Band(2530.0, 3000.0))
    )
    val busVoltsDial = Dial(
        L.busVolts.x, L.busVolts.y, L.smallR, "BUS", "VOLTS",
        0.0, 3000.0, 3, 5, listOf(Band(2530.0, 3000.0))
    )
    val wattDial = Dial(
        L.wattmeter.x, L.wattmeter.y, L.smallR, "OUTPUT", "KILOWATTS",
        -40.0, 160.0, 5, 4, listOf(Band(-40.0, 0.0), Band(132.0, 160.0))
    )
    val ampDial = Dial(
        L.ammeter.x, L.ammeter.y, L.smallR, "LINE", "AMPERES",
        0.0, 60.0, 3, 5, listOf(Band(45.0, 60.0))
    )
    val tachDial = Dial(
        L.tachometer.x, L.tachometer.y, L.engR, "SPEED", "REV. PER MIN.",
        0.0, 900.0, 3, 5, listOf(Band(Spec.OVERSPEED_RPM, 900.0))
    )
    val oilDial = Dial(
        L.oilGauge.x, L.oilGauge.y, L.engR, "OIL", "LBS. SQ. IN.",
        0.0, 40.0, 4, 5, listOf(Band(0.0, 8.0))
    )
    val tempDial = Dial(
        L.tempGauge.x, L.tempGauge.y, L.engR, "JACKET", "DEG. CENT.",
        0.0, 150.0, 5, 5, listOf(Band(105.0, 150.0))
    )

    private var background: Bitmap? = null
    private var backgroundAmbient = -1f

    /** Alarms across the annunciator strip, in the order they are wired. */
    private val alarmNames = listOf("OVERSPEED", "LOW OIL", "HOT", "KNOCK", "REV. PWR", "FIELD", "FUSE", "BATTERY")

    // ------------------------------------------------------------------ static

    private fun buildBackground(ambient: Float): Bitmap {
        val bmp = Bitmap.createBitmap(L.w.toInt(), L.h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawRoom(c, ambient)
        drawHeaderPlate(c, ambient)
        drawGaugeBoardStatic(c, ambient)
        drawEngineDeckStatic(c, ambient)
        drawSwitchBoardStatic(c, ambient)
        drawAnnunciatorStatic(c, ambient)
        return bmp
    }

    private fun drawRoom(c: Canvas, ambient: Float) {
        c.drawColor(Theme.dim(Theme.IRON_DARK, ambient))
        // the pool of light from the lamp hung over the board
        Theme.fill.alpha = 255
        Theme.fill.shader = RadialGradient(
            L.w * 0.5f, L.h * 0.30f, L.h * 0.72f,
            intArrayOf(
                Theme.withAlpha(0xFFFFD9A0.toInt(), (34 * ambient).toInt()),
                Theme.withAlpha(0xFF000000.toInt(), 0)
            ), null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, L.w, L.h, Theme.fill)
        Theme.fill.shader = null
        // brick courses behind the board, barely visible
        val brick = Theme.line(Theme.withAlpha(Color.WHITE, (7 * ambient).toInt()), 1.4f)
        var y = 0f
        while (y < L.h) {
            c.drawLine(0f, y, L.w, y, brick)
            y += L.h * 0.031f
        }
    }

    private fun drawHeaderPlate(c: Canvas, ambient: Float) {
        val r = RectF(0f, 0f, L.w, L.header.bottom)
        Theme.fill.alpha = 255
        Theme.fill.shader = LinearGradient(
            0f, 0f, 0f, r.bottom,
            intArrayOf(Theme.dim(Theme.MAHOGANY, ambient), Theme.dim(0xFF2E1A0E.toInt(), ambient)),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(r, Theme.fill)
        Theme.fill.shader = null
        c.drawLine(0f, r.bottom, L.w, r.bottom, Theme.line(Theme.dim(Theme.BRASS_DARK, ambient), 3f))
        Theme.namePlate(
            c,
            RectF(L.w * 0.025f, r.height() * 0.19f, L.w * 0.470f, r.height() * 0.81f),
            "MILLBROOK ELECTRIC LIGHT & POWER", r.height() * 0.30f, ambient
        )
    }

    private fun drawGaugeBoardStatic(c: Canvas, ambient: Float) {
        val r = RectF(L.w * 0.012f, L.gaugeBoard.top + 4f, L.w * 0.988f, L.gaugeBoard.bottom - 4f)
        Theme.ironPlate(c, r, 12f)
        val n = 9
        for (i in 0 until n) {
            val x = r.left + r.width() * (i + 0.5f) / n
            Theme.rivet(c, x, r.top + 11f, 6.5f)
            Theme.rivet(c, x, r.bottom - 11f, 6.5f)
        }

        Instruments.drawSynchroscopeFace(c, L.synchroscope.x, L.synchroscope.y, L.bigR, ambient)
        freqDial.drawFace(c, ambient)

        // the three synchronising lamps sit on their own little sub-plate
        val lr = RectF(
            L.lamps[0].x - L.lampR * 2.0f, L.lampY - L.lampR * 1.55f,
            L.lamps[2].x + L.lampR * 2.0f, L.lampY + L.lampR * 1.55f
        )
        Theme.ironPlate(c, lr, 8f, 0.5f)
        Theme.engrave(
            c, "SYNCHRONISING LAMPS", lr.centerX(), lr.top - L.lampR * 0.42f,
            L.lampR * 0.56f, Theme.dim(Theme.BRASS_LIT, ambient)
        )

        genVoltsDial.drawFace(c, ambient)
        busVoltsDial.drawFace(c, ambient)
        wattDial.drawFace(c, ambient)
        ampDial.drawFace(c, ambient)
    }

    private fun drawEngineDeckStatic(c: Canvas, ambient: Float) {
        val r = RectF(L.w * 0.012f, L.engineDeck.top + 4f, L.w * 0.988f, L.engineDeck.bottom - 4f)
        Theme.ironPlate(c, r, 12f, 0.7f)
        Theme.rivet(c, r.left + 14f, r.top + 14f, 6f)
        Theme.rivet(c, r.right - 14f, r.top + 14f, 6f)
        Theme.rivet(c, r.left + 14f, r.bottom - 14f, 6f)
        Theme.rivet(c, r.right - 14f, r.bottom - 14f, 6f)

        tachDial.drawFace(c, ambient)
        oilDial.drawFace(c, ambient)
        tempDial.drawFace(c, ambient)
    }

    private fun drawSwitchBoardStatic(c: Canvas, ambient: Float) {
        Theme.slatePanel(c, L.boardPlate)
        Theme.screw(c, L.boardPlate.left + 16f, L.boardPlate.top + 16f, 8f)
        Theme.screw(c, L.boardPlate.right - 16f, L.boardPlate.top + 16f, 8f, 70f)
        Theme.screw(c, L.boardPlate.left + 16f, L.boardPlate.bottom - 16f, 8f, 110f)
        Theme.screw(c, L.boardPlate.right - 16f, L.boardPlate.bottom - 16f, 8f, 20f)
    }

    private fun drawAnnunciatorStatic(c: Canvas, ambient: Float) {
        val r = RectF(0f, L.annunciator.top, L.w, L.h)
        Theme.fill.alpha = 255
        Theme.fill.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            intArrayOf(Theme.dim(0xFF201C15.toInt(), ambient), Theme.dim(Theme.IRON_DARK, ambient)),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(r, Theme.fill)
        Theme.fill.shader = null
        c.drawLine(0f, r.top, L.w, r.top, Theme.line(Theme.dim(Theme.BRASS_DARK, ambient), 2.5f))
        for (i in alarmNames.indices) {
            val p = L.alarmPos(i, alarmNames.size)
            Theme.engrave(
                c, alarmNames[i], p.x, r.bottom - r.height() * 0.14f,
                L.alarmR * 0.62f, Theme.dim(Theme.BRASS_DARK, ambient)
            )
        }
    }

    // ------------------------------------------------------------------ dynamic

    /** How much light there is in the room to read the board by. */
    fun ambientFor(p: Plant): Float {
        val kerosene = 0.30f
        val house = if (p.grid.busVolts > Spec.RATED_VOLTS * 0.55) {
            (p.grid.busVolts / Spec.RATED_VOLTS).coerceIn(0f.toDouble(), 1.15).toFloat() * 0.55f
        } else 0f
        val panel = (p.ctl.ignition.panelLamps * p.engine.batteryCharge).toFloat() * 0.32f
        return (kerosene + house + panel).coerceIn(0.30f, 1.0f)
    }

    fun draw(c: Canvas, p: Plant, now: Long, crankAngle: Float, crankEffort: Float, pressed: Set<String>) {
        val ambient = ambientFor(p)
        // Rebuild the cached ironwork only when the light in the room really changes.
        if (background == null || abs(backgroundAmbient - ambient) > 0.045f) {
            background?.recycle()
            background = buildBackground(ambient)
            backgroundAmbient = ambient
        }
        c.drawBitmap(background!!, 0f, 0f, null)

        drawHeaderLive(c, p, ambient)
        drawGaugesLive(c, p, ambient, now)
        drawEngineLive(c, p, ambient, crankAngle, crankEffort, pressed)
        drawSwitchboardLive(c, p, ambient)
        drawAnnunciatorLive(c, p, ambient, now)
        if (p.ended) drawEndCard(c, p)
    }

    private fun drawHeaderLive(c: Canvas, p: Plant, ambient: Float) {
        val h = L.header
        val right = L.w * 0.985f
        Theme.engrave(
            c, p.clockText(), right, h.centerY() - h.height() * 0.10f,
            h.height() * 0.30f, Theme.dim(Theme.BRASS_LIT, ambient), Paint.Align.RIGHT
        )
        val kw = p.grid.demandW / 1000.0
        val secs = p.grid.secondsToChange
        Theme.engrave(
            c, "TOWN LOAD %.0f KW   NEXT IN %02d".format(kw, secs.toInt()), right,
            h.centerY() + h.height() * 0.30f, h.height() * 0.235f,
            Theme.dim(if (secs < 8.0) Theme.LAMP_AMBER else Theme.BRASS, ambient), Paint.Align.RIGHT
        )
    }

    private fun drawGaugesLive(c: Canvas, p: Plant, ambient: Float, now: Long) {
        // Synchroscope: still and dark when there is nothing to compare against.
        Instruments.drawSynchroscopeNeedle(c, L.synchroscope.x, L.synchroscope.y, L.bigR, p.syncPhase, ambient)
        Theme.dialGlass(c, L.synchroscope.x, L.synchroscope.y, L.bigR)

        freqDial.drawNeedle(c, p.hz, ambient)
        // A second, red pointer showing where the bus actually is.
        freqDial.drawNeedle(c, p.grid.busHz, ambient, Theme.DANGER)
        Theme.dialGlass(c, L.freqDial.x, L.freqDial.y, L.bigR)

        val lampB = p.lampBrightness().toFloat()
        for (l in L.lamps) {
            Theme.lamp(c, l.x, l.y, L.lampR, Theme.LAMP_AMBER, lampB)
        }

        genVoltsDial.drawNeedle(c, p.genVolts, ambient)
        Theme.dialGlass(c, L.genVolts.x, L.genVolts.y, L.smallR)
        busVoltsDial.drawNeedle(c, p.grid.busVolts, ambient)
        Theme.dialGlass(c, L.busVolts.x, L.busVolts.y, L.smallR)
        wattDial.drawNeedle(c, p.outputKw, ambient)
        Theme.dialGlass(c, L.wattmeter.x, L.wattmeter.y, L.smallR)
        ampDial.drawNeedle(c, p.gen.lineAmps, ambient)
        Theme.dialGlass(c, L.ammeter.x, L.ammeter.y, L.smallR)
    }

    private fun drawEngineLive(
        c: Canvas, p: Plant, ambient: Float, crankAngle: Float, crankEffort: Float, pressed: Set<String>
    ) {
        tachDial.drawNeedle(c, p.rpm, ambient)
        Theme.dialGlass(c, L.tachometer.x, L.tachometer.y, L.engR)
        oilDial.drawNeedle(c, p.engine.oilPressureKpa * 0.145, ambient)   // kPa shown as lbs/sq in
        Theme.dialGlass(c, L.oilGauge.x, L.oilGauge.y, L.engR)
        tempDial.drawNeedle(c, p.engine.jacketTempC, ambient)
        Theme.dialGlass(c, L.tempGauge.x, L.tempGauge.y, L.engR)

        val labels = IgnitionMode.entries.map { it.label }
        val live = IgnitionMode.entries.withIndex().filter { it.value.ignites }.map { it.index }.toSet()
        Widgets.keySwitch(
            c, L.keySwitch.x, L.keySwitch.y, L.keySwitchR, labels,
            p.ctl.ignition.ordinal, live, ambient
        )

        Widgets.quadrantLever(
            c, L.throttleLever, p.ctl.throttle.toFloat(), "THROTTLE", "OPEN", "SHUT", ambient
        )
        Widgets.quadrantLever(
            c, L.sparkLever, p.ctl.sparkLever.toFloat(), "SPARK", "ADV.", "RET.", ambient, Theme.BRASS_GREEN
        )
        Widgets.handwheel(
            c, L.mixtureKnob.x, L.mixtureKnob.y, L.mixtureKnobR, p.ctl.mixture.toFloat(), "MIXTURE", ambient
        )
        Widgets.handwheel(
            c, L.excitationKnob.x, L.excitationKnob.y, L.excitationKnobR, p.ctl.excitation.toFloat(),
            "FIELD RHEO.", ambient, Theme.BRASS_GREEN
        )

        Widgets.toggleLever(
            c, L.compRelease, if (p.ctl.compressionRelease) 1f else 0f, "RELIEF COCK", "OPEN", "SHUT", ambient
        )
        Widgets.pushButton(
            c, L.primerButton.x, L.primerButton.y, L.primerR, "primer" in pressed,
            "PRIMER", ambient
        )
        Widgets.crank(c, L.crankHandle.x, L.crankHandle.y, L.crankR, crankAngle, crankEffort, ambient)
        Widgets.pushButton(
            c, L.starterButton.x, L.starterButton.y, L.starterR, p.engine.starterEngaged,
            "STARTER", ambient, if (p.engine.starterCranking) Theme.LAMP_AMBER else Theme.BRASS
        )
        Widgets.handwheel(
            c, L.waterWheel.x, L.waterWheel.y, L.waterWheelR, p.ctl.waterValve.toFloat(), "WATER", ambient
        )
        Widgets.handwheel(
            c, L.oilerWheel.x, L.oilerWheel.y, L.oilerWheelR, p.ctl.oilerRate.toFloat(), "OILER", ambient
        )

        // how many charges of raw gasoline are still in the intake
        if (p.ctl.primerCharges > 0) {
            val n = p.ctl.primerCharges
            val gap = L.primerR * 0.34f
            for (i in 0 until n) {
                c.drawCircle(
                    L.primerButton.x + (i - (n - 1) / 2f) * gap,
                    L.primerButton.y - L.primerR * 1.62f, L.primerR * 0.115f,
                    Theme.solid(Theme.dim(Theme.LAMP_AMBER, ambient))
                )
            }
        }
    }

    private fun drawSwitchboardLive(c: Canvas, p: Plant, ambient: Float) {
        Widgets.mainBreaker(
            c, L.mainBreaker, if (p.ctl.mainBreakerClosed) 1f else 0f, ambient,
            p.genVolts > Spec.RATED_VOLTS * 0.5
        )
        Widgets.knifeSwitch(
            c, L.fieldSwitch, if (p.ctl.fieldSwitchClosed) 1f else 0f, "FIELD", "", ambient,
            live = p.gen.fieldFlux > 0.05
        )
        for (i in L.feeders.indices) {
            val f = p.grid.feeders[i]
            val closed = p.ctl.feederClosed[i] && !f.fuseBlown
            Widgets.knifeSwitch(
                c, L.feeders[i], if (p.ctl.feederClosed[i]) 1f else 0f, f.shortName,
                if (f.fuseBlown) "FUSE OUT" else "%.0f A".format(f.amps), ambient,
                blown = f.fuseBlown, live = closed && p.grid.busVolts > 500
            )
        }
    }

    private fun drawAnnunciatorLive(c: Canvas, p: Plant, ambient: Float, now: Long) {
        val e = p.engine
        val flash = if ((now / 260L) % 2L == 0L) 1f else 0.30f
        val states = floatArrayOf(
            if (p.rpm > Spec.OVERSPEED_RPM) flash else if (p.rpm > Spec.RATED_RPM * 1.10) 0.55f else 0f,
            if (e.oilFilm < 0.35) flash else if (e.oilFilm < 0.60) 0.55f else 0f,
            if (e.jacketTempC > 112) flash else if (e.jacketTempC > 98) 0.55f else 0f,
            if (e.knockIndex > 0.45) flash else if (e.knockIndex > 0.22) 0.55f else 0f,
            if (p.outputKw < -4.0) flash else 0f,
            if (!p.ctl.fieldSwitchClosed || p.gen.fieldFlux < 0.04) 0.65f else 0f,
            if (p.grid.feeders.any { it.fuseBlown }) flash else 0f,
            if (e.batteryCharge < 0.12) flash else if (e.batteryCharge < 0.30) 0.55f else 0f
        )
        for (i in states.indices) {
            val pos = L.alarmPos(i, states.size)
            val col = if (i == 5) Theme.LAMP_AMBER else Theme.LAMP_RED
            Theme.lamp(c, pos.x, pos.y, L.alarmR, col, states[i])
        }
    }

    private fun drawEndCard(c: Canvas, p: Plant) {
        c.drawColor(0xC4000000.toInt())
        val cx = L.w / 2f
        val bodySize = L.w * 0.0295f
        val boxW = L.w * 0.850f
        val textW = boxW - L.w * 0.090f

        // Wrap the account of what went wrong before deciding how tall the card is.
        val wrapPaint = Theme.label(bodySize, Theme.IVORY_SHADE, false)
        val lines = ArrayList<String>()
        var lineText = ""
        for (w in p.failure.detail.split(" ")) {
            val test = if (lineText.isEmpty()) w else "$lineText $w"
            if (wrapPaint.measureText(test) > textW && lineText.isNotEmpty()) {
                lines.add(lineText); lineText = w
            } else lineText = test
        }
        if (lineText.isNotEmpty()) lines.add(lineText)

        val rows = listOf(
            "CURRENT DELIVERED" to "%.1f kWh".format(p.grid.energyDeliveredKwh),
            "NOT SERVED" to "%.1f kWh".format(p.grid.unservedKwh),
            "BROWNOUT" to "%.0f sec".format(p.grid.brownoutSeconds),
            "PEAK OUTPUT" to "%.0f kW".format(p.peakOutputKw),
            "ON THE BOARDS" to "%.0f min".format(p.shiftSeconds / 60.0)
        )

        val plateH = L.h * 0.040f
        val padTop = L.h * 0.014f
        val boxH = padTop * 2 + plateH + bodySize * (1.6f + lines.size * 1.45f) +
            bodySize * (0.9f + rows.size * 1.35f) + bodySize * 3.4f
        val box = RectF(cx - boxW / 2, (L.h - boxH) / 2f, cx + boxW / 2, (L.h + boxH) / 2f)
        Theme.ironPlate(c, box, 14f)

        Theme.namePlate(
            c, RectF(box.left + L.w * 0.030f, box.top + padTop, box.right - L.w * 0.030f, box.top + padTop + plateH),
            p.failure.headline, L.h * 0.0195f
        )

        var y = box.top + padTop + plateH + bodySize * 1.6f
        for (line in lines) {
            c.drawText(line, cx, y, Theme.label(bodySize, Theme.IVORY_SHADE, false))
            y += bodySize * 1.45f
        }
        y += bodySize * 0.9f
        for ((k, v) in rows) {
            Theme.engrave(c, k, box.left + L.w * 0.045f, y, bodySize * 0.95f, Theme.BRASS, Paint.Align.LEFT)
            Theme.engrave(c, v, box.right - L.w * 0.045f, y, bodySize * 0.95f, Theme.IVORY, Paint.Align.RIGHT)
            y += bodySize * 1.35f
        }
        y += bodySize * 0.9f
        Theme.engrave(c, "SHIFT MARK  ${p.score()}", cx, y, bodySize * 1.45f, Theme.LAMP_AMBER)
        y += bodySize * 1.8f
        Theme.engrave(c, "TOUCH TO TAKE ANOTHER SHIFT", cx, y, bodySize * 0.95f, Theme.BRASS_DARK)
    }

    fun release() {
        background?.recycle()
        background = null
    }
}
