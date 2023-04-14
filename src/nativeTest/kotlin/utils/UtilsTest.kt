package utils

import kotlin.test.Test
import kotlin.test.expect

class UtilsTest {
    @Test
    fun testToHex() {
        expect("00") { 0.toByte().toHex() }
        expect("ff") { 0xFF.toByte().toHex() }
        expect("0f") { 0x0F.toByte().toHex() }
        expect("a0") { 0xA0.toByte().toHex() }
        expect("0000") { 0.toUShort().toHex() }
        expect("00ff") { 0xff.toUShort().toHex() }
        expect("ffff") { 0xffff.toUShort().toHex() }
        expect("ff00") { 0xff00.toUShort().toHex() }
        expect("0f0f") { 0x0f0f.toUShort().toHex() }
        expect("0103140070008400d80000000a00000608081000700084ebde") {
            byteArrayOf(1, 3, 20, 0, 0x70, 0, 0x84.toByte(), 0, 0xd8.toByte(), 0, 0, 0, 10, 0, 0, 6, 8, 8, 0x10, 0, 0x70, 0, 0x84.toByte(), 0xeb.toByte(), 0xde.toByte()).toHex()
        }
    }

    @Test
    fun testFromHex() {
        expect("01031a0070008400d80000000a0000060808") {
            "01031a0070008400d80000000a0000060808".fromHex().toHex()
        }
    }

    @Test
    fun testHibyte() {
        expect(0.toByte()) { 0.toUShort().hibyte }
        expect(0.toByte()) { 1.toUShort().hibyte }
        expect(0.toByte()) { 0x80.toUShort().hibyte }
        expect(0.toByte()) { 0xFF.toUShort().hibyte }
        expect(1.toByte()) { 0x100.toUShort().hibyte }
        expect(1.toByte()) { 0x180.toUShort().hibyte }
        expect(0x80.toByte()) { 0x8000.toUShort().hibyte }
        expect(0x80.toByte()) { 0x8080.toUShort().hibyte }
        expect(0x80.toByte()) { 0x80FF.toUShort().hibyte }
        expect(0xFF.toByte()) { 0xFFFF.toUShort().hibyte }
    }

    @Test
    fun testLobyte() {
        expect(0.toByte()) { 0.toUShort().lobyte }
        expect(1.toByte()) { 1.toUShort().lobyte }
        expect(0x80.toByte()) { 0x80.toUShort().lobyte }
        expect(0xff.toByte()) { 0xFF.toUShort().lobyte }
        expect(0.toByte()) { 0x100.toUShort().lobyte }
        expect(0x80.toByte()) { 0x180.toUShort().lobyte }
        expect(0.toByte()) { 0x8000.toUShort().lobyte }
        expect(0x80.toByte()) { 0x8080.toUShort().lobyte }
        expect(0xff.toByte()) { 0x80FF.toUShort().lobyte }
        expect(0xFF.toByte()) { 0xFFFF.toUShort().lobyte }
    }

    @Test
    fun testGetUShortHiLoAt() {
        var bytes = byteArrayOf(0, 1, 2, 3, 4)
        expect(1.toUShort()) { bytes.getUShortHiLoAt(0) }
        expect(1f) { bytes.getUShortHiLoAt(0).toFloat() }
        expect(0.1f) { bytes.getUShortHiLoAt(0).toFloat() / 10 }
        expect(0x0102.toUShort()) { bytes.getUShortHiLoAt(1) }
        bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte())
        expect(0xFFFE.toUShort()) { bytes.getUShortHiLoAt(0) }
        expect(0xFEFD.toUShort()) { bytes.getUShortHiLoAt(1) }
    }

    @Test
    fun testSetUShortHiLoAt() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4)
        bytes.setUShortHiLoAt(0, 0xdead.toUShort())
        expect("dead020304") { bytes.toHex() }
    }

    @Test
    fun testGetUIntHiLoAt() {
        var bytes = byteArrayOf(0, 1, 2, 3, 4)
        expect(0x00010203.toUInt()) { bytes.getUIntHiLoAt(0) }
        expect(0x01020304.toUInt()) { bytes.getUIntHiLoAt(1) }
        bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte(), 0xFC.toByte())
        expect(0xFFFEFDFC.toUInt()) { bytes.getUIntHiLoAt(0) }
    }

    @Test
    fun testToAsciiString() {
        expect("    MT4830      ") {
            byteArrayOf(0x20, 0x20, 0x20, 0x20, 0x4D, 0x54, 0x34, 0x38, 0x33, 0x30, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20).toAsciiString()
        }
        expect("") { byteArrayOf().toAsciiString() }
    }
}
