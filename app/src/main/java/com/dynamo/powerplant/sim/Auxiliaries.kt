package com.dynamo.powerplant.sim

import kotlin.math.max
import kotlin.math.min

/**
 * The tanks. Everything the engine consumes has to come from somewhere, and in
 * a plant of this period that somewhere is a vessel with a gauge glass on it
 * that somebody has to keep an eye on.
 *
 *  - **Fuel** comes up from the buried tank outside to a gravity day tank in the
 *    roof, and falls from there to the carburettor. The transfer pump is an
 *    electric machine on the main bus; the day tank holds about a quarter of an
 *    hour at full load, so transferring is a job you come back to.
 *  - **Jacket water** circulates from a header tank. It boils some away every
 *    hour the engine is hot, and the make-up valve off the town main is what
 *    puts it back. Let the header run dry and the circulating pump has nothing
 *    to circulate, whatever the gate valve says.
 *  - **Lubricating oil** sits in the sump and goes up the bores through the
 *    lubricator, and does not come back. There is a drum and a hand pump.
 */
class Auxiliaries {

    companion object {
        const val MAIN_TANK_CAP_L = 4000.0
        const val DAY_TANK_CAP_L = 260.0
        const val HEADER_CAP_L = 380.0
        const val SUMP_CAP_L = 110.0

        /** Litres a second the transfer pump lifts when it is running. */
        const val TRANSFER_RATE_LPS = 1.10
        /** Litres a second the town main will give through a wide open valve. */
        const val MAKE_UP_RATE_LPS = 0.85
        /** Litres a second the hand pump puts in the sump. */
        const val OIL_PUMP_RATE_LPS = 0.34

        /** Litres an hour at full load, which is what sets the day tank's endurance. */
        const val FULL_LOAD_LPH = 1420.0
    }

    // --- fuel ---
    var mainTankL: Double = MAIN_TANK_CAP_L
    var dayTankL: Double = 205.0
    /** Litres spilled out of the day tank overflow. The inspector counts these. */
    var spilledL: Double = 0.0
    var transferPumpRunning: Boolean = false
    /** True when the day tank has run down far enough to starve the carburettor. */
    var fuelStarved: Boolean = false

    // --- jacket water ---
    var headerL: Double = 330.0
    /** Water lost as steam this shift. */
    var boiledOffL: Double = 0.0
    /** Temperature of the water leaving the jacket, which is the one that matters. */
    var jacketOutletC: Double = 12.0

    // --- lubricating oil ---
    var sumpL: Double = 88.0
    var oilTempC: Double = 12.0
    /** Litres of oil put up the bores this shift. */
    var oilUsedL: Double = 0.0

    /**
     * How much fuel the carburettor is actually getting, 0..1. The gravity tank
     * has to have something in it and the cock has to be open.
     */
    fun fuelAvailable(ctl: Controls): Double {
        if (!ctl.fuelCock) return 0.0
        // A gravity tank feeds by head, so it goes off long before the glass
        // reads empty: below about thirty-five litres the carburettor starts to
        // go hungry, and the last few slosh away from the outlet entirely.
        return clamp(dayTankL / 35.0, 0.0, 1.0)
    }

    /** How much of the header is available to circulate, 0..1. */
    fun coolantAvailable(): Double = clamp(headerL / 55.0, 0.0, 1.0)

    /**
     * @param burnPu 0..1 how hard the engine is working, for the fuel rate
     * @param jacketC the iron temperature, for evaporation and the outlet rise
     * @param flow 0..1 what the pumps and gate are actually circulating
     * @param oilFeedLps litres a second the lubricator is putting up the bores
     */
    fun step(
        dt: Double, ctl: Controls, burnPu: Double, jacketC: Double,
        flow: Double, oilFeedLps: Double, heatIn: Double
    ) {
        // ---- fuel ---------------------------------------------------------------
        if (transferPumpRunning && ctl.fuelTransfer && mainTankL > 0.0) {
            val moved = min(TRANSFER_RATE_LPS * dt, mainTankL)
            mainTankL -= moved
            dayTankL += moved
            if (dayTankL > DAY_TANK_CAP_L) {
                // Straight out of the overflow and onto the engine room floor.
                spilledL += dayTankL - DAY_TANK_CAP_L
                dayTankL = DAY_TANK_CAP_L
            }
        }
        if (ctl.fuelCock) {
            val lps = FULL_LOAD_LPH / 3600.0 * clamp(burnPu, 0.0, 1.4)
            dayTankL = max(0.0, dayTankL - lps * dt)
        }
        fuelStarved = fuelAvailable(ctl) < 0.80

        // ---- jacket water -------------------------------------------------------
        // An open header tank loses water all the time it is warm, and steeply
        // once the jacket is anywhere near boiling. Over a full shift it wants
        // topping up about once, which is what the make-up valve is for.
        val evap = clamp((jacketC - 45.0) / 55.0, 0.0, 1.0)
        val lost = (0.008 + 0.300 * Math.pow(evap, 1.5)) * (0.30 + flow) * dt
        headerL = max(0.0, headerL - lost)
        boiledOffL += lost
        if (ctl.makeUpValve > 0.0) {
            val added = min(MAKE_UP_RATE_LPS * ctl.makeUpValve * dt, HEADER_CAP_L - headerL)
            headerL += max(0.0, added)
        }
        // The outlet is hotter than the iron by whatever the water is carrying
        // away, divided by how fast it is going past.
        val rise = if (flow > 0.02) clamp(heatIn / (0.6 + flow * 5.5), 0.0, 42.0) else 42.0
        val targetOut = jacketC + rise
        jacketOutletC += (targetOut - jacketOutletC) * min(1.0, dt * 0.30)

        // ---- lubricating oil ----------------------------------------------------
        val used = min(oilFeedLps * dt, sumpL)
        sumpL = max(0.0, sumpL - used)
        oilUsedL += used
        if (ctl.oilReplenish) {
            sumpL = min(SUMP_CAP_L, sumpL + OIL_PUMP_RATE_LPS * dt)
        }
        // Oil runs a good deal hotter than the jacket and cools far more slowly.
        val oilTarget = 14.0 + jacketC * 0.86 + burnPu * 34.0
        oilTempC += (oilTarget - oilTempC) * min(1.0, dt * 0.035)
    }

    fun reset() {
        mainTankL = MAIN_TANK_CAP_L
        dayTankL = 205.0
        spilledL = 0.0
        transferPumpRunning = false
        fuelStarved = false
        headerL = 330.0
        boiledOffL = 0.0
        jacketOutletC = 12.0
        sumpL = 88.0
        oilTempC = 12.0
        oilUsedL = 0.0
    }
}
