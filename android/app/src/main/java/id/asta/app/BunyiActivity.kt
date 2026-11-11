package id.asta.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout

/** Layar yang muncul saat alarm, timer, atau pengingat berbunyi (juga di atas layar kunci). */
class BunyiActivity : Activity() {
    private val penerima = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val jenis = intent.getStringExtra("jenis") ?: "pengingat"
        val pesan = intent.getStringExtra("pesan") ?: ""

        val kolom = LinearLayout(this)
        kolom.orientation = LinearLayout.VERTICAL
        kolom.gravity = Gravity.CENTER
        kolom.setBackgroundColor(Warna.LATAR)
        kolom.setPadding(dp(24), dp(24), dp(24), dp(24))

        kolom.addView(buatTeks(Format.jenis(jenis).uppercase(), 16f, Warna.CYAN, true))
        kolom.addView(buatTeks(if (pesan.isBlank()) Format.jenis(jenis) else pesan, 28f, Warna.TEKS, true))
        kolom.addView(buatTeks(Format.waktu(System.currentTimeMillis()), 14f, Warna.REDUP))
        kolom.addView(buatTombol("Matikan") {
            sendBroadcast(Notifikasi.aksi(this, K.AKSI_MATIKAN, jenis, pesan))
            finish()
        }, paramLebar(32))
        kolom.addView(buatTombol("Tunda " + K.TUNDA_MENIT + " menit") {
            sendBroadcast(Notifikasi.aksi(this, K.AKSI_TUNDA, jenis, pesan))
            finish()
        }, paramLebar(8))
        setContentView(kolom)

        val filter = IntentFilter(K.AKSI_BERHENTI)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(penerima, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(penerima, filter)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(penerima)
        } catch (e: Exception) {
            // abaikan
        }
        super.onDestroy()
    }
}
