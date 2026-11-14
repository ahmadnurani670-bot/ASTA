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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Layar obrolan Asta. Semua logika ada di Asta; layar ini hanya menampilkan dan mengirim. */
class MainActivity : Activity(), Asta.Pendengar {
    private lateinit var log: LinearLayout
    private lateinit var gulir: ScrollView
    private lateinit var kolom: EditText
    private lateinit var status: TextView
    private lateinit var tMik: TextView
    private lateinit var tMenu: TextView
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

        // Kepala: nama dan keadaan di kiri, satu menu di kanan.
        val kepala = LinearLayout(this)
        kepala.orientation = LinearLayout.HORIZONTAL
        kepala.gravity = Gravity.CENTER_VERTICAL
        kepala.setPadding(dp(20), dp(16), dp(8), dp(10))
        val judul = LinearLayout(this)
        judul.orientation = LinearLayout.VERTICAL
        val nama = buatTeks("Asta", 24f, Warna.TEKS, true)
        nama.letterSpacing = 0.12f
        nama.setPadding(0, 0, 0, 0)
        judul.addView(nama)
        status = buatTeks("Siaga", 12f, Warna.REDUP)
        status.setPadding(0, 0, 0, 0)
        judul.addView(status)
        kepala.addView(judul, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tMenu = datar("Menu") { bukaMenu() }
        kepala.addView(tMenu)
        akar.addView(kepala)

        val garis = View(this)
        garis.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Warna.CYAN, Warna.UNGU, Color.TRANSPARENT),
        )
        garis.alpha = 0.55f
        akar.addView(garis, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))

        gulir = ScrollView(this)
        gulir.isVerticalScrollBarEnabled = false
        log = LinearLayout(this)
        log.orientation = LinearLayout.VERTICAL
        log.setPadding(dp(16), dp(8), dp(16), dp(16))
        gulir.addView(log)
        akar.addView(gulir, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        chip = buatTeks("", 13f, Warna.CYAN)
        chip.visibility = View.GONE
        chip.setPadding(dp(20), dp(6), dp(20), dp(6))
        chip.setOnClickListener { lampiran = null; chip.visibility = View.GONE }
        akar.addView(chip)

        // Kolom masukan: satu kapsul berisi lampiran, teks, dan mik; tombol kirim di luarnya.
        val masuk = LinearLayout(this)
        masuk.orientation = LinearLayout.HORIZONTAL
        masuk.gravity = Gravity.CENTER_VERTICAL
        masuk.setPadding(dp(12), dp(6), dp(12), dp(12))
        val kapsul = LinearLayout(this)
        kapsul.orientation = LinearLayout.HORIZONTAL
        kapsul.gravity = Gravity.CENTER_VERTICAL
        kapsul.background = kotak(Warna.PERMUKAAN, 24, Warna.GARIS)
        kapsul.setPadding(dp(4), dp(2), dp(4), dp(2))
        kapsul.addView(datar("Lampir") { pilihBerkas() })
        kolom = EditText(this)
        kolom.hint = "Ketik pesan"
        kolom.textSize = 15f
        kolom.background = null
        kolom.setPadding(dp(6), dp(10), dp(6), dp(10))
        kolom.setTextColor(Warna.TEKS)
        kolom.setHintTextColor(Warna.REDUP)
        kolom.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        kolom.maxLines = 4
        kapsul.addView(kolom, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tMik = datar("Mik") { ketukMik() }
        kapsul.addView(tMik)
        masuk.addView(kapsul, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val tombolKirim = buatTombol("Kirim", true) { kirim() }
        val lpK = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48))
        lpK.leftMargin = dp(8)
        masuk.addView(tombolKirim, lpK)
        akar.addView(masuk)
        setContentView(akar)

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
        Gelembung.sembunyi()
        Asta.daftarkan(this)
        keadaan(Asta.keadaan)
        AsService.mulai(this)
        Sinkron.jadwalkan(this, 1500)
        if (Izin.dijeda(this)) tambah("info", "Asta sedang dihentikan: tindakan ke aplikasi lain ditolak. Lanjutkan di layar Izin.")
    }

    override fun onStop() {
        Asta.layarTampil = false
        if (Setelan.bool(this, "gelembung", false) && Setelan.bool(this, "latar", true) && !Izin.dijeda(this) && Gelembung.bisa(this)) Gelembung.tampil(this)
        Asta.lepas(this)
        super.onStop()
    }

    /** Tombol teks tanpa latar, untuk aksi kecil di kepala dan kolom masukan. */
    private fun datar(teks: String, aksi: () -> Unit): TextView {
        val t = TextView(this)
        t.text = teks
        t.textSize = 14f
        t.setTextColor(Warna.CYAN)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(12), dp(10), dp(12), dp(10))
        t.setOnClickListener { aksi() }
        return t
    }

    private fun bukaMenu() {
        val p = PopupMenu(this, tMenu)
        val suaraNyala = Setelan.bool(this, "suara", true)
        p.menu.add(0, 1, 0, "Catatan")
        p.menu.add(0, 2, 1, "Pengingat")
        p.menu.add(0, 3, 2, "Kontak")
        p.menu.add(0, 4, 3, "Izin")
        p.menu.add(0, 5, 4, if (suaraNyala) "Matikan suara" else "Nyalakan suara")
        p.menu.add(0, 6, 5, "Setelan")
        p.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> startActivity(Intent(this, CatatanActivity::class.java))
                2 -> startActivity(Intent(this, PengingatActivity::class.java))
                3 -> startActivity(Intent(this, KontakActivity::class.java))
                4 -> startActivity(Intent(this, IzinActivity::class.java))
                5 -> {
                    Setelan.simpanBool(this, "suara", !suaraNyala)
                    if (suaraNyala) Suara.berhenti()
                    Toast.makeText(this, if (suaraNyala) "Suara dimatikan." else "Suara dinyalakan.", Toast.LENGTH_SHORT).show()
                }
                6 -> startActivity(Intent(this, SetelanActivity::class.java))
            }
            true
        }
        p.show()
    }

    private fun gulirBawah() {
        gulir.post { gulir.fullScroll(View.FOCUS_DOWN) }
    }

    private fun tambah(dari: String, teks: String): TextView {
        val t = TextView(this)
        t.text = teks
        t.textSize = 15f
        t.setLineSpacing(0f, 1.15f)
        t.setTextIsSelectable(true)
        val r = dp(18).toFloat()
        val s = dp(5).toFloat()
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10)
        when (dari) {
            "saya" -> {
                val lat = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Warna.UNGU_TUA, Warna.BIRU_TUA))
                lat.cornerRadii = floatArrayOf(r, r, r, r, s, s, r, r)
                t.background = lat
                t.setTextColor(Color.WHITE)
                lp.gravity = Gravity.END
                lp.leftMargin = dp(56)
            }
            "asta" -> {
                val lat = GradientDrawable()
                lat.setColor(Warna.PERMUKAAN)
                lat.setStroke(dp(1), Warna.GARIS)
                lat.cornerRadii = floatArrayOf(r, r, r, r, r, r, s, s)
                t.background = lat
                t.setTextColor(Warna.TEKS)
                lp.gravity = Gravity.START
                lp.rightMargin = dp(40)
            }
            else -> {
                t.setTextColor(Warna.REDUP)
                t.textSize = 12f
                t.gravity = Gravity.CENTER_HORIZONTAL
                lp.gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        if (dari == "saya" || dari == "asta") t.setPadding(dp(14), dp(10), dp(14), dp(10)) else t.setPadding(dp(8), dp(4), dp(8), dp(4))
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
        status.text = when (k) {
            "dengar" -> "Mendengarkan"
            "berpikir" -> "Berpikir"
            "bicara" -> "Berbicara"
            else -> "Siaga"
        }
        status.setTextColor(
            when (k) {
                "dengar" -> Color.parseColor("#5CE0B0")
                "berpikir" -> Warna.UNGU
                "bicara" -> Warna.CYAN
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
