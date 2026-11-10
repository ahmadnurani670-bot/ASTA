package id.asta.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Penjadwal {
    private const val FLAG = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun alarm(ctx: Context): AlarmManager =
        ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun intentBunyi(ctx: Context, id: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
        i.action = K.AKSI_BUNYI
        i.putExtra("id", id)
        return PendingIntent.getBroadcast(ctx, id, i, FLAG)
    }

    fun bisaPresisi(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm(ctx).canScheduleExactAlarms()

    /** true = dijadwalkan presisi; false = jatuh ke alarm tidak presisi (izin belum aktif). */
    fun pasang(ctx: Context, p: Pengingat): Boolean {
        val am = alarm(ctx)
        val tampil = PendingIntent.getActivity(ctx, 0, Intent(ctx, PengingatActivity::class.java), FLAG)
        return try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(p.waktuMs, tampil), intentBunyi(ctx, p.id))
            true
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.waktuMs, intentBunyi(ctx, p.id))
            false
        }
    }

    fun batal(ctx: Context, id: Int) {
        alarm(ctx).cancel(intentBunyi(ctx, id))
    }

    /** Dipanggil setelah restart atau perubahan waktu: pasang ulang semua jadwal. */
    fun pasangSemua(ctx: Context) {
        val sekarang = System.currentTimeMillis()
        for (p in Gudang.semua(ctx)) {
            var q = p
            if (q.waktuMs <= sekarang) {
                if (q.hari.isNotEmpty()) {
                    q = q.copy(waktuMs = waktuBerikutnya(q.hari, q.jam, q.menit, sekarang))
                    Gudang.perbarui(ctx, q)
                } else {
                    Notifikasi.terlewat(ctx, q)
                    Gudang.hapus(ctx, q.id)
                    continue
                }
            }
            pasang(ctx, q)
        }
    }

    /** Waktu bunyi berikutnya (setelah setelahMs) pada jam:menit di salah satu hari yang dipilih. */
    fun waktuBerikutnya(hari: List<Int>, jam: Int, menit: Int, setelahMs: Long): Long {
        val kal = Calendar.getInstance()
        kal.timeInMillis = setelahMs
        kal.set(Calendar.HOUR_OF_DAY, jam)
        kal.set(Calendar.MINUTE, menit)
        kal.set(Calendar.SECOND, 0)
        kal.set(Calendar.MILLISECOND, 0)
        for (i in 0..7) {
            val cocokHari = hari.isEmpty() || hari.contains(kal.get(Calendar.DAY_OF_WEEK))
            if (kal.timeInMillis > setelahMs && cocokHari) return kal.timeInMillis
            kal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return kal.timeInMillis
    }
}
