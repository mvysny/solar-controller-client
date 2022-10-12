package utils

import kotlin.test.expect

/**
 * A memory buffer, stores all written bytes to [writtenBytes]; [readFully] will offer
 * bytes from [toReturn].
 * @param maxIOBytes max number of bytes to accept during [write] and offer during [read].
 */
class Buffer(val maxIOBytes: Int = 1024) : IO {
    /**
     * Holds bytes written via [write]
     */
    val writtenBytes = mutableListOf<Byte>()    // not very effective, but this is just for testing purposes

    /**
     * Will be returned via [read].
     */
    val toReturn = mutableListOf<Byte>()     // not very effective, but this is just for testing purposes

    /**
     * The current read pointer; next call to [readFully] will return byte from [toReturn]
     * at this index. Automatically increased as [readFully] is called further.
     */
    var readPointer = 0

    override fun write(bytes: ByteArray, offset: Int, length: Int): Int {
        val byteCount = length.coerceAtMost(maxIOBytes)
        writtenBytes.addAll(bytes.toList().subList(offset, offset + byteCount))
        return byteCount
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (readPointer >= toReturn.size) return -1
        val readUntilIndex = offset + length.coerceAtMost(maxIOBytes)
        var bytesRead = 0
        for (index: Int in offset until readUntilIndex) {
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
        "Buffer(written=${writtenBytes.toByteArray().toHex()}, toReturn=${toReturn.toByteArray().toHex()}, readPointer=$readPointer)"

    fun expectWrittenBytes(hexBytes: String) {
        expect(hexBytes) { writtenBytes.toByteArray().toHex() }
    }
}

fun MutableList<Byte>.addAll(hex: String) {
    addAll(hex.fromHex().toList())
}
