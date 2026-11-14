package id.asta.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Layar Periksa: menunjukkan apa yang terlihat Asta untuk kalender dan WhatsApp, supaya masalah mudah dilacak. */
class DiagnosaActivity : Activity() {
    private lateinit var hasil: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gulir = ScrollView(this)
        gulir.setBackgroundColor(Warna.LATAR)
        val isi = LinearLayout(this)
        isi.orientation = LinearLayout.VERTICAL
        isi.setPadding(dp(16), dp(24), dp(16), dp(40))
        gulir.addView(isi)
        setContentView(gulir)

        isi.addView(buatTeks("Periksa kalender dan WhatsApp", 22f, Warna.CYAN, true))
        isi.addView(buatTeks("Ketuk tombol di bawah. Hasilnya bisa disalin ke saya kalau masih bermasalah.", 13f, Warna.REDUP))
        isi.addView(buatTombol("1. Berikan izin kalender dan kontak") {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CONTACTS), 3)
        }, paramLebar(12))
        isi.addView(buatTombol("2. Periksa kalender") { periksaKalender() }, paramLebar())
        isi.addView(buatTombol("3. Tes tambah lalu hapus acara") { tesAcara() }, paramLebar())
        isi.addView(buatTombol("4. Periksa WhatsApp dan kontak") { periksaWa() }, paramLebar())
        isi.addView(buatTombol("5. Tes buka browser dari latar belakang") { tesLatar("Browser") {
            Pembuka.buka(this, android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")), "Browser")
        } }, paramLebar())
        isi.addView(buatTombol("6. Tes buka aplikasi dari latar belakang") { tesLatar("Aplikasi") {
            val a = Aksi.semuaApl(this).firstOrNull { it.label.contains("Kalkulator", true) || it.label.contains("Calculator", true) || it.label.contains("Jam", true) || it.label.contains("Clock", true) }
            if (a == null) "Tidak ada aplikasi tes (Kalkulator/Jam) di ponsel ini." else {
                val i = packageManager.getLaunchIntentForPackage(a.pkg)
                if (i == null) a.label + " tidak punya pintu masuk." else Pembuka.buka(this, i, a.label)
            }
        } }, paramLebar())
        hasil = buatTeks("", 13f, Warna.TEKS)
        hasil.setTextIsSelectable(true)
        isi.addView(hasil, paramLebar(16))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val t = StringBuilder("Hasil izin:\n")
        for (i in permissions.indices) {
            t.append(permissions[i].substringAfterLast('.')).append(": ")
                .append(if (grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED) "diberikan" else "DITOLAK").append('\n')
        }
        t.append("\nKalau ditolak dan dialog tidak muncul lagi, buka Pengaturan Android > Aplikasi > Asta > Izin, lalu izinkan Kalender dan Kontak.")
        hasil.text = t.toString()
    }

    private fun periksaKalender() {
        val t = StringBuilder()
        t.append("Izin baca kalender: ").append(ok(Manifest.permission.READ_CALENDAR)).append('\n')
        t.append("Izin tulis kalender: ").append(ok(Manifest.permission.WRITE_CALENDAR)).append('\n')
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            try {
                val d = Kalender.daftarKalender(this)
                t.append("Jumlah kalender: ").append(d.size).append('\n')
                for (k in d) {
                    t.append("- ").append(k.nama).append(" | ").append(k.akun).append(" | ").append(k.jenis)
                        .append(" | ").append(if (k.bisaTulis) "bisa ditulis" else "hanya baca")
                        .append(if (k.tampil) "" else " | disembunyikan").append('\n')
                }
                t.append("\nJadwal 7 hari ke depan:\n").append(Kalender.lihat(this, "", ""))
            } catch (e: Exception) {
                t.append("Galat: ").append(e.javaClass.simpleName).append(' ').append(e.message ?: "").append('\n')
            }
        }
        hasil.text = t.toString()
    }

    private fun tesAcara() {
        hasil.text = "Menguji..."
        Thread {
            val besok = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(Date(System.currentTimeMillis() + 36L * 3600 * 1000))
            var r = Kalender.tambah(this, "Tes Asta (boleh dihapus)", besok, "", "", "0")
            if (r.startsWith("Acara #")) {
                val id = r.substringAfter("#").substringBefore(' ')
                Alat.batalkanTerakhir()
                r += "\nAcara tes sudah dihapus lagi. (nomor $id)"
            }
            runOnUiThread { hasil.text = r }
        }.start()
    }

    /** Menunggu 8 detik supaya pengguna sempat ke layar utama, lalu mencoba membuka sesuatu seperti saat Asta di latar belakang. */
    private fun tesLatar(nama: String, aksi: () -> String) {
        hasil.text = "Ketuk tombol Beranda sekarang. Dalam 8 detik Asta akan mencoba membuka $nama. Kembali ke sini sesudahnya untuk melihat hasil."
        Thread {
            Thread.sleep(8000)
            val r = try { aksi() } catch (e: Exception) { "Galat: " + e.javaClass.simpleName + " " + (e.message ?: "") }
            runOnUiThread { hasil.text = "Hasil tes $nama:\n" + r }
        }.start()
    }

    private fun periksaWa() {
        hasil.text = try { KontakAsta.diagnosa(this) } catch (e: Exception) { "Galat: " + e.javaClass.simpleName + " " + (e.message ?: "") }
    }

    private fun ok(izin: String): String =
        if (checkSelfPermission(izin) == PackageManager.PERMISSION_GRANTED) "ya" else "BELUM"
}
