import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import utils.*

fun main(args: Array<String>) {
    val parser = ArgParser("solar-controller-client")
    val device by parser.argument(ArgType.String, description = "the file name of the serial device to communicate with, e.g. /dev/ttyUSB0 . Pass in `dummy` for a dummy Renogy client")
    val status by parser.option(ArgType.Boolean, fullName = "status", description = "print the Renogy Rover status as JSON to stdout and quit")
    val utc by parser.option(ArgType.Boolean, fullName = "utc", description = "dump date in UTC instead of local, handy for Grafana")
    val logfile by parser.option(ArgType.String, fullName = "logfile", description = "appends status to file other than the default 'log.csv'")
    val statefile by parser.option(ArgType.String, fullName = "statefile", description = "overwrites status to file other than the default 'status.json'")
    val pollingInterval by parser.option(ArgType.Int, fullName = "pollinginterval", shortName = "i", description = "in seconds: how frequently to poll the controller for data, defaults to 10")
    parser.parse(args)

    val isDummy = device == "dummy"
    val io: IO = if (isDummy) DevZero() else SerialPort(File(device))
    io.use { serialPort ->
        (serialPort as? SerialPort)?.configure()
        val utc2 = utc == true
        val client: RenogyClient = if (isDummy) DummyRenogyClient() else RenogyModbusClient(serialPort)

        if (status == true) {
            val allData: RenogyData = client.getAllData()
            println(allData.toJson())
        } else {
            println("Accessing device $client")
            val systemInfo = client.getSystemInfo()
            println("Device $systemInfo")
            val stateFile2 = File(statefile ?: "status.json")
            val logFile2 = File(logfile ?: "log.csv")
            val pollInterval = pollingInterval ?: 10
            println("Polling the device every $pollInterval seconds; writing status to $stateFile2, appending data to $logFile2")
            println("Press CTRL+C or send SIGTERM to end the program")

            repeatEvery((pollingInterval ?: 10) * 1000L) {
                val allData: RenogyData = client.getAllData(systemInfo)
                stateFile2.writeContents(allData.toJson())
                logFile2.appendCSV(allData, utc2)
                true
            }
        }
    }
}

private fun File.appendCSV(data: RenogyData, utc: Boolean) {
    val writeHeader = !exists()
    openAppend().use { io ->
        val csv = CSVWriter(io)
        if (writeHeader) {
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
}
