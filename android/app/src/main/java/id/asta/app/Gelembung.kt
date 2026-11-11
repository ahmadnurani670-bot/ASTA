package id.asta.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Gelembung Asta yang melayang di atas aplikasi lain: ketuk untuk bicara, seret untuk memindahkan. */
object Gelembung {
    private var wm: WindowManager? = null
    private var akar: LinearLayout? = null
    private var panel: TextView? = null
    private var bulat: TextView? = null
    private var letak: WindowManager.LayoutParams? = null
    private val utama = Handler(Looper.getMainLooper())
    private val sembunyiPanel = Runnable { panel?.visibility = View.GONE }
    private val sb = StringBuilder()
    private var selesai = true

    fun bisa(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    private fun warna(k: String): Int = when (k) {
        "dengar" -> Color.parseColor("#4cff9a")
        "berpikir" -> Color.parseColor("#ffb347")
        "bicara" -> Color.parseColor("#6aa8ff")
        else -> Warna.CYAN
    }

    private fun tampilkanPanel(teks: String, galat: Boolean = false) {
        val p = panel ?: return
        p.text = if (teks.length > 400) teks.take(400) + "..." else teks
        p.setTextColor(if (galat) Warna.AMBER else Warna.TEKS)
        p.visibility = View.VISIBLE
        utama.removeCallbacks(sembunyiPanel)
    }

    private fun jadwalSembunyi() {
        utama.removeCallbacks(sembunyiPanel)
        utama.postDelayed(sembunyiPanel, 12_000L)
    }

    private val pendengar = object : Asta.Pendengar {
        override fun pesanSaya(teks: String) { tampilkanPanel("Anda: $teks") }
        override fun astaMulai() { sb.setLength(0); selesai = false }
        override fun astaKalimat(teks: String) {
            if (selesai) return
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(teks)
            tampilkanPanel(sb.toString())
        }
        override fun astaSelesai(penuh: String) { selesai = true; tampilkanPanel(penuh); jadwalSembunyi() }
        override fun keadaan(k: String) {
            val b = bulat?.background as? GradientDrawable ?: return
            val ctx = bulat?.context ?: return
            b.setStroke(ctx.dp(2), warna(k))
            bulat?.setTextColor(warna(k))
        }
        override fun info(teks: String, galat: Boolean) { tampilkanPanel(teks, galat); jadwalSembunyi() }
    }

    fun tampil(ctx: Context) {
        val app = ctx.applicationContext
        utama.post {
            if (akar != null || !bisa(app)) return@post
            val manajer = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val kotak = LinearLayout(app)
            kotak.orientation = LinearLayout.VERTICAL

            val p = TextView(app)
            p.textSize = 14f
            p.setTextColor(Warna.TEKS)
            p.setPadding(app.dp(12), app.dp(10), app.dp(12), app.dp(10))
            p.maxWidth = app.dp(260)
            p.maxLines = 8
            p.visibility = View.GONE
            val latarPanel = GradientDrawable()
            latarPanel.setColor(Color.parseColor("#F2061423"))
            latarPanel.cornerRadius = app.dp(14).toFloat()
            latarPanel.setStroke(app.dp(1), Warna.CYAN)
            p.background = latarPanel
            p.setOnClickListener {
                p.visibility = View.GONE
                val i = Intent(app, MainActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(i)
            }
            kotak.addView(p, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

            val b = TextView(app)
            b.text = "A"
            b.textSize = 22f
            b.gravity = Gravity.CENTER
            b.setTextColor(Warna.CYAN)
            b.setTypeface(b.typeface, android.graphics.Typeface.BOLD)
            val latarBulat = GradientDrawable()
            latarBulat.shape = GradientDrawable.OVAL
            latarBulat.setColor(Color.parseColor("#E6030A14"))
            latarBulat.setStroke(app.dp(2), Warna.CYAN)
            b.background = latarBulat
            val ukuran = app.dp(56)
            val lpB = LinearLayout.LayoutParams(ukuran, ukuran)
            lpB.topMargin = app.dp(6)
            kotak.addView(b, lpB)

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = app.dp(8)
            lp.y = app.dp(220)

            var awalX = 0
            var awalY = 0
            var sentuhX = 0f
            var sentuhY = 0f
            var bergeser = false
            b.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        awalX = lp.x; awalY = lp.y
                        sentuhX = e.rawX; sentuhY = e.rawY
                        bergeser = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - sentuhX
                        val dy = e.rawY - sentuhY
                        if (!bergeser && (dx * dx + dy * dy) > app.dp(10) * app.dp(10)) bergeser = true
                        if (bergeser) {
                            lp.x = awalX + dx.toInt()
                            lp.y = awalY + dy.toInt()
                            try { manajer.updateViewLayout(kotak, lp) } catch (ex: Exception) { }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!bergeser) {
                            if (Asta.keadaan == "siaga") Asta.dengarLalu(app) else Asta.berhenti()
                        }
                        true
                    }
                    else -> false
                }
            }

            try {
                manajer.addView(kotak, lp)
            } catch (ex: Exception) {
                return@post
            }
            wm = manajer
            akar = kotak
            panel = p
            bulat = b
            letak = lp
            Asta.daftarkan(pendengar)
        }
    }

    fun sembunyi() {
        utama.post {
            val k = akar ?: return@post
            try { wm?.removeView(k) } catch (e: Exception) { }
            akar = null
            panel = null
            bulat = null
            letak = null
            wm = null
            Asta.lepas(pendengar)
        }
    }
}
