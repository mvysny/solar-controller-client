package utils

import kotlinx.cinterop.cValue
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.pointed
import kotlinx.cinterop.staticCFunction
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import platform.posix.*
import kotlin.system.getTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private fun setupSignal(signal: Int) {
    if (signal(signal, staticCFunction<Int, Unit> {}) == SIG_ERR) {
        iofail("signal $signal")
    }
}

private var signalsWereSetUp = false
private fun setupSignalsForSleepOnce() {
    // if we don't set up signals then nanosleep() will kill the process when CTRL+C is pressed.
    // with signals set up, it will correctly end with -1 and set errno to EINTR.
    if (!signalsWereSetUp) {
        signalsWereSetUp = true
        setupSignal(SIGINT)
        setupSignal(SIGTERM)
    }
}

/**
 * Sleeps for [millis]. Returns true if the sleep was successful.
 * If interrupted by SIGINT (CTRL+C) or SIGTERM, ends immediately and returns false.
 * Throws an exception on other failure.
 */
fun sleepMillis(millis: Long): Boolean {
    require(millis >= 0) { "$millis: must be 0 or greater" }
    if (millis == 0L) {
        return true
    }

    setupSignalsForSleepOnce()
    val time = cValue<timespec> {
        tv_sec = millis / 1000
        tv_nsec = (millis % 1000) * 1000000
    }
    if (nanosleep(time, null) != 0) {
        if (errno == EINTR) {
            // interrupted by SIGINT (CTRL+C) or SIGTERM, return false.
            return false
        }
        // some other error, fail.
        iofail("nanosleep")
    }
    return true
}

/**
 * Calls [block] repeatedly, every [millis]. Takes the duration of block into consideration:
 * say block took 2ms out of 10ms "window", then the next block invocation will be scheduled
 * in 8ms.
 *
 * If interrupted by SIGINT (CTRL+C) or SIGTERM, ends immediately. If [block] returns false,
 * ends immediately.
 */
fun repeatEvery(millis: Long, block: () -> Boolean) {
    require(millis > 0) { "$millis: must be 1 or greater" }

    // slice the time into windows of size "millis". Running the block will take
    // a bit from the window; afterwards we need to sleep the remainder of the window
    // in order for the next block call()
    val start = getTimeMillis()
    while(true) {
        if (!block()) return

        val windowUsedMs = (getTimeMillis() - start) % millis
        val windowRemainderMs = millis - windowUsedMs
        if (!sleepMillis(windowRemainderMs)) {
            // interrupted - return
            return
        }
    }
}

// there's kotlinx-datetime library which I could use, but sadly there are two issues:
// no ARM support: https://github.com/Kotlin/kotlinx-datetime/issues/165
// no support for leap seconds: https://github.com/Kotlin/kotlinx-datetime/issues/230

/**
 * @property year e.g. 2022
 * @property month 1..12
 * @property day 1..31
 */
@Serializable(with = LocalDateSerializer::class)
data class LocalDate(val year: Int, val month: Int, val day: Int) : Comparable<LocalDate> {
    init {
        require(month in 1..12) { "month: expected 1..12 but was $month" }
        require(day in 1..31) { "day: expected 1..31 but was $day" }
    }
    private val ymd: Long get() = ((year.toLong() * 12) + month) * 31 + day
    override fun compareTo(other: LocalDate): Int = ymd.compareTo(other.ymd)

    /**
     * Formats the date in yyyy-MM-dd
     */
    fun format(): String = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    companion object {
        fun today(): LocalDate = LocalDateTime.now().date

        private val FORMAT_PATTERN = Regex("(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d)")

        /**
         * Parses the value produced by [format].
         * @param formatted in the form of `yyyy-MM-dd`.
         */
        fun parse(formatted: String): LocalDate {
            val result = FORMAT_PATTERN.matchEntire(formatted)
            requireNotNull(result) { "parsed string must be in the format of yyyy-MM-dd but got $formatted" }
            return LocalDate(result.groupValues[1].toInt(), result.groupValues[2].toInt(), result.groupValues[3].toInt())
        }
    }
}

/**
 * @property hour 0..23
 * @property minute 0..59
 * @property second 0..60
 */
@Serializable(with = LocalTimeSerializer::class)
data class LocalTime(val hour: Int, val minute: Int, val second: Int) : Comparable<LocalTime> {
    init {
        require(hour in 0..23) { "hour: expected 0..23 but was $hour" }
        require(minute in 0..59) { "minute: expected 0..59 but was $minute" }
        require(second in 0..60) { "second: expected 0..60 but was $second" }
    }
    val secondsSinceMidnight: Int get() = ((hour * 60) + minute) * 60 + second
    override fun compareTo(other: LocalTime): Int = secondsSinceMidnight.compareTo(other.secondsSinceMidnight)

    /**
     * Formats the date+time in the form of `HH:mm:ss`.
     */
    fun format(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}:${second.toString().padStart(2, '0')}"

    companion object {
        fun now(): LocalTime = LocalDateTime.now().time
        val MIDNIGHT = LocalTime(0, 0, 0)
        val MIN = MIDNIGHT
        private val FORMAT_PATTERN = Regex("(\\d\\d):(\\d\\d):(\\d\\d)")

        /**
         * Parses the value produced by [format].
         * @param formatted in the form of `HH:mm:ss`.
         */
        fun parse(formatted: String): LocalTime {
            val result = FORMAT_PATTERN.matchEntire(formatted)
            requireNotNull(result) { "parsed string must be in the format of HH:mm:ss but got $formatted" }
            return LocalTime(result.groupValues[1].toInt(), result.groupValues[2].toInt(), result.groupValues[3].toInt())
        }
    }
}

@Serializable(with = LocalDateTimeSerializer::class)
data class LocalDateTime(val date: LocalDate, val time: LocalTime) : Comparable<LocalDateTime> {
    override fun compareTo(other: LocalDateTime): Int = compareValuesBy(this, other, { it.date }, { it.time })

    /**
     * Formats the date+time in the form of `yyyy-MM-dd HH:mm:ss`.
     */
    fun format(): String = "${date.format()} ${time.format()}"
    companion object {
        /**
         * Returns the current date+time in user's local time zone.
         */
        fun now(): LocalDateTime {
            // time returns number of seconds since Epoch, 1970-01-01 00:00:00 +0000 (UTC). -1 or negative value means error.
            val t: Long = checkNativeNonNegativeLong("time") { time(null) }
            // localtime() returns a pointer to static data and hence is not thread-safe. NULL means error.
            val tm: tm = checkNativeNotNull("localtime") { localtime(cValuesOf(t)) } .pointed

            val date = LocalDate(tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday)
            val time = LocalTime(tm.tm_hour, tm.tm_min, tm.tm_sec)
            return LocalDateTime(date, time)
        }

        private val FORMAT_PATTERN = Regex("(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d) (\\d\\d):(\\d\\d):(\\d\\d)")
        /**
         * Parses the value produced by [format].
         * @param formatted in the form of `yyyy-MM-dd HH:mm:ss`.
         */
        fun parse(formatted: String): LocalDateTime {
            val result = FORMAT_PATTERN.matchEntire(formatted)
            requireNotNull(result) { "parsed string must be in the format of yyyy-MM-dd HH:mm:ss but got $formatted" }
            val date = LocalDate(
                result.groupValues[1].toInt(),
                result.groupValues[2].toInt(),
                result.groupValues[3].toInt()
            )
            val time = LocalTime(result.groupValues[4].toInt(), result.groupValues[5].toInt(), result.groupValues[6].toInt())
            return LocalDateTime(date, time)
        }
    }
}

enum class ZoneId {
    UTC
}

@Serializable(with = ZonedDateTimeSerializer::class)
data class ZonedDateTime(val dateTime: LocalDateTime, val zone: ZoneId) {
    /**
     * Formats the date+time in RFC 3339 `yyyy-MM-ddTHH:mm:ssZ`.
     */
    fun format(): String = "${dateTime.date.format()}T${dateTime.time.format()}Z"

    companion object {
        /**
         * Returns the current date+time in user local time zone, or alternatively in UTC if [utc] is true.
         */
        fun now(zone: ZoneId = ZoneId.UTC): ZonedDateTime {
            // time returns number of seconds since Epoch, 1970-01-01 00:00:00 +0000 (UTC). -1 or negative value means error.
            val t: Long = checkNativeNonNegativeLong("time") { time(null) }
            // localtime() returns a pointer to static data and hence is not thread-safe. NULL means error.
            val tm: tm = checkNativeNotNull("gmtime") { gmtime(cValuesOf(t)) } .pointed

            val date = LocalDate(tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday)
            val time = LocalTime(tm.tm_hour, tm.tm_min, tm.tm_sec)
            return ZonedDateTime(LocalDateTime(date, time), zone)
        }

        private val FORMAT_PATTERN = Regex("(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d)T(\\d\\d):(\\d\\d):(\\d\\d)Z")
        /**
         * Parses the value produced by [format].
         * @param formatted in the form of RFC 3339 `yyyy-MM-ddTHH:mm:ssZ`.
         */
        fun parse(formatted: String): ZonedDateTime {
            val result = FORMAT_PATTERN.matchEntire(formatted)
            requireNotNull(result) { "parsed string must be in the format of yyyy-MM-ddTHH:mm:ssZ but got $formatted" }
            val date = LocalDate(
                result.groupValues[1].toInt(),
                result.groupValues[2].toInt(),
                result.groupValues[3].toInt()
            )
            val time = LocalTime(result.groupValues[4].toInt(), result.groupValues[5].toInt(), result.groupValues[6].toInt())
            return ZonedDateTime(LocalDateTime(date, time), ZoneId.UTC)
        }
    }
}

object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.format())
    }
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.format())
    }
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.format())
    }
}

object ZonedDateTimeSerializer : KSerializer<ZonedDateTime> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("ZonedDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ZonedDateTime = ZonedDateTime.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: ZonedDateTime) {
        encoder.encodeString(value.format())
    }
}

/**
 * An instant in time. Only delta between two subsequent instants makes sense.
 * @property millis current system time in milliseconds since certain moment in the past,
 * only delta between two instants makes sense.
*/
value class Instant private constructor(private val millis: Long) : Comparable<Instant> {
    override fun compareTo(other: Instant): Int = this.millis.compareTo(other.millis)

    /**
     * Returns the number of milliseconds between two instants.
     */
    operator fun minus(other: Instant): Duration = (millis - other.millis).milliseconds

    companion object {
        fun now(): Instant = Instant(getTimeMillis())
    }
}

fun String.toLocalTime() = LocalTime.parse(this)
fun String.toLocalDate() = LocalDate.parse(this)
fun String.toLocalDateTime() = LocalDateTime.parse(this)
fun String.toZonedDateTime() = ZonedDateTime.parse(this)
