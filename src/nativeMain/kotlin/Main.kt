import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import utils.SerialPort
import utils.repeatEvery
import utils.use
import utils.writeToFile

fun main(args: Array<String>) {
    val parser = ArgParser("solar-controller-client")
    val device by parser.argument(ArgType.String, description = "the file name of the serial device to communicate with, e.g. /dev/ttyUSB0")
    val status by parser.option(ArgType.Boolean, fullName = "status", description = "print the Renogy Rover status as JSON to stdout and quit")
    val logfile by parser.option(ArgType.String, fullName = "logfile", description = "appends status to file other than the default 'log.csv'")
    val statefile by parser.option(ArgType.String, fullName = "statefile", description = "overwrites status to file other than the default 'status.json'")
    val pollingInterval by parser.option(ArgType.Int, fullName = "pollinginterval", shortName = "i", description = "in seconds: how frequently to poll the controller for data, defaults to 10")
    parser.parse(args)

    SerialPort(device).use { serialPort ->
        serialPort.configure()
        val client: RenogyClient = RenogyModbusClient(serialPort)

        if (status == true) {
            val allData: RenogyData = client.getAllData()
            println(allData.toJson())
        } else {
            val systemInfo = client.getSystemInfo()
            repeatEvery((pollingInterval ?: 10) * 1000L) {
                val allData: RenogyData = client.getAllData(systemInfo)
                writeToFile(statefile ?: "status.json", allData.toJson())
                // todo append to CSV
                true
            }
        }
    }
}
