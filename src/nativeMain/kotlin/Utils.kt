import kotlinx.cinterop.toKString
import platform.posix.PARENB
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

fun checkZero(op: String, call: () -> Int): Int =
    checkNative(op, 0..0, call)

fun checkNonNegative(op: String, call: () -> Int): Int =
    checkNative(op, 0..Int.MAX_VALUE, call)
fun checkNonNegativeLong(op: String, call: () -> Long): Long =
    checkNative(op, 0L..Long.MAX_VALUE, call)

inline fun UInt.remove(flag: Int): UInt = this.remove(flag.toUInt())
inline fun UInt.remove(flag: UInt): UInt = this and (flag.inv())
inline fun UInt.add(flag: Int): UInt = this or (flag.toUInt())
