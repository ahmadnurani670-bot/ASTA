package id.asta.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/** Setelan Asta: kunci API, nama, model, mode, suara, latar belakang, gelembung, dan izin sistem. */
class SetelanActivity : Activity() {
    private lateinit var kunci: EditText
    private lateinit var nama: EditText
    private lateinit var modelChat: EditText
    private lateinit var modelKuat: EditText
    private lateinit var tMode: android.widget.Button
    private lateinit var laju: SeekBar
    private lateinit var sSuara: Switch
    private lateinit var sOto: Switch
    private lateinit var sLatar: Switch
    private lateinit var sGelembung: Switch
    private lateinit var infoIzin: TextView
    private var mode = "Standar"

    private fun kolom(isi: LinearLayout, label: String, hint: String, nilai: String, sandi: Boolean = false): EditText {
        isi.addView(buatTeks(label, 13f, Warna.REDUP), paramLebar(12))
        val e = EditText(this)
        e.hint = hint
        e.setText(nilai)
        e.setTextColor(Warna.TEKS)
        e.setHintTextColor(Warna.REDUP)
        e.inputType = if (sandi) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        isi.addView(e)
        return e
    }

    private fun saklar(isi: LinearLayout, label: String, nilai: Boolean): Switch {
        val s = Switch(this)
        s.text = label
        s.setTextColor(Warna.TEKS)
        s.isChecked = nilai
        s.setPadding(0, dp(8), 0, dp(8))
        isi.addView(s, paramLebar(4))
        return s
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

        isi.addView(buatTeks("Setelan Asta", 22f, Warna.CYAN, true))

        val adaKunci = Setelan.kunciApi(this).isNotEmpty()
        kunci = kolom(isi, "Kunci API Gemini (disimpan terenkripsi di ponsel ini)",
            if (adaKunci) "Tersimpan. Ketik untuk mengganti." else "Tempel kunci di sini", "", true)
        isi.addView(buatTeks("Buat gratis di aistudio.google.com/apikey. Kunci hanya dikirim ke Google saat Asta menjawab.", 12f, Warna.REDUP))
        nama = kolom(isi, "Asta memanggilmu dengan", "Contoh: nama atau sebutan pilihanmu", Setelan.teks(this, "nama"))
        modelChat = kolom(isi, "Model obrolan", Setelan.MODEL_CHAT, Setelan.teks(this, "model_chat"))
        modelKuat = kolom(isi, "Model untuk lampiran dan pencarian web", Setelan.MODEL_KUAT, Setelan.teks(this, "model_kuat"))

        mode = Setelan.teks(this, "mode", "Standar")
        tMode = buatTombol("Mode: $mode") { pilihMode() }
        isi.addView(tMode, paramLebar(12))

        isi.addView(buatTeks("Kecepatan bicara", 13f, Warna.REDUP), paramLebar(12))
        laju = SeekBar(this)
        laju.max = 15
        laju.progress = ((Setelan.laju(this) - 0.5f) / 0.1f).roundToInt().coerceIn(0, 15)
        isi.addView(laju)

        sSuara = saklar(isi, "Suara Asta aktif", Setelan.bool(this, "suara", true))
        sOto = saklar(isi, "Mode percakapan: dengarkan lagi setelah Asta selesai bicara", Setelan.bool(this, "oto", false))
        sLatar = saklar(isi, "Berjalan di latar belakang (notifikasi Asta aktif)", Setelan.bool(this, "latar", true))
        sGelembung = saklar(isi, "Gelembung Asta di atas aplikasi lain", Setelan.bool(this, "gelembung", false))
        sGelembung.setOnCheckedChangeListener { _, aktif ->
            if (aktif && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Izinkan \"Tampil di atas aplikasi lain\" untuk Asta, lalu kembali.", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }

        infoIzin = buatTeks("", 12f, Warna.REDUP)
        isi.addView(infoIzin, paramLebar(8))

        isi.addView(buatTombol("Simpan") { simpan(); Toast.makeText(this, "Disimpan.", Toast.LENGTH_SHORT).show() }, paramLebar(16))
        isi.addView(buatTombol("Tes suara") {
            simpan()
            Suara.ucapkan(this, "Halo, ini suara Asta.")
            Handler(Looper.getMainLooper()).postDelayed({
                Toast.makeText(this, "Mesin suara: " + Suara.status(), Toast.LENGTH_LONG).show()
            }, 1500)
        }, paramLebar())
        isi.addView(buatTombol("Izinkan mikrofon") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }, paramLebar())
        isi.addView(buatTombol("Izinkan notifikasi") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }, paramLebar())
        isi.addView(buatTombol("Izinkan kalender dan kontak") {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CONTACTS), 3)
        }, paramLebar())
        isi.addView(buatTombol("Periksa kalender dan WhatsApp") {
            startActivity(Intent(this, DiagnosaActivity::class.java))
        }, paramLebar())
        isi.addView(buatTombol("Sinkronisasi antar HP") {
            startActivity(Intent(this, SinkronActivity::class.java))
        }, paramLebar())
        isi.addView(buatTombol("Kontak Asta (untuk telepon WhatsApp)") {
            startActivity(Intent(this, KontakActivity::class.java))
        }, paramLebar())
        isi.addView(buatTombol("Izin tampil di atas aplikasi lain") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, paramLebar())
        isi.addView(buatTombol("Pengecualian hemat baterai") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, paramLebar())
        isi.addView(buatTombol("Izin per aplikasi (Izin Asta)") {
            startActivity(Intent(this, IzinActivity::class.java))
        }, paramLebar())
        isi.addView(buatTombol("Lupakan percakapan") {
            AlertDialog.Builder(this)
                .setMessage("Hapus semua ingatan percakapan Asta?")
                .setPositiveButton("Hapus") { _, _ -> Otak.lupakan(this); Toast.makeText(this, "Percakapan dilupakan.", Toast.LENGTH_SHORT).show() }
                .setNegativeButton("Batal", null)
                .show()
        }, paramLebar())
    }

    private fun pilihMode() {
        val nama = Setelan.MODE.keys.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Mode Asta")
            .setSingleChoiceItems(nama, nama.indexOf(mode)) { d, which ->
                mode = nama[which]
                tMode.text = "Mode: $mode"
                d.dismiss()
            }
            .show()
    }

    private fun simpan() {
        val sebelum = Setelan.ekspor(this).getJSONObject("nilai").toString()
        val k = kunci.text.toString().trim()
        if (k.isNotEmpty()) {
            Setelan.simpanKunciApi(this, k)
            kunci.setText("")
            kunci.hint = "Tersimpan. Ketik untuk mengganti."
        }
        Setelan.simpanTeks(this, "nama", nama.text.toString().trim())
        Setelan.simpanTeks(this, "model_chat", modelChat.text.toString().trim())
        Setelan.simpanTeks(this, "model_kuat", modelKuat.text.toString().trim())
        Setelan.simpanTeks(this, "mode", mode)
        Setelan.simpanTeks(this, "laju", (0.5f + laju.progress * 0.1f).toString())
        Setelan.simpanBool(this, "suara", sSuara.isChecked)
        Setelan.simpanBool(this, "oto", sOto.isChecked)
        Setelan.simpanBool(this, "latar", sLatar.isChecked)
        Setelan.simpanBool(this, "gelembung", sGelembung.isChecked)
        if (Setelan.ekspor(this).getJSONObject("nilai").toString() != sebelum) {
            Setelan.stempel(this)
            Sinkron.jadwalkan(this)
        }
        AsService.mulai(this)
    }

    override fun onPause() {
        simpan()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        val mik = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val atas = Settings.canDrawOverlays(this)
        val kal = checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val kon = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        infoIzin.text = "Kalender: " + (if (kal) "diizinkan" else "belum") + " - Kontak: " + (if (kon) "diizinkan" else "belum") + "\nMikrofon: " + (if (mik) "diizinkan" else "belum") + " - Tampil di atas aplikasi lain: " + (if (atas) "diizinkan" else "belum")
        AsService.mulai(this)
    }
}
