package dev.or2.central.account

import de.mkammerer.argon2.Argon2
import de.mkammerer.argon2.Argon2Factory

class PasswordHasher {
    private val argon2: Argon2 by lazy { Argon2Factory.create() }

    fun verify(
        storedHash: String,
        plainPassword: String,
    ): Boolean {
        val h = storedHash.trim()
        if (h.isEmpty()) return false
        val pw = plainPassword.toCharArray()
        return try {
            argon2.verify(h, pw)
        } catch (_: IllegalArgumentException) {
            false
        } finally {
            argon2.wipeArray(pw)
        }
    }

    fun hash(plainPassword: String): String {
        val pw = plainPassword.toCharArray()
        return try {
            argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KIB, ARGON2_PARALLELISM, pw)
        } finally {
            argon2.wipeArray(pw)
        }
    }

    private companion object {
        private const val ARGON2_ITERATIONS = 20
        private const val ARGON2_MEMORY_KIB = 65536
        private const val ARGON2_PARALLELISM = 1
    }
}