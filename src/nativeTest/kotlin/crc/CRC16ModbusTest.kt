package crc

import kotlin.test.Test
import kotlin.test.assertEquals

class CRC16ModbusTest {
    @Test
    fun testEmptyArray() {
        assertEquals(listOf(0xFF.toByte(), 0xFF.toByte()), CRC16Modbus().crcBytes.toList())
    }

    @Test
    fun testSimpleArray() {
        val crc = CRC16Modbus()
        crc.update(byteArrayOf(1.toByte(), 3.toByte(), 0.toByte(), 0x0a.toByte(), 0.toByte(), 1.toByte()))
        assertEquals(listOf(0xA4.toByte(), 0x08.toByte()), crc.crcBytes.toList())
    }

    @Test
    fun testSimpleArray2() {
        val crc = CRC16Modbus()
        crc.update(byteArrayOf(1.toByte(), 3.toByte(), 2.toByte(), 0x18.toByte(), 0x14.toByte()))
        assertEquals(listOf(0xb2.toByte(), 0x4b.toByte()), crc.crcBytes.toList())
    }
}
