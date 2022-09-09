import kotlinx.cinterop.toKString
import platform.posix.errno
import platform.posix.strerror

fun <R: Comparable<R>> checkNative(op: String, range: ClosedRange<R>, call: () -> R): R {
    val result: R = call()
    if (result !in range) {
        val err = strerror(errno)?.toKString() ?: ""
        val message = "Error $errno from $op: $err"
        if (errno == 2) {
            throw FileNotFoundException(message)
        }
        throw IOException(message)
    }
    return result
}

/**
 * Checks that [operation] [call] ended with a zero result.
 */
fun checkZero(operation: String, call: () -> Int): Int =
    checkNative(operation, 0..0, call)

fun checkNonNegative(op: String, call: () -> Int): Int =
    checkNative(op, 0..Int.MAX_VALUE, call)
fun checkNonNegativeLong(op: String, call: () -> Long): Long =
    checkNative(op, 0L..Long.MAX_VALUE, call)

/**
 * Returns a flag bitset with given [flag] removed. Same as `this &= ~flag`.
 */
inline fun UInt.remove(flag: Int): UInt = this.remove(flag.toUInt())
/**
 * Returns a flag bitset with given [flag] removed. Same as `this &= ~flag`.
 */
inline fun UInt.remove(flag: UInt): UInt = this and (flag.inv())
/**
 * Returns a flag bitset with given [flag] added. Same as `this |= flag`.
 */
inline fun UInt.add(flag: Int): UInt = this or (flag.toUInt())

inline val UShort.hibyte: Byte get() = (this / 256.toUShort()).and(0xFF.toUInt()).toByte()
inline val UShort.lobyte: Byte get() = this.and(0xFF.toUShort()).toByte()

/**
 * Formats this byte array as a string of hex, e.g. "02ff35"
 */
fun ByteArray.toHex(): String = joinToString(separator = "") { it.toHex() }
fun Byte.toHex(): String {
    val hex = toUByte().toInt().toString(16)
    return if (hex.length == 1) "0${hex}" else hex
}
fun UShort.toHex(): String = toInt().toString(16).padStart(4, '0')
fun ByteArray.toAsciiString() = map { it.toUByte().toInt().toChar() } .toCharArray().concatToString()
fun ByteArray.getUShortHiLoAt(index: Int): UShort {
    require(index in 0..(size - 2)) { "Invalid value of $index: size=${size}" }
    return ((get(index).toUByte().toUShort() * 256.toUShort()).toUShort() + get(index + 1).toUByte().toUShort()).toUShort()
}
fun ByteArray.setUShortHiLoAt(index: Int, value: UShort) {
    require(index in 0..(size - 2)) { "Invalid value of $index: size=${size}" }
    this[0] = ((value.toInt() and 0x000000ff)).toByte()
    this[1] = ((value.toInt() and 0x0000ff00).ushr(8)).toByte()
}
