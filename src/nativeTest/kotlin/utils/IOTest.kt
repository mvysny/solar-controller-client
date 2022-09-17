package utils

import kotlin.test.Test
import kotlin.test.expect

class FileTest {
    @Test
    fun testExists() {
        expect(true) { File("build.gradle.kts").exists() }
        expect(false) { File("foo").exists() }
    }
}
