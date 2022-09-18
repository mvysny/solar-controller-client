import utils.sleepMillis
import kotlin.test.Test

class DummyRenogyClientTest {
    @Test
    fun smoke() {
        val client = DummyRenogyClient(false)
        client.getAllData()
        client.getAllData()
        sleepMillis(10)
        client.getAllData()
    }
}