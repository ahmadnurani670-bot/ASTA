package id.asta.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Pengendali pusat: menjalankan percakapan, suara, dan pembatalan. Layar dan gelembung hanya menampilkan. */
object Asta {
    interface Pendengar {
        fun pesanSaya(teks: String)
        fun astaMulai()
        fun astaKalimat(teks: String)
        fun astaSelesai(penuh: String)
        fun keadaan(k: String)
        fun info(teks: String, galat: Boolean)
    }

    @Volatile var layarTampil = false
    @Volatile var keadaan = "siaga"
        private set

    private val daftar = CopyOnWriteArrayList<Pendengar>()
    private val pelaksana = Executors.newSingleThreadExecutor()
    private val utama = Handler(Looper.getMainLooper())
    @Volatile private var nomorKerja = 0

    fun daftarkan(p: Pendengar) { if (!daftar.contains(p)) daftar.add(p) }
    fun lepas(p: Pendengar) { daftar.remove(p) }

    private fun siar(f: (Pendengar) -> Unit) {
        for (p in daftar) utama.post { f(p) }
    }

    private fun ubah(k: String) {
        keadaan = k
        siar { it.keadaan(k) }
    }

    fun info(teks: String, galat: Boolean) = siar { it.info(teks, galat) }

    private val perintahHenti = Regex("^(asta[, ]+)?(stop|berhenti|diam|cukup)[.! ]*$", RegexOption.IGNORE_CASE)
    private val perintahBatal = Regex("^(asta[, ]+)?(batal|batalkan|batalkan itu|urungkan)[.! ]*$", RegexOption.IGNORE_CASE)

    /** Perintah singkat yang ditangani tanpa Gemini. Mengembalikan true kalau sudah ditangani. */
    private fun perintahLokal(app: Context, t: String): Boolean {
        if (perintahHenti.matches(t)) {
            berhenti()
            return true
        }
        if (perintahBatal.matches(t)) {
            if (keadaan != "siaga") {
                berhenti()
                info("Dihentikan.", false)
            } else {
                val hasil = Alat.batalkanTerakhir()
                siar { it.astaMulai() }
                siar { it.astaSelesai(hasil) }
                if (Setelan.bool(app, "suara", true)) Suara.ucapkan(app, hasil)
            }
            return true
        }
        return false
    }

    fun kirim(ctx: Context, teks: String, lampiran: Lampiran? = null) {
        val app = ctx.applicationContext
        val t = teks.trim()
        if (t.isEmpty() && lampiran == null) return
        if (lampiran == null && perintahLokal(app, t)) return
        val kerja = ++nomorKerja
        siar { it.pesanSaya(if (lampiran != null) "[" + lampiran.nama + "] " + t else t) }
        ubah("berpikir")
        pelaksana.execute {
            val jawaban: String
            try {
                jawaban = Otak.tanya(app, t, lampiran) { alat -> siar { it.info("Menjalankan: $alat", false) } }
            } catch (e: Exception) {
                if (kerja == nomorKerja) {
                    siar { it.info(Galat.ramah(e), true) }
                    ubah("siaga")
                }
                return@execute
            }
            if (kerja != nomorKerja) return@execute
            tampilkanDanUcapkan(app, jawaban, kerja)
        }
    }

    private fun tampilkanDanUcapkan(app: Context, jawaban: String, kerja: Int) {
        siar { it.astaMulai() }
        if (!Setelan.bool(app, "suara", true)) {
            siar { it.astaSelesai(jawaban) }
            ubah("siaga")
            lanjutOtomatis(app)
            return
        }
        val panjang = jawaban.length > Suara.BATAS
        if (panjang) siar { it.astaKalimat(jawaban) }
        ubah("bicara")
        val mulai = AtomicBoolean(false)
        // Cadangan: kalau suara tidak kunjung mulai, teks tetap ditampilkan utuh.
        utama.postDelayed({
            if (!mulai.get() && kerja == nomorKerja) siar { it.astaSelesai(jawaban) }
        }, 6000)
        val saatKalimat: (String) -> Unit = { k ->
            mulai.set(true)
            siar { it.astaKalimat(k) }
        }
        Suara.ucapkan(app, jawaban, if (panjang) null else saatKalimat) {
            if (kerja == nomorKerja) {
                siar { it.astaSelesai(jawaban) }
                ubah("siaga")
                lanjutOtomatis(app)
            }
        }
    }

    private fun lanjutOtomatis(app: Context) {
        if (Setelan.bool(app, "oto", false) && !Izin.dijeda(app)) dengarLalu(app)
    }

    private fun pesanDengar(kode: String): String = when (kode) {
        "izin" -> "Izin mikrofon belum diberikan."
        "tidak-tersedia" -> "Pengenal suara tidak tersedia di ponsel ini. Pasang atau aktifkan Layanan Suara Google."
        "jaringan" -> "Pengenal suara butuh internet."
        "tidak-ada-suara" -> "Tidak ada suara terdengar."
        else -> "Mikrofon gagal ($kode)."
    }

    fun dengarLalu(ctx: Context) {
        val app = ctx.applicationContext
        Suara.berhenti()
        ubah("dengar")
        Dengar.mulai(
            app,
            { teks -> if (teks.isBlank()) ubah("siaga") else kirim(app, teks) },
            { kode ->
                ubah("siaga")
                if (kode != "dibatalkan") info(pesanDengar(kode), kode != "tidak-ada-suara")
            },
        )
    }

    fun berhenti() {
        nomorKerja++
        Otak.batal = true
        Persetujuan.batalSemua()
        Suara.berhenti()
        Dengar.batal()
        ubah("siaga")
    }
}
