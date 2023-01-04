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
        withSerialPort { RenogyModbusClient(it).getAllData(cachedSystemInfo) }

    override fun close() {}

    override fun toString(): String = "OpenCloseClient($file)"

    companion object {
        private val log = Log.get(OpenCloseClient::class)
    }
}