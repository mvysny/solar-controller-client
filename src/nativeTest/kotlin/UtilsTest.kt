@file:OptIn(ExperimentalUnsignedTypes::class)

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
    }

    @Test
    fun testGetUShortHiLoAt() {
        var bytes = byteArrayOf(0, 1, 2, 3, 4)
        expect(1.toUShort()) { bytes.getUShortHiLoAt(0) }
        expect(0x0102.toUShort()) { bytes.getUShortHiLoAt(1) }
        bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte())
        expect(0xFFFE.toUShort()) { bytes.getUShortHiLoAt(0) }
        expect(0xFEFD.toUShort()) { bytes.getUShortHiLoAt(1) }
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
