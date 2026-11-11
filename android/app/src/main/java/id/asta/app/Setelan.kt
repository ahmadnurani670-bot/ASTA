package id.asta.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Pilihan pengguna. Kunci API disimpan terenkripsi dengan Android Keystore. */
object Setelan {
    const val MODEL_CHAT = "gemini-3.5-flash-lite"
    const val MODEL_KUAT = "gemini-3.8-flash"

    val MODE = linkedMapOf(
        "Standar" to "Mode standar: seimbang, jelas, dan ringkas.",
        "Singkat" to "Mode singkat: jawab dalam satu kalimat pendek saja.",
        "Guru" to "Mode guru: jelaskan konsep langkah demi langkah dengan contoh sederhana, lalu tutup dengan satu pertanyaan latihan.",
        "Santai" to "Mode santai: bicara akrab seperti teman dekat, boleh bercanda ringan.",
        "Fokus" to "Mode fokus: langsung ke inti tanpa basa-basi, berikan hanya jawaban dan langkah tindakan yang perlu.",
    )

    private fun p(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("setelan_asta", Context.MODE_PRIVATE)

    fun teks(ctx: Context, k: String, d: String = ""): String = p(ctx).getString(k, d) ?: d

    fun simpanTeks(ctx: Context, k: String, v: String) {
        p(ctx).edit().putString(k, v).apply()
    }

    fun bool(ctx: Context, k: String, d: Boolean): Boolean = p(ctx).getBoolean(k, d)

    fun simpanBool(ctx: Context, k: String, v: Boolean) {
        p(ctx).edit().putBoolean(k, v).apply()
    }

    fun laju(ctx: Context): Float = (teks(ctx, "laju", "1.0").toFloatOrNull() ?: 1.0f).coerceIn(0.5f, 2.0f)

    fun modelChat(ctx: Context): String = teks(ctx, "model_chat", MODEL_CHAT).ifBlank { MODEL_CHAT }

    fun modelKuat(ctx: Context): String = teks(ctx, "model_kuat", MODEL_KUAT).ifBlank { MODEL_KUAT }

    fun kunciApi(ctx: Context): String {
        val s = teks(ctx, "kunci_enc")
        if (s.isEmpty()) return ""
        return try { Rahasia.buka(s) } catch (e: Exception) { "" }
    }

    fun simpanKunciApi(ctx: Context, k: String) {
        simpanTeks(ctx, "kunci_enc", if (k.isBlank()) "" else Rahasia.kunci(k.trim()))
    }
}

private object Rahasia {
    private const val ALIAS = "asta_kunci"

    private fun kunciAes(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val ada = ks.getKey(ALIAS, null)
        if (ada is SecretKey) return ada
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return g.generateKey()
    }

    fun kunci(teks: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, kunciAes())
        val iv = c.iv
        val ct = c.doFinal(teks.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    fun buka(s: String): String {
        val b = Base64.decode(s, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, kunciAes(), GCMParameterSpec(128, b, 0, 12))
        return String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8)
    }
}
