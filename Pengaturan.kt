package id.asta.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.Toast

object Pengaturan {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("asta", Context.MODE_PRIVATE)

    /** Alamat PWA Asta: pilihan pengguna, atau bawaan dari saat APK dibangun. */
    fun alamat(ctx: Context): String {
        val tersimpan = prefs(ctx).getString("alamat", "") ?: ""
        return if (tersimpan.isNotBlank()) tersimpan else BuildConfig.ALAMAT_ASTA
    }

    fun simpanAlamat(ctx: Context, a: String) {
        prefs(ctx).edit().putString("alamat", a.trim()).apply()
    }

    fun tanyaAlamat(act: Activity, saatSimpan: () -> Unit) {
        val kolom = EditText(act)
        kolom.setText(alamat(act))
        kolom.hint = "https://nama.github.io/repo/"
        kolom.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        AlertDialog.Builder(act)
            .setTitle("Alamat Asta")
            .setMessage("Alamat halaman Asta (PWA) Anda di GitHub Pages.")
            .setView(kolom)
            .setPositiveButton("Simpan") { _, _ ->
                val a = kolom.text.toString().trim()
                if (a.startsWith("https://")) {
                    simpanAlamat(act, a)
                    saatSimpan()
                } else {
                    Toast.makeText(act, "Alamat harus diawali https://", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
