package id.asta.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
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
        isi.addView(buatTeks("Asta hanya boleh menelepon kontak di daftar ini, dan selalu meminta persetujuan Anda dulu. Ketuk satu kontak untuk menyimpannya ke kontak ponsel atau menghapusnya. Panggilan WhatsApp hanya bisa dimulai kalau nomornya juga tersimpan di kontak ponsel.", 13f, Warna.REDUP))
        isi.addView(buatTombol("Tambah kontak") { tambah() }, paramLebar())
        val daftar = KontakAsta.semua(this)
        if (daftar.isEmpty()) {
            isi.addView(buatTeks("Belum ada kontak.", 14f, Warna.REDUP), paramLebar(12))
            return
        }
        for (k in daftar) {
            val t = buatTeks(k.nama + "\n" + k.nomor, 15f, Warna.TEKS)
            t.setPadding(0, dp(10), 0, dp(10))
            t.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(k.nama)
                    .setItems(arrayOf("Simpan ke kontak ponsel (agar WhatsApp bisa dipanggil)", "Hapus dari daftar Asta")) { _, w ->
                        if (w == 0) simpanKePonsel(k) else { KontakAsta.hapus(this, k.nama); bangun() }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
            isi.addView(t)
        }
    }

    private fun simpanKePonsel(k: Kontak) {
        val i = Intent(Intent.ACTION_INSERT)
        i.type = ContactsContract.Contacts.CONTENT_TYPE
        i.putExtra(ContactsContract.Intents.Insert.NAME, k.nama)
        i.putExtra(ContactsContract.Intents.Insert.PHONE, "+" + k.nomor)
        try {
            startActivity(i)
            Toast.makeText(this, "Simpan kontaknya, lalu tunggu WhatsApp menyinkronkan (atau buka WhatsApp dan segarkan kontak).", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak ada aplikasi Kontak untuk menyimpan.", Toast.LENGTH_LONG).show()
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
