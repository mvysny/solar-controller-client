import utils.*
import kotlin.time.Duration.Companion.days

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

    /**
     * Deletes all records older than given number of [days].
     */
    fun deleteRecordsOlderThan(days: Int = 365)
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

    override fun deleteRecordsOlderThan(days: Int) {
        // it would take too much time to process a huge CSV file; also CSV is considered experimental, so don't bother
        StderrIO.writeln("Record cleanup not implemented for CSV")
    }

    override fun toString(): String = "CSVDataLogger($file, utc=$utc)"
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

    override fun deleteRecordsOlderThan(days: Int) {
    }

    override fun toString(): String {
        return "StdoutCSVDataLogger(utc=$utc)"
    }
}

class SqliteDataLogger(val file: File) : DataLogger {
    private fun sql(sql: String) {
        exec("sqlite3 ${file.pathname} \"PRAGMA busy_timeout = 1000; $sql\"")
    }
    override fun init() {
        if (!file.exists()) {
            sql("create table log(" +
                    "DateTime integer primary key not null," +
                    "BatterySOC integer not null," +
                    "BatteryVoltage real not null," +
                    "ChargingCurrentToBattery real not null," +
                    "BatteryTemp int not null," +
                    "ControllerTemp int not null," +
                    "SolarPanelVoltage real not null," +
                    "SolarPanelCurrent real not null," +
                    "SolarPanelPower int not null," +
                    "Daily_BatteryMinVoltage real not null," +
                    "Daily_BatteryMaxVoltage real not null," +
                    "Daily_MaxChargingCurrent real not null," +
                    "Daily_MaxChargingPower int not null," +
                    "Daily_ChargingAmpHours real not null," +
                    "Daily_PowerGeneration real not null," +
                    "Stats_DaysUp int not null," +
                    "Stats_BatteryOverDischargeCount int not null," +
                    "Stats_BatteryFullChargeCount int not null," +
                    "Stats_TotalChargingBatteryAH int not null," +
                    "Stats_CumulativePowerGenerationWH real not null," +
                    "ChargingState int," +
                    "Faults text)")
        }
    }

    override fun append(data: RenogyData) {
        val cols = mutableListOf<String>()
        val values = mutableListOf<String>()

        fun add(col: String, value: Any?) {
            if (value != null) {
                cols.add(col)
                values.add(
                    when (value) {
                        is Number, is UShort, is UInt, is UByte -> value.toString()
                        else -> "'$value'"
                    }
                )
            }
        }

        add("DateTime", getSecondsSinceEpoch())
        add("BatterySOC", data.powerStatus.batterySOC)
        add("BatteryVoltage", data.powerStatus.batteryVoltage)
        add("ChargingCurrentToBattery", data.powerStatus.chargingCurrentToBattery)
        add("BatteryTemp", data.powerStatus.batteryTemp)
        add("ControllerTemp", data.powerStatus.controllerTemp)
        add("SolarPanelVoltage", data.powerStatus.solarPanelVoltage)
        add("SolarPanelCurrent", data.powerStatus.solarPanelCurrent)
        add("SolarPanelPower", data.powerStatus.solarPanelPower)
        add("Daily_BatteryMinVoltage", data.dailyStats.batteryMinVoltage)
        add("Daily_BatteryMaxVoltage", data.dailyStats.batteryMaxVoltage)
        add("Daily_MaxChargingCurrent", data.dailyStats.maxChargingCurrent)
        add("Daily_MaxChargingPower", data.dailyStats.maxChargingPower)
        add("Daily_ChargingAmpHours", data.dailyStats.chargingAmpHours)
        add("Daily_PowerGeneration", data.dailyStats.powerGeneration)
        add("Stats_DaysUp", data.historicalData.daysUp)
        add("Stats_BatteryOverDischargeCount", data.historicalData.batteryOverDischargeCount)
        add("Stats_BatteryFullChargeCount", data.historicalData.batteryFullChargeCount)
        add("Stats_TotalChargingBatteryAH", data.historicalData.totalChargingBatteryAH)
        add("Stats_CumulativePowerGenerationWH", data.historicalData.cumulativePowerGenerationWH)
        add("ChargingState", data.status.chargingState?.value)
        add("Faults", data.status.faults.joinToString(",") { it.name } .ifBlank { null })

        sql("insert or replace into log (${cols.joinToString(",")}) values (${values.joinToString(",")})")
    }

    override fun deleteRecordsOlderThan(days: Int) {
        val deleteOlderThan = getSecondsSinceEpoch() - days.days.inWholeSeconds
        sql("delete from log where DateTime <= $deleteOlderThan")
    }

    override fun toString(): String = "SqliteDataLogger($file)"
}
