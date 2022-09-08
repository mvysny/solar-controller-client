import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsTest {
    @Test
    fun testToHex() {
        assertEquals("00", 0.toByte().toHex())
        assertEquals("ff", 0xFF.toByte().toHex())
        assertEquals("0f", 0x0F.toByte().toHex())
        assertEquals("a0", 0xA0.toByte().toHex())
    }
}
