package dev.openrune.central.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object Ed25519 {
    init {
        // Java 11 doesn't ship Ed25519 in the default providers; use BouncyCastle.
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val b64d = Base64.getUrlDecoder()

    data class KeyPairStrings(
        val publicKey: String,
        val privateKey: String
    )

    fun generateKeyPair(): KeyPairStrings {
        val kpg = KeyPairGenerator.getInstance("Ed25519", "BC")
        val kp = kpg.generateKeyPair()
        return KeyPairStrings(
            publicKey = b64.encodeToString(kp.public.encoded),   // X.509
            privateKey = b64.encodeToString(kp.private.encoded)  // PKCS8
        )
    }

    fun sign(privateKeyB64: String, data: ByteArray): String {
        try {
            val privateKey = decodePrivateKey(privateKeyB64)
            val sig = Signature.getInstance("Ed25519", "BC")
            sig.initSign(privateKey)
            sig.update(data)
            return b64.encodeToString(sig.sign())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Ed25519 private key/sign input: ${e.message}", e)
        }
    }

    fun verify(publicKeyB64: String, data: ByteArray, signatureB64: String): Boolean {
        return try {
            val publicKey = decodePublicKey(publicKeyB64)
            val sig = Signature.getInstance("Ed25519", "BC")
            sig.initVerify(publicKey)
            sig.update(data)
            sig.verify(b64d.decode(signatureB64))
        } catch (_: Exception) {
            false
        }
    }

    private fun decodePublicKey(publicKeyB64: String): PublicKey {
        val bytes = b64d.decode(publicKeyB64)
        val spec = X509EncodedKeySpec(bytes)
        return KeyFactory.getInstance("Ed25519", "BC").generatePublic(spec)
    }

    private fun decodePrivateKey(privateKeyB64: String): PrivateKey {
        val bytes = b64d.decode(privateKeyB64)
        val spec = PKCS8EncodedKeySpec(bytes)
        return KeyFactory.getInstance("Ed25519", "BC").generatePrivate(spec)
    }
}

