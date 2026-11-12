package id.asta.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Daftar Kontak Asta: hanya kontak di sini yang boleh ditelepon Asta lewat WhatsApp. */
class KontakActivity : Activity() {
    private lateinit var isi: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gulir = ScrollView(this)
        gulir.setBackgroundColor(Warna.LATAR)
        isi = LinearLayout(this)
        isi.orientation = LinearLayout.VERTICAL
        isi.setPadding(dp(16), dp(24), dp(16), dp(32))
        gulir.addView(isi)
        setContentView(gulir)
    }

    override fun onResume() {
        super.onResume()
        bangun()
    }

    private fun bangun() {
        isi.removeAllViews()
        isi.addView(buatTeks("Kontak Asta", 22f, Warna.CYAN, true))
        isi.addView(buatTeks("Asta hanya boleh menelepon kontak di daftar ini, dan selalu meminta persetujuan Anda dulu. Ketuk lama untuk menghapus.", 13f, Warna.REDUP))
        isi.addView(buatTombol("Tambah kontak") { tambah() }, paramLebar())
        val daftar = KontakAsta.semua(this)
        if (daftar.isEmpty()) {
            isi.addView(buatTeks("Belum ada kontak.", 14f, Warna.REDUP), paramLebar(12))
            return
        }
        for (k in daftar) {
            val t = buatTeks(k.nama + "\n" + k.nomor, 15f, Warna.TEKS)
            t.setPadding(0, dp(10), 0, dp(10))
            t.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setMessage("Hapus " + k.nama + " dari daftar?")
                    .setPositiveButton("Hapus") { _, _ -> KontakAsta.hapus(this, k.nama); bangun() }
                    .setNegativeButton("Batal", null)
                    .show()
                true
            }
            isi.addView(t)
        }
    }

    private fun tambah() {
        val kotak = LinearLayout(this)
        kotak.orientation = LinearLayout.VERTICAL
        kotak.setPadding(dp(20), dp(8), dp(20), 0)
        val nama = EditText(this)
        nama.hint = "Nama (cara Anda menyebutnya)"
        nama.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        val nomor = EditText(this)
        nomor.hint = "Nomor WhatsApp, misalnya 0812xxxxxxxx"
        nomor.inputType = InputType.TYPE_CLASS_PHONE
        kotak.addView(nama)
        kotak.addView(nomor)
        AlertDialog.Builder(this)
            .setTitle("Tambah kontak")
            .setView(kotak)
            .setPositiveButton("Simpan") { _, _ ->
                val galat = KontakAsta.tambah(this, nama.text.toString(), nomor.text.toString())
                if (galat != null) Toast.makeText(this, galat, Toast.LENGTH_LONG).show()
                bangun()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
