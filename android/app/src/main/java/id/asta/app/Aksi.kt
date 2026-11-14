package id.asta.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.util.Locale

/** Tindakan Asta terhadap aplikasi lain. Setiap tindakan memeriksa tingkat izin, lalu dicatat di log. */
object Aksi {
    class Apl(val label: String, val pkg: String, val komponen: ComponentName? = null)
    private class Hasil(val apl: Apl?, val galat: String?)

    @Suppress("DEPRECATION")
    fun semuaApl(ctx: Context): List<Apl> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { Apl(it.loadLabel(pm).toString(), it.activityInfo.packageName, ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    private fun norm(t: String): String = t.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /** Singkatan yang sering diucapkan, dipetakan ke potongan nama aplikasi atau paket. */
    private val alias = mapOf(
        "wa" to listOf("whatsapp"), "ig" to listOf("instagram"), "yt" to listOf("youtube"), "fb" to listOf("facebook"),
        "gmaps" to listOf("maps"), "peta" to listOf("maps"), "kamera" to listOf("camera", "kamera"),
        "browser" to listOf("chrome", "browser", "peramban", "firefox", "edge", "opera", "brave", "samsunginternet"),
        "peramban" to listOf("chrome", "browser", "peramban", "firefox", "edge", "opera", "brave", "samsunginternet"),
        "internet" to listOf("chrome", "browser", "peramban", "samsunginternet"),
        "galeri" to listOf("gallery", "galeri", "foto", "photos"), "foto" to listOf("photos", "foto", "gallery", "galeri"),
        "pesan" to listOf("messages", "pesan", "sms"), "telepon" to listOf("phone", "telepon", "dialer"),
        "kontak" to listOf("contacts", "kontak"), "kalkulator" to listOf("calculator", "kalkulator"),
        "jam" to listOf("clock", "jam"), "kalender" to listOf("calendar", "kalender"),
    )

    private fun skor(a: Apl, q: String): Int {
        val l = norm(a.label)
        val p = a.pkg.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        if (l.isEmpty()) return 0
        if (l == q) return 100
        if (l.startsWith(q)) return 80 - minOf(30, l.length - q.length)
        if (l.contains(q)) return 60
        if (q.length >= 4 && q.contains(l) && l.length >= 3) return 55
        if (q.length >= 4 && p.contains(q)) return 40
        return 0
    }

    private fun cari(ctx: Context, nama: String): Hasil {
        val q = norm(nama)
        if (q.isEmpty()) return Hasil(null, "Nama aplikasi kosong.")
        val semua = semuaApl(ctx)
        val kunci = (alias[q] ?: emptyList()).ifEmpty { listOf(q) }
        var terbaik = 0
        val nilai = HashMap<String, Int>()
        for (a in semua) {
            var n = 0
            for (k in kunci) n = maxOf(n, skor(a, k))
            if (n > 0) { nilai[a.pkg] = n; if (n > terbaik) terbaik = n }
        }
        if (terbaik == 0) return Hasil(null, "Aplikasi \"" + nama + "\" tidak ditemukan di ponsel ini.")
        val teratas = semua.filter { nilai[it.pkg] == terbaik }
        // Kalau beberapa cocok sama baik, ambil yang labelnya paling pendek; persetujuan selalu menyebut nama aplikasinya.
        return Hasil(teratas.minByOrNull { it.label.length }, null)
    }

    private const val JEDA = "Asta sedang dihentikan oleh pengguna, jadi tidak bisa menyentuh aplikasi lain."

    fun daftarDiizinkan(ctx: Context): String {
        if (Izin.dijeda(ctx)) return JEDA
        val baris = semuaApl(ctx)
            .map { it to Izin.tingkat(ctx, it.pkg) }
            .filter { it.second >= Izin.BUKA }
            .joinToString("\n") { it.first.label + ": tingkat " + it.second + " (" + Izin.NAMA[it.second] + ")" }
        return if (baris.isEmpty()) "Belum ada aplikasi yang diizinkan. Pengguna bisa mengaturnya di layar Izin Asta." else baris
    }

    fun buka(ctx: Context, nama: String): String {
        if (Izin.dijeda(ctx)) return JEDA
        val h = cari(ctx, nama)
        val a = h.apl ?: return h.galat ?: "Aplikasi tidak ditemukan."
        if (Izin.tingkat(ctx, a.pkg) < Izin.BUKA) {
            CatatanAsta.tulis(ctx, "DITOLAK buka " + a.label + " (izin tertutup)")
            return a.label + " belum diizinkan untuk Asta. Pengguna bisa mengubahnya di layar Izin Asta."
        }
        val i = (ctx.packageManager.getLaunchIntentForPackage(a.pkg)
            ?: a.komponen?.let { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(it) })
            ?: return a.label + " tidak bisa dibuka."
        CatatanAsta.tulis(ctx, "Membuka " + a.label)
        return Pembuka.buka(ctx, i, a.label)
    }

    /** Dipanggil dari thread latar, jadi boleh menunggu persetujuan pengguna. */
    fun kirimTeks(ctx: Context, nama: String, teks: String): String {
        if (Izin.dijeda(ctx)) return JEDA
        val isiTeks = teks.trim()
        if (isiTeks.isEmpty()) return "Teks kosong."
        val h = cari(ctx, nama)
        val a = h.apl ?: return h.galat ?: "Aplikasi tidak ditemukan."
        if (Izin.tingkat(ctx, a.pkg) < Izin.KIRIM) {
            CatatanAsta.tulis(ctx, "DITOLAK kirim teks ke " + a.label + " (izin kurang dari tingkat 2)")
            return a.label + " belum diizinkan menerima teks dari Asta (perlu tingkat 2). Pengguna bisa mengubahnya di layar Izin Asta."
        }
        val setuju = Persetujuan.minta(ctx, "Kirim teks ke " + a.label, "Asta ingin mengirim teks lewat menu bagikan: \"" + isiTeks.take(300) + "\"")
        if (!setuju) {
            CatatanAsta.tulis(ctx, "Pengguna menolak kirim teks ke " + a.label)
            return "Pengguna tidak menyetujui pengiriman teks ke " + a.label + "."
        }
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, isiTeks)
        i.setPackage(a.pkg)
        CatatanAsta.tulis(ctx, "Mengirim teks ke " + a.label + ": " + isiTeks.take(80))
        val hasil = Pembuka.buka(ctx, i, a.label)
        return hasil + " Pengguna masih perlu menekan kirim sendiri di aplikasi itu."
    }
}
