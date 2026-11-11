package id.asta.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Log semua tindakan Asta terhadap aplikasi lain. Bisa dibaca dari layar Izin Asta. */
object CatatanAsta {
    private val kunci = Any()
    private val format = SimpleDateFormat("d MMM HH:mm:ss", Locale("id", "ID"))

    private fun berkas(ctx: Context) = File(ctx.applicationContext.filesDir, "log_asta.txt")

    fun tulis(ctx: Context, teks: String) {
        synchronized(kunci) {
            try {
                val f = berkas(ctx)
                f.appendText(format.format(Date()) + "  " + teks.replace('\n', ' ') + "\n")
                if (f.length() > 200_000L) f.writeText(f.readText().takeLast(100_000))
            } catch (e: Exception) {
                // log tidak boleh membuat Asta macet
            }
        }
    }

    fun baca(ctx: Context, maks: Int = 80): String {
        synchronized(kunci) {
            return try {
                val f = berkas(ctx)
                if (!f.exists()) "Belum ada tindakan." else f.readLines().takeLast(maks).reversed().joinToString("\n")
            } catch (e: Exception) {
                "Log tidak bisa dibaca."
            }
        }
    }

    fun hapus(ctx: Context) {
        synchronized(kunci) {
            try { berkas(ctx).delete() } catch (e: Exception) { }
        }
    }
}
