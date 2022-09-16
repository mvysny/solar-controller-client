@file:OptIn(ExperimentalTime::class)

import utils.Instant
import utils.LocalDate
import utils.nextFloat
import utils.nextUShort
import kotlin.random.Random
import kotlin.system.getTimeMillis
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * Returns random data, emulating stuff returned by an actual Renogy Client
 */
class DummyRenogyClient : RenogyClient {
    override fun getSystemInfo(): SystemInfo =
        SystemInfo(24, 40, 40, ProductType.Controller, "RENOGY ROVER", "v1.2.3", "v4.5.6", "1501FFFF")


    /**
     * When the "device" was powered up (=when this class was created).
     */
    private val poweredOnAt: Instant = Instant.now()
    private var dailyStats: DailyStats? = null
    private var lastDailyStatsRetrievedAt: Instant? = null
    private var lastDailyStatsRetrievedAtDay: LocalDate? = null

    private fun getDailyStats(): DailyStats {
        TODO("implement")
    }

    private fun getHistoricalData(): HistoricalData {
        val daysUp = (Instant.now() - poweredOnAt).inWholeDays + 1
        TODO("implement")
    }

    override fun getAllData(cachedSystemInfo: SystemInfo?): RenogyData {
        val systemInfo = cachedSystemInfo ?: getSystemInfo()
        val solarPanelVoltage = Random.nextFloat(40f, 61f)
        val solarPanelCurrent = Random.nextFloat(0f, 10f)
        val dummyPowerStatus = PowerStatus(
            batterySOC = Random.nextUShort(66.toUShort(), 100.toUShort()),
            batteryVoltage = Random.nextFloat(24f, 28f),
            chargingCurrentToBattery = Random.nextFloat(0f, 10f),
            batteryTemp = Random.nextInt(18, 24),
            controllerTemp = Random.nextInt(18, 24),
            loadVoltage = 0f,
            loadCurrent = 0f,
            loadPower = 0.toUShort(),
            solarPanelVoltage = solarPanelVoltage,
            solarPanelCurrent = solarPanelCurrent,
            solarPanelPower = (solarPanelVoltage * solarPanelCurrent).toInt().toUShort())
        updateStats(dummyPowerStatus)
        val dummyDailyStats = getDailyStats()
        val dummyHistoricalData = getHistoricalData()
        val dummyStatus = RenogyStatus(false, 0.toUByte(), ChargingState.MpptChargingMode, setOf(ControllerFaults.ControllerTemperatureTooHigh))
        val dummyRenogyData = RenogyData(systemInfo, dummyPowerStatus, dummyDailyStats, dummyHistoricalData, dummyStatus)
        return dummyRenogyData
    }

    private fun updateStats(dummyPowerStatus: PowerStatus) {
    }
}
