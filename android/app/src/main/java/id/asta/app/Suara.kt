package id.asta.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Suara keluar lewat TextToSpeech Android, dibacakan per kalimat supaya teks bisa tampil tepat saat kalimatnya diucapkan. */
object Suara {
    const val BATAS = 600

    private class Potongan(val teks: String, val mulai: ((String) -> Unit)?, val selesai: (() -> Unit)?)

    private var tts: TextToSpeech? = null
    @Volatile private var siap = false
    @Volatile private var bahasaAda = true
    private val menunggu = ArrayList<() -> Unit>()
    private val peta = ConcurrentHashMap<String, Potongan>()
    private var nomor = 0

    private val pendengar = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val p = utteranceId?.let { peta[it] } ?: return
            p.mulai?.invoke(p.teks)
        }

        override fun onDone(utteranceId: String?) {
            val p = utteranceId?.let { peta.remove(it) } ?: return
            p.selesai?.invoke()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            val p = utteranceId?.let { peta.remove(it) } ?: return
            p.selesai?.invoke()
        }
    }

    fun status(): String = when {
        tts == null -> "belum-dimulai"
        !siap -> "belum-siap"
        !bahasaAda -> "bahasa-indonesia-belum-terpasang"
        else -> "siap"
    }

    private fun pastikan(ctx: Context, lanjut: () -> Unit) {
        val langsung: Boolean
        val mulaiBaru: Boolean
        synchronized(this) {
            langsung = siap
            mulaiBaru = !siap && tts == null
            if (!langsung) menunggu.add(lanjut)
        }
        if (langsung) {
            lanjut()
            return
        }
        if (!mulaiBaru) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            val m = tts
            if (status == TextToSpeech.SUCCESS && m != null) {
                val hasil = m.setLanguage(Locale("id", "ID"))
                bahasaAda = hasil != TextToSpeech.LANG_MISSING_DATA && hasil != TextToSpeech.LANG_NOT_SUPPORTED
                m.setOnUtteranceProgressListener(pendengar)
                val daftar: List<() -> Unit>
                synchronized(this@Suara) {
                    siap = true
                    daftar = ArrayList(menunggu)
                    menunggu.clear()
                }
                for (f in daftar) f()
            } else {
                synchronized(this@Suara) {
                    tts = null
                    menunggu.clear()
                }
            }
        }
    }

    private fun pecah(teks: String, maks: Int = 140): List<String> {
        val hasil = ArrayList<String>()
        for (kalimat in teks.trim().split(Regex("(?<=[.!?])\\s+"))) {
            var k = kalimat.trim()
            while (k.length > maks) {
                var i = maxOf(k.lastIndexOf(',', maks - 1), k.lastIndexOf(';', maks - 1))
                if (i < 30) i = k.lastIndexOf(' ', maks - 1)
                if (i < 30) i = maks
                val potong = if (k[i] == ',' || k[i] == ';') i + 1 else i
                hasil.add(k.substring(0, potong).trim())
                k = k.substring(potong).trim()
            }
            if (k.isNotEmpty()) hasil.add(k)
        }
        val gabung = ArrayList<String>()
        var sisa = ""
        for (p in hasil) {
            sisa = (sisa + " " + p).trim()
            if (sisa.length >= 25) {
                gabung.add(sisa)
                sisa = ""
            }
        }
        if (sisa.isNotEmpty()) {
            if (gabung.isNotEmpty()) gabung[gabung.size - 1] = gabung[gabung.size - 1] + " " + sisa else gabung.add(sisa)
        }
        return gabung
    }

    /** Membacakan teks. Teks lebih dari BATAS hanya diberi pengantar singkat (teks lengkap tampil di layar). */
    fun ucapkan(ctx: Context, teks: String, saatKalimat: ((String) -> Unit)? = null, saatSelesai: (() -> Unit)? = null) {
        val bersih = teks.replace(Regex("[*#`_]"), "").trim()
        if (bersih.isEmpty()) {
            saatSelesai?.invoke()
            return
        }
        val panjang = bersih.length > BATAS
        val bagian = pecah(if (panjang) "Jawaban lengkap ada di layar." else bersih)
        val laju = Setelan.laju(ctx)
        pastikan(ctx) {
            val m = tts
            if (m == null) {
                saatSelesai?.invoke()
            } else {
                m.setSpeechRate(laju)
                for ((i, b) in bagian.withIndex()) {
                    val id = "u" + (++nomor)
                    peta[id] = Potongan(b, if (panjang) null else saatKalimat, if (i == bagian.size - 1) saatSelesai else null)
                    m.speak(b, TextToSpeech.QUEUE_ADD, null, id)
                }
            }
        }
    }

    fun berhenti() {
        peta.clear()
        try { tts?.stop() } catch (e: Exception) { }
    }
}
