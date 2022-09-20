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
        StderrIO.writeln("$this: close failed: $e")
        e.printStackTrace()
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
interface IO : Closeable {
    /**
     * Tries to write a subarray of [bytes] denoted by [offset] and [length]. Blocks until at least one byte is written.
     * Fails if length is 0.
     *
     * You most probably want to call [writeFully].
     * @return the actual number of bytes written, 1 or greater.
     */
    fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Int

    /**
     * Reads at least one byte from the underlying IO. Populates [bytes] at given [offset] and [length].
     * Blocks until at least one byte has been retrieved.
     * @param length must be 1 or more
     * @return 1 or more - the number of bytes actually read and stored into [bytes]. Not more than [length]. -1 on EOF
     */
    fun read(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Int
}

/**
 * Writes all [bytes] to the underlying IO. Blocks until the bytes are written.
 * Does nothing if the array is empty.
 */
fun IO.writeFully(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
    if (length == 0) {
        return
    }
    var current = offset
    while (current < offset + length) {
        val bytesWritten = write(bytes, current, length - (current - offset))
        check(bytesWritten > 0) { "write returned $bytesWritten" }
        current += bytesWritten
    }
}


/**
 * Reads all [bytes] from the underlying IO. Blocks until the byte array is fully populated.
 * Does nothing if the array is empty.
 * @throws EOFException if we reach the end of stream during the read.
 */
fun IO.readFully(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
    require(offset in bytes.indices) { "offset: out of bounds $offset, must be ${bytes.indices}" }
    require(length >= 0) { "length must be 0 or greater but was $length" }
    require((offset + length - 1) in bytes.indices) { "length: out of bounds $offset+$length, must be ${bytes.indices}" }

    var current = offset
    while (current < offset + length) {
        val bytesRead: Int = read(bytes, current, length - (current - offset))
        if (bytesRead < 0) throw EOFException()
        check(bytesRead > 0) { "read should return 1+ but returned $bytesRead" }
        current += bytesRead
    }
}

/**
 * Reads [noBytes] and returns it in a newly allocated byte array.
 */
fun IO.readFully(noBytes: Int): ByteArray {
    require(noBytes >= 0) { "$noBytes: must be 0 or higher" }
    val bytes = ByteArray(noBytes)
    readFully(bytes)
    return bytes
}

fun IO.write(line: String) {
    writeFully(line.encodeToByteArray())
}

fun IO.writeln(line: String) {
    write(line)
    writeFully(byteArrayOf('\n'.code.toByte()))
}

/**
 * A generic I/O exception.
 * @param errno the [platform.posix.errno] value.
 */
open class IOException(message: String, cause: Throwable? = null, val errno: Int? = null) : Exception(message, cause)
open class EOFException(message: String = "EOF", cause: Throwable? = null) : IOException(message, cause)
open class FileNotFoundException(message: String, cause: Throwable? = null) : IOException(message, cause, 2)

data class File(val pathname: String) {
    init {
        require(pathname.isNotBlank()) { "pathname is blank" }
    }
    fun openAppend(mode: Int = rwrwr): IO = IOFile(this, O_WRONLY or O_APPEND or O_CREAT, mode)
    fun openOverwrite(mode: Int = rwrwr): IO = IOFile(this, O_WRONLY or O_TRUNC or O_CREAT, mode)
    fun openRead(): IO = IOFile(this, O_RDONLY)
    fun writeContents(contents: String) {
        openOverwrite().use { file -> file.writeFully(contents.encodeToByteArray()) }
    }
    fun appendContents(contents: String) {
        openAppend().use { file -> file.writeFully(contents.encodeToByteArray()) }
    }
    fun exists(): Boolean {
        val result = access(pathname, F_OK)
        if (result == 0) return true
        if (errno == ENOENT) return false
        iofail("access")
    }

    override fun toString(): String = "File($pathname)"
}

open class FDIO(protected val fd: Int) : IO {
    override fun write(bytes: ByteArray, offset: Int, length: Int): Int {
        require(offset in bytes.indices) { "offset: out of bounds $offset, must be ${bytes.indices}" }
        require(length > 0) { "length must be 1 or greater but was $length" }
        require((offset+length-1) in bytes.indices) { "length: out of bounds $offset+$length, must be ${bytes.indices}" }

        return bytes.usePinned { pinned ->
            val bytesWritten: Long = checkNativeNonNegativeLong("write") {
                write(fd, pinned.addressOf(offset), length.toULong())
            }
            bytesWritten.toInt()
        }
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        require(offset in bytes.indices) { "offset: out of bounds $offset, must be ${bytes.indices}" }
        require(length > 0) { "length must be 1 or greater but was $length" }
        require((offset+length-1) in bytes.indices) { "length: out of bounds $offset+$length, must be ${bytes.indices}" }

        return bytes.usePinned { pinned ->
            val bytesRead: Long = checkNativeNonNegativeLong("read") {
                read(fd, pinned.addressOf(offset), length.toULong())
            }
            if (bytesRead == 0L) {
                return -1
            }
            bytesRead.toInt()
        }
    }

    override fun close() {
        checkNativeZero("close") { close(fd) }
    }
}

/**
 * STDOUT.
 */
object StdoutIO : FDIO(1) {
    override fun close() {}
    override fun toString(): String = "StdoutIO"
}

/**
 * STDERR.
 */
object StderrIO : FDIO(2) {
    override fun close() {}
    override fun toString(): String = "StderrIO"
}

/**
 * Reads/writes from/to a file with given [file].
 * @param oflag the open file flag, one of `O_*` constants. Most useful combos:
 * `O_WRONLY or O_TRUNC or O_CREAT` overwrites the file with a zero-sized one;
 * `O_WRONLY or O_APPEND or O_CREAT` starts writing at the end of the file, creating it if necessary;
 * `O_RDWR` - opens the file for read/write; the file must exist.
 * @param mode the file mode when it's created, e.g. `S_IRWXU`.
 */
open class IOFile(val file: File, oflag: Int = O_RDWR, mode: Int = 0) : FDIO(checkNativeNonNegative("open $file") { open(file.pathname, oflag, mode) }) {
    override fun toString(): String = "IOFile($file)"
}

/**
 * A serial port IO, opens a serial port communication over given [file].
 *
 * Don't forget to call [configure]!
 */
class SerialPort(file: File) : IOFile(file) {
    /**
     * Configure the serial port to 9600 baud, 8 bits, 1 stop bit, no parity.
     * @param baud the desired baud rate, one of the `B*` constants, e.g. [B9600].
     * @param dataBits how many data bits per byte, one of the `CS*` constants, defaults to [CS8] (most common)
     */
    fun configure(baud: Int = B9600, dataBits: Int = CS8) {
        // taken from https://blog.mbedded.ninja/programming/operating-systems/linux/linux-serial-ports-using-c-cpp/#reading-and-writing
        memScoped {
            val tty: termios = alloc<termios>()
            checkNativeZero("tcgetattr (get serial port configuration) for $file") { tcgetattr(fd, tty.ptr) }

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

            checkNativeZero("tcsetattr (set serial port configuration) for $file") { tcsetattr(fd, TCSANOW, tty.ptr) }
        }
    }

    override fun toString(): String = "SerialPort($file)"
}

private val rwrwr = S_IRUSR or S_IWUSR or S_IRGRP or S_IWGRP or S_IROTH

/**
 * All written bytes are thrown away; provides an endless stream of zeroes as input.
 */
class DevZero: IO {
    override fun write(bytes: ByteArray, offset: Int, length: Int): Int {
        // throw away
        return length
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        bytes.fill(0.toByte(), offset, offset + length)
        return length
    }

    override fun close() {}
    override fun toString(): String = "DevZero()"
}
