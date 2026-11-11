package id.asta.app

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** Tingkat izin Asta per aplikasi, daftar blokir permanen, dan status "dihentikan". */
object Izin {
    const val TUTUP = 0
    const val BUKA = 1
    const val KIRIM = 2
    const val NOTIF = 3
    const val OPERASI = 4

    /** Tingkat tertinggi yang sudah dibuat. Naikkan saat tingkat 3 dan 4 selesai. */
    const val TERTINGGI_SEKARANG = 2

    val NAMA = listOf("Tertutup", "Buka", "Kirim teks", "Notifikasi", "Operasikan")
    val PENJELASAN = listOf(
        "Tingkat 0, Tertutup: Asta tidak boleh menyentuh aplikasi ini.",
        "Tingkat 1, Buka: Asta hanya boleh membuka aplikasi ini.",
        "Tingkat 2, Kirim teks: tingkat 1, ditambah mengirim teks lewat menu bagikan (dengan persetujuan Anda).",
        "Tingkat 3, Notifikasi: tingkat 2, ditambah membaca notifikasi aplikasi ini. (Belum tersedia.)",
        "Tingkat 4, Operasikan: tingkat 3, ditambah mengetuk, mengetik, dan membaca layar. (Belum tersedia.)",
    )

    // Cocok persis dengan salah satu bagian nama paket, misalnya "id.co.bri.brimo" -> "bri".
    private val SEGMEN = setOf(
        "bca", "bri", "bni", "btn", "bsi", "blu", "ovo", "dana", "jago", "jenius", "seabank", "livin", "brimo",
        "mandiri", "cimb", "ocbc", "permata", "danamon", "maybank", "linkaja", "gopay", "shopeepay", "paypal",
        "bitwarden", "lastpass", "dashlane", "keepass", "keepassdx", "1password", "enpass", "authenticator",
    )
    // Cocok sebagai potongan nama paket.
    private val POTONGAN_PAKET = listOf(
        "bank", "wallet", "gopay", "password", "passwords", "packageinstaller", "permissioncontroller",
        "com.android.settings", "com.samsung.android.settings", "com.android.vending", "com.sec.android.app.samsungapps",
        "com.miui.securitycenter", "com.android.providers.settings",
    )
    // Cocok sebagai potongan nama yang tampil.
    private val POTONGAN_NAMA = listOf(
        "bank", "dompet", "wallet", "pay", "password", "kata sandi", "sandi", "authenticator", "vault",
        "pengaturan", "settings", "play store", "galaxy store", "pemasang", "installer",
    )

    fun terblokir(pkg: String, label: String): Boolean {
        val p = pkg.lowercase(Locale.ROOT)
        val l = label.lowercase(Locale.ROOT)
        if (p.split('.').any { it in SEGMEN }) return true
        if (POTONGAN_PAKET.any { p.contains(it) }) return true
        if (POTONGAN_NAMA.any { l.contains(it) }) return true
        return false
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("izin_asta", Context.MODE_PRIVATE)

    private fun peta(ctx: Context): JSONObject =
        try { JSONObject(prefs(ctx).getString("peta", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    fun tingkat(ctx: Context, pkg: String): Int =
        peta(ctx).optInt(pkg, TUTUP).coerceIn(TUTUP, TERTINGGI_SEKARANG)

    /** Mengembalikan teks galat, atau null kalau berhasil. */
    fun atur(ctx: Context, pkg: String, label: String, t: Int): String? {
        if (t < TUTUP || t > OPERASI) return "Tingkat tidak valid."
        if (t > TERTINGGI_SEKARANG) return "Tingkat " + t + " belum tersedia di versi ini."
        if (t >= NOTIF && terblokir(pkg, label)) return "Aplikasi ini dikunci permanen dan tidak bisa naik ke tingkat 3 atau 4."
        val m = peta(ctx)
        if (t == TUTUP) m.remove(pkg) else m.put(pkg, t)
        prefs(ctx).edit().putString("peta", m.toString()).apply()
        CatatanAsta.tulis(ctx, "Izin " + label + " diubah menjadi tingkat " + t + " (" + NAMA[t] + ")")
        return null
    }

    fun dijeda(ctx: Context): Boolean = prefs(ctx).getBoolean("dijeda", false)

    fun setDijeda(ctx: Context, b: Boolean) {
        prefs(ctx).edit().putBoolean("dijeda", b).apply()
    }
}
