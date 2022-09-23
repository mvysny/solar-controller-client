import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import utils.File
import utils.toFile

/**
 * @property device the file name of the serial device to communicate with, e.g. /dev/ttyUSB0 . Pass in `dummy` for a dummy Renogy client
 * @property status print the Renogy Rover status as JSON to stdout and quit
 * @property utc CSV: dump date in UTC instead of local, handy for Grafana
 * @property csv appends status to a CSV file, disables stdout status logging
 * @property sqlite appends status to a sqlite database, disables stdout status logging
 * @property stateFile overwrites status to file other than the default 'status.json'
 * @property pollInterval in seconds: how frequently to poll the controller for data, defaults to 10
 * @property pruneLog prunes log entries older than x days, defaults to 365
 */
data class Args(
    val device: File,
    val status: Boolean,
    val utc: Boolean,
    val csv: File?,
    val sqlite: File?,
    val stateFile: File,
    val pollInterval: Int,
    val pruneLog: Int
) {
    init {
        require(pollInterval > 0) { "pollInterval: must be 1 or greater but was $pollInterval" }
        require(pruneLog > 0) { "pruneLog: must be 1 or greater but was $pruneLog" }
    }

    val isDummy: Boolean get() = device.pathname == "dummy"

    companion object {
        fun parse(args: Array<String>): Args {
            val parser = ArgParser("solar-controller-client")
            val device by parser.argument(ArgType.String, description = "the file name of the serial device to communicate with, e.g. /dev/ttyUSB0 . Pass in `dummy` for a dummy Renogy client")
            val status by parser.option(ArgType.Boolean, fullName = "status", description = "print the Renogy Rover status as JSON to stdout and quit")
            val utc by parser.option(ArgType.Boolean, fullName = "utc", description = "CSV: dump date in UTC instead of local, handy for Grafana")
            val csv by parser.option(ArgType.String, fullName = "csv", description = "appends status to a CSV file, disables stdout status logging")
            val sqlite by parser.option(ArgType.String, fullName = "sqlite", description = "appends status to a sqlite database, disables stdout status logging")
            val statefile by parser.option(ArgType.String, fullName = "statefile", description = "overwrites status to file other than the default 'status.json'")
            val pollingInterval by parser.option(ArgType.Int, fullName = "pollinterval", shortName = "i", description = "in seconds: how frequently to poll the controller for data, defaults to 10")
            val pruneLog by parser.option(ArgType.Int, fullName = "prunelog", description = "prunes log entries older than x days, defaults to 365")
            parser.parse(args)

            return Args(device.toFile(), status == true, utc == true, csv?.toFile(), sqlite?.toFile(), (statefile ?: "status.json").toFile(), pollingInterval ?: 10, pruneLog ?: 365)
        }
    }
}