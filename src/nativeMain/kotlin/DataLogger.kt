import clients.RenogyData
import utils.*
import kotlin.time.Duration.Companion.days

/**
 * Logs [RenogyData] somewhere.
 */
interface DataLogger : Closeable {
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
        data.dailyStats.chargingAh,
        data.dailyStats.powerGenerationWh,
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
 * Aggregates multiple [DataLogger]s. Add them to [dataLoggers] before calling [init].
  */
class CompositeDataLogger : DataLogger {
    val dataLoggers = mutableListOf<DataLogger>()
    override fun init() {
        dataLoggers.forEach { it.init() }
    }

    override fun append(data: RenogyData) {
        dataLoggers.forEach { it.append(data) }
    }

    override fun deleteRecordsOlderThan(days: Int) {
        log.info("Deleting old records")
        dataLoggers.forEach { it.deleteRecordsOlderThan(days) }
        log.info("Successfully deleted old records")
    }

    override fun close() {
        dataLoggers.forEach { it.closeQuietly() }
        log.debug("Closed $dataLoggers")
        dataLoggers.clear()
    }

    override fun toString(): String = "CompositeDataLogger($dataLoggers)"

    companion object {
        private val log = Log.get(CompositeDataLogger::class)
    }

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
        log.info("Record cleanup not implemented for CSV")
    }

    override fun toString(): String = "CSVDataLogger($file, utc=$utc)"

    override fun close() {}

    companion object {
        private val log = Log.get(CSVDataLogger::class)
    }
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

    override fun close() {}
}

class SqliteDataLogger(val file: File, val busyTimeoutMs: Int = 3000) : DataLogger {
    private fun sql(sql: String) {
        log.debug("Running: $sql")
        exec("sqlite3 ${file.pathname} \"PRAGMA busy_timeout = $busyTimeoutMs; $sql\"")
    }
    override fun init() {
        log.debug("Logging into $file")
        if (!file.exists()) {
            log.debug("Database $file doesn't exist, creating new")
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
                    "Daily_ChargingAmpHours int not null," +
                    "Daily_PowerGeneration int not null," +
                    "Stats_DaysUp int not null," +
                    "Stats_BatteryOverDischargeCount int not null," +
                    "Stats_BatteryFullChargeCount int not null," +
                    "Stats_TotalChargingBatteryAH int not null," +
                    "Stats_CumulativePowerGenerationWH int not null," +
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
        add("Daily_ChargingAmpHours", data.dailyStats.chargingAh)
        add("Daily_PowerGeneration", data.dailyStats.powerGenerationWh)
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
        log.info("Deleting old records")
        val deleteOlderThan = getSecondsSinceEpoch() - days.days.inWholeSeconds
        sql("delete from log where DateTime <= $deleteOlderThan")
        log.info("Successfully deleted old records")
    }

    override fun toString(): String = "SqliteDataLogger($file)"

    override fun close() {}

    companion object {
        private val log = Log.get(SqliteDataLogger::class)
    }
}

/**
 * Logs data into PostgreSQL via the `psql` command-line client.
 * @param url the connection URL, e.g. `postgresql://user:pass@localhost:5432/postgres`
 */
class PostgresDataLogger(val url: String) : DataLogger {
    private fun sql(sql: String) {
        log.debug("Running: $sql")
        exec("psql $url -c \"$sql\"")
    }
    override fun init() {
        log.debug("Logging into $url")
        sql("CREATE TABLE IF NOT EXISTS log (" +
                "DateTime bigint primary key not null," +
                "BatterySOC smallint not null," +
                "BatteryVoltage real not null," +
                "ChargingCurrentToBattery real not null," +
                "BatteryTemp smallint not null," +
                "ControllerTemp smallint not null," +
                "SolarPanelVoltage real not null," +
                "SolarPanelCurrent real not null," +
                "SolarPanelPower smallint not null," +
                "Daily_BatteryMinVoltage real not null," +
                "Daily_BatteryMaxVoltage real not null," +
                "Daily_MaxChargingCurrent real not null," +
                "Daily_MaxChargingPower smallint not null," +
                "Daily_ChargingAmpHours smallint not null," +
                "Daily_PowerGeneration smallint not null," +
                "Stats_DaysUp int not null," +
                "Stats_BatteryOverDischargeCount smallint not null," +
                "Stats_BatteryFullChargeCount smallint not null," +
                "Stats_TotalChargingBatteryAH int not null," +
                "Stats_CumulativePowerGenerationWH int not null," +
                "ChargingState smallint," +
                "Faults text)")
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
        add("Daily_ChargingAmpHours", data.dailyStats.chargingAh)
        add("Daily_PowerGeneration", data.dailyStats.powerGenerationWh)
        add("Stats_DaysUp", data.historicalData.daysUp)
        add("Stats_BatteryOverDischargeCount", data.historicalData.batteryOverDischargeCount)
        add("Stats_BatteryFullChargeCount", data.historicalData.batteryFullChargeCount)
        add("Stats_TotalChargingBatteryAH", data.historicalData.totalChargingBatteryAH)
        add("Stats_CumulativePowerGenerationWH", data.historicalData.cumulativePowerGenerationWH)
        add("ChargingState", data.status.chargingState?.value)
        add("Faults", data.status.faults.joinToString(",") { it.name } .ifBlank { null })

        sql("insert into log (${cols.joinToString(",")}) values (${values.joinToString(",")})")
    }

    override fun deleteRecordsOlderThan(days: Int) {
        log.info("Deleting old records")
        val deleteOlderThan = getSecondsSinceEpoch() - days.days.inWholeSeconds
        sql("delete from log where DateTime <= $deleteOlderThan")
        log.info("Successfully deleted old records")
    }

    override fun close() {}

    override fun toString(): String =
        "PostgresDataLogger($url)"

    companion object {
        private val log = Log.get(PostgresDataLogger::class)
    }
}
