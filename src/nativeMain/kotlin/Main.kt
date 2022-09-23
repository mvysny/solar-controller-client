import utils.*

fun main(_args: Array<String>) {
    val args = Args.parse(_args)

    val dataLoggers = mutableListOf<DataLogger>(StdoutCSVDataLogger(args.utc))
    if (args.csv != null) {
        dataLoggers.removeAll { it is StdoutCSVDataLogger }
        dataLoggers.add(CSVDataLogger(args.csv, args.utc))
    }
    if (args.sqlite != null) {
        dataLoggers.removeAll { it is StdoutCSVDataLogger }
        dataLoggers.add(SqliteDataLogger(args.sqlite))
    }

    val io: IO = if (args.isDummy) DevZero() else SerialPort(args.device)
    io.use { serialPort ->
        (serialPort as? SerialPort)?.configure()
        val client: RenogyClient = if (args.isDummy) DummyRenogyClient() else RenogyModbusClient(serialPort)

        if (args.status) {
            val allData: RenogyData = client.getAllData()
            println(allData.toJson())
        } else {
            println("Accessing device $client")
            val systemInfo = client.getSystemInfo()
            println("Device $systemInfo")
            println("Polling the device every ${args.pollInterval} seconds; writing status to ${args.stateFile}, appending data to $dataLoggers")
            println("Press CTRL+C or send SIGTERM to end the program\n")

            dataLoggers.forEach {
                it.init()
                it.deleteRecordsOlderThan(args.pruneLog)
            }

            val midnightAlarm = MidnightAlarm { dataLoggers.forEach { it.deleteRecordsOlderThan(args.pruneLog) } }
            repeatEvery(args.pollInterval * 1000L) {
                try {
                    val allData: RenogyData = client.getAllData(systemInfo)
                    args.stateFile.writeContents(allData.toJson())
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
