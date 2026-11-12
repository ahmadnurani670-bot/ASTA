package id.asta.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Layar sinkronisasi antar HP lewat Gist rahasia di GitHub. */
class SinkronActivity : Activity() {
    private lateinit var token: EditText
    private lateinit var sandi: EditText
    private lateinit var status: TextView

    private fun isian(isi: LinearLayout, label: String, hint: String): EditText {
        isi.addView(buatTeks(label, 13f, Warna.REDUP), paramLebar(12))
        val e = EditText(this)
        e.hint = hint
        e.setTextColor(Warna.TEKS)
        e.setHintTextColor(Warna.REDUP)
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        isi.addView(e)
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gulir = ScrollView(this)
        gulir.setBackgroundColor(Warna.LATAR)
        val isi = LinearLayout(this)
        isi.orientation = LinearLayout.VERTICAL
        isi.setPadding(dp(16), dp(24), dp(16), dp(40))
        gulir.addView(isi)
        setContentView(gulir)

        isi.addView(buatTeks("Sinkronisasi antar HP", 22f, Warna.CYAN, true))
        isi.addView(buatTeks(
            "Yang disinkronkan: catatan, riwayat percakapan, Kontak Asta, dan setelan (nama, mode, kecepatan, model, suara). " +
                "Tidak ikut: kunci API Gemini, alarm dan pengingat, serta pilihan khusus perangkat (gelembung, latar belakang).",
            13f, Warna.TEKS,
        ))
        isi.addView(buatTeks(
            "Data disimpan sebagai Gist rahasia di akun GitHub Anda dan dienkripsi dengan kata sandi di bawah, jadi GitHub tidak bisa membacanya.",
            12f, Warna.REDUP,
        ))
        isi.addView(buatTeks(
            "Cara membuat token: buka github.com/settings/tokens, pilih Generate new token (classic), centang hanya izin gist, lalu salin tokennya. " +
                "Di HP lain, isi token dan kata sandi yang sama.",
            12f, Warna.AMBER,
        ), paramLebar(8))

        val aktif = Sinkron.aktif(this)
        token = isian(isi, "Token GitHub (izin gist)", if (aktif) "Tersimpan. Ketik untuk mengganti." else "ghp_...")
        sandi = isian(isi, "Kata sandi enkripsi (sama di semua HP)", if (aktif) "Tersimpan. Ketik untuk mengganti." else "Pilih kata sandi yang kuat")
        isi.addView(buatTeks("Kalau kata sandi lupa, data di Gist tidak bisa dibuka. Hapus Gist bernama \"Asta sinkron (terenkripsi)\" di GitHub untuk mulai ulang.", 12f, Warna.REDUP), paramLebar(4))

        status = buatTeks(if (Sinkron.status.isEmpty()) "Belum pernah disinkronkan." else Sinkron.status, 13f, Warna.CYAN)
        isi.addView(status, paramLebar(12))

        isi.addView(buatTombol("Simpan dan sinkronkan sekarang") { simpanDanJalankan() }, paramLebar(12))
        isi.addView(buatTombol("Putuskan sinkronisasi di HP ini") {
            AlertDialog.Builder(this)
                .setMessage("Hapus token dan kata sandi dari HP ini? Data di Gist tidak dihapus.")
                .setPositiveButton("Putuskan") { _, _ ->
                    Sinkron.putuskan(this)
                    status.text = Sinkron.status
                    token.hint = "ghp_..."
                    sandi.hint = "Pilih kata sandi yang kuat"
                }
                .setNegativeButton("Batal", null)
                .show()
        }, paramLebar())
    }

    private fun simpanDanJalankan() {
        val t = token.text.toString().trim()
        val s = sandi.text.toString().trim()
        if (t.isNotEmpty()) Setelan.simpanRahasia(this, "gh_token", t)
        if (s.isNotEmpty()) {
            if (s.length < 6) {
                Toast.makeText(this, "Kata sandi minimal 6 karakter.", Toast.LENGTH_LONG).show()
                return
            }
            Setelan.simpanRahasia(this, "gh_sandi", s)
        }
        token.setText("")
        sandi.setText("")
        if (!Sinkron.aktif(this)) {
            status.text = "Isi token GitHub dan kata sandi dulu."
            return
        }
        token.hint = "Tersimpan. Ketik untuk mengganti."
        sandi.hint = "Tersimpan. Ketik untuk mengganti."
        status.text = "Menyinkronkan..."
        Sinkron.jalankan(this) { pesan -> status.text = pesan }
    }
}
