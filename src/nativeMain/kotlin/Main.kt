import utils.*

fun main(_args: Array<String>) {
    val args = Args.parse(_args)

    val dataLoggers = args.getDataLoggers()

    val io: IO = if (args.isDummy) DevZero() else SerialPort(args.device)
    io.use { serialPort ->
        (serialPort as? SerialPort)?.configure()
        val client: RenogyClient =
            if (args.isDummy) DummyRenogyClient() else RenogyModbusClient(
                serialPort
            )

        if (args.status) {
            val allData: RenogyData = client.getAllData()
            println(allData.toJson())
        } else {
            mainLoop(client, args, dataLoggers)
        }
    }
}

private val log = Log.get("Main")

/**
 * Runs the main loop: periodically polls [client] for new Solar Controller data,
 * then logs the data to all [dataLoggers].
 */
private fun mainLoop(
    client: RenogyClient,
    args: Args,
    dataLoggers: List<DataLogger>
) {
    log.info("Accessing device $client")
    val systemInfo: SystemInfo = client.getSystemInfo()
    log.info("Device $systemInfo")
    log.info("Polling the device every ${args.pollInterval} seconds; writing status to ${args.stateFile}, appending data to $dataLoggers")
    log.info("Press CTRL+C or send SIGTERM to end the program\n")

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
            log.err("Main loop failure", e)
        }
        true
    }
}
