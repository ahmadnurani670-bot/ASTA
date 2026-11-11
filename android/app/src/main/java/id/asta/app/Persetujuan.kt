package id.asta.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Meminta persetujuan pengguna lewat suara (pertanyaan dibacakan) dan notifikasi (tombol Izinkan/Tolak). */
object Persetujuan {
    private class Tunggu {
        val selesai = CountDownLatch(1)
        @Volatile var setuju = false
    }

    private val menunggu = ConcurrentHashMap<String, Tunggu>()
    private const val SALURAN = "izin_asta"
    private const val FLAG = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /** Memblokir thread pemanggil (maksimal 60 detik). Jangan dipanggil dari thread utama. */
    fun minta(ctx: Context, judul: String, isi: String): Boolean {
        val app = ctx.applicationContext
        val id = UUID.randomUUID().toString()
        val t = Tunggu()
        menunggu[id] = t
        tampilkan(app, id, judul, isi)
        Suara.ucapkan(app, "$judul. Ketuk Izinkan di notifikasi untuk menyetujui.")
        val tepat = t.selesai.await(60, TimeUnit.SECONDS)
        menunggu.remove(id)
        app.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
        return tepat && t.setuju
    }

    fun jawab(id: String, setuju: Boolean) {
        val t = menunggu[id] ?: return
        t.setuju = setuju
        t.selesai.countDown()
    }

    fun batalSemua() {
        for (t in menunggu.values) {
            t.setuju = false
            t.selesai.countDown()
        }
    }

    private fun aksi(ctx: Context, id: String, setuju: Boolean): PendingIntent {
        val i = Intent(ctx, PersetujuanReceiver::class.java)
        i.putExtra("id", id)
        i.putExtra("setuju", setuju)
        return PendingIntent.getBroadcast(ctx, id.hashCode() + (if (setuju) 1 else 2), i, FLAG)
    }

    private fun tampilkan(ctx: Context, id: String, judul: String, isi: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(SALURAN, "Persetujuan Asta", NotificationManager.IMPORTANCE_HIGH))
        val ikon = Icon.createWithResource(ctx, R.drawable.ic_notif)
        val n = Notification.Builder(ctx, SALURAN)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(judul)
            .setContentText(isi)
            .setStyle(Notification.BigTextStyle().bigText(isi))
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(ikon, "Izinkan", aksi(ctx, id, true)).build())
            .addAction(Notification.Action.Builder(ikon, "Tolak", aksi(ctx, id, false)).build())
            .build()
        nm.notify(id.hashCode(), n)
    }
}

class PersetujuanReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        Persetujuan.jawab(id, intent.getBooleanExtra("setuju", false))
    }
}
