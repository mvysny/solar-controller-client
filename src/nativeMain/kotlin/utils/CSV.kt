package utils

class CSVWriter(private val io: IO) {
    fun writeHeader(vararg header: String) {
        writeLine(header)
    }
    fun writeLine(vararg line: Any?) {
        val row = line.joinToString(",") {
            when {
                it == null -> ""
                it is Number -> it.toString()
                else -> "\"$it\""
            }
        }
        io.writeln(row)
    }
}
