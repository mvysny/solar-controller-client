import kotlinx.cinterop.*
import platform.posix.*

interface Closeable {
    /**
     * Closes this closeable. Closing the same instance multiple times is undefined.
     */
    fun close()
}

interface IO {
    /**
     * Writes all [bytes] to the underlying IO. Blocks until the bytes are written.
     */
    fun write(bytes: ByteArray)
    /**
     * Reads all [bytes] from the underlying IO. Blocks until the byte array is fully populated.
     */
    fun read(bytes: ByteArray)
}

open class IOException(message: String?, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String?) : this(message, null)
}
open class EOFException(message: String?, cause: Throwable?) : IOException(message, cause) {
    constructor(message: String?) : this(message, null)
}

open class IOFile(val fname: String) : IO, Closeable {
    protected val fd: Int = checkNonNegative("open") { open(fname, O_RDWR) }

    override fun write(bytes: ByteArray) {
        var current = 0
        while (current < bytes.size) {
            bytes.usePinned { pinned ->
                val bytesWritten: Long = checkNonNegativeLong("write") {
                    write(fd, pinned.addressOf(current), (bytes.size - current).toULong())
                }
                current += bytesWritten.toInt()
            }
        }
    }

    override fun read(bytes: ByteArray) {
        var current = 0
        while (current < bytes.size) {
            bytes.usePinned { pinned ->
                val bytesRead: Long = checkNonNegativeLong("read") {
                    read(fd, pinned.addressOf(current), (bytes.size - current).toULong())
                }
                current += bytesRead.toInt()
                if (bytesRead == 0L) {
                    throw EOFException("EOF")
                }
            }
        }
    }

    override fun close() {
        checkZero("close") { close(fd) }
    }
}

class SerialPort(fname: String) : IOFile(fname) {
    fun configure() {
        // taken from https://blog.mbedded.ninja/programming/operating-systems/linux/linux-serial-ports-using-c-cpp/#reading-and-writing
        memScoped {
            val tty: termios = alloc<termios>()
            checkZero("tcgetattr") { tcgetattr(fd, tty.ptr) }

            tty.c_cflag = tty.c_cflag.remove(PARENB) // Clear parity bit, disabling parity (most common)
            tty.c_cflag = tty.c_cflag.remove(CSTOPB) // Clear stop field, only one stop bit used in communication (most common)
            tty.c_cflag = tty.c_cflag.remove(CSIZE) // Clear all bits that set the data size
            tty.c_cflag = tty.c_cflag.add(CS8) // 8 bits per byte (most common)
            tty.c_cflag = tty.c_cflag.remove(CRTSCTS) // Disable RTS/CTS hardware flow control (most common)
            tty.c_cflag = tty.c_cflag.add(CREAD.or(CLOCAL)) // Turn on READ & ignore ctrl lines (CLOCAL = 1)

            tty.c_cc[VTIME] = 10.toUByte()
        }
    }
}
