package utils

import kotlinx.cinterop.cValue
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.pointed
import kotlinx.cinterop.staticCFunction
import platform.posix.*
import kotlin.system.getTimeMillis

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
