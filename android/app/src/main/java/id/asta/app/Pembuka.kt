package id.asta.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Membuka layar aplikasi lain. Dari latar belakang Android 10 ke atas sering menolak SECARA DIAM-DIAM
 * (startActivity tidak melempar galat), jadi di latar belakang Asta selalu menaruh notifikasi pengaman juga.
 */
object Pembuka {
    private fun notifikasi(app: Context, i: Intent, judul: String) {
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("buka_asta", "Buka dari Asta", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(app, judul.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(app, "buka_asta")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Asta: $judul")
            .setContentText("Ketuk untuk membuka kalau layar belum berpindah.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000L)
        val layarPenuh = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true
        if (layarPenuh) b.setFullScreenIntent(pi, true)
        nm.notify(judul.hashCode(), b.build())
    }

    fun buka(ctx: Context, i: Intent, judul: String): String {
        val app = ctx.applicationContext
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val depan = Asta.layarTampil
        try {
            app.startActivity(i)
        } catch (e: Exception) {
            try { notifikasi(app, i, judul) } catch (x: Exception) { }
            return "$judul tidak bisa dibuka langsung (" + e.javaClass.simpleName + (e.message?.let { ": " + it.take(80) } ?: "") + "). " +
                "Saya menaruh notifikasi; pengguna cukup mengetuknya."
        }
        if (depan) return "$judul dibuka."
        try { notifikasi(app, i, judul) } catch (e: Exception) { }
        return "Saya sudah mencoba membuka $judul. Karena Asta sedang di latar belakang, Android kadang memblokirnya tanpa kabar, " +
            "jadi saya juga menaruh notifikasi: kalau layar belum berpindah, pengguna cukup mengetuknya."
    }
}
