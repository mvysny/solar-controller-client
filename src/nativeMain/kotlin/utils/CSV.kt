package utils

class CSVWriter(private val io: IO) {
    fun writeHeader(vararg header: String) {
        writeLine(*header)
    }
    fun writeLine(vararg line: Any?) {
        val row = line.joinToString(",") {
            when (it) {
                null -> ""
                is Float -> it.toString2Decimals()
                is Number, is UShort, is UInt, is UByte -> it.toString()
                else -> "\"$it\""
            }
        }
        io.writeln(row)
    }
}
