package utils

import kotlin.test.Test
import kotlin.test.expect

class FileTest {
    @Test
    fun testExists() {
        expect(true) { File("build.gradle.kts").exists() }
        expect(false) { File("foo").exists() }
    }

    @Test
    fun testWriteReadFully() {
        val f = File.temp()
        f.writeTextUTF8("Hello, world!")
        expect("Hello, world!") { f.readTextUTF8() }
    }

    @Test
    fun testFileSize() {
        val f = File.temp()
        f.writeTextUTF8("Hello, world!")
        expect(13) { f.getSize().toInt() }
    }
}

class IOTest {
    @Test
    fun testReadFullyInt() {
        val buffer = Buffer(1)
        buffer.toReturn.addAll("010302181e324c")
        val read = buffer.readFully(7)
        expect("010302181e324c") { read.toHex() }
    }

    @Test
    fun testReadFullyZeroOffset() {
        val buffer = Buffer(1)
        buffer.toReturn.addAll(listOf(1, 3, 2, 0x18, 0x1E, 0x32, 0x4c))
        val bytes = ByteArray(7) { 0 }
        buffer.readFully(bytes)
        expect(listOf<Byte>(1, 3, 2, 0x18, 0x1E, 0x32, 0x4c)) { bytes.toList() }
    }

    @Test
    fun testReadFullyNonzeroOffset() {
        val buffer = Buffer(1)
        buffer.toReturn.addAll(listOf(1, 3, 2, 0x18, 0x1E, 0x32, 0x4c))
        val bytes = ByteArray(7) { 0 }
        buffer.readFully(bytes, 2, 4)
        expect(listOf<Byte>(0, 0, 1, 3, 2, 0x18, 0)) { bytes.toList() }
    }

    @Test
    fun testWriteFullyZeroOffset() {
        val buffer = Buffer(1)
        buffer.writeFully(byteArrayOf(1, 3, 2, 0x18, 0x1E, 0x32, 0x4c))
        expect(listOf<Byte>(1, 3, 2, 0x18, 0x1E, 0x32, 0x4c)) { buffer.writtenBytes }
    }

    @Test
    fun testWriteFullyNonzeroOffset() {
        val buffer = Buffer(1)
        buffer.writeFully(byteArrayOf(1, 3, 2, 0x18, 0x1E, 0x32, 0x4c), 2, 4)
        expect(listOf<Byte>(2, 0x18, 0x1E, 0x32)) { buffer.writtenBytes }
    }
}
