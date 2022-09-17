package utils

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.expect

class TimeUtilsTest {
    @Test
    fun testSleepMillis() {
        sleepMillis(1L)
    }
}

class LocalDateTest {
    @Test
    fun format() {
        expect("2022-01-01") { LocalDate(2022, 1, 1).format() }
        expect("1995-12-25") { LocalDate(1995, 12, 25).format() }
    }

    @Test
    fun parse() {
        expect(LocalDate(2022, 1, 1)) { LocalDate.parse("2022-01-01") }
        expect(LocalDate(1995, 12, 25)) { LocalDate.parse("1995-12-25") }
    }

    @Test
    fun testFromToJson() {
        expect(LocalDate(2022, 12, 31)) { Json.decodeFromString("\"2022-12-31\"") }
        expect("\"2022-12-31\"") { Json.encodeToString(LocalDate(2022, 12, 31)) }
    }

    @Test
    fun testToday() {
        LocalDate.today()
    }

    @Test
    fun compare() {
        expect(true) { LocalDate(2022, 1, 1) > LocalDate(1995, 12, 25) }
    }
}

class LocalDateTimeTest {
    @Test
    fun localDateTimeNow() {
        LocalDateTime.now()
    }
}

class LocalTimeTest {
    @Test
    fun format() {
        expect("00:00:00") { LocalTime.MIDNIGHT.format() }
        expect("01:23:45") { LocalTime(1, 23, 45).format() }
        expect("23:01:09") { LocalTime(23, 1, 9).format() }
    }

    @Test
    fun testParse() {
        expect(LocalTime(1, 23, 45)) { LocalTime.parse("01:23:45") }
        expect(LocalTime.MIDNIGHT) { LocalTime.parse("00:00:00") }
        expect(LocalTime(23, 1, 9)) { LocalTime.parse("23:01:09") }
    }

    @Test
    fun testFromToJson() {
        expect(LocalTime(1, 23, 45)) { Json.decodeFromString("\"01:23:45\"") }
        expect("\"01:23:45\"") { Json.encodeToString(LocalTime(1, 23, 45)) }
    }

    @Test
    fun testSecondsSinceMidnight() {
        expect(0) { LocalTime.MIDNIGHT.secondsSinceMidnight }
        expect(5025) { LocalTime(1, 23, 45).secondsSinceMidnight }
        expect(23 * 60 * 60 + 60 + 9) { LocalTime(23, 1, 9).secondsSinceMidnight }
    }

    @Test
    fun compare() {
        expect(true) { LocalTime.MIDNIGHT < LocalTime(1, 23, 45) }
        expect(true) { LocalTime.MIDNIGHT < LocalTime(23, 1, 9) }
        expect(true) { LocalTime(1, 23, 45) < LocalTime(23, 1, 9) }
        expect(true) { LocalTime(1, 23, 45) <= LocalTime(1, 23, 45) }
    }
}
