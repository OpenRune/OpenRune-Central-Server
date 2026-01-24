package dev.openrune.central.crypto

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ed25519Test {
    @Test
    fun `sign-verify roundtrip works`() {
        val kp = Ed25519.generateKeyPair()
        val msg = "hello".encodeToByteArray()
        val sig = Ed25519.sign(kp.privateKey, msg)
        assertTrue(Ed25519.verify(kp.publicKey, msg, sig))
    }

    @Test
    fun `verify fails with wrong public key`() {
        val kp1 = Ed25519.generateKeyPair()
        val kp2 = Ed25519.generateKeyPair()
        val msg = "hello".encodeToByteArray()
        val sig = Ed25519.sign(kp1.privateKey, msg)
        assertFalse(Ed25519.verify(kp2.publicKey, msg, sig))
    }

    @Test
    fun `verify fails with modified message`() {
        val kp = Ed25519.generateKeyPair()
        val msg = "hello".encodeToByteArray()
        val sig = Ed25519.sign(kp.privateKey, msg)
        val modified = "hell0".encodeToByteArray()
        assertFalse(Ed25519.verify(kp.publicKey, modified, sig))
    }

    @Test
    fun `verify fails with malformed inputs`() {
        val kp = Ed25519.generateKeyPair()
        val msg = "hello".encodeToByteArray()
        val sig = Ed25519.sign(kp.privateKey, msg)
        assertFalse(Ed25519.verify("not-a-key", msg, sig))
        assertFalse(Ed25519.verify(kp.publicKey, msg, "not-a-sig"))
    }
}

