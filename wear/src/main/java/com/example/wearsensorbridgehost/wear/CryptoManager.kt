package com.example.wearsensorbridgehost.wear

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val encryptCipher = Cipher.getInstance(TRANSFORMATION)
    private val decryptCipher = Cipher.getInstance(TRANSFORMATION)

    fun encrypt(data: ByteArray): ByteArray {
        encryptCipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = encryptCipher.iv
        val encrypted = encryptCipher.doFinal(data)
        // Prepend IV to the encrypted data for decryption later
        return iv + encrypted
    }

    fun decrypt(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, 12) // GCM IV length is 12 bytes
        val encrypted = data.copyOfRange(12, data.size)
        decryptCipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        return decryptCipher.doFinal(encrypted)
    }

    private fun getKey(): SecretKey {
        val existingKey = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    private fun createKey(): SecretKey {
        return KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    companion object {
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        private const val ALIAS = "secret_sensor_key"
    }
}
