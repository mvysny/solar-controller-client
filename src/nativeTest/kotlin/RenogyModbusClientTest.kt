import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect
import kotlin.test.fail

class RenogyModbusClientTest {
    @Test
    fun readRegister000ANormalResponse() {
        val buffer = Buffer()
        buffer.toReturn.addAll(listOf(1, 3, 2, 0x18, 0x1E, 0x32, 0x4c))
        val client = RenogyModbusClient(buffer)
        val response = client.readRegister(0x0A.toUShort(), 0x01.toUShort())
        buffer.expectWrittenBytes("0103000a0001a408")
        expect("181e") { response.toHex() }
    }

    @Test
    fun readRegister000AErrorResponse() {
        val buffer = Buffer()
        buffer.toReturn.addAll(listOf(1, 0x83.toByte(), 2, 0xc0.toByte(), 0xf1.toByte()))
        val client = RenogyModbusClient(buffer)
        try {
            client.readRegister(0x0A.toUShort(), 0x01.toUShort())
            fail("Expected to fail with RenogyException")
        } catch (e: RenogyException) {
            // okay
            expect("0x02: PDU start address is not correct or PDU start address + data length") {
                e.message
            }
        }
        buffer.expectWrittenBytes("0103000a0001a408")
    }

    @Test
    fun readRegister000CNormalResponse() {
        val buffer = Buffer()
        buffer.toReturn.addAll(listOf(1, 3, 0x10, 0x20, 0x20, 0x20, 0x20, 0x4D, 0x54, 0x34, 0x38, 0x33, 0x30, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0xEE.toByte(), 0x98.toByte()))
        val client = RenogyModbusClient(buffer)
        val response = client.readRegister(0x0C.toUShort(), 0x08.toUShort())
        buffer.expectWrittenBytes("0103000c0008840f")
        expect("202020204d5434383330202020202020") { response.toHex() }
    }

    @Test
    fun testToAsciiString() {
        expect("    MT4830      ") {
            byteArrayOf(0x20, 0x20, 0x20, 0x20, 0x4D, 0x54, 0x34, 0x38, 0x33, 0x30, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20).toAsciiString()
        }
        expect("") { byteArrayOf().toAsciiString() }
    }
}
