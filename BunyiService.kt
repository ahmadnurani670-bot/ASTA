package id.asta.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Membunyikan alarm: nada alarm berulang, getar, dan Asta membacakan pesannya. */
class BunyiService : Service() {
    private var pemutar: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsSiap = false
    private var ucapan = ""
    private val penangan = Handler(Looper.getMainLooper())

    private val ulangUcap = object : Runnable {
        override fun run() {
            if (ttsSiap && ucapan.isNotBlank()) {
                tts?.speak(ucapan, TextToSpeech.QUEUE_FLUSH, null, "asta")
            }
            penangan.postDelayed(this, 15_000L)
        }
    }

    private val henti = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jenis = intent?.getStringExtra("jenis") ?: "pengingat"
        val pesan = intent?.getStringExtra("pesan") ?: ""
        val notif = Notifikasi.bunyi(this, jenis, pesan)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(K.NOTIF_BUNYI, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(K.NOTIF_BUNYI, notif)
        }
        val awal = when (jenis) {
            "timer" -> "Timer selesai."
            "alarm" -> "Alarm."
            else -> "Pengingat."
        }
        ucapan = if (pesan.isBlank()) awal else "$awal $pesan"
        mulaiSuara()
        penangan.removeCallbacks(henti)
        penangan.postDelayed(henti, K.BATAS_BUNYI_MS)
        return START_NOT_STICKY
    }

    private fun mulaiSuara() {
        if (pemutar == null) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                pemutar = mp
            } catch (e: Exception) {
                // tanpa nada pun, getar dan suara Asta tetap berjalan
            }
            getar()
        }
        if (tts == null) {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("id", "ID")
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    ttsSiap = true
                    penangan.postDelayed(ulangUcap, 3_000L)
                }
            }
        }
    }

    private fun getar() {
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        } catch (e: Exception) {
            // abaikan
        }
    }

    override fun onDestroy() {
        penangan.removeCallbacksAndMessages(null)
        try {
            pemutar?.stop()
        } catch (e: Exception) {
            // abaikan
        }
        pemutar?.release()
        pemutar = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        try {
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
        } catch (e: Exception) {
            // abaikan
        }
        val selesai = Intent(K.AKSI_BERHENTI)
        selesai.setPackage(packageName)
        sendBroadcast(selesai)
        super.onDestroy()
    }
}
