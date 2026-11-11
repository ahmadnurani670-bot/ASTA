package id.asta.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Daftar catatan. Ketuk lama pada satu catatan untuk menghapusnya. */
class CatatanActivity : Activity() {
    private lateinit var isi: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gulir = ScrollView(this)
        gulir.setBackgroundColor(Warna.LATAR)
        isi = LinearLayout(this)
        isi.orientation = LinearLayout.VERTICAL
        isi.setPadding(dp(16), dp(24), dp(16), dp(32))
        gulir.addView(isi)
        setContentView(gulir)
    }

    override fun onResume() {
        super.onResume()
        bangun()
    }

    private fun bangun() {
        isi.removeAllViews()
        isi.addView(buatTeks("Catatan", 22f, Warna.CYAN, true))
        val daftar = Catatan.semua(this)
        if (daftar.isEmpty()) {
            isi.addView(buatTeks("Belum ada catatan. Minta Asta: \"catat beli baterai\".", 14f, Warna.REDUP))
            return
        }
        isi.addView(buatTombol("Salin semua") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("catatan", daftar.joinToString("\n") { it.teks }))
            Toast.makeText(this, "Catatan disalin.", Toast.LENGTH_SHORT).show()
        }, paramLebar())
        for (n in daftar.reversed()) {
            val t = buatTeks(n.teks, 15f, Warna.TEKS)
            t.setPadding(0, dp(10), 0, dp(10))
            t.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setMessage("Hapus catatan ini?")
                    .setPositiveButton("Hapus") { _, _ -> Catatan.hapus(this, n.id); bangun() }
                    .setNegativeButton("Batal", null)
                    .show()
                true
            }
            isi.addView(t)
        }
    }
}
