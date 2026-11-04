package com.itz.compartido.data.local

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted PBKDF2 credentials for local authentication; never persist the entered password. */
internal object PasswordHasher {
    private const val ITERATIONS = 210_000

    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return listOf("pbkdf2", ITERATIONS.toString(), encode(salt),
            encode(derive(password, salt, ITERATIONS))).joinToString(":")
    }

    fun verify(password: String, stored: String): Boolean = try {
        val parts = stored.split(":")
        parts.size == 4 && parts[0] == "pbkdf2" &&
            parts[1].toInt() in 100_000..1_000_000 &&
            MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]),
                derive(password, Base64.getDecoder().decode(parts[2]), parts[1].toInt()))
    } catch (_: IllegalArgumentException) { false }

    private fun encode(value: ByteArray) = Base64.getEncoder().encodeToString(value)
    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
