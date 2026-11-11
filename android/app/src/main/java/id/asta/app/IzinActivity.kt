package id.asta.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Layar "Izin Asta": tingkat izin per aplikasi, status berjalan, dan log tindakan. */
class IzinActivity : Activity() {
    private lateinit var status: LinearLayout
    private lateinit var daftarView: LinearLayout
    private var semua: List<Aksi.Apl> = emptyList()
    private var saring = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gulir = ScrollView(this)
        gulir.setBackgroundColor(Warna.LATAR)
        val isi = LinearLayout(this)
        isi.orientation = LinearLayout.VERTICAL
        isi.setPadding(dp(16), dp(24), dp(16), dp(32))
        gulir.addView(isi)
        setContentView(gulir)

        isi.addView(buatTeks("Izin Asta", 22f, Warna.CYAN, true))
        isi.addView(buatTeks("Semua aplikasi mulai dari Tingkat 0. Asta hanya menyentuh aplikasi yang Anda naikkan tingkatnya.", 14f, Warna.TEKS))
        for (p in Izin.PENJELASAN) isi.addView(buatTeks(p, 12f, Warna.REDUP))
        isi.addView(buatTeks(
            "Aplikasi bank, dompet digital, pengelola kata sandi, Pengaturan, dan pemasang aplikasi dikunci: tidak bisa naik ke tingkat 3 atau 4.",
            12f, Warna.AMBER,
        ))

        status = LinearLayout(this)
        status.orientation = LinearLayout.VERTICAL
        isi.addView(status, paramLebar())

        isi.addView(buatTombol("Lihat log tindakan") { tampilLog() }, paramLebar())

        val cari = EditText(this)
        cari.hint = "Cari aplikasi"
        cari.setTextColor(Warna.TEKS)
        cari.setHintTextColor(Warna.REDUP)
        cari.inputType = InputType.TYPE_CLASS_TEXT
        cari.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saring = (s?.toString() ?: "").trim().lowercase()
                isiDaftar()
            }
        })
        isi.addView(cari, paramLebar(16))

        daftarView = LinearLayout(this)
        daftarView.orientation = LinearLayout.VERTICAL
        isi.addView(daftarView)
    }

    override fun onResume() {
        super.onResume()
        semua = Aksi.semuaApl(this)
        segarkanStatus()
        isiDaftar()
    }

    private fun segarkanStatus() {
        status.removeAllViews()
        if (Izin.dijeda(this)) {
            status.addView(buatTeks("Asta sedang dihentikan. Tidak ada tindakan ke aplikasi lain yang dijalankan.", 14f, Warna.AMBER))
            status.addView(buatTombol("Lanjutkan Asta") {
                Izin.setDijeda(this, false)
                AsService.mulai(this)
                CatatanAsta.tulis(this, "Asta dilanjutkan oleh pengguna")
                segarkanStatus()
            }, paramLebar())
        } else {
            status.addView(buatTeks("Asta aktif.", 14f, Warna.CYAN))
            status.addView(buatTombol("Hentikan Asta") {
                AsService.hentikan(this)
                segarkanStatus()
            }, paramLebar())
        }
    }

    private fun isiDaftar() {
        daftarView.removeAllViews()
        val tampil = semua
            .filter { saring.isEmpty() || it.label.lowercase().contains(saring) }
            .sortedByDescending { Izin.tingkat(this, it.pkg) }
        if (tampil.isEmpty()) {
            daftarView.addView(buatTeks("Tidak ada aplikasi yang cocok.", 14f, Warna.REDUP))
            return
        }
        for (a in tampil) {
            val t = Izin.tingkat(this, a.pkg)
            val kunci = Izin.terblokir(a.pkg, a.label)
            val baris = LinearLayout(this)
            baris.orientation = LinearLayout.VERTICAL
            baris.setPadding(0, dp(8), 0, dp(8))
            baris.addView(buatTeks(a.label, 16f, Warna.TEKS, true))
            val sub = "Tingkat " + t + " - " + Izin.NAMA[t] + (if (kunci) "  (dikunci: maksimal tingkat 2)" else "")
            baris.addView(buatTeks(sub, 13f, if (t > 0) Warna.CYAN else Warna.REDUP))
            baris.isClickable = true
            baris.setOnClickListener { pilih(a) }
            daftarView.addView(baris)
        }
    }

    private fun pilih(a: Aksi.Apl) {
        val sekarang = Izin.tingkat(this, a.pkg)
        val kunci = Izin.terblokir(a.pkg, a.label)
        val item = (0..4).map {
            "" + it + ". " + Izin.NAMA[it] + when {
                it > Izin.TERTINGGI_SEKARANG -> " (belum tersedia)"
                it >= Izin.NOTIF && kunci -> " (dilarang)"
                else -> ""
            }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(a.label)
            .setSingleChoiceItems(item, sekarang) { d, which ->
                val galat = Izin.atur(this, a.pkg, a.label, which)
                if (galat != null) {
                    Toast.makeText(this, galat, Toast.LENGTH_LONG).show()
                } else {
                    d.dismiss()
                    isiDaftar()
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun tampilLog() {
        AlertDialog.Builder(this)
            .setTitle("Log tindakan Asta")
            .setMessage(CatatanAsta.baca(this))
            .setPositiveButton("Tutup", null)
            .setNeutralButton("Hapus log") { _, _ -> CatatanAsta.hapus(this) }
            .show()
    }
}
