package id.asta.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Layar daftar pengingat, alarm, dan timer, plus status izin yang diperlukan. */
class PengingatActivity : Activity() {
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
        isi.addView(buatTeks("Pengingat Asta", 22f, Warna.CYAN, true))

        if (!Penjadwal.bisaPresisi(this)) {
            isi.addView(buatTeks("Izin alarm presisi belum aktif. Alarm bisa terlambat.", 14f, Warna.AMBER))
            isi.addView(buatTombol("Buka pengaturan alarm") {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }, paramLebar())
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            isi.addView(buatTeks("Notifikasi belum diizinkan.", 14f, Warna.AMBER))
            isi.addView(buatTombol("Izinkan notifikasi") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
            }, paramLebar())
        }
        if (Build.VERSION.SDK_INT >= 34 &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) {
            isi.addView(buatTeks("Tampilan layar penuh saat alarm berbunyi belum diizinkan.", 14f, Warna.AMBER))
            isi.addView(buatTombol("Izinkan layar penuh") {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }, paramLebar())
        }

        val daftar = Gudang.semua(this)
        val judulDaftar = if (daftar.isEmpty()) "Belum ada pengingat, alarm, atau timer aktif." else "Aktif (" + daftar.size + ")"
        isi.addView(buatTeks(judulDaftar, 14f, Warna.REDUP), paramLebar(16))
        for (p in daftar) {
            isi.addView(buatTeks(Format.baris(p), 15f, Warna.TEKS), paramLebar(10))
            isi.addView(buatTombol("Hapus #" + p.id) {
                Penjadwal.batal(this, p.id)
                Gudang.hapus(this, p.id)
                bangun()
            }, paramLebar(2))
        }

        isi.addView(buatTombol("Buka Asta") {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, paramLebar(24))
        isi.addView(buatTombol("Setelan Asta") {
            startActivity(Intent(this, SetelanActivity::class.java))
        }, paramLebar())
    }
}
