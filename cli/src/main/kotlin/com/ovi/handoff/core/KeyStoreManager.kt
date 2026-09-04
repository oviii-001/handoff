package com.ovi.handoff.core

import java.io.File
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class KeyStoreManager(private val keyDir: File) {
    private val pubFile = File(keyDir, "device.pub")
    private val privFile = File(keyDir, "device.priv")

    fun getOrGenerateKeyPair(): KeyPair {
        if (pubFile.exists() && privFile.exists()) {
            return loadKeyPair()
        }
        return generateAndSaveKeyPair()
    }

    private fun generateAndSaveKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        val kp = kpg.generateKeyPair()
        
        keyDir.mkdirs()
        pubFile.writeBytes(kp.public.encoded)
        privFile.writeBytes(kp.private.encoded)
        
        return kp
    }

    private fun loadKeyPair(): KeyPair {
        val kf = KeyFactory.getInstance("Ed25519")
        val pubKey = kf.generatePublic(X509EncodedKeySpec(pubFile.readBytes()))
        val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privFile.readBytes()))
        return KeyPair(pubKey, privKey)
    }

    fun sign(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val sig = Signature.getInstance("Ed25519")
        sig.initSign(privateKey)
        sig.update(data)
        return sig.sign()
    }
}
