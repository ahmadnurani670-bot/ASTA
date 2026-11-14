package id.asta.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
    val LATAR: Int = Color.parseColor("#070B1A")
    val PERMUKAAN: Int = Color.parseColor("#0E1430")
    val PERMUKAAN2: Int = Color.parseColor("#151D42")
    val GARIS: Int = Color.parseColor("#262F63")
    val CYAN: Int = Color.parseColor("#5CC8FF")
    val UNGU: Int = Color.parseColor("#8B6CFF")
    val TEKS: Int = Color.parseColor("#E8EDFF")
    val REDUP: Int = Color.parseColor("#8C97C2")
    val AMBER: Int = Color.parseColor("#FFB347")
    val UNGU_TUA: Int = Color.parseColor("#6B4DF0")
    val BIRU_TUA: Int = Color.parseColor("#3F7BF0")
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

fun Context.kotak(warna: Int, radius: Int, garis: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(warna)
    g.cornerRadius = dp(radius).toFloat()
    if (garis != 0) g.setStroke(dp(1), garis)
    return g
}

/** Tombol bergaya satu sistem: permukaan gelap bergaris tipis, atau (utama) gradasi ungu ke biru. */
fun Context.buatTombol(isi: String, utama: Boolean = false, aksi: () -> Unit): Button {
    val b = Button(this)
    b.text = isi
    b.setAllCaps(false)
    b.textSize = 14f
    b.setTextColor(if (utama) Color.WHITE else Warna.TEKS)
    b.stateListAnimator = null
    b.elevation = 0f
    b.minHeight = dp(44)
    b.minimumHeight = dp(44)
    b.setPadding(dp(16), 0, dp(16), 0)
    val dasar: GradientDrawable = if (utama) {
        val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Warna.UNGU_TUA, Warna.BIRU_TUA))
        g.cornerRadius = dp(12).toFloat()
        g
    } else kotak(Warna.PERMUKAAN2, 12, Warna.GARIS)
    b.background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), dasar, null)
    b.setOnClickListener { aksi() }
    return b
}

fun Context.paramLebar(atas: Int = 8): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    p.topMargin = dp(atas)
    return p
}
