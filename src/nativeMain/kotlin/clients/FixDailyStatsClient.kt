package clients

import utils.Log
import utils.MidnightAlarm

/**
 * Renogy resets the stats at arbitrary time. Currently, for me, the stats are reset at 9:17am, which is a huge wtf.
 * Therefore, we can not trust the daily data, and we need to override/adjust as follows:
 *
 * * [DailyStats.batteryMaxVoltage] and [DailyStats.batteryMinVoltage] - we'll calculate this ourselves during the
 */
class FixDailyStatsClient(val delegate: RenogyClient) : RenogyClient by delegate {

    /**
     * The time from midnight until 9:17am (or any other arbitrary point in time until Renogy finally resets the data)
     * is called the "Don't Trust Renogy" period. If this is true, we're currently in the "Don't Trust Renogy" period.
     */
    private var inDontTrustPeriod: Boolean = false

    /**
     * [DailyStats.powerGenerationWh] from Renogy's previous measurement. If the current measurement is lower,
     * Renogy has performed the daily value reset.
     */
    private var prevPowerGenerationWh: UShort? = null

    /**
     * We'll calculate our own battery stats as well.
     */
    private var dailyBatteryMinVoltage: Float? = null
    /**
     * We'll calculate our own battery stats as well.
     */
    private var dailyBatteryMaxVoltage: Float? = null

    /**
     * Cumulative power generation at midnight as reported by Renogy. We use this to offset the power generation
     * during the "Don't Trust Renogy" period.
     */
    private var powerGenerationAtMidnight: UShort = 0.toUShort()

    /**
     * Cumulative power generation during the "Don't Trust Renogy" period. We'll add this value to [DailyStats.powerGenerationWh]
     * when outside of the "Don't Trust Renogy" period, to offset for power generation during the period itself.
     */
    private var powerGenerationDuringDontTrustPeriod: UShort = 0.toUShort()

    private val midnightAlarm = MidnightAlarm {
        // if we crossed the day barrier, force zero power generation until Renogy itself zeroes it out.
        inDontTrustPeriod = true
    }

    override fun getAllData(cachedSystemInfo: SystemInfo?): RenogyData {
        val allData = delegate.getAllData()
        val currentDailyStatsFromRenogy = allData.dailyStats

        val crossedMidnight = midnightAlarm.tick()
        if (crossedMidnight) {
            powerGenerationAtMidnight = currentDailyStatsFromRenogy.powerGenerationWh
        }

        val prevPowerGenerationWh = this.prevPowerGenerationWh
        if (prevPowerGenerationWh != null && prevPowerGenerationWh > currentDailyStatsFromRenogy.powerGenerationWh) {
            // Renogy finally performed the daily value reset.
            log.info("Renogy performed the daily value reset, ending the Don't Trust Period")
            inDontTrustPeriod = false
            powerGenerationDuringDontTrustPeriod = (prevPowerGenerationWh - powerGenerationAtMidnight).toUShort().coerceAtLeast(0.toUShort())
            if (crossedMidnight) {
                // no "Don't Trust Renogy" period -> zero
                powerGenerationDuringDontTrustPeriod = 0.toUShort()
            }
        }
        this.prevPowerGenerationWh = currentDailyStatsFromRenogy.powerGenerationWh

        // calculate our own dailyBatteryMinVoltage and dailyBatteryMaxVoltage
        if (crossedMidnight || dailyBatteryMinVoltage == null || dailyBatteryMaxVoltage == null) {
            dailyBatteryMinVoltage = allData.powerStatus.batteryVoltage
            dailyBatteryMaxVoltage = allData.powerStatus.batteryVoltage
        } else {
            dailyBatteryMinVoltage = dailyBatteryMinVoltage!!.coerceAtMost(allData.powerStatus.batteryVoltage)
            dailyBatteryMaxVoltage = dailyBatteryMaxVoltage!!.coerceAtLeast(allData.powerStatus.batteryVoltage)
        }

        val newDailyStats = if (inDontTrustPeriod) currentDailyStatsFromRenogy.copy(
            batteryMinVoltage = dailyBatteryMinVoltage!!,
            batteryMaxVoltage = dailyBatteryMaxVoltage!!,
            maxChargingCurrent = 0f,
            maxChargingPower = 0.toUShort(),
            chargingAh = 0.toUShort(),
            powerGenerationWh = (currentDailyStatsFromRenogy.powerGenerationWh - powerGenerationAtMidnight).toUShort()
        ) else currentDailyStatsFromRenogy.copy(
            powerGenerationWh = (currentDailyStatsFromRenogy.powerGenerationWh + powerGenerationDuringDontTrustPeriod).toUShort()
        )

        return allData.copy(dailyStats = newDailyStats)
    }

    override fun toString(): String = "FixDailyStatsClient($delegate)"

    companion object {
        private val log = Log.get(FixDailyStatsClient::class)
    }
}
