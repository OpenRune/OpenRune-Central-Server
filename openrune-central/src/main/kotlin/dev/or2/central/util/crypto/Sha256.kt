package dev.or2.central.util.crypto

import java.security.MessageDigest

fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var r = 0
    for (i in a.indices) {
        r = r or (a[i].toInt() xor b[i].toInt())
    }
    return r == 0
}

