package id.asta.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

object Notifikasi {
    private const val FLAG = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun siapkanSaluran(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val bunyi = NotificationChannel(K.SALURAN_BUNYI, "Alarm dan pengingat berbunyi", NotificationManager.IMPORTANCE_HIGH)
        bunyi.setSound(null, null)          // suara diputar sendiri oleh BunyiService
        bunyi.enableVibration(false)
        bunyi.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val info = NotificationChannel(K.SALURAN_INFO, "Info pengingat", NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(bunyi)
        nm.createNotificationChannel(info)
    }

    fun aksi(ctx: Context, aksi: String, jenis: String, pesan: String): Intent {
        val i = Intent(ctx, AlarmReceiver::class.java)
        i.action = aksi
        i.putExtra("jenis", jenis)
        i.putExtra("pesan", pesan)
        return i
    }

    fun bunyi(ctx: Context, jenis: String, pesan: String): Notification {
        siapkanSaluran(ctx)
        val judul = when (jenis) {
            "timer" -> "Timer selesai"
            "alarm" -> "Alarm"
            else -> "Pengingat"
        }
        val buka = Intent(ctx, BunyiActivity::class.java)
        buka.putExtra("jenis", jenis)
        buka.putExtra("pesan", pesan)
        buka.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val layar = PendingIntent.getActivity(ctx, 1, buka, FLAG)
        val matikan = PendingIntent.getBroadcast(ctx, 2, aksi(ctx, K.AKSI_MATIKAN, jenis, pesan), FLAG)
        val tunda = PendingIntent.getBroadcast(ctx, 3, aksi(ctx, K.AKSI_TUNDA, jenis, pesan), FLAG)
        val ikon = Icon.createWithResource(ctx, R.drawable.ic_notif)
        return Notification.Builder(ctx, K.SALURAN_BUNYI)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(judul)
            .setContentText(if (pesan.isBlank()) judul else pesan)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(layar)
            .setFullScreenIntent(layar, true)
            .addAction(Notification.Action.Builder(ikon, "Matikan", matikan).build())
            .addAction(Notification.Action.Builder(ikon, "Tunda " + K.TUNDA_MENIT + " menit", tunda).build())
            .build()
    }

    /** Cadangan kalau layanan bunyi tidak bisa dimulai: tampilkan notifikasinya saja. */
    fun tampilkanSaja(ctx: Context, jenis: String, pesan: String) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.notify(K.NOTIF_BUNYI, bunyi(ctx, jenis, pesan))
        } catch (e: Exception) {
            // abaikan
        }
    }

    fun terlewat(ctx: Context, p: Pengingat) {
        try {
            siapkanSaluran(ctx)
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val n = Notification.Builder(ctx, K.SALURAN_INFO)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Terlewat: " + Format.jenis(p.jenis))
                .setContentText(if (p.pesan.isBlank()) Format.waktu(p.waktuMs) else p.pesan)
                .setAutoCancel(true)
                .build()
            nm.notify(5000 + p.id, n)
        } catch (e: Exception) {
            // abaikan
        }
    }
}
