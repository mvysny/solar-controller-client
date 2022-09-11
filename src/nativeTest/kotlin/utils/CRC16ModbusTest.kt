package utils

import kotlin.test.Test
import kotlin.test.expect

class CRC16ModbusTest {
    @Test
    fun testEmptyArray() {
        expect(0xFFFF.toUShort()) { CRC16Modbus().crc }
    }

    @Test
    fun testSimpleArray() {
        val crc = CRC16Modbus()
        crc.update(byteArrayOf(1.toByte(), 3.toByte(), 0.toByte(), 0x0a.toByte(), 0.toByte(), 1.toByte()))
        expect(listOf(0xA4.toByte(), 0x08.toByte())) { crc.crcBytes.toList() }
        expect(0x08A4.toUShort()) { crc.crc }
    }

    @Test
    fun testSimpleArray2() {
        val crc = CRC16Modbus()
        crc.update(byteArrayOf(1.toByte(), 3.toByte(), 2.toByte(), 0x18.toByte(), 0x14.toByte()))
        expect(listOf(0xb2.toByte(), 0x4b.toByte())) { crc.crcBytes.toList() }
        expect(0x4BB2.toUShort()) { crc.crc }
    }
}
