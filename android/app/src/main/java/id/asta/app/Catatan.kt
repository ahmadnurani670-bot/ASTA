package id.asta.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Nota(val id: String, val waktu: Long, val teks: String)

object Catatan {
    private val kunci = Any()

    private fun berkas(ctx: Context) = File(ctx.applicationContext.filesDir, "catatan.json")

    private fun baca(ctx: Context): MutableList<Nota> {
        val hasil = mutableListOf<Nota>()
        try {
            val f = berkas(ctx)
            if (!f.exists()) return hasil
            val a = JSONArray(f.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                hasil.add(Nota(o.getString("id"), o.optLong("t", 0L), o.optString("teks", "")))
            }
        } catch (e: Exception) {
            // berkas rusak: pakai yang terbaca
        }
        return hasil
    }

    private fun tulis(ctx: Context, daftar: List<Nota>) {
        val a = JSONArray()
        for (n in daftar) a.put(JSONObject().put("id", n.id).put("t", n.waktu).put("teks", n.teks))
        berkas(ctx).writeText(a.toString())
    }

    fun semua(ctx: Context): List<Nota> = synchronized(kunci) { baca(ctx) }

    fun tambah(ctx: Context, teks: String): String = synchronized(kunci) {
        val d = baca(ctx)
        val n = Nota("n" + System.currentTimeMillis() + "-" + (d.size + 1), System.currentTimeMillis(), teks)
        d.add(n)
        tulis(ctx, d)
        n.id
    }

    fun hapus(ctx: Context, id: String) {
        synchronized(kunci) {
            val d = baca(ctx)
            if (d.removeAll { it.id == id }) tulis(ctx, d)
        }
    }
}
