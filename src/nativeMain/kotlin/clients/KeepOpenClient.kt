package clients

import utils.*

/**
 * Keeps a comm pipe open during the whole duration of this client.
 * Workarounds [Issue 10](https://github.com/mvysny/solar-controller-client/issues/10) by closing/reopening
 * the pipe on timeout.
 *
 * The client will then re-throw the exception and will not reattempt to re-read new data. The reason is
 * that the main loop will call us again anyways.
 */
class KeepOpenClient(val file: File) : RenogyClient {
    private var io: SerialPort? = null

    private fun getIO(): SerialPort {
        if (io == null) {
            io = SerialPort(file).apply {
                configure()
                drainQuietly()
            }
        }
        return io!!
    }

    private fun <T> runAndMitigateExceptions(block: (IO) -> T) : T {
        try {
            return block(getIO())
        } catch (e: RenogyException) {
            // perhaps there's some leftover data in the serial port? Drain.
            log.warn("Caught $e, draining $io")
            io?.drainQuietly()
            throw e
        } catch (t: TimeoutException) {
            // the serial port would simply endlessly fail with TimeoutException.
            // Try to remedy the situation by closing the IO and opening it again on next request.
            log.warn("Caught $t, closing $io")
            io?.closeQuietly()
            io = null
            throw t
        }
    }

    override fun getSystemInfo(): SystemInfo =
        runAndMitigateExceptions { io -> RenogyModbusClient(io).getSystemInfo() }

    override fun getAllData(cachedSystemInfo: SystemInfo?): RenogyData =
        runAndMitigateExceptions { io -> RenogyModbusClient(io).getAllData(cachedSystemInfo) }

    override fun close() {
        io?.close()
        io = null
    }

    override fun toString(): String = "KeepOpenClient($file)"

    companion object {
        private val log = Log.get(KeepOpenClient::class)
    }
}