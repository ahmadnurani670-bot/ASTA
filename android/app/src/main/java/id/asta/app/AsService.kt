package id.asta.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder

/** Menjaga Asta tetap hidup di latar belakang dan menampilkan gelembung di atas aplikasi lain. */
class AsService : Service() {
    companion object {
        const val AKSI_HENTIKAN = "id.asta.app.HENTIKAN_ASTA"
        const val AKSI_BICARA = "id.asta.app.BICARA_ASTA"
        private const val ID_NOTIF = 4002
        private const val SALURAN = "asta_aktif"
        private const val FLAG = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun mulai(ctx: Context) {
            if (!Setelan.bool(ctx, "latar", true) || Izin.dijeda(ctx)) {
                Gelembung.sembunyi()
                return
            }
            try {
                ctx.startForegroundService(Intent(ctx, AsService::class.java))
            } catch (e: Exception) {
                // Android menolak memulai dari latar belakang; coba lagi saat aplikasi dibuka
            }
        }

        fun hentikan(ctx: Context) {
            Izin.setDijeda(ctx, true)
            CatatanAsta.tulis(ctx, "Asta dihentikan oleh pengguna")
            Asta.berhenti()
            Gelembung.sembunyi()
            ctx.stopService(Intent(ctx, AsService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AKSI_HENTIKAN) {
            hentikan(this)
            stopSelf()
            return START_NOT_STICKY
        }
        val n = bangunNotif()
        try {
            val mikOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            when {
                Build.VERSION.SDK_INT >= 34 -> {
                    var tipe = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    if (mikOk) tipe = tipe or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    startForeground(ID_NOTIF, n, tipe)
                }
                Build.VERSION.SDK_INT >= 30 ->
                    startForeground(ID_NOTIF, n, if (mikOk) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
                else -> startForeground(ID_NOTIF, n)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (Izin.dijeda(this)) {
            Gelembung.sembunyi()
            stopSelf()
            return START_NOT_STICKY
        }
        if (Setelan.bool(this, "gelembung", false) && Gelembung.bisa(this)) Gelembung.tampil(this) else Gelembung.sembunyi()
        if (intent?.action == AKSI_BICARA) Asta.dengarLalu(this)
        return START_STICKY
    }

    override fun onDestroy() {
        Gelembung.sembunyi()
        super.onDestroy()
    }

    private fun bangunNotif(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(SALURAN, "Asta aktif", NotificationManager.IMPORTANCE_LOW))
        val buka = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), FLAG)
        val izin = PendingIntent.getActivity(this, 11, Intent(this, IzinActivity::class.java), FLAG)
        val henti = PendingIntent.getService(this, 12, Intent(this, AsService::class.java).setAction(AKSI_HENTIKAN), FLAG)
        val bicara = PendingIntent.getService(this, 13, Intent(this, AsService::class.java).setAction(AKSI_BICARA), FLAG)
        val ikon = Icon.createWithResource(this, R.drawable.ic_notif)
        return Notification.Builder(this, SALURAN)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Asta aktif")
            .setContentText("Berjalan di latar belakang.")
            .setOngoing(true)
            .setContentIntent(buka)
            .addAction(Notification.Action.Builder(ikon, "Bicara", bicara).build())
            .addAction(Notification.Action.Builder(ikon, "Izin", izin).build())
            .addAction(Notification.Action.Builder(ikon, "Hentikan Asta", henti).build())
            .build()
    }
}
