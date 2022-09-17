@file:OptIn(ExperimentalTime::class)

import utils.*
import kotlin.random.Random
import kotlin.system.getTimeMillis
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * Returns random data, emulating stuff returned by an actual Renogy Client
 * @property maxSolarPanelAmperage max rated amperage of the solar panel array
 * @property maxSolarPanelVoltage max rated voltage of the solar panel array
 */
class DummyRenogyClient : RenogyClient {
    val maxSolarPanelVoltage = 61f
    val maxSolarPanelAmperage = 5f

    /**
     * Adjustment percentage per hour-of-day, so that we generate 0% at midnight. Makes the
     * dummy data more realistic.
     */
    private val solarPanelGenerationPercentagePerHour = listOf<Float>(0f, 0f, 0f, 0f, 0f, 0f,
        0.1f, 0.3f, 0.6f, 0.75f, 0.8f, 0.8f,
        0.85f, 0.95f, 0.8f, 0.75f, 0.5f, 0.3f,
        0.1f, 0f, 0f, 0f, 0f, 0f
    )

    override fun getSystemInfo(): SystemInfo =
        SystemInfo(24, 40, 40, ProductType.Controller, "RENOGY ROVER", "v1.2.3", "v4.5.6", "1501FFFF")

    /**
     * When the "device" was powered up (=when this class was created).
     */
    private val poweredOnAt: Instant = Instant.now()
    private var lastDailyStatsRetrievedAt: Instant = poweredOnAt
    private var lastDailyStatsRetrievedAtDay: LocalDate? = null
    private var totalChargingBatteryAH: Float = 0f
    private var cumulativePowerGenerationWH: Float = 0f

    private fun getDailyStats(): DailyStats {
        TODO("implement")
    }

    private fun getHistoricalData(): HistoricalData {
        val daysUp = (Instant.now() - poweredOnAt).inWholeDays + 1
        return HistoricalData(daysUp.toUShort(), 0.toUShort(), 0.toUShort(), totalChargingBatteryAH.toUInt(), 0.toUInt(), cumulativePowerGenerationWH, 0f)
    }

    override fun getAllData(cachedSystemInfo: SystemInfo?): RenogyData {
        val systemInfo = cachedSystemInfo ?: getSystemInfo()
        val now: LocalDateTime = LocalDateTime.now()

        // generate dummy power data flowing from the solar panels; calculate the rest of the values
        val solarPanelVoltage = Random.nextFloat(maxSolarPanelVoltage * 0.66f, maxSolarPanelVoltage)
        val solarPanelCurrent = Random.nextFloat(0f, maxSolarPanelAmperage)
        // this is the most important value: this is the power (in Watts) the solar array is producing at this moment.
        var solarPanelPowerW = solarPanelVoltage * solarPanelCurrent
        // adjust the generated power according to the hour-of-day, so that we won't generate 100% power at midnight :-D
        solarPanelPowerW *= solarPanelGenerationPercentagePerHour[now.time.hour]

        val batteryVoltage = Random.nextFloat(
            systemInfo.maxVoltage.toFloat(),
            systemInfo.maxVoltage.toFloat() * 1.19f
        )
        // how much current flows into the battery at the moment.
        val currentToBattery = solarPanelPowerW / batteryVoltage

        val dummyPowerStatus = PowerStatus(
            batterySOC = Random.nextUShort(66.toUShort(), 100.toUShort()),
            batteryVoltage = batteryVoltage,
            chargingCurrentToBattery = currentToBattery,
            batteryTemp = Random.nextInt(18, 24),
            controllerTemp = Random.nextInt(18, 24),
            loadVoltage = 0f, // ignore the load, pretend there's none
            loadCurrent = 0f,
            loadPower = 0.toUShort(),
            solarPanelVoltage = solarPanelVoltage,
            solarPanelCurrent = solarPanelCurrent,
            solarPanelPower = solarPanelPowerW.toInt().toUShort())

        updateStats(solarPanelPowerW, batteryVoltage)

        val dummyDailyStats = getDailyStats()
        val dummyHistoricalData = getHistoricalData()
        val dummyStatus = RenogyStatus(false, 0.toUByte(), ChargingState.MpptChargingMode, setOf(ControllerFaults.ControllerTemperatureTooHigh))
        val dummyRenogyData = RenogyData(systemInfo, dummyPowerStatus, dummyDailyStats, dummyHistoricalData, dummyStatus)
        return dummyRenogyData
    }

    /**
     * Updates statistics. Now we can calculate [DailyStats] and [HistoricalData] correctly.
     * @param solarPanelPowerW solar array produces this amount of watts now.
     * @param batteryVoltage actual battery voltage.
     */
    private fun updateStats(solarPanelPowerW: Float, batteryVoltage: Float) {
        val currentToBattery = solarPanelPowerW / batteryVoltage
        val now = Instant.now()
        val millisSinceLastMeasurement = now - lastDailyStatsRetrievedAt
        val ampHoursToBatterySinceLastMeasurement: Float = currentToBattery * millisSinceLastMeasurement.inWholeMilliseconds / 1000f / 60f / 60f

        totalChargingBatteryAH += ampHoursToBatterySinceLastMeasurement
        cumulativePowerGenerationWH += ampHoursToBatterySinceLastMeasurement * batteryVoltage

        lastDailyStatsRetrievedAt = now
    }
}
