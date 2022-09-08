import kotlinx.cinterop.toKString
import platform.posix.errno
import platform.posix.strerror

fun <R: Comparable<R>> checkNative(op: String, range: ClosedRange<R>, call: () -> R): R {
    val result: R = call()
    if (result !in range) {
        val err = strerror(errno)?.toKString() ?: ""
        throw IOException("Error $errno from $op: $err")
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
