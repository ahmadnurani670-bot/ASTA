package id.asta.app

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Jembatan antara halaman Asta (JavaScript) dan fungsi Android.
 * Dipanggil di JavaScript sebagai window.AstaNative. Semua hasil berupa teks
 * berbahasa Indonesia yang langsung bisa disampaikan Asta ke pengguna.
 */
class JembatanAsta(private val act: MainActivity) {
    private val ctx: Context = act.applicationContext

    @JavascriptInterface
    fun versi(): String = "0.1"

    @JavascriptInterface
    fun aturPengingat(pesan: String?, waktu: String?): String {
        val ms = parseWaktu(waktu ?: "")
            ?: return "Format waktu tidak dikenali. Pakai yyyy-MM-ddTHH:mm."
        if (ms <= System.currentTimeMillis() + 5_000L) {
            return "Waktu itu sudah lewat. Sebutkan waktu yang akan datang."
        }
        val p = Gudang.tambah(ctx, "pengingat", (pesan ?: "").trim(), ms, emptyList(), 0, 0)
        val presisi = Penjadwal.pasang(ctx, p)
        return "Pengingat #" + p.id + " diatur untuk " + Format.waktu(ms) + "." + catatan(presisi)
    }

    @JavascriptInterface
    fun aturAlarm(jam: String?, pesan: String?, ulang: String?): String {
        val m = Regex("(\\d{1,2})[:.](\\d{2})").find(jam ?: "")
            ?: return "Format jam tidak dikenali. Pakai HH:mm."
        val j = m.groupValues[1].toInt()
        val mn = m.groupValues[2].toInt()
        if (j > 23 || mn > 59) return "Jam tidak valid."
        val hari = parseHari(ulang ?: "")
            ?: return "Pola ulang tidak dikenali. Pakai sekali, harian, hari kerja, akhir pekan, atau nama hari."
        val ms = Penjadwal.waktuBerikutnya(hari, j, mn, System.currentTimeMillis())
        val p = Gudang.tambah(ctx, "alarm", (pesan ?: "").trim(), ms, hari, j, mn)
        val presisi = Penjadwal.pasang(ctx, p)
        return "Alarm #" + p.id + " diatur. Bunyi berikutnya " + Format.waktu(ms) + Format.ulang(hari) + "." + catatan(presisi)
    }

    @JavascriptInterface
    fun aturTimer(detik: String?, label: String?): String {
        val d = (detik ?: "").trim().toDoubleOrNull()?.toLong()
            ?: return "Durasi tidak valid."
        if (d < 1L || d > 86_400L) return "Durasi timer harus antara 1 detik dan 24 jam."
        val ms = System.currentTimeMillis() + d * 1000L
        val p = Gudang.tambah(ctx, "timer", (label ?: "").trim(), ms, emptyList(), 0, 0)
        val presisi = Penjadwal.pasang(ctx, p)
        return "Timer #" + p.id + " dimulai untuk " + d + " detik, selesai pukul " + Format.waktu(ms) + "." + catatan(presisi)
    }

    @JavascriptInterface
    fun daftar(): String {
        val d = Gudang.semua(ctx)
        if (d.isEmpty()) return "Belum ada pengingat, alarm, atau timer aktif."
        return d.joinToString("\n") { Format.baris(it) }
    }

    @JavascriptInterface
    fun hapus(id: String?): String {
        val s = (id ?: "").trim().lowercase(Locale.ROOT)
        if (s == "semua") {
            val d = Gudang.semua(ctx)
            for (p in d) {
                Penjadwal.batal(ctx, p.id)
                Gudang.hapus(ctx, p.id)
            }
            return "Semua pengingat dihapus (" + d.size + ")."
        }
        val n = s.removePrefix("#").toIntOrNull() ?: return "Nomor tidak valid."
        if (Gudang.cari(ctx, n) == null) return "Tidak ada pengingat dengan nomor " + n + "."
        Penjadwal.batal(ctx, n)
        Gudang.hapus(ctx, n)
        return "Pengingat #" + n + " dihapus."
    }

    @JavascriptInterface
    fun buka(): String {
        val i = Intent(ctx, PengingatActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return "Daftar pengingat dibuka di layar pengguna."
    }

    @JavascriptInterface
    fun ubahAlamat() {
        act.runOnUiThread { Pengaturan.tanyaAlamat(act) { act.muatUlang() } }
    }

    @JavascriptInterface
    fun dengar() {
        act.runOnUiThread { act.mulaiDengar() }
    }

    @JavascriptInterface
    fun berhentiDengar() {
        act.runOnUiThread { act.berhentiDengar() }
    }

    private fun catatan(presisi: Boolean): String =
        if (presisi) "" else " Catatan: izin alarm presisi belum aktif, jadi bunyinya bisa terlambat. Aktifkan di layar Pengingat."

    private fun parseWaktu(s: String): Long? {
        return try {
            val bersih = s.trim().replace(Regex("(Z|[+-]\\d{2}:?\\d{2})$"), "").replace(' ', 'T')
            val waktu = LocalDateTime.parse(bersih)
            waktu.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseHari(u: String): List<Int>? {
        val s = u.trim().lowercase(Locale.ROOT)
        if (s.isEmpty() || s == "sekali" || s == "tidak") return emptyList()
        if (s.contains("hari kerja")) return listOf(2, 3, 4, 5, 6)
        if (s.contains("akhir pekan")) return listOf(7, 1)
        if (s.contains("harian") || s.contains("setiap hari") || s.contains("tiap hari")) {
            return listOf(1, 2, 3, 4, 5, 6, 7)
        }
        val peta = mapOf(
            "minggu" to 1, "ahad" to 1, "senin" to 2, "selasa" to 3,
            "rabu" to 4, "kamis" to 5, "jumat" to 6, "sabtu" to 7,
        )
        val hasil = sortedSetOf<Int>()
        for ((nama, nomor) in peta) {
            if (s.contains(nama)) hasil.add(nomor)
        }
        return if (hasil.isEmpty()) null else hasil.toList()
    }
}
