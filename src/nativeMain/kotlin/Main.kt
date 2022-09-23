import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import utils.*

fun main(args: Array<String>) {
    val parser = ArgParser("solar-controller-client")
    val device by parser.argument(ArgType.String, description = "the file name of the serial device to communicate with, e.g. /dev/ttyUSB0 . Pass in `dummy` for a dummy Renogy client")
    val status by parser.option(ArgType.Boolean, fullName = "status", description = "print the Renogy Rover status as JSON to stdout and quit")
    val utc by parser.option(ArgType.Boolean, fullName = "utc", description = "dump date in UTC instead of local, handy for Grafana")
    val csv by parser.option(ArgType.String, fullName = "csv", description = "appends status to a CSV file, disables stdout status logging")
    val sqlite by parser.option(ArgType.String, fullName = "sqlite", description = "appends status to a sqlite database, disables stdout status logging")
    val statefile by parser.option(ArgType.String, fullName = "statefile", description = "overwrites status to file other than the default 'status.json'")
    val pollingInterval by parser.option(ArgType.Int, fullName = "pollinterval", shortName = "i", description = "in seconds: how frequently to poll the controller for data, defaults to 10")
    val pruneLog by parser.option(ArgType.Int, fullName = "prunelog", description = "prunes log entries older than x days, defaults to 365")
    parser.parse(args)

    val utc2 = utc == true
    val pruneLog2 = pruneLog ?: 365
    require(pruneLog2 >= 1) { "--prunelog: Expected 1 or more days, got $pruneLog2" }
    val dataLoggers = mutableListOf<DataLogger>(StdoutCSVDataLogger(utc2))
    if (csv != null) {
        dataLoggers.removeAll { it is StdoutCSVDataLogger }
        dataLoggers.add(CSVDataLogger(File(csv!!), utc2))
    }
    if (sqlite != null) {
        dataLoggers.removeAll { it is StdoutCSVDataLogger }
        dataLoggers.add(SqliteDataLogger(File(sqlite!!)))
    }

    val isDummy = device == "dummy"
    val io: IO = if (isDummy) DevZero() else SerialPort(File(device))
    io.use { serialPort ->
        (serialPort as? SerialPort)?.configure()
        val client: RenogyClient = if (isDummy) DummyRenogyClient() else RenogyModbusClient(serialPort)

        if (status == true) {
            val allData: RenogyData = client.getAllData()
            println(allData.toJson())
        } else {
            println("Accessing device $client")
            val systemInfo = client.getSystemInfo()
            println("Device $systemInfo")
            val stateFile2 = File(statefile ?: "status.json")
            val pollInterval = pollingInterval ?: 10
            require(pollInterval >= 1) { "--pollinterval: must be 1 or greater but was $pollInterval" }
            println("Polling the device every $pollInterval seconds; writing status to $stateFile2, appending data to $dataLoggers")
            println("Press CTRL+C or send SIGTERM to end the program\n")

            dataLoggers.forEach {
                it.init()
                it.deleteRecordsOlderThan(pruneLog2)
            }

            val midnightAlarm = MidnightAlarm { dataLoggers.forEach { it.deleteRecordsOlderThan(pruneLog2) } }
            repeatEvery(pollInterval * 1000L) {
                try {
                    val allData: RenogyData = client.getAllData(systemInfo)
                    stateFile2.writeContents(allData.toJson())
                    dataLoggers.forEach { it.append(allData) }
                    midnightAlarm.tick()
                } catch (e: Exception) {
                    // don't crash on exception; print it out and continue.
                    e.printStackTrace()
                }
                true
            }
        }
    }
}
