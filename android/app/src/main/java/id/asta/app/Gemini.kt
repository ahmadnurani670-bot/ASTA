package id.asta.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GalatGemini(pesan: String, val status: Int) : Exception(pesan)

object Galat {
    fun ramah(e: Throwable): String {
        val m = e.message ?: ""
        return when {
            e is GalatGemini && e.status == 400 && m.contains("API key", true) -> "Kunci API tidak valid. Periksa di Setelan."
            e is GalatGemini && e.status == 403 -> "Kunci API ditolak (403). Periksa kunci dan izinnya di Setelan."
            e is GalatGemini && e.status == 404 -> "Model tidak ditemukan. Periksa nama model di Setelan."
            e is GalatGemini && e.status == 429 -> "Kuota Gemini habis atau terlalu sering dipakai. Coba lagi sebentar."
            e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.ConnectException ->
                "Tidak ada koneksi internet."
            else -> "Gangguan: " + (if (m.isBlank()) e.javaClass.simpleName else m).take(200)
        }
    }
}

/** Klien REST Gemini tanpa pustaka tambahan. */
object Gemini {
    private const val DASAR = "https://generativelanguage.googleapis.com/v1beta/models/"

    fun panggil(kunci: String, model: String, isi: JSONObject): JSONObject {
        var percobaan = 0
        while (true) {
            val c = URL(DASAR + URLEncoder.encode(model, "UTF-8") + ":generateContent").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 15000
                c.readTimeout = 60000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-goog-api-key", kunci)
                c.doOutput = true
                c.outputStream.use { it.write(isi.toString().toByteArray(Charsets.UTF_8)) }
                val kode = c.responseCode
                val aliran = if (kode in 200..299) c.inputStream else c.errorStream
                val teks = aliran?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (kode in 200..299) return JSONObject(teks)
                if (percobaan == 0 && (kode == 429 || kode == 503)) {
                    percobaan++
                    Thread.sleep(3000)
                    continue
                }
                var pesan = "HTTP $kode"
                try {
                    pesan = JSONObject(teks).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    // pakai pesan bawaan
                }
                throw GalatGemini(pesan, kode)
            } finally {
                c.disconnect()
            }
        }
    }
}
