package id.asta.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Alat yang bisa dipanggil Gemini, plus tumpukan "urungkan" untuk membatalkan tindakan terakhir. */
object Alat {
    class Urung(val keterangan: String, val aksi: () -> Unit)

    private val tumpukan = ArrayList<Urung>()

    @Synchronized
    fun catatUrung(keterangan: String, aksi: () -> Unit) {
        tumpukan.add(Urung(keterangan, aksi))
        while (tumpukan.size > 20) tumpukan.removeAt(0)
    }

    fun batalkanTerakhir(): String {
        val u: Urung = synchronized(this) {
            if (tumpukan.isEmpty()) null else tumpukan.removeAt(tumpukan.size - 1)
        } ?: return "Tidak ada tindakan yang bisa dibatalkan."
        return try {
            u.aksi()
            "Dibatalkan: " + u.keterangan + "."
        } catch (e: Exception) {
            "Gagal membatalkan " + u.keterangan + "."
        }
    }

    private fun fn(nama: String, desk: String, wajib: List<Pair<String, String>> = emptyList(), opsional: List<Pair<String, String>> = emptyList()): JSONObject {
        val o = JSONObject().put("name", nama).put("description", desk)
        if (wajib.isNotEmpty() || opsional.isNotEmpty()) {
            val props = JSONObject()
            val perlu = JSONArray()
            for ((k, d) in wajib) {
                props.put(k, JSONObject().put("type", "STRING").put("description", d))
                perlu.put(k)
            }
            for ((k, d) in opsional) props.put(k, JSONObject().put("type", "STRING").put("description", d))
            val param = JSONObject().put("type", "OBJECT").put("properties", props)
            if (perlu.length() > 0) param.put("required", perlu)
            o.put("parameters", param)
        }
        return o
    }

    fun deklarasi(): JSONArray {
        val a = JSONArray()
        a.put(fn("waktu_sekarang", "Mengembalikan hari, tanggal, dan jam saat ini."))
        a.put(fn("catat", "Menambahkan teks ke daftar catatan pengguna.", listOf("teks" to "Isi catatan.")))
        a.put(fn("buka_catatan", "Membuka daftar catatan di layar pengguna."))
        a.put(fn("atur_pengingat", "Mengatur pengingat pada satu waktu tertentu.",
            listOf("pesan" to "Isi pengingat.", "waktu" to "Waktu setempat, format yyyy-MM-ddTHH:mm.")))
        a.put(fn("atur_alarm", "Mengatur alarm pada jam tertentu, bisa berulang.",
            listOf("jam" to "Format HH:mm, 24 jam."),
            listOf("pesan" to "Label alarm.", "ulang" to "sekali, harian, hari kerja, akhir pekan, atau nama hari.")))
        a.put(fn("atur_timer", "Memulai timer hitung mundur.",
            listOf("detik" to "Durasi dalam detik."), listOf("label" to "Label timer.")))
        a.put(fn("daftar_pengingat", "Menampilkan semua pengingat, alarm, dan timer yang aktif."))
        a.put(fn("hapus_pengingat", "Menghapus pengingat, alarm, atau timer.", listOf("id" to "Nomornya, atau kata semua.")))
        a.put(fn("batalkan_terakhir", "Membatalkan tindakan terakhir Asta (misalnya alarm yang baru dibuat atau catatan yang baru ditambah)."))
        a.put(fn("cari_web", "Mencari informasi terbaru di internet lewat Google Search dan merangkumnya.",
            listOf("pertanyaan" to "Pertanyaan atau kata kunci pencarian.")))
        a.put(fn("baca_halaman", "Membuka sebuah halaman web dan mengembalikan teks isinya (untuk dibaca atau dirangkum).",
            listOf("url" to "Alamat halaman, diawali https://")))
        a.put(fn("buka_situs", "Membuka sebuah situs di browser pengguna.", listOf("url" to "Alamat situs, diawali https://")))
        a.put(fn("cari_youtube", "Membuka hasil pencarian YouTube untuk lagu atau video.", listOf("judul" to "Judul lagu atau video.")))
        a.put(fn("daftar_aplikasi", "Daftar aplikasi yang diizinkan pengguna untuk Asta, beserta tingkat izinnya."))
        a.put(fn("buka_aplikasi", "Membuka aplikasi lewat namanya. Hanya untuk aplikasi yang diizinkan.", listOf("nama" to "Nama aplikasi, misalnya WhatsApp.")))
        a.put(fn("kirim_teks_ke_aplikasi", "Menyiapkan teks di aplikasi lain lewat menu bagikan. Pengguna diminta menyetujui dan menekan kirim sendiri. Hanya untuk aplikasi tingkat 2 ke atas.",
            listOf("nama" to "Nama aplikasi tujuan.", "teks" to "Teks yang akan dikirim.")))
        a.put(fn("buka_izin_asta", "Membuka layar Izin Asta untuk mengatur izin tiap aplikasi, menghentikan Asta, dan membaca log."))
        return a
    }

    private fun urlAman(u: String): String? {
        val s = u.trim()
        return if (s.startsWith("https://", true) || s.startsWith("http://", true)) s else null
    }

    fun jalankan(ctx: Context, nama: String, args: JSONObject): String {
        return when (nama) {
            "waktu_sekarang" -> SimpleDateFormat("EEEE, d MMMM yyyy, 'pukul' HH:mm", Locale("id", "ID")).format(Date())
            "catat" -> {
                val t = args.optString("teks").trim()
                if (t.isEmpty()) return "Teks catatan kosong."
                val id = Catatan.tambah(ctx, t)
                catatUrung("catatan \"" + t.take(30) + "\"") { Catatan.hapus(ctx, id) }
                "Sudah dicatat."
            }
            "buka_catatan" -> Pembuka.buka(ctx, Intent(ctx, CatatanActivity::class.java), "Catatan")
            "atur_pengingat" -> Alarm.pengingat(ctx, args.optString("pesan"), args.optString("waktu"))
            "atur_alarm" -> Alarm.alarm(ctx, args.optString("jam"), args.optString("pesan"), args.optString("ulang"))
            "atur_timer" -> Alarm.timer(ctx, args.optString("detik"), args.optString("label"))
            "daftar_pengingat" -> Alarm.daftar(ctx)
            "hapus_pengingat" -> Alarm.hapus(ctx, args.optString("id"))
            "batalkan_terakhir" -> batalkanTerakhir()
            "cari_web" -> {
                val q = args.optString("pertanyaan").trim()
                if (q.isEmpty()) "Pertanyaan pencarian kosong." else Web.cari(ctx, q)
            }
            "baca_halaman" -> Web.bacaHalaman(args.optString("url"))
            "buka_situs" -> {
                val u = urlAman(args.optString("url")) ?: return "Hanya alamat http atau https yang boleh dibuka."
                Pembuka.buka(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(u)), "Situs")
            }
            "cari_youtube" -> {
                val j = args.optString("judul").trim()
                if (j.isEmpty()) return "Judul kosong."
                Pembuka.buka(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(j, "UTF-8"))), "YouTube")
            }
            "daftar_aplikasi" -> Aksi.daftarDiizinkan(ctx)
            "buka_aplikasi" -> Aksi.buka(ctx, args.optString("nama"))
            "kirim_teks_ke_aplikasi" -> Aksi.kirimTeks(ctx, args.optString("nama"), args.optString("teks"))
            "buka_izin_asta" -> Pembuka.buka(ctx, Intent(ctx, IzinActivity::class.java), "Izin Asta")
            else -> "Alat tidak dikenal."
        }
    }
}
