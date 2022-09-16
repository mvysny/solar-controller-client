package utils

import kotlinx.cinterop.*
import platform.posix.*

interface Closeable {
    /**
     * Closes this closeable. Closing the same instance multiple times is undefined.
     */
    fun close()
}

/**
 * Closes this closeable. Calls [Closeable.close] but catches any exception and prints it to stderr.
 */
fun Closeable.closeQuietly() {
    try {
        close()
    } catch (e: Exception) {
        eprintln("$this: close failed: $e")
    }
}

/**
 * Runs [block] with given closeable and closes the closeable afterwards.
 */
fun <C: Closeable, R> C.use(block: (C) -> R): R {
    try {
        return block(this)
    } finally {
        closeQuietly()
    }
}

/**
 * An IO pipe supporting most basic operations.
 */
interface IO {
    /**
     * Writes all [bytes] to the underlying IO. Blocks until the bytes are written.
     * Does nothing if the array is empty.
     */
    fun write(bytes: ByteArray)
    /**
     * Reads all [bytes] from the underlying IO. Blocks until the byte array is fully populated.
     * Does nothing if the array is empty.
     */
    fun read(bytes: ByteArray)

    /**
     * Reads [noBytes] and returns it in a newly allocated byte array.
     */
    fun readBytes(noBytes: Int): ByteArray {
        require(noBytes >= 0) { "$noBytes: must be 0 or higher" }
        val bytes = ByteArray(noBytes)
        read(bytes)
        return bytes
    }
}

/**
 * A generic I/O exception.
 * @param errno the [platform.posix.errno] value.
 */
open class IOException(message: String, cause: Throwable? = null, val errno: Int? = null) : Exception(message, cause)
open class EOFException(message: String, cause: Throwable? = null) : IOException(message, cause)
open class FileNotFoundException(message: String, cause: Throwable? = null) : IOException(message, cause, 2)

/**
 * Reads/writes from/to a file with given [fname].
 * @param oflag the open file flag, one of `O_*` constants. Most useful combos:
 * `O_WRONLY or O_TRUNC or O_CREAT` overwrites the file with a zero-sized one;
 * `O_WRONLY or O_APPEND or O_CREAT` starts writing at the end of the file, creating it if necessary;
 * `O_RDWR` - opens the file for read/write; the file must exist.
 * @param mode the file mode when it's created, e.g. `S_IRWXU`.
 */
open class IOFile(val fname: String, oflag: Int = O_RDWR, mode: Int = 0) : IO, Closeable {
    init {
        require(fname.isNotBlank()) { "fname is blank" }
    }
    protected val fd: Int = checkNativeNonNegative("open $fname") { open(fname, oflag, mode) }

    override fun write(bytes: ByteArray) {
        var current = 0
        while (current < bytes.size) {
            bytes.usePinned { pinned ->
                val bytesWritten: Long = checkNativeNonNegativeLong("write") {
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
                val bytesRead: Long = checkNativeNonNegativeLong("read") {
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
        checkNativeZero("close") { close(fd) }
    }

    override fun toString(): String = "IOFile('$fname')"
}

/**
 * A serial port IO, opens a serial port communication over given [fname].
 *
 * Don't forget to call [configure]!
 */
class SerialPort(fname: String) : IOFile(fname) {
    /**
     * Configure the serial port to 9600 baud, 8 bits, 1 stop bit, no parity.
     * @param baud the desired baud rate, one of the `B*` constants, e.g. [B9600].
     * @param dataBits how many data bits per byte, one of the `CS*` constants, defaults to [CS8] (most common)
     */
    fun configure(baud: Int = B9600, dataBits: Int = CS8) {
        // taken from https://blog.mbedded.ninja/programming/operating-systems/linux/linux-serial-ports-using-c-cpp/#reading-and-writing
        memScoped {
            val tty: termios = alloc<termios>()
            checkNativeZero("tcgetattr (get serial port configuration) for $fname") { tcgetattr(fd, tty.ptr) }

            tty.c_cflag = tty.c_cflag.remove(PARENB) // Clear parity bit, disabling parity (most common)
            tty.c_cflag = tty.c_cflag.remove(CSTOPB) // Clear stop field, only one stop bit used in communication (most common)
            tty.c_cflag = tty.c_cflag.remove(CSIZE) // Clear all bits that set the data size
            tty.c_cflag = tty.c_cflag.add(dataBits) // 8 bits per byte (most common)
            tty.c_cflag = tty.c_cflag.remove(CRTSCTS) // Disable RTS/CTS hardware flow control (most common)
            tty.c_cflag = tty.c_cflag.add(CREAD or CLOCAL) // Turn on READ & ignore ctrl lines (CLOCAL = 1)

            tty.c_lflag = tty.c_lflag.remove(ICANON)
            tty.c_lflag = tty.c_lflag.remove(ECHO) // Disable echo
            tty.c_lflag = tty.c_lflag.remove(ECHOE) // Disable erasure
            tty.c_lflag = tty.c_lflag.remove(ECHONL) // Disable new-line echo
            tty.c_lflag = tty.c_lflag.remove(ISIG) // Disable interpretation of INTR, QUIT and SUSP
            tty.c_iflag = tty.c_iflag.remove(IXON or IXOFF or IXANY) // Turn off s/w flow ctrl
            tty.c_iflag = tty.c_iflag.remove(IGNBRK or BRKINT or PARMRK or ISTRIP or INLCR or IGNCR or ICRNL) // Disable any special handling of received bytes

            tty.c_oflag = tty.c_oflag.remove(OPOST) // Prevent special interpretation of output bytes (e.g. newline chars)
            tty.c_oflag = tty.c_oflag.remove(ONLCR) // Prevent conversion of newline to carriage return/line feed
            // tty.c_oflag &= ~OXTABS; // Prevent conversion of tabs to spaces (NOT PRESENT ON LINUX)
            // tty.c_oflag &= ~ONOEOT; // Prevent removal of C-d chars (0x004) in output (NOT PRESENT ON LINUX)

            tty.c_cc[VTIME] = 10.toUByte()    // Wait for up to 1s (10 deciseconds), returning as soon as any data is received.
            tty.c_cc[VMIN] = 1.toUByte()  // block endlessly until at least 1 byte is read.

            // Set in/out baud rate to be 9600
            checkNativeZero("cfsetispeed") { cfsetispeed(tty.ptr, baud.toUInt()) }
            checkNativeZero("cfsetospeed") { cfsetospeed(tty.ptr, baud.toUInt()) }

            checkNativeZero("tcsetattr (set serial port configuration) for $fname") { tcsetattr(fd, TCSANOW, tty.ptr) }
        }
    }

    override fun toString(): String = "SerialPort('$fname')"
}

private val rwrwr = S_IRUSR or S_IWUSR or S_IRGRP or S_IWGRP or S_IROTH
fun writeToFile(fname: String, contents: String) {
    IOFile(fname, O_WRONLY or O_TRUNC or O_CREAT, rwrwr).use { file -> file.write(contents.encodeToByteArray()) }
}
fun appendToFile(fname: String, contents: String) {
    IOFile(fname, O_WRONLY or O_APPEND or O_CREAT, rwrwr).use { file -> file.write(contents.encodeToByteArray()) }
}
