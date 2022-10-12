package clients

import utils.*

/**
 * Opens a comm pipe on every request, then closes it afterwards.
 */
class OpenCloseClient(val file: File) : RenogyClient {
    private fun open(): SerialPort {
        val io = SerialPort(file)
        io.configure()
        return io
    }

    private fun <T> withSerialPort(block: (SerialPort) -> T): T =
        open().use { serialPort ->
            try {
                block(serialPort)
            } catch (e: RenogyException) {
                // perhaps there's some leftover data in the serial port? Drain.
                log.warn("Caught $e, draining $serialPort")
                serialPort.drainQuietly()
                throw e
            }
        }

    override fun getSystemInfo(): SystemInfo =
        withSerialPort { RenogyModbusClient(it).getSystemInfo() }

    override fun getAllData(cachedSystemInfo: SystemInfo?): RenogyData =
        withSerialPort { RenogyModbusClient(it).getAllData() }

    override fun close() {}

    override fun toString(): String = "OpenCloseClient($file)"

    companion object {
        private val log = Log.get(OpenCloseClient::class)
    }
}

/**
 * Keeps a comm pipe open during the whole duration of this client.
 * Workarounds https://github.com/mvysny/solar-controller-client/issues/10 by closing/reopening
 * the pipe on timeout.
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

    private fun <T> runAndMitigateExceptions(block: () -> T) : T {
        try {
            return block()
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
        runAndMitigateExceptions { RenogyModbusClient(getIO()).getSystemInfo() }

    override fun getAllData(cachedSystemInfo: SystemInfo?): RenogyData =
        runAndMitigateExceptions { RenogyModbusClient(getIO()).getAllData(cachedSystemInfo) }

    override fun close() {
        io?.close()
        io = null
    }

    override fun toString(): String = "KeepOpenClient($file)"

    companion object {
        private val log = Log.get(KeepOpenClient::class)
    }
}
