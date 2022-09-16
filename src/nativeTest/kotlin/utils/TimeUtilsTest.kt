package utils

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
