package id.asta.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** hapus > 0 berarti catatan sudah dihapus (batu nisan) supaya penghapusan ikut tersinkron. */
class Nota(val id: String, val waktu: Long, val teks: String, val hapus: Long = 0L)

object Catatan {
    private val kunci = Any()
    private const val SIMPAN_BATU_NISAN_MS = 30L * 24 * 3600 * 1000

    private fun berkas(ctx: Context) = File(ctx.applicationContext.filesDir, "catatan.json")

    private fun baca(ctx: Context): MutableList<Nota> {
        val hasil = mutableListOf<Nota>()
        try {
            val f = berkas(ctx)
            if (!f.exists()) return hasil
            val a = JSONArray(f.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                hasil.add(Nota(o.getString("id"), o.optLong("t", 0L), o.optString("teks", ""), o.optLong("x", 0L)))
            }
        } catch (e: Exception) {
            // berkas rusak: pakai yang terbaca
        }
        return hasil
    }

    private fun tulis(ctx: Context, daftar: List<Nota>) {
        val batas = System.currentTimeMillis() - SIMPAN_BATU_NISAN_MS
        val a = JSONArray()
        for (n in daftar) {
            if (n.hapus in 1 until batas) continue
            val o = JSONObject().put("id", n.id).put("t", n.waktu).put("teks", n.teks)
            if (n.hapus > 0) o.put("x", n.hapus)
            a.put(o)
        }
        berkas(ctx).writeText(a.toString())
    }

    fun semua(ctx: Context): List<Nota> = synchronized(kunci) { baca(ctx).filter { it.hapus == 0L } }

    fun semuaMentah(ctx: Context): List<Nota> = synchronized(kunci) { baca(ctx) }

    fun gantiMentah(ctx: Context, daftar: List<Nota>) {
        synchronized(kunci) { tulis(ctx, daftar) }
    }

    fun tambah(ctx: Context, teks: String): String {
        val id: String
        synchronized(kunci) {
            val d = baca(ctx)
            val sekarang = System.currentTimeMillis()
            id = "n" + sekarang + "-" + (Math.random() * 1_000_000).toInt()
            d.add(Nota(id, sekarang, teks))
            tulis(ctx, d)
        }
        Sinkron.jadwalkan(ctx)
        return id
    }

    fun hapus(ctx: Context, id: String) {
        synchronized(kunci) {
            val d = baca(ctx)
            val i = d.indexOfFirst { it.id == id }
            if (i >= 0 && d[i].hapus == 0L) {
                d[i] = Nota(d[i].id, d[i].waktu, d[i].teks, System.currentTimeMillis())
                tulis(ctx, d)
            }
        }
        Sinkron.jadwalkan(ctx)
    }
}
