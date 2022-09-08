import kotlinx.cinterop.*
import platform.posix.*

interface Closeable {
    fun close()
}

interface IO {
    /**
     * Writes all [bytes] to the underlying IO.
     */
    fun write(bytes: ByteArray)
    /**
     * Reads at most [bytes] from the underlying IO. May also read 0 bytes, for example
     * if this is a socket or a pipe and the read timed out. The function may also
     * block while at least a single byte is read, this again depends on the underlying socket/pipe
     * configuration.
     */
    fun read(bytes: ByteArray): Int
}

open class IOFile(val fname: String) : IO, Closeable {
    protected val fd: Int = checkNonNegative("open") { open(fname, O_RDWR) }

    override fun write(bytes: ByteArray) {
        var current = 0
        while (current < bytes.size) {
            bytes.usePinned { pinned ->
                val bytesWritten: Long = checkNonNegativeLong("write") {
                    write(fd, pinned.addressOf(0), bytes.size.toULong())
                }
                current += bytesWritten.toInt()
            }
        }
    }

    override fun read(bytes: ByteArray): Int {
        TODO("Not yet implemented")
    }

    override fun close() {
        close(fd)
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
