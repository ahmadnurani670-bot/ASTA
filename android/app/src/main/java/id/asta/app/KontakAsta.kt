package id.asta.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class Kontak(val nama: String, val nomor: String)

/** Daftar kontak milik Asta (diisi pengguna) dan panggilan WhatsApp ke kontak di daftar itu. */
object KontakAsta {
    private const val MIME_SUARA = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
    private const val MIME_VIDEO = "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
    private const val WA = "com.whatsapp"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("kontak_asta", Context.MODE_PRIVATE)

    fun normal(nomor: String): String {
        val d = nomor.filter { it.isDigit() }
        return if (d.startsWith("0")) "62" + d.substring(1) else d
    }

    fun semua(ctx: Context): List<Kontak> {
        val hasil = ArrayList<Kontak>()
        try {
            val a = JSONArray(prefs(ctx).getString("daftar", "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                hasil.add(Kontak(o.getString("nama"), o.getString("nomor")))
            }
        } catch (e: Exception) {
            // daftar rusak: pakai yang terbaca
        }
        return hasil
    }

    private fun simpanMentah(ctx: Context, daftar: List<Kontak>, stempel: Long) {
        val a = JSONArray()
        for (k in daftar) a.put(JSONObject().put("nama", k.nama).put("nomor", k.nomor))
        prefs(ctx).edit().putString("daftar", a.toString()).putLong("diubah", stempel).apply()
    }

    private fun simpan(ctx: Context, daftar: List<Kontak>) {
        simpanMentah(ctx, daftar, System.currentTimeMillis())
        Sinkron.jadwalkan(ctx)
    }

    fun ekspor(ctx: Context): JSONObject {
        val a = JSONArray()
        for (k in semua(ctx)) a.put(JSONObject().put("nama", k.nama).put("nomor", k.nomor))
        return JSONObject().put("diubah", prefs(ctx).getLong("diubah", 0L)).put("daftar", a)
    }

    fun impor(ctx: Context, diubah: Long, daftar: JSONArray) {
        val d = ArrayList<Kontak>()
        for (i in 0 until daftar.length()) {
            val o = daftar.getJSONObject(i)
            d.add(Kontak(o.getString("nama"), o.getString("nomor")))
        }
        simpanMentah(ctx, d, diubah)
    }

    /** Mengembalikan teks galat, atau null kalau berhasil. */
    fun tambah(ctx: Context, nama: String, nomor: String): String? {
        val n = nama.trim()
        val no = normal(nomor)
        if (n.isEmpty()) return "Nama kosong."
        if (no.length < 8) return "Nomor terlalu pendek. Tulis lengkap, misalnya 0812xxxxxxxx."
        val d = semua(ctx).filter { !it.nama.equals(n, true) }.toMutableList()
        d.add(Kontak(n, no))
        simpan(ctx, d)
        return null
    }

    fun hapus(ctx: Context, nama: String) {
        simpan(ctx, semua(ctx).filter { !it.nama.equals(nama, true) })
    }

    fun daftarTeks(ctx: Context): String {
        val d = semua(ctx)
        if (d.isEmpty()) return "Daftar Kontak Asta masih kosong. Pengguna bisa menambahkannya di layar Kontak."
        return d.joinToString("\n") { it.nama }
    }

    private class Hasil(val kontak: Kontak?, val galat: String?)

    private fun cari(ctx: Context, nama: String): Hasil {
        val q = nama.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return Hasil(null, "Nama kontak kosong.")
        val semua = semua(ctx)
        val tepat = semua.filter { it.nama.lowercase(Locale.ROOT) == q }
        if (tepat.size == 1) return Hasil(tepat[0], null)
        val mirip = semua.filter { it.nama.lowercase(Locale.ROOT).contains(q) }
        return when {
            mirip.size == 1 -> Hasil(mirip[0], null)
            mirip.isEmpty() -> Hasil(null, "Kontak \"$nama\" tidak ada di daftar Kontak Asta. Pengguna bisa menambahkannya di layar Kontak.")
            else -> Hasil(null, "Ada beberapa kontak yang cocok: " + mirip.take(5).joinToString(", ") { it.nama } + ". Sebutkan nama yang lebih tepat.")
        }
    }

    private class Baris(val id: Long?, val alasan: String)

    private fun cariBaris(ctx: Context, nomor: String, mime: String): Baris {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return Baris(null, "izin Kontak belum diberikan (Setelan Asta > Izinkan kalender dan kontak)")
        }
        val akhir = nomor.takeLast(9)
        // 1. Cari kontak ponsel dengan nomor itu (tahan beda format 0812 / +62812).
        val idKontak = HashSet<Long>()
        for (varian in listOf("+$nomor", nomor)) {
            try {
                val u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(varian))
                ctx.contentResolver.query(u, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { c ->
                    while (c.moveToNext()) idKontak.add(c.getLong(0))
                }
            } catch (e: Exception) {
                // lanjut ke cara berikutnya
            }
        }
        // 2. Cari baris panggilan WhatsApp milik kontak itu, atau yang nomornya cocok.
        var total = 0
        var cocok: Long? = null
        val proj = arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DATA1, ContactsContract.Data.CONTACT_ID)
        ctx.contentResolver.query(
            ContactsContract.Data.CONTENT_URI, proj, ContactsContract.Data.MIMETYPE + " LIKE ?", arrayOf("%" + mime.substringAfter("vnd.com.")), null,
        )?.use { c ->
            while (c.moveToNext()) {
                total++
                val d = (c.getString(1) ?: "").filter { it.isDigit() }
                val sama = (d.isNotEmpty() && d.endsWith(akhir)) || idKontak.contains(c.getLong(2))
                if (cocok == null && sama) cocok = c.getLong(0)
            }
        }
        val hasil = cocok
        if (hasil != null) return Baris(hasil, "")
        val alasan = when {
            total == 0 -> "di buku kontak ponsel tidak ada baris panggilan WhatsApp sama sekali (WhatsApp belum menyinkronkan kontak, atau versinya tidak menyediakannya)"
            idKontak.isEmpty() -> "nomor ini belum tersimpan di kontak ponsel (ada $total baris WhatsApp lain)"
            else -> "kontak ponselnya ada, tapi belum punya baris panggilan WhatsApp (nomornya mungkin belum terdaftar di WhatsApp atau belum tersinkron)"
        }
        return Baris(null, alasan)
    }

    fun teleponWhatsApp(ctx: Context, nama: String, video: Boolean): String {
        if (Izin.dijeda(ctx)) return "Asta sedang dihentikan oleh pengguna, jadi tidak bisa menelepon."
        val h = cari(ctx, nama)
        val k = h.kontak ?: return h.galat ?: "Kontak tidak ditemukan."
        if (ctx.packageManager.getLaunchIntentForPackage(WA) == null) return "WhatsApp belum terpasang di ponsel ini."
        val jenis = if (video) "Video call" else "Telepon"
        if (!Persetujuan.minta(ctx, jenis + " " + k.nama + " lewat WhatsApp", "Asta ingin memulai panggilan WhatsApp ke " + k.nama + ".")) {
            CatatanAsta.tulis(ctx, "Pengguna menolak panggilan WhatsApp ke " + k.nama)
            return "Pengguna tidak menyetujui panggilan ke " + k.nama + "."
        }
        val mime = if (video) MIME_VIDEO else MIME_SUARA
        val baris = cariBaris(ctx, k.nomor, mime)
        val id = baris.id
        if (id != null) {
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(Uri.parse("content://com.android.contacts/data/$id"), mime)
            i.setPackage(WA)
            CatatanAsta.tulis(ctx, "Memulai panggilan WhatsApp ke " + k.nama)
            return Pembuka.buka(ctx, i, "Panggilan WhatsApp ke " + k.nama) +
                " WhatsApp mungkin meminta konfirmasi di layarnya. Asta tidak bisa mengakhiri panggilan WhatsApp."
        }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + k.nomor))
        i.setPackage(WA)
        CatatanAsta.tulis(ctx, "Membuka chat WhatsApp " + k.nama + " (baris panggilan tidak ditemukan)")
        return "Panggilan langsung ke " + k.nama + " tidak bisa dimulai karena " + baris.alasan + ". " +
            Pembuka.buka(ctx, i, "Chat WhatsApp " + k.nama) + " Pengguna bisa mengetuk ikon telepon di sana."
    }

    /** Laporan pemeriksaan untuk layar Periksa: apa yang terlihat Asta di ponsel. */
    fun diagnosa(ctx: Context): String {
        val b = StringBuilder()
        b.append("WhatsApp terpasang: ").append(if (ctx.packageManager.getLaunchIntentForPackage(WA) != null) "ya" else "TIDAK").append('\n')
        try {
            b.append("Versi WhatsApp: ").append(ctx.packageManager.getPackageInfo(WA, 0).versionName).append('\n')
        } catch (e: Exception) {
            b.append("Versi WhatsApp: tidak terbaca\n")
        }
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            b.append("Izin Kontak: BELUM diberikan\n")
            return b.toString()
        }
        b.append("Izin Kontak: ya\n")
        val jumlah = HashMap<String, Int>()
        try {
            ctx.contentResolver.query(
                ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data.MIMETYPE),
                ContactsContract.Data.MIMETYPE + " LIKE ?", arrayOf("%whatsapp%"), null,
            )?.use { c -> while (c.moveToNext()) { val m = c.getString(0) ?: ""; jumlah[m] = (jumlah[m] ?: 0) + 1 } }
        } catch (e: Exception) {
            b.append("Gagal membaca data kontak: ").append(e.javaClass.simpleName).append('\n')
        }
        if (jumlah.isEmpty()) b.append("Baris data WhatsApp di buku kontak: 0 (WhatsApp belum menyinkronkan kontak)\n")
        for ((m, n) in jumlah) b.append("  ").append(m.substringAfter("vnd.com.")).append(": ").append(n).append('\n')
        val d = semua(ctx)
        if (d.isEmpty()) b.append("Kontak Asta: kosong\n")
        for (k in d) {
            val s = cariBaris(ctx, k.nomor, MIME_SUARA)
            b.append("Kontak Asta ").append(k.nama).append(" (").append(k.nomor).append("): ")
                .append(if (s.id != null) "siap ditelepon" else "TIDAK bisa, " + s.alasan).append('\n')
        }
        return b.toString()
    }
}
