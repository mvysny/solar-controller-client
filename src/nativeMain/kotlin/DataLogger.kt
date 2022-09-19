import utils.*

/**
 * Logs [RenogyData] somewhere.
 */
interface DataLogger {
    /**
     * Initializes the logger; e.g. makes sure the CSV file exists and creates one with a header if it doesn't.
     */
    fun init()

    /**
     * Appends [data] to the logger.
     */
    fun append(data: RenogyData)
}

private fun IO.csvRenogyWriteHeader() {
    val csv = CSVWriter(this)
    csv.writeHeader(
        "DateTime",
        "BatterySOC",
        "BatteryVoltage",
        "ChargingCurrentToBattery",
        "BatteryTemp",
        "ControllerTemp",
        "SolarPanelVoltage",
        "SolarPanelCurrent",
        "SolarPanelPower",
        "Daily.BatteryMinVoltage",
        "Daily.BatteryMaxVoltage",
        "Daily.MaxChargingCurrent",
        "Daily.MaxChargingPower",
        "Daily.ChargingAmpHours",
        "Daily.PowerGeneration",
        "Stats.DaysUp",
        "Stats.BatteryOverDischargeCount",
        "Stats.BatteryFullChargeCount",
        "Stats.TotalChargingBatteryAH",
        "Stats.CumulativePowerGenerationWH",
        "ChargingState",
        "Faults"
    )
}

private fun IO.csvRenogyWriteData(data: RenogyData, utc: Boolean) {
    val csv = CSVWriter(this)
    csv.writeLine(
        if (utc) ZonedDateTime.now(ZoneId.UTC).format() else LocalDateTime.now().format(),
        data.powerStatus.batterySOC,
        data.powerStatus.batteryVoltage,
        data.powerStatus.chargingCurrentToBattery,
        data.powerStatus.batteryTemp,
        data.powerStatus.controllerTemp,
        data.powerStatus.solarPanelVoltage,
        data.powerStatus.solarPanelCurrent,
        data.powerStatus.solarPanelPower,
        data.dailyStats.batteryMinVoltage,
        data.dailyStats.batteryMaxVoltage,
        data.dailyStats.maxChargingCurrent,
        data.dailyStats.maxChargingPower,
        data.dailyStats.chargingAmpHours,
        data.dailyStats.powerGeneration,
        data.historicalData.daysUp,
        data.historicalData.batteryOverDischargeCount,
        data.historicalData.batteryFullChargeCount,
        data.historicalData.totalChargingBatteryAH,
        data.historicalData.cumulativePowerGenerationWH,
        data.status.chargingState?.name,
        data.status.faults.joinToString(",") { it.name }
    )
}

/**
 * Logs [RenogyData] to a CSV file.
 */
class CSVDataLogger(val file: File, val utc: Boolean) : DataLogger {
    override fun init() {
        if (!file.exists()) {
            file.openAppend().use { io -> io.csvRenogyWriteHeader() }
        }
    }

    override fun append(data: RenogyData) {
        file.openAppend().use { io -> io.csvRenogyWriteData(data, utc) }
    }

    override fun toString(): String = "CSVDataLogger(file=$file, utc=$utc)"
}

/**
 * Logs [RenogyData] as a CSV file to stdout.
 */
class StdoutCSVDataLogger(val utc: Boolean) : DataLogger {
    override fun init() {
        StdoutIO.csvRenogyWriteHeader()
    }

    override fun append(data: RenogyData) {
        StdoutIO.csvRenogyWriteData(data, utc)
    }

    override fun toString(): String {
        return "StdoutCSVDataLogger(utc=$utc)"
    }
}
