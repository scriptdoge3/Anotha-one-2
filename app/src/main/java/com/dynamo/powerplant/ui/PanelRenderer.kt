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
        -200.0, 600.0, 4, 5, listOf(Band(-200.0, 0.0), Band(550.0, 600.0))
    )
    val ampDial = Dial(
        L.ammeter.x, L.ammeter.y, L.smallR, "LINE", "AMPERES",
        0.0, 250.0, 5, 5, listOf(Band(200.0, 250.0))
    )
    val tachDial = Dial(
        L.tachometer.x, L.tachometer.y, L.engR, "SPEED", "REV. PER MIN.",
        0.0, 900.0, 3, 5, listOf(Band(Spec.OVERSPEED_RPM, 900.0))
    )
    val oilDial = Dial(
        L.oilGauge.x, L.oilGauge.y, L.auxGaugeR, "OIL", "LBS. SQ. IN.",
        0.0, 40.0, 4, 5, listOf(Band(0.0, 8.0))
    )
    val tempDial = Dial(
        L.tempGauge.x, L.tempGauge.y, L.auxGaugeR, "JACKET", "DEG. CENT.",
        0.0, 150.0, 5, 5, listOf(Band(105.0, 150.0))
    )

    private var background: Bitmap? = null
    private var backgroundAmbient = -1f
    private var backgroundTab: Tab? = null

    /** Alarms across the annunciator strip, in the order they are wired. */
    private val alarmNames = listOf("OVERSPEED", "LOW OIL", "HOT", "KNOCK", "REV. PWR", "FIELD", "FUSE", "BATTERY")

    // ------------------------------------------------------------------ static

    private fun buildBackground(ambient: Float, tab: Tab): Bitmap {
        val bmp = Bitmap.createBitmap(L.w.toInt(), L.h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawRoom(c, ambient)
        drawHeaderPlate(c, ambient)
        drawGaugeBoardStatic(c, ambient)
        if (tab == Tab.CONTROL) drawControlDeckStatic(c, ambient) else drawElectricalDeckStatic(c, ambient)
        drawAnnunciatorStatic(c, ambient)
        return bmp
    }

    private fun drawRoom(c: Canvas, ambient: Float) {
        c.drawColor(Theme.dim(Theme.PANEL_DARK, ambient))
        // the wash from the reflector lamps hung over the board
        Theme.fill.alpha = 255
        Theme.fill.shader = RadialGradient(
            L.w * 0.5f, L.h * 0.26f, L.h * 0.78f,
            intArrayOf(
                Theme.withAlpha(0xFFD8E6F0.toInt(), (26 * ambient).toInt()),
                Theme.withAlpha(0xFF000000.toInt(), 0)
            ), null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, L.w, L.h, Theme.fill)
        Theme.fill.shader = null
        // the seams of the sheet steel behind the board
        val seam = Theme.line(Theme.withAlpha(Color.WHITE, (6 * ambient).toInt()), 1.4f)
        var x = 0f
        while (x < L.w) {
            c.drawLine(x, 0f, x, L.h, seam)
            x += L.w * 0.125f
        }
    }

    private fun drawHeaderPlate(c: Canvas, ambient: Float) {
        val r = RectF(0f, 0f, L.w, L.header.bottom)
        Theme.fill.alpha = 255
        Theme.fill.shader = LinearGradient(
            0f, 0f, 0f, r.bottom,
            intArrayOf(
                Theme.dim(Theme.PANEL_LIT, ambient),
                Theme.dim(Theme.PANEL, ambient),
                Theme.dim(Theme.PANEL_DARK, ambient)
            ),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(r, Theme.fill)
        Theme.fill.shader = null
        // a deco band across the foot of the header
        c.drawLine(0f, r.bottom - 5f, L.w, r.bottom - 5f, Theme.line(Theme.dim(Theme.NICKEL, ambient), 2.4f))
        c.drawLine(0f, r.bottom, L.w, r.bottom, Theme.line(Theme.dim(Theme.NICKEL_DARK, ambient), 4f))
        Theme.chevrons(
            c, RectF(L.w * 0.485f, r.height() * 0.30f, L.w * 0.615f, r.height() * 0.66f),
            Theme.withAlpha(Theme.NICKEL, (70 * ambient).toInt()), 3
        )
        Theme.namePlate(
            c,
            RectF(L.w * 0.025f, r.height() * 0.19f, L.w * 0.470f, r.height() * 0.81f),
            "MILLBROOK LIGHT & POWER Co.", r.height() * 0.30f, ambient
        )
    }

    private fun drawGaugeBoardStatic(c: Canvas, ambient: Float) {
        val r = RectF(L.w * 0.012f, L.gaugeBoard.top + 4f, L.w * 0.988f, L.gaugeBoard.bottom - 4f)
        Theme.panelPlate(c, r, 8f)
        Theme.screw(c, r.left + 18f, r.top + 18f, 7f)
        Theme.screw(c, r.right - 18f, r.top + 18f, 7f, 68f)
        Theme.screw(c, r.left + 18f, r.bottom - 18f, 7f, 112f)
        Theme.screw(c, r.right - 18f, r.bottom - 18f, 7f, 18f)

        Instruments.drawSynchroscopeFace(c, L.synchroscope.x, L.synchroscope.y, L.bigR, ambient)
        freqDial.drawFace(c, ambient)

        // the three synchronising lamps sit on their own little sub-plate
        val lr = RectF(
            L.lamps[0].x - L.lampR * 2.0f, L.lampY - L.lampR * 1.55f,
            L.lamps[2].x + L.lampR * 2.0f, L.lampY + L.lampR * 1.55f
        )
        Theme.panelPlate(c, lr, 8f, 0.5f)
        Theme.engrave(
            c, "SYNCHRONISING LAMPS", lr.centerX(), lr.top - L.lampR * 0.42f,
            L.lampR * 0.56f, Theme.dim(Theme.NICKEL_LIT, ambient)
        )

        genVoltsDial.drawFace(c, ambient)
        busVoltsDial.drawFace(c, ambient)
        wattDial.drawFace(c, ambient)
        ampDial.drawFace(c, ambient)
    }

    private fun drawControlDeckStatic(c: Canvas, ambient: Float) {
        c.drawRect(L.deck, Theme.solid(Theme.dim(Theme.PANEL_DARK, ambient)))
        subPanel(c, L.enginePanel, "ENGINE", ambient)
        subPanel(c, L.oilWaterPanel, "OIL AND WATER", ambient)
        subPanel(c, L.generatorPanel, "GENERATOR", ambient)

        tachDial.drawFace(c, ambient)
        oilDial.drawFace(c, ambient)
        tempDial.drawFace(c, ambient)
    }

    /** One bordered section of the control board, with its name across the top. */
    private fun subPanel(c: Canvas, r: RectF, title: String, ambient: Float) {
        Theme.panelPlate(c, r, 7f, 0.7f, pinstripe = false)
        val hh = L.panelHeader(r)
        val head = RectF(r.left + 3f, r.top + 3f, r.right - 3f, r.top + hh)
        Theme.fill.alpha = 255
        Theme.fill.shader = LinearGradient(
            0f, head.top, 0f, head.bottom,
            intArrayOf(Theme.dim(Theme.PANEL_LIT, ambient), Theme.dim(Theme.PANEL, ambient)),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(head, Theme.fill)
        Theme.fill.shader = null
        c.drawLine(head.left, head.bottom, head.right, head.bottom,
            Theme.line(Theme.dim(Theme.NICKEL_DARK, ambient), 2f))
        Theme.engrave(
            c, title, r.left + r.width() * 0.035f, head.centerY() + hh * 0.30f,
            hh * 0.62f, Theme.dim(Theme.NICKEL_LIT, ambient), Paint.Align.LEFT
        )
        // a deco rule running out from the title to the right edge
        val tw = Theme.label(hh * 0.62f, Theme.MARK).measureText(title)
        val x0 = r.left + r.width() * 0.035f + tw + hh * 0.55f
        if (x0 < head.right - 20f) {
            c.drawLine(x0, head.centerY() - hh * 0.10f, head.right - 14f, head.centerY() - hh * 0.10f,
                Theme.line(Theme.withAlpha(Theme.NICKEL, (60 * ambient).toInt()), 2f))
            c.drawLine(x0, head.centerY() + hh * 0.10f, head.right - 14f, head.centerY() + hh * 0.10f,
                Theme.line(Theme.withAlpha(Theme.NICKEL, (60 * ambient).toInt()), 2f))
        }
        Theme.screw(c, r.right - 14f, r.bottom - 14f, 6f, 40f)
        Theme.screw(c, r.left + 14f, r.bottom - 14f, 6f, 100f)
    }

    private fun drawElectricalDeckStatic(c: Canvas, ambient: Float) {
        val r = RectF(L.w * 0.012f, L.deck.top + 4f, L.w * 0.988f, L.deck.bottom - 4f)
        Theme.panelPlate(c, r, 8f, 0.7f, pinstripe = false)
        Theme.boardPanel(c, L.boardPlate)
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
            intArrayOf(Theme.dim(0xFF201C15.toInt(), ambient), Theme.dim(Theme.PANEL_DARK, ambient)),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(r, Theme.fill)
        Theme.fill.shader = null
        c.drawLine(0f, r.top, L.w, r.top, Theme.line(Theme.dim(Theme.NICKEL_DARK, ambient), 2.5f))
        for (i in alarmNames.indices) {
            val p = L.alarmPos(i, alarmNames.size)
            Theme.engrave(
                c, alarmNames[i], p.x, r.bottom - r.height() * 0.14f,
                L.alarmR * 0.62f, Theme.dim(Theme.NICKEL_DARK, ambient)
            )
        }
    }

    // ------------------------------------------------------------------ dynamic

    /** How much light there is in the room to read the board by. */
    fun ambientFor(p: Plant): Float {
        val standby = 0.42f      // the emergency oil lamps over the board
        val house = if (p.grid.volts > Spec.RATED_VOLTS * 0.55) {
            (p.grid.volts / Spec.RATED_VOLTS).coerceIn(0f.toDouble(), 1.15).toFloat() * 0.55f
        } else 0f
        val panel = p.panelLampLevel().toFloat() * 0.30f
        return (standby + house + panel).coerceIn(0.45f, 1.0f)
    }

    fun draw(
        c: Canvas, p: Plant, now: Long, pressed: Set<String>, tab: Tab = Tab.CONTROL
    ) {
        val ambient = ambientFor(p)
        // Rebuild the cached steelwork only when the light or the deck changes.
        if (background == null || backgroundTab != tab || abs(backgroundAmbient - ambient) > 0.045f) {
            background?.recycle()
            background = buildBackground(ambient, tab)
            backgroundAmbient = ambient
            backgroundTab = tab
        }
        c.drawBitmap(background!!, 0f, 0f, null)

        drawHeaderLive(c, p, ambient)
        drawGaugesLive(c, p, ambient, now)
        if (tab == Tab.CONTROL) {
            drawControlLive(c, p, ambient, pressed)
        } else {
            Mimic.draw(c, L.mimic, p, ambient, (now % 100000L) / 1000f)
            drawSwitchboardLive(c, p, ambient)
        }
        drawTabBar(c, p, ambient, tab)
        drawAnnunciatorLive(c, p, ambient, now)
        if (p.ended) drawEndCard(c, p)
    }

    /** The two decks, and which one you are looking at. */
    private fun drawTabBar(c: Canvas, p: Plant, ambient: Float, tab: Tab) {
        Theme.fill.alpha = 255
        Theme.fill.shader = LinearGradient(
            0f, L.tabBar.top, 0f, L.tabBar.bottom,
            intArrayOf(Theme.dim(Theme.PANEL, ambient), Theme.dim(Theme.PANEL_DARK, ambient)),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(L.tabBar, Theme.fill)
        Theme.fill.shader = null
        c.drawLine(0f, L.tabBar.top, L.w, L.tabBar.top, Theme.line(Theme.dim(Theme.NICKEL_DARK, ambient), 2f))

        for ((i, t) in Tab.entries.withIndex()) {
            val r = L.tabRect(i)
            val on = t == tab
            if (on) {
                Theme.fill.alpha = 255
                Theme.fill.shader = LinearGradient(
                    0f, r.top, 0f, r.bottom,
                    intArrayOf(Theme.dim(Theme.PANEL_LIT, ambient), Theme.dim(Theme.PANEL, ambient)),
                    null, Shader.TileMode.CLAMP
                )
                c.drawRect(RectF(r.left + 3f, r.top + 3f, r.right - 3f, r.bottom), Theme.fill)
                Theme.fill.shader = null
                c.drawLine(r.left + 3f, r.top + 3f, r.right - 3f, r.top + 3f,
                    Theme.line(Theme.dim(Theme.ACCENT, ambient), 4f))
            }
            Theme.engrave(
                c, t.label, r.centerX(), r.centerY() + r.height() * 0.14f,
                r.height() * 0.34f,
                Theme.dim(if (on) Theme.MARK else Theme.MARK_SOFT, ambient)
            )
            if (i > 0) c.drawLine(r.left, r.top + 8f, r.left, r.bottom - 8f,
                Theme.line(Theme.dim(Theme.NICKEL_DARK, ambient), 1.6f))
        }

        // A small warning pip on whichever deck is not showing but wants attention.
        val other = if (tab == Tab.CONTROL) Tab.ELECTRICAL else Tab.CONTROL
        val wants = if (other == Tab.CONTROL) {
            p.engine.oilFilm < 0.5 || p.engine.jacketTempC > 105 || p.rpm > Spec.OVERSPEED_RPM
        } else {
            p.service.loads.any { it.fuseBlown } || p.service.overloaded ||
                p.outputKw < -4.0 || abs(p.outputKw - p.grid.dispatchKw()) > 14.0
        }
        if (wants) {
            val r = L.tabRect(other.ordinal)
            Theme.lamp(c, r.right - r.height() * 0.42f, r.centerY(), r.height() * 0.16f, Theme.LAMP_RED, 1f)
        }
    }

    private fun drawHeaderLive(c: Canvas, p: Plant, ambient: Float) {
        val h = L.header
        val right = L.w * 0.985f
        Theme.engrave(
            c, p.clockText(), right, h.centerY() - h.height() * 0.10f,
            h.height() * 0.30f, Theme.dim(Theme.NICKEL_LIT, ambient), Paint.Align.RIGHT
        )
        val order = p.grid.dispatchKw()
        val secs = p.grid.secondsToChange
        val onBars = if (p.ctl.mainBreakerClosed) p.outputKw else 0.0
        val onOrder = p.ctl.mainBreakerClosed && abs(onBars - order) < 45.0
        Theme.engrave(
            c, "ORDER %.0f   ON BARS %.0f KW   NEXT %02d".format(order, onBars, secs.toInt()), right,
            h.centerY() + h.height() * 0.31f, h.height() * 0.205f,
            Theme.dim(
                when {
                    secs < 8.0 -> Theme.LAMP_AMBER
                    onOrder -> Theme.ACCENT_COOL
                    else -> Theme.MARK_SOFT
                }, ambient
            ), Paint.Align.RIGHT
        )
    }

    private fun drawGaugesLive(c: Canvas, p: Plant, ambient: Float, now: Long) {
        // Synchroscope: still and dark when there is nothing to compare against.
        Instruments.drawSynchroscopeNeedle(c, L.synchroscope.x, L.synchroscope.y, L.bigR, p.syncPhase, ambient)
        Theme.dialGlass(c, L.synchroscope.x, L.synchroscope.y, L.bigR)

        freqDial.drawNeedle(c, p.hz, ambient)
        // A second, red pointer showing where the bus actually is.
        freqDial.drawNeedle(c, p.grid.hz, ambient, Theme.ACCENT)
        Theme.dialGlass(c, L.freqDial.x, L.freqDial.y, L.bigR)

        val lampB = p.lampBrightness().toFloat()
        for (l in L.lamps) {
            Theme.lamp(c, l.x, l.y, L.lampR, Theme.LAMP_AMBER, lampB)
        }

        genVoltsDial.drawNeedle(c, p.genVolts, ambient)
        Theme.dialGlass(c, L.genVolts.x, L.genVolts.y, L.smallR)
        busVoltsDial.drawNeedle(c, p.grid.volts, ambient)
        Theme.dialGlass(c, L.busVolts.x, L.busVolts.y, L.smallR)
        wattDial.drawNeedle(c, p.outputKw, ambient)
        Theme.dialGlass(c, L.wattmeter.x, L.wattmeter.y, L.smallR)
        ampDial.drawNeedle(c, p.gen.lineAmps, ambient)
        Theme.dialGlass(c, L.ammeter.x, L.ammeter.y, L.smallR)
    }

    private fun drawControlLive(
        c: Canvas, p: Plant, ambient: Float, pressed: Set<String>
    ) {
        // ---- ENGINE ----------------------------------------------------------
        tachDial.drawNeedle(c, p.rpm, ambient)
        Theme.dialGlass(c, L.tachometer.x, L.tachometer.y, L.engR)

        val labels = IgnitionMode.entries.map { it.label }
        val live = IgnitionMode.entries.withIndex().filter { it.value.ignites }.map { it.index }.toSet()
        Widgets.keySwitch(
            c, L.keySwitch.x, L.keySwitch.y, L.keySwitchR, labels,
            p.ctl.ignition.ordinal, live, ambient
        )
        Widgets.quadrantLever(
            c, L.throttleLever, p.ctl.throttle.toFloat(), "THROTTLE", "OPEN", "SHUT", ambient, Theme.ACCENT
        )
        Widgets.quadrantLever(
            c, L.sparkLever, p.ctl.sparkLever.toFloat(), "SPARK", "ADV.", "RET.", ambient, Theme.ACCENT_COOL
        )
        Widgets.handwheel(
            c, L.mixtureKnob.x, L.mixtureKnob.y, L.mixtureKnobR, p.ctl.mixture.toFloat(), "MIXTURE", ambient
        )

        Widgets.toggleLever(
            c, L.compRelease, if (p.ctl.compressionRelease) 1f else 0f, "RELIEF COCK", "OPEN", "SHUT", ambient
        )
        Widgets.pushButton(
            c, L.primerButton.x, L.primerButton.y, L.primerR, "primer" in pressed, "PRIMER", ambient
        )
        Widgets.pushButton(
            c, L.starterButton.x, L.starterButton.y, L.starterR, p.engine.starterEngaged,
            "STARTER", ambient, if (p.engine.starterCranking) Theme.ACCENT else Theme.NICKEL
        )
        if (p.ctl.primerCharges > 0) {
            val n = p.ctl.primerCharges
            val gap = L.primerR * 0.34f
            for (i in 0 until n) {
                c.drawCircle(
                    L.primerButton.x + (i - (n - 1) / 2f) * gap,
                    L.primerButton.y - L.primerR * 1.62f, L.primerR * 0.115f,
                    Theme.solid(Theme.dim(Theme.ACCENT, ambient))
                )
            }
        }

        // ---- OIL AND WATER ---------------------------------------------------
        oilDial.drawNeedle(c, p.engine.oilPressureKpa * 0.145, ambient)
        Theme.dialGlass(c, L.oilGauge.x, L.oilGauge.y, L.auxGaugeR)
        tempDial.drawNeedle(c, p.engine.jacketTempC, ambient)
        Theme.dialGlass(c, L.tempGauge.x, L.tempGauge.y, L.auxGaugeR)
        Widgets.handwheel(
            c, L.oilerWheel.x, L.oilerWheel.y, L.oilerWheelR, p.ctl.oilerRate.toFloat(), "OILER", ambient
        )
        // The gate is only worth anything while the pump is actually turning.
        val pumpOn = p.engine.waterPumpRunning
        Widgets.handwheel(
            c, L.waterWheel.x, L.waterWheel.y, L.waterWheelR, p.ctl.waterValve.toFloat(),
            if (pumpOn) "WATER GATE" else "PUMP OUT", ambient,
            if (pumpOn) Theme.NICKEL else Theme.DANGER,
            if (pumpOn) Theme.ACCENT else Theme.DANGER
        )

        // ---- GENERATOR -------------------------------------------------------
        Widgets.handwheel(
            c, L.excitationKnob.x, L.excitationKnob.y, L.excitationKnobR,
            p.ctl.excitation.toFloat(), "FIELD RHEO.", ambient, Theme.ACCENT_COOL, Theme.ACCENT_COOL
        )
        Widgets.barMeter(
            c, L.fieldMeter, p.gen.fieldFlux.toFloat(), "FIELD", ambient, warnBelow = 0.05f
        )
        val varPu = ((p.outputKvar / 400.0) * 0.5 + 0.5).toFloat().coerceIn(0f, 1f)
        Widgets.barMeter(c, L.varMeter, varPu, "REACTIVE  LAG / LEAD", ambient, warnBelow = -1f)
        val pf = run {
            val s2 = Math.hypot(p.outputKw, p.outputKvar)
            if (s2 < 1.0) 1.0 else abs(p.outputKw) / s2
        }
        Theme.engrave(
            c, "POWER FACTOR", L.powerFactorAt.x, L.powerFactorAt.y - L.generatorPanel.height() * 0.115f,
            L.generatorPanel.height() * 0.090f, Theme.dim(Theme.MARK_SOFT, ambient)
        )
        Theme.engrave(
            c, "%.2f".format(pf), L.powerFactorAt.x, L.powerFactorAt.y + L.generatorPanel.height() * 0.075f,
            L.generatorPanel.height() * 0.185f, Theme.dim(Theme.MARK, ambient)
        )
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
        for (i in L.auxSwitches.indices) {
            val l = p.service.loads[i]
            Widgets.knifeSwitch(
                c, L.auxSwitches[i], if (p.ctl.auxClosed[i]) 1f else 0f, l.shortName,
                if (l.fuseBlown) "FUSE OUT" else "%.1f kW".format(l.kw), ambient,
                blown = l.fuseBlown, live = l.running
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
            if (p.service.loads.any { it.fuseBlown } || p.service.overloaded) flash else 0f,
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
        val wrapPaint = Theme.label(bodySize, Theme.MARK_SOFT, false)
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
            "EXPORTED" to "%.1f kWh".format(p.grid.energyExportedKwh),
            "OFF ORDER" to "%.0f sec".format(p.grid.secondsOffOrder),
            "PEAK OUTPUT" to "%.0f kW".format(p.peakOutputKw),
            "ON THE BOARDS" to "%.0f min".format(p.shiftSeconds / 60.0)
        )

        val plateH = L.h * 0.040f
        val padTop = L.h * 0.014f
        val boxH = padTop * 2 + plateH + bodySize * (1.6f + lines.size * 1.45f) +
            bodySize * (0.9f + rows.size * 1.35f) + bodySize * 3.4f
        val box = RectF(cx - boxW / 2, (L.h - boxH) / 2f, cx + boxW / 2, (L.h + boxH) / 2f)
        Theme.panelPlate(c, box, 14f)

        Theme.namePlate(
            c, RectF(box.left + L.w * 0.030f, box.top + padTop, box.right - L.w * 0.030f, box.top + padTop + plateH),
            p.failure.headline, L.h * 0.0195f
        )

        var y = box.top + padTop + plateH + bodySize * 1.6f
        for (line in lines) {
            c.drawText(line, cx, y, Theme.label(bodySize, Theme.MARK_SOFT, false))
            y += bodySize * 1.45f
        }
        y += bodySize * 0.9f
        for ((k, v) in rows) {
            Theme.engrave(c, k, box.left + L.w * 0.045f, y, bodySize * 0.95f, Theme.NICKEL, Paint.Align.LEFT)
            Theme.engrave(c, v, box.right - L.w * 0.045f, y, bodySize * 0.95f, Theme.MARK, Paint.Align.RIGHT)
            y += bodySize * 1.35f
        }
        y += bodySize * 0.9f
        Theme.engrave(c, "SHIFT MARK  ${p.score()}", cx, y, bodySize * 1.45f, Theme.LAMP_AMBER)
        y += bodySize * 1.8f
        Theme.engrave(c, "TOUCH TO TAKE ANOTHER SHIFT", cx, y, bodySize * 0.95f, Theme.NICKEL_DARK)
    }

    fun release() {
        background?.recycle()
        background = null
    }
}
