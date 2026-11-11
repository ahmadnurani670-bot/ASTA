package id.asta.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Membuka layar aplikasi lain. Dari latar belakang Android sering menolak, jadi ada cadangan notifikasi. */
object Pembuka {
    fun buka(ctx: Context, i: Intent, judul: String): String {
        val app = ctx.applicationContext
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Asta.layarTampil || Settings.canDrawOverlays(app)) {
            return try {
                app.startActivity(i)
                "$judul dibuka."
            } catch (e: Exception) {
                "$judul gagal dibuka."
            }
        }
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("buka_asta", "Buka dari Asta", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(app, judul.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            judul.hashCode(),
            Notification.Builder(app, "buka_asta")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Asta ingin membuka $judul")
                .setContentText("Ketuk untuk membuka.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        )
        return "Android tidak mengizinkan saya membuka $judul dari latar belakang. Saya sudah menaruh notifikasi; pengguna cukup mengetuknya. " +
            "Izin tampil di atas aplikasi lain (Setelan) membuatnya langsung terbuka."
    }
}
