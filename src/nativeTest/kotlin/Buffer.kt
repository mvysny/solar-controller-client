import kotlin.test.expect

class Buffer : IO {
    // not very effective, but this is just for testing purposes
    val writtenBytes = mutableListOf<Byte>()
    val toReturn = mutableListOf<Byte>()
    var readPointer = 0

    override fun write(bytes: ByteArray) {
        writtenBytes.addAll(bytes.toList())
    }

    override fun read(bytes: ByteArray) {
        require(readPointer + bytes.size <= toReturn.size) { "asked to read ${bytes.size} but there's not enough data in $this" }
        for (index: Int in bytes.indices) {
            bytes[index] = toReturn[readPointer++]
        }
    }

    override fun toString(): String =
        "Buffer(written=${writtenBytes.toByteArray().toHex()}, toReturn=${toReturn.toByteArray().toHex()}, readPointer=$readPointer)"

    fun expectWrittenBytes(hexBytes: String) {
        expect(hexBytes) { writtenBytes.toByteArray().toHex() }
    }
}
