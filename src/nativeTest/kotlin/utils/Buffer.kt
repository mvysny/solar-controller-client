package utils

import kotlin.test.expect

/**
 * A memory buffer, stores all written bytes to [writtenBytes]; [readFully] will offer
 * bytes from [toReturn].
 */
class Buffer : IO {
    // not very effective, but this is just for testing purposes
    val writtenBytes = mutableListOf<Byte>()
    val toReturn = mutableListOf<Byte>()

    /**
     * The current read pointer; next call to [readFully] will return byte from [toReturn]
     * at this index. Automatically increased as [readFully] is called further.
     */
    var readPointer = 0

    override fun write(bytes: ByteArray, offset: Int, length: Int): Int {
        writtenBytes.addAll(bytes.toList().subList(offset, offset + length))
        return length
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        require(readPointer < toReturn.size) { "EOF" }
        var bytesRead = 0
        for (index: Int in offset until offset+length) {
            if (readPointer >= toReturn.size) {
                return bytesRead
            }
            bytes[index] = toReturn[readPointer++]
            bytesRead++
        }
        return bytesRead
    }

    override fun close() {}

    override fun toString(): String =
        "utils.Buffer(written=${writtenBytes.toByteArray().toHex()}, toReturn=${toReturn.toByteArray().toHex()}, readPointer=$readPointer)"

    fun expectWrittenBytes(hexBytes: String) {
        expect(hexBytes) { writtenBytes.toByteArray().toHex() }
    }
}
