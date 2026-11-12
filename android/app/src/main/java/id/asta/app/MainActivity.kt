package id.asta.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Layar obrolan Asta. Semua logika ada di Asta; layar ini hanya menampilkan dan mengirim. */
class MainActivity : Activity(), Asta.Pendengar {
    private lateinit var log: LinearLayout
    private lateinit var gulir: ScrollView
    private lateinit var kolom: EditText
    private lateinit var status: TextView
    private lateinit var tMik: Button
    private lateinit var tSuara: Button
    private lateinit var chip: TextView
    private var lampiran: Lampiran? = null
    private var gelAsta: TextView? = null
    private var selesai = true

    companion object {
        private const val KODE_BERKAS = 100
        private const val KODE_MIK = 101
        private const val KODE_NOTIF = 102
        private const val BATAS_BERKAS = 10 * 1024 * 1024
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifikasi.siapkanSaluran(this)

        val akar = LinearLayout(this)
        akar.orientation = LinearLayout.VERTICAL
        akar.setBackgroundColor(Warna.LATAR)

        val kepala = LinearLayout(this)
        kepala.orientation = LinearLayout.HORIZONTAL
        kepala.gravity = Gravity.CENTER_VERTICAL
        kepala.setPadding(dp(16), dp(14), dp(16), dp(4))
        kepala.addView(buatTeks("A S T A", 20f, Warna.CYAN, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        status = buatTeks("SIAGA", 12f, Warna.REDUP)
        kepala.addView(status)
        akar.addView(kepala)

        val baris = LinearLayout(this)
        baris.orientation = LinearLayout.HORIZONTAL
        baris.setPadding(dp(8), 0, dp(8), dp(4))
        tSuara = mini("") {
            val baru = !Setelan.bool(this, "suara", true)
            Setelan.simpanBool(this, "suara", baru)
            if (!baru) Suara.berhenti()
            segarkanSuara()
        }
        baris.addView(tSuara)
        baris.addView(mini("Catatan") { startActivity(Intent(this, CatatanActivity::class.java)) })
        baris.addView(mini("Pengingat") { startActivity(Intent(this, PengingatActivity::class.java)) })
        baris.addView(mini("Kontak") { startActivity(Intent(this, KontakActivity::class.java)) })
        baris.addView(mini("Izin") { startActivity(Intent(this, IzinActivity::class.java)) })
        baris.addView(mini("Setelan") { startActivity(Intent(this, SetelanActivity::class.java)) })
        val gulirMenu = HorizontalScrollView(this)
        gulirMenu.isHorizontalScrollBarEnabled = false
        gulirMenu.addView(baris)
        akar.addView(gulirMenu)

        gulir = ScrollView(this)
        log = LinearLayout(this)
        log.orientation = LinearLayout.VERTICAL
        log.setPadding(dp(12), dp(4), dp(12), dp(12))
        gulir.addView(log)
        akar.addView(gulir, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        chip = buatTeks("", 13f, Warna.AMBER)
        chip.visibility = View.GONE
        chip.setPadding(dp(16), dp(4), dp(16), dp(4))
        chip.setOnClickListener { lampiran = null; chip.visibility = View.GONE }
        akar.addView(chip)

        val masuk = LinearLayout(this)
        masuk.orientation = LinearLayout.HORIZONTAL
        masuk.gravity = Gravity.CENTER_VERTICAL
        masuk.setPadding(dp(8), dp(4), dp(8), dp(8))
        masuk.addView(mini("+") { pilihBerkas() })
        tMik = mini("Mik") { ketukMik() }
        masuk.addView(tMik)
        kolom = EditText(this)
        kolom.hint = "Ketik pesan"
        kolom.setTextColor(Warna.TEKS)
        kolom.setHintTextColor(Warna.REDUP)
        kolom.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        kolom.maxLines = 4
        masuk.addView(kolom, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        masuk.addView(mini("Kirim") { kirim() })
        akar.addView(masuk)
        setContentView(akar)

        segarkanSuara()
        val lama = Otak.salinan(this)
        if (lama.isEmpty()) tambah("info", "Asta siap. Ketik atau bicara.")
        for ((dari, teks) in lama) tambah(dari, teks)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), KODE_NOTIF)
        }
        if (Setelan.kunciApi(this).isEmpty()) {
            tambah("info", "Kunci API Gemini belum diisi. Buka Setelan.")
            startActivity(Intent(this, SetelanActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        Asta.layarTampil = true
        Asta.daftarkan(this)
        keadaan(Asta.keadaan)
        AsService.mulai(this)
        if (Izin.dijeda(this)) tambah("info", "Asta sedang dihentikan: tindakan ke aplikasi lain ditolak. Lanjutkan di layar Izin.")
    }

    override fun onStop() {
        Asta.layarTampil = false
        Asta.lepas(this)
        super.onStop()
    }

    private fun mini(teks: String, aksi: () -> Unit): Button {
        val b = buatTombol(teks, aksi)
        b.textSize = 12f
        b.minWidth = 0
        b.minimumWidth = 0
        return b
    }

    private fun segarkanSuara() {
        tSuara.text = if (Setelan.bool(this, "suara", true)) "Suara: ON" else "Suara: OFF"
    }

    private fun gulirBawah() {
        gulir.post { gulir.fullScroll(View.FOCUS_DOWN) }
    }

    private fun tambah(dari: String, teks: String): TextView {
        val t = TextView(this)
        t.text = teks
        t.textSize = 15f
        t.setTextIsSelectable(true)
        val lat = GradientDrawable()
        lat.cornerRadius = dp(14).toFloat()
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        when (dari) {
            "saya" -> {
                lat.setColor(Color.parseColor("#12385a"))
                t.setTextColor(Warna.TEKS)
                lp.gravity = Gravity.END
                lp.leftMargin = dp(48)
            }
            "asta" -> {
                lat.setColor(Color.parseColor("#0a1c2e"))
                lat.setStroke(dp(1), Color.parseColor("#1a4a6e"))
                t.setTextColor(Warna.TEKS)
                lp.gravity = Gravity.START
                lp.rightMargin = dp(48)
            }
            else -> {
                lat.setColor(Color.TRANSPARENT)
                t.setTextColor(Warna.REDUP)
                t.textSize = 12f
                lp.gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        t.background = lat
        t.setPadding(dp(12), dp(8), dp(12), dp(8))
        log.addView(t, lp)
        gulirBawah()
        return t
    }

    private fun kirim() {
        val teks = kolom.text.toString().trim()
        if (teks.isEmpty() && lampiran == null) return
        if (Setelan.kunciApi(this).isEmpty()) {
            Toast.makeText(this, "Isi kunci API di Setelan dulu.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SetelanActivity::class.java))
            return
        }
        val l = lampiran
        kolom.setText("")
        lampiran = null
        chip.visibility = View.GONE
        Asta.kirim(this, teks, l)
    }

    private fun ketukMik() {
        if (Asta.keadaan != "siaga") {
            Asta.berhenti()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), KODE_MIK)
            return
        }
        Asta.dengarLalu(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == KODE_MIK && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            AsService.mulai(this)
            Asta.dengarLalu(this)
        }
    }

    private fun pilihBerkas() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "*/*"
        i.addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(i, KODE_BERKAS)
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak ada aplikasi pemilih berkas.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != KODE_BERKAS || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        Thread {
            try {
                var nama = "berkas"
                contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val k = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (k >= 0 && c.moveToFirst()) nama = c.getString(k)
                }
                val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                runOnUiThread {
                    if (bytes.isEmpty()) {
                        Toast.makeText(this, "Berkas kosong atau tidak bisa dibaca.", Toast.LENGTH_LONG).show()
                    } else if (bytes.size > BATAS_BERKAS) {
                        Toast.makeText(this, "Berkas terlalu besar (maksimal 10 MB).", Toast.LENGTH_LONG).show()
                    } else {
                        lampiran = Lampiran(nama, mime, Base64.encodeToString(bytes, Base64.NO_WRAP))
                        chip.text = "Lampiran: $nama (ketuk untuk membuang)"
                        chip.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Berkas gagal dibaca.", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    // ---------- Asta.Pendengar ----------

    override fun pesanSaya(teks: String) { tambah("saya", teks) }

    override fun astaMulai() {
        gelAsta = tambah("asta", "")
        selesai = false
    }

    override fun astaKalimat(teks: String) {
        val g = gelAsta ?: return
        if (selesai) return
        g.text = if (g.text.isEmpty()) teks else g.text.toString() + " " + teks
        gulirBawah()
    }

    override fun astaSelesai(penuh: String) {
        val g = gelAsta ?: tambah("asta", "").also { gelAsta = it }
        g.text = penuh
        selesai = true
        gulirBawah()
    }

    override fun keadaan(k: String) {
        status.text = k.uppercase()
        status.setTextColor(
            when (k) {
                "dengar" -> Color.parseColor("#4cff9a")
                "berpikir" -> Warna.AMBER
                "bicara" -> Color.parseColor("#6aa8ff")
                else -> Warna.REDUP
            }
        )
        tMik.text = if (k == "siaga") "Mik" else "Stop"
    }

    override fun info(teks: String, galat: Boolean) {
        val t = tambah("info", teks)
        if (galat) t.setTextColor(Warna.AMBER)
    }
}
