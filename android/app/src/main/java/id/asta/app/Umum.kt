package id.asta.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object K {
    const val AKSI_BUNYI = "id.asta.app.BUNYI"
    const val AKSI_MATIKAN = "id.asta.app.MATIKAN"
    const val AKSI_TUNDA = "id.asta.app.TUNDA"
    const val AKSI_BERHENTI = "id.asta.app.BERHENTI"
    const val SALURAN_BUNYI = "bunyi"
    const val SALURAN_INFO = "info"
    const val NOTIF_BUNYI = 4001
    const val TUNDA_MENIT = 10
    const val BATAS_BUNYI_MS = 120_000L
}

object Warna {
    val LATAR: Int = Color.parseColor("#030a14")
    val CYAN: Int = Color.parseColor("#33d6ff")
    val TEKS: Int = Color.parseColor("#d6ebff")
    val REDUP: Int = Color.parseColor("#7f9bb5")
    val AMBER: Int = Color.parseColor("#ffb347")
}

object Format {
    private val tanggal = SimpleDateFormat("EEE, d MMM yyyy HH:mm", Locale("id", "ID"))
    private val namaHari = listOf("", "Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")

    fun waktu(ms: Long): String = synchronized(tanggal) { tanggal.format(Date(ms)) }

    fun jenis(j: String): String = when (j) {
        "timer" -> "Timer"
        "alarm" -> "Alarm"
        else -> "Pengingat"
    }

    fun ulang(hari: List<Int>): String {
        if (hari.isEmpty()) return ""
        if (hari.size == 7) return " (setiap hari)"
        val nama = hari.map { namaHari[it] }
        return " (setiap " + nama.joinToString(", ") + ")"
    }

    fun baris(p: Pengingat): String {
        val sb = StringBuilder()
        sb.append("#").append(p.id).append(" ").append(jenis(p.jenis)).append(" ").append(waktu(p.waktuMs))
        if (p.pesan.isNotBlank()) sb.append(" - ").append(p.pesan)
        sb.append(ulang(p.hari))
        return sb.toString()
    }
}

fun Context.dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

fun Context.buatTeks(isi: String, ukuran: Float, warna: Int, tebal: Boolean = false): TextView {
    val t = TextView(this)
    t.text = isi
    t.textSize = ukuran
    t.setTextColor(warna)
    if (tebal) t.setTypeface(t.typeface, Typeface.BOLD)
    t.setPadding(0, dp(4), 0, dp(4))
    return t
}

fun Context.buatTombol(isi: String, aksi: () -> Unit): Button {
    val b = Button(this)
    b.text = isi
    b.setAllCaps(false)
    b.setOnClickListener { aksi() }
    return b
}

fun Context.paramLebar(atas: Int = 8): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    p.topMargin = dp(atas)
    return p
}
