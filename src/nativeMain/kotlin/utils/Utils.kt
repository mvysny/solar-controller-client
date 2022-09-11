package utils

import FileNotFoundException
import IOException
import kotlinx.cinterop.*
import platform.posix.*

fun <R: Comparable<R>> checkNative(op: String, range: ClosedRange<R>, call: () -> R): R {
    val result: R = call()
    if (result !in range) {
        iofail(op)
    }
    return result
}

/**
 * Always throws [IOException] with the current [errno].
 */
fun iofail(op: String): Nothing {
    val e = errno
    val err = strerror(e)?.toKString() ?: ""
    val message = "Error $e from $op: $err"
    if (e == 2) {
        throw FileNotFoundException(message)
    }
    throw IOException(message, null, e)
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

/**
 * Formats this byte as a 2-character hex value, e.g. "03" or "fe".
 */
fun Byte.toHex(): String {
    val hex = toUByte().toInt().toString(16)
    return if (hex.length == 1) "0${hex}" else hex
}

/**
 * Formats this [UShort] as a 4-character hex value, e.g. "0000" or "ffff".
 */
fun UShort.toHex(): String = toInt().toString(16).padStart(4, '0')

/**
 * Converts every byte in the array to char and concatenates it as a string. Pure ASCII is used, no UTF-8 conversion is done.
 */
fun ByteArray.toAsciiString() = map { it.toUByte().toInt().toChar() } .toCharArray().concatToString()

/**
 * Returns the [UShort] value at [index] and [index]+1. High byte first, then the low byte.
 */
inline fun ByteArray.getUShortHiLoAt(index: Int): UShort {
    require(index in 0..(size - 2)) { "Invalid value of $index: size=${size}" }
    return ((get(index).toUByte().toUShort() * 256.toUShort()).toUShort() + get(index + 1).toUByte().toUShort()).toUShort()
}
/**
 * Sets the [UShort] value at [index] and [index]+1. High byte first, then the low byte.
 */
inline fun ByteArray.setUShortHiLoAt(index: Int, value: UShort) {
    require(index in 0..(size - 2)) { "Invalid index $index: size=${size}" }
    this[index + 1] = ((value.toInt() and 0x000000ff)).toByte()
    this[index] = ((value.toInt() and 0x0000ff00).ushr(8)).toByte()
}

/**
 * Returns the [UShort] value at [index]..[index]+1. Highest byte first, then the lowest byte.
 */
inline fun ByteArray.getUIntHiLoAt(index: Int): UInt {
    require(index in 0..(size - 4)) { "Invalid index $index: size=${size}" }
    var result: UInt = 0.toUInt()
    result = result.shl(8) + this[index].toUByte().toUInt()
    result = result.shl(8) + this[index + 1].toUByte().toUInt()
    result = result.shl(8) + this[index + 2].toUByte().toUInt()
    result = result.shl(8) + this[index + 3].toUByte().toUInt()
    return result
}

private val STDERR = fdopen(2, "w")

/**
 * Prints [message] to utils.STDERR.
 */
fun eprintln(message: String) {
    fprintf(STDERR, "%s\n", message)
    fflush(STDERR)
}

/**
 * Returns the current date and time, in the format of 2022-09-11 00:14:45
 */
fun getLocalDateTime(): String {
    val t = checkNonNegativeLong("time") { time(null) }
    val tm = localtime(cValuesOf(t))!!.pointed
    val yyyymmdd = "${tm.tm_year + 1900}-${(tm.tm_mon + 1).toString().padStart(2, '0')}-${tm.tm_mday.toString().padStart(2, '0')}"
    val hhmmss = "${tm.tm_hour.toString().padStart(2, '0')}:${tm.tm_min.toString().padStart(2, '0')}:${tm.tm_sec.toString().padStart(2, '0')}"
    return "$yyyymmdd $hhmmss"
}
