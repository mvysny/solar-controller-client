import kotlinx.serialization.json.Json

fun main(vararg params: String) {
    require(params.size == 1) { "Expected 1 parameter: the file name of the serial device to communicate with, e.g. /dev/ttyUSB0" }

    SerialPort(params[0]).use { serialPort ->
        serialPort.configure()
        val client = RenogyModbusClient(serialPort)
        val allData: RenogyData = client.getAllData()
        println(allData.toJson())
    }
}
