package id.asta.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Pengingat(
    val id: Int,
    val jenis: String,      // "alarm", "timer", atau "pengingat"
    val pesan: String,
    val waktuMs: Long,      // waktu bunyi berikutnya
    val hari: List<Int>,    // hari berulang (Calendar.SUNDAY=1 .. SATURDAY=7); kosong = sekali
    val jam: Int,
    val menit: Int,
)

object Gudang {
    private val kunci = Any()

    private fun berkas(ctx: Context): File = File(ctx.applicationContext.filesDir, "pengingat.json")

    private fun baca(ctx: Context): MutableList<Pengingat> {
        val hasil = mutableListOf<Pengingat>()
        try {
            val f = berkas(ctx)
            if (!f.exists()) return hasil
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val hari = mutableListOf<Int>()
                val h = o.optJSONArray("hari")
                if (h != null) {
                    for (j in 0 until h.length()) hari.add(h.getInt(j))
                }
                hasil.add(
                    Pengingat(
                        o.getInt("id"),
                        o.optString("jenis", "pengingat"),
                        o.optString("pesan", ""),
                        o.getLong("waktuMs"),
                        hari,
                        o.optInt("jam", 0),
                        o.optInt("menit", 0),
                    )
                )
            }
        } catch (e: Exception) {
            // berkas rusak: mulai dari daftar yang sudah terbaca
        }
        return hasil
    }

    private fun tulis(ctx: Context, daftar: List<Pengingat>) {
        val arr = JSONArray()
        for (p in daftar) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("jenis", p.jenis)
            o.put("pesan", p.pesan)
            o.put("waktuMs", p.waktuMs)
            o.put("hari", JSONArray(p.hari))
            o.put("jam", p.jam)
            o.put("menit", p.menit)
            arr.put(o)
        }
        val f = berkas(ctx)
        val sementara = File(f.parentFile, "pengingat.json.tmp")
        sementara.writeText(arr.toString())
        if (!sementara.renameTo(f)) {
            f.writeText(arr.toString())
            sementara.delete()
        }
    }

    fun semua(ctx: Context): List<Pengingat> = synchronized(kunci) { baca(ctx).sortedBy { it.waktuMs } }

    fun cari(ctx: Context, id: Int): Pengingat? = synchronized(kunci) { baca(ctx).firstOrNull { it.id == id } }

    fun tambah(
        ctx: Context, jenis: String, pesan: String, waktuMs: Long,
        hari: List<Int>, jam: Int, menit: Int,
    ): Pengingat = synchronized(kunci) {
        val daftar = baca(ctx)
        val id = (daftar.maxOfOrNull { it.id } ?: 0) + 1
        val p = Pengingat(id, jenis, pesan, waktuMs, hari, jam, menit)
        daftar.add(p)
        tulis(ctx, daftar)
        p
    }

    fun perbarui(ctx: Context, p: Pengingat) {
        synchronized(kunci) {
            val daftar = baca(ctx)
            val i = daftar.indexOfFirst { it.id == p.id }
            if (i >= 0) daftar[i] = p else daftar.add(p)
            tulis(ctx, daftar)
        }
    }

    fun hapus(ctx: Context, id: Int): Boolean = synchronized(kunci) {
        val daftar = baca(ctx)
        val ada = daftar.removeAll { it.id == id }
        if (ada) tulis(ctx, daftar)
        ada
    }
}
