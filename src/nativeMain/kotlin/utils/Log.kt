package utils

enum class LogLevel {
    DEBUG, INFO, WARN, ERR
}

interface Log {
    fun debug(msg: String, ex: Throwable? = null) {
        log(LogLevel.DEBUG, msg, ex)
    }
    fun info(msg: String, ex: Throwable? = null) {
        log(LogLevel.INFO, msg, ex)
    }
    fun warn(msg: String, ex: Throwable? = null) {
        log(LogLevel.WARN, msg, ex)
    }
    fun err(msg: String, ex: Throwable? = null) {
        log(LogLevel.ERR, msg, ex)
    }
    fun log(level: LogLevel, msg: String, ex: Throwable?)

    companion object {
        var minLevel: LogLevel = LogLevel.INFO

        /**
         * @param tag who's logging. Almost always the class name.
         */
        fun get(tag: String): Log = StderrLog(tag)
    }
}

/**
 * @param tag who's logging. Almost always the class name.
 */
class StderrLog(val tag: String) : Log {
    override fun log(level: LogLevel, msg: String, ex: Throwable?) {
        if (level >= Log.minLevel) {
            StderrIO.writeln(buildString {
                append(LocalDateTime.now().format())
                append(" [").append(level).append("] ").append(tag).append(": ")
                    .append(msg)
            })
            ex?.printStackTrace()
        }
    }
}
