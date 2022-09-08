// Copyright https://github.com/QuickBirdEng/crc-kotlin
package crc

infix fun UShort.shl(bitCount: Int): UShort = (this.toUInt() shl bitCount).toUShort()
infix fun UShort.shr(bitCount: Int): UShort = (this.toUInt() shr bitCount).toUShort()

infix fun UByte.shl(bitCount: Int): UByte = (this.toUInt() shl bitCount).toUByte()
infix fun UByte.shr(bitCount: Int): UByte = (this.toUInt() shr bitCount).toUByte()

fun UByte.toBigEndianUShort(): UShort = this.toUShort() shl 8
fun UByte.toBigEndianUInt(): UInt = this.toUInt() shl 24

@ExperimentalUnsignedTypes
interface CRC<T> {
    val lookupTable: List<T>
    val value: T

    fun update(inputs: UByteArray)

    fun reset()

    fun update(input: UByte) {
        update(ubyteArrayOf(input))
    }

    fun update(inputs: ByteArray) {
        update(inputs.toUByteArray())
    }

}

/// Class to conveniently calculate CRC-16. It uses the CRC16-CCITT polynomial (0x1021)  by default
@ExperimentalUnsignedTypes
class CRC16(val polynomial: UShort = 0x1021.toUShort()) : CRC<UShort> {
    override val lookupTable: List<UShort> = (0 until 256).map { crc16(it.toUByte(), polynomial) }

    override var value: UShort = 0.toUShort()
        private set

    override fun update(inputs: UByteArray) {
        value = crc16(inputs, value)
    }

    override fun reset() {
        value = 0.toUShort()
    }

    private fun crc16(inputs: UByteArray, initialValue: UShort = 0.toUShort()): UShort {
        return inputs.fold(initialValue) { remainder, byte ->
            val bigEndianInput = byte.toBigEndianUShort()
            val index = (bigEndianInput xor remainder) shr 8
            lookupTable[index.toInt()] xor (remainder shl 8)
        }
    }

    private fun crc16(input: UByte, polynomial: UShort): UShort {
        val bigEndianInput = input.toBigEndianUShort()

        return (0 until 8).fold(bigEndianInput) { result, _ ->
            val isMostSignificantBitOne = result and 0x8000.toUShort() != 0.toUShort()
            val shiftedResult = result shl 1

            when (isMostSignificantBitOne) {
                true -> shiftedResult xor polynomial
                false -> shiftedResult
            }
        }
    }
}