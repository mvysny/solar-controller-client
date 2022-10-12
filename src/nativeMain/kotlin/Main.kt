import clients.*
import utils.*

fun main(_args: Array<String>) {
    val args = Args.parse(_args)

    val dataLoggers = args.getDataLoggers()

    val client: RenogyClient = if (args.isDummy) DummyRenogyClient() else KeepOpenClient(args.device)
    client.use {
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
            log.debug("Getting all data from $client")
            val allData: RenogyData = client.getAllData(systemInfo)
            log.debug("Writing data to ${args.stateFile}")
            args.stateFile.writeTextUTF8(allData.toJson())
            dataLoggers.forEach { it.append(allData) }
            midnightAlarm.tick()
            log.debug("Main loop: done")
        } catch (e: Exception) {
            // don't crash on exception; print it out and continue.
            log.warn("Main loop failure", e)
        }
        true
    }
}
