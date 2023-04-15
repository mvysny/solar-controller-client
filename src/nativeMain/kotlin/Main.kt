import clients.*
import utils.*

fun main(_args: Array<String>) {
    val args = Args.parse(_args)

    args.newDataLogger().use { dataLogger ->
        val client: RenogyClient = if (args.isDummy) DummyRenogyClient() else FixDailyStatsClient(RetryOnTimeoutClient(args.device))
        client.use {
            if (args.printStatusOnly) {
                val allData: RenogyData = client.getAllData()
                println(allData.toJson())
            } else {
                mainLoop(client, args, dataLogger)
            }
        }
    }
}

private val log = Log.get("Main")

/**
 * Runs the main loop: periodically polls [client] for new Solar Controller data,
 * then logs the data to the [dataLogger].
 */
private fun mainLoop(
    client: RenogyClient,
    args: Args,
    dataLogger: DataLogger
) {
    log.info("Accessing solar controller via $client")
    val systemInfo: SystemInfo = client.getSystemInfo()
    log.info("Solar Controller: $systemInfo")
    log.info("Polling the solar controller every ${args.pollInterval} seconds; writing status to ${args.stateFile}, appending data to $dataLogger")
    log.info("Press CTRL+C or send SIGTERM to end the program\n")

    dataLogger.init()
    dataLogger.deleteRecordsOlderThan(args.pruneLog)

    val midnightAlarm = MidnightAlarm { dataLogger.deleteRecordsOlderThan(args.pruneLog) }
    repeatEvery(args.pollInterval * 1000L) {
        try {
            log.debug("Getting all data from $client")
            val allData: RenogyData = client.getAllData(systemInfo)
            log.debug("Writing data to ${args.stateFile}")
            args.stateFile.writeTextUTF8(allData.toJson())
            dataLogger.append(allData)
            midnightAlarm.tick()
            log.debug("Main loop: done")
        } catch (e: Exception) {
            // don't crash on exception; print it out and continue. The KeepOpenClient will recover for serialport errors.
            log.warn("Main loop failure", e)
        }
        true
    }
}
