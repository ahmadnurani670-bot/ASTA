package id.asta.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SandiSalah : Exception("Kata sandi enkripsi tidak cocok dengan data yang tersimpan.")

/** Enkripsi AES-256-GCM dengan kunci dari kata sandi (PBKDF2). Isi Gist tidak bisa dibaca tanpa kata sandi. */
private object Sandi {
    private fun kunci(sandi: String, garam: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(sandi.toCharArray(), garam, 120_000, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    private fun acak(n: Int): ByteArray {
        val b = ByteArray(n)
        SecureRandom().nextBytes(b)
        return b
    }

    fun kunciTeks(teks: String, sandi: String): String {
        val garam = acak(16)
        val iv = acak(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, kunci(sandi, garam), GCMParameterSpec(128, iv))
        val ct = c.doFinal(teks.toByteArray(Charsets.UTF_8))
        return JSONObject().put("v", 1)
            .put("s", Base64.encodeToString(garam, Base64.NO_WRAP))
            .put("i", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("c", Base64.encodeToString(ct, Base64.NO_WRAP))
            .toString()
    }

    fun bukaTeks(paket: String, sandi: String): String {
        val o = JSONObject(paket)
        val garam = Base64.decode(o.getString("s"), Base64.NO_WRAP)
        val iv = Base64.decode(o.getString("i"), Base64.NO_WRAP)
        val ct = Base64.decode(o.getString("c"), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, kunci(sandi, garam), GCMParameterSpec(128, iv))
        try {
            return String(c.doFinal(ct), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw SandiSalah()
        }
    }
}

/** Sinkronisasi antar HP lewat Gist rahasia di akun GitHub pengguna, dienkripsi dengan kata sandi pengguna. */
object Sinkron {
    private const val DESKRIPSI = "Asta sinkron (terenkripsi)"
    private const val BERKAS = "asta-sinkron.json"
    private const val API = "https://api.github.com"

    private val pelaksana = Executors.newSingleThreadExecutor()
    private val utama = Handler(Looper.getMainLooper())
    private var app: Context? = null
    @Volatile var status = ""
    @Volatile private var terakhirMulai = 0L

    private val tugas = Runnable {
        val a = app
        if (a != null) jalankan(a, null)
    }

    fun aktif(ctx: Context): Boolean =
        Setelan.rahasia(ctx, "gh_token").isNotEmpty() && Setelan.rahasia(ctx, "gh_sandi").isNotEmpty()

    /** Menjadwalkan sinkron sebentar lagi; beberapa perubahan beruntun digabung jadi satu. */
    fun jadwalkan(ctx: Context, tundaMs: Long = 5000L) {
        val a = ctx.applicationContext
        if (!aktif(a)) return
        app = a
        utama.removeCallbacks(tugas)
        utama.postDelayed(tugas, tundaMs)
    }

    fun putuskan(ctx: Context) {
        Setelan.simpanRahasia(ctx, "gh_token", "")
        Setelan.simpanRahasia(ctx, "gh_sandi", "")
        Setelan.simpanTeks(ctx, "gist_id", "")
        status = "Sinkronisasi diputuskan."
    }

    /** Menjalankan sinkron di thread latar. saatSelesai dipanggil di thread utama. */
    fun jalankan(ctx: Context, saatSelesai: ((String) -> Unit)?) {
        val a = ctx.applicationContext
        if (!aktif(a)) {
            saatSelesai?.invoke("Isi token GitHub dan kata sandi enkripsi dulu.")
            return
        }
        pelaksana.execute {
            terakhirMulai = System.currentTimeMillis()
            val hasil = sinkronkan(a)
            status = hasil
            if (saatSelesai != null) utama.post { saatSelesai(hasil) }
        }
    }

    private fun sinkronkan(ctx: Context): String {
        try {
            val token = Setelan.rahasia(ctx, "gh_token")
            val sandi = Setelan.rahasia(ctx, "gh_sandi")
            var id = Setelan.teks(ctx, "gist_id")
            if (id.isEmpty()) id = cariGist(token) ?: ""

            var jauh: JSONObject? = null
            if (id.isNotEmpty()) {
                val isi = bacaGist(token, id)
                if (isi != null) jauh = JSONObject(Sandi.bukaTeks(isi, sandi))
            }
            val lokal = ekspor(ctx)
            val gabung = if (jauh != null) gabung(lokal, jauh) else lokal
            terapkan(ctx, gabung)

            val paket = Sandi.kunciTeks(gabung.toString(), sandi)
            if (id.isEmpty()) id = buatGist(token, paket) else tulisGist(token, id, paket)
            Setelan.simpanTeks(ctx, "gist_id", id)
            return "Sinkron berhasil pukul " + SimpleDateFormat("HH:mm:ss", Locale("id", "ID")).format(Date()) + "."
        } catch (e: SandiSalah) {
            return "Kata sandi enkripsi tidak cocok dengan data di Gist. Pakai kata sandi yang sama di semua HP."
        } catch (e: Exception) {
            return "Sinkron gagal: " + Galat.ramah(e)
        }
    }

    // ---------- Data ----------

    private fun ekspor(ctx: Context): JSONObject {
        val cat = JSONArray()
        for (n in Catatan.semuaMentah(ctx)) {
            val o = JSONObject().put("id", n.id).put("t", n.waktu).put("teks", n.teks)
            if (n.hapus > 0) o.put("x", n.hapus)
            cat.put(o)
        }
        return JSONObject()
            .put("v", 1)
            .put("catatan", cat)
            .put("riwayat", Otak.ekspor(ctx))
            .put("kontak", KontakAsta.ekspor(ctx))
            .put("setelan", Setelan.ekspor(ctx))
    }

    private fun terapkan(ctx: Context, g: JSONObject) {
        val cat = ArrayList<Nota>()
        val a = g.optJSONArray("catatan") ?: JSONArray()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            cat.add(Nota(o.getString("id"), o.optLong("t", 0L), o.optString("teks", ""), o.optLong("x", 0L)))
        }
        Catatan.gantiMentah(ctx, cat)
        val r = g.optJSONObject("riwayat")
        if (r != null) Otak.impor(ctx, r.optLong("lupa", 0L), r.optJSONArray("isi") ?: JSONArray())
        val k = g.optJSONObject("kontak")
        if (k != null) KontakAsta.impor(ctx, k.optLong("diubah", 0L), k.optJSONArray("daftar") ?: JSONArray())
        val s = g.optJSONObject("setelan")
        if (s != null) Setelan.impor(ctx, s.optLong("diubah", 0L), s.optJSONObject("nilai") ?: JSONObject())
    }

    /** Aturan gabung: catatan per id (penghapusan menang), riwayat digabung menurut waktu, kontak dan setelan memakai yang terbaru. */
    private fun gabung(a: JSONObject, b: JSONObject): JSONObject {
        val peta = LinkedHashMap<String, JSONObject>()
        for (sumber in listOf(a, b)) {
            val arr = sumber.optJSONArray("catatan") ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val ada = peta[id]
                if (ada == null) {
                    peta[id] = o
                } else {
                    val x = maxOf(ada.optLong("x", 0L), o.optLong("x", 0L))
                    if (x > 0) ada.put("x", x)
                }
            }
        }
        val cat = JSONArray()
        for (o in peta.values) cat.put(o)

        val ra = a.optJSONObject("riwayat")
        val rb = b.optJSONObject("riwayat")
        val lupa = maxOf(ra?.optLong("lupa", 0L) ?: 0L, rb?.optLong("lupa", 0L) ?: 0L)
        val kunciR = HashSet<String>()
        val daftarR = ArrayList<JSONObject>()
        for (r in listOf(ra, rb)) {
            val arr = r?.optJSONArray("isi") ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val t = o.optLong("t", 0L)
                if (lupa > 0 && t <= lupa) continue
                if (kunciR.add(t.toString() + "|" + o.optString("role") + "|" + o.optString("text"))) daftarR.add(o)
            }
        }
        daftarR.sortBy { it.optLong("t", 0L) }
        val isiR = JSONArray()
        for (o in daftarR) isiR.put(o)

        fun terbaru(kunci: String): JSONObject {
            val x = a.optJSONObject(kunci)
            val y = b.optJSONObject(kunci)
            if (x == null) return y ?: JSONObject()
            if (y == null) return x
            return if (y.optLong("diubah", 0L) > x.optLong("diubah", 0L)) y else x
        }

        return JSONObject()
            .put("v", 1)
            .put("catatan", cat)
            .put("riwayat", JSONObject().put("lupa", lupa).put("isi", isiR))
            .put("kontak", terbaru("kontak"))
            .put("setelan", terbaru("setelan"))
    }

    // ---------- GitHub Gist ----------

    private class Jawaban(val kode: Int, val teks: String)

    private fun http(metode: String, alamat: String, token: String, badan: String?): Jawaban {
        val c = URL(alamat).openConnection() as HttpURLConnection
        try {
            c.requestMethod = metode
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            c.setRequestProperty("User-Agent", "AstaAsisten")
            if (badan != null) {
                c.setRequestProperty("Content-Type", "application/json")
                c.doOutput = true
                c.outputStream.use { it.write(badan.toByteArray(Charsets.UTF_8)) }
            }
            val kode = c.responseCode
            val aliran = if (kode < 400) c.inputStream else c.errorStream
            val teks = aliran?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Jawaban(kode, teks)
        } finally {
            c.disconnect()
        }
    }

    private fun periksa(j: Jawaban, apa: String) {
        if (j.kode in 200..299) return
        val pesan = when (j.kode) {
            401 -> "Token GitHub ditolak (401). Buat token baru dengan izin gist."
            403 -> "GitHub menolak akses (403). Pastikan token punya izin gist, atau coba lagi sebentar."
            404 -> "Gist tidak ditemukan (404). Cek token, atau putuskan lalu hubungkan ulang."
            else -> "GitHub membalas kode " + j.kode
        }
        throw GalatGemini("$apa: $pesan", j.kode)
    }

    private fun cariGist(token: String): String? {
        for (halaman in 1..5) {
            val j = http("GET", "$API/gists?per_page=100&page=$halaman", token, null)
            periksa(j, "Mencari Gist")
            val arr = JSONArray(j.teks)
            if (arr.length() == 0) return null
            for (i in 0 until arr.length()) {
                val g = arr.getJSONObject(i)
                if (g.optString("description") == DESKRIPSI && g.optJSONObject("files")?.has(BERKAS) == true) return g.getString("id")
            }
        }
        return null
    }

    private fun bacaGist(token: String, id: String): String? {
        val j = http("GET", "$API/gists/$id", token, null)
        periksa(j, "Membaca Gist")
        val f = JSONObject(j.teks).optJSONObject("files")?.optJSONObject(BERKAS) ?: return null
        if (f.optBoolean("truncated", false)) {
            val mentah = http("GET", f.getString("raw_url"), token, null)
            periksa(mentah, "Membaca Gist")
            return mentah.teks
        }
        return f.optString("content", "").ifEmpty { null }
    }

    private fun badanBerkas(paket: String): JSONObject =
        JSONObject().put("files", JSONObject().put(BERKAS, JSONObject().put("content", paket)))

    private fun buatGist(token: String, paket: String): String {
        val badan = badanBerkas(paket).put("description", DESKRIPSI).put("public", false)
        val j = http("POST", "$API/gists", token, badan.toString())
        periksa(j, "Membuat Gist")
        return JSONObject(j.teks).getString("id")
    }

    private fun tulisGist(token: String, id: String, paket: String) {
        val j = http("PATCH", "$API/gists/$id", token, badanBerkas(paket).toString())
        periksa(j, "Menulis Gist")
    }
}
