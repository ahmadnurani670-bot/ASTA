package id.asta.app

import android.content.Context
import android.content.Intent
import java.util.Locale

/** Tindakan Asta terhadap aplikasi lain. Setiap tindakan memeriksa tingkat izin, lalu dicatat di log. */
object Aksi {
    class Apl(val label: String, val pkg: String)
    private class Hasil(val apl: Apl?, val galat: String?)

    @Suppress("DEPRECATION")
    fun semuaApl(ctx: Context): List<Apl> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { Apl(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    private fun cari(ctx: Context, nama: String): Hasil {
        val q = nama.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return Hasil(null, "Nama aplikasi kosong.")
        val semua = semuaApl(ctx)
        val tepat = semua.filter { it.label.lowercase(Locale.ROOT) == q }
        if (tepat.size == 1) return Hasil(tepat[0], null)
        val mirip = semua.filter { it.label.lowercase(Locale.ROOT).contains(q) }
        return when {
            mirip.size == 1 -> Hasil(mirip[0], null)
            mirip.isEmpty() -> Hasil(null, "Aplikasi \"" + nama + "\" tidak ditemukan.")
            else -> Hasil(null, "Ada beberapa aplikasi yang cocok: " + mirip.take(5).joinToString(", ") { it.label } + ". Sebutkan nama yang lebih tepat.")
        }
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
        val i = ctx.packageManager.getLaunchIntentForPackage(a.pkg) ?: return a.label + " tidak bisa dibuka."
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
