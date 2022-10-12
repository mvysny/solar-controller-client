import clients.dummyRenogyData
import utils.File
import kotlin.test.Test
import kotlin.test.expect

class SqliteDataLoggerTest {
    @Test
    fun smoke() {
        val file = File.temp("sqlite", ".db")
        SqliteDataLogger(file).init()
        expect(true) { file.exists() } // check that the database file has been created.
    }

    @Test
    fun insertOneRecord() {
        val file = File.temp("sqlite", ".db")
        val logger = SqliteDataLogger(file)
        logger.init()
        logger.append(dummyRenogyData)
        expect(true) { file.exists() } // check that the database file has been created.
    }
}
