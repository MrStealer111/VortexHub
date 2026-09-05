package com.vpnmobile.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Crypto {
    private const val ALIAS = "VortexHubKey"
    private const val TRANS = "AES/GCM/NoPadding"

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getKey(ALIAS, null)?.let { return it as SecretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        return kg.generateKey()
    }

    fun encrypt(plain: String): String = try {
        val cipher = Cipher.getInstance(TRANS)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv
        val enc = cipher.doFinal(plain.toByteArray())
        val combined = iv + enc
        Base64.encodeToString(combined, Base64.NO_WRAP)
    } catch (_: Exception) { plain }

    fun decrypt(encStr: String): String = try {
        val combined = Base64.decode(encStr, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val enc = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance(TRANS)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(enc))
    } catch (_: Exception) { encStr }
}
