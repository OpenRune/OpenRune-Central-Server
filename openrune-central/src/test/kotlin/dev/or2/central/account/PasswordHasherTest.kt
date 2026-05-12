package dev.or2.central.account

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordHasherTest {
    private val hasher = PasswordHasher()

    @Test
    fun argon2RoundTrip() {
        val stored = hasher.hash("my-secret")
        assertTrue(stored.startsWith("\$argon2"))
        assertTrue(hasher.verify(stored, "my-secret"))
        assertFalse(hasher.verify(stored, "wrong"))
    }
}
