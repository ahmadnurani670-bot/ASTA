package id.asta.app

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Kalender lewat penyedia kalender Android, jadi Google Kalender yang tersinkron di ponsel ikut terbaca dan tertulis. */
object Kalender {
    const val BELUM = "Izin kalender belum diberikan. Minta pengguna membuka Setelan Asta lalu mengetuk Izinkan kalender dan kontak."
    private val format = SimpleDateFormat("EEE, d MMM HH:mm", Locale("id", "ID"))
    private val formatJam = SimpleDateFormat("HH:mm", Locale("id", "ID"))
    private val formatTgl = SimpleDateFormat("EEE, d MMM", Locale("id", "ID"))

    private fun izinBaca(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun izin(ctx: Context): Boolean =
        izinBaca(ctx) && ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    class Kal(val id: Long, val nama: String, val akun: String, val jenis: String, val akses: Int, val tampil: Boolean, val utama: Boolean) {
        val bisaTulis: Boolean get() = akses >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
    }

    /** Semua kalender di ponsel, diurutkan: akun Google milik sendiri dan yang tampil lebih dulu. Tanpa kolom yang tidak ada di semua ponsel. */
    fun daftarKalender(ctx: Context): List<Kal> {
        val proj = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE, CalendarContract.Calendars.OWNER_ACCOUNT,
        )
        val hasil = ArrayList<Kal>()
        ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val akun = c.getString(2) ?: ""
                hasil.add(Kal(c.getLong(0), c.getString(1) ?: "", akun, c.getString(3) ?: "", c.getInt(4), c.getInt(5) == 1,
                    akun.isNotEmpty() && akun == (c.getString(6) ?: "")))
            }
        }
        return hasil.sortedWith(compareByDescending<Kal> { it.utama && it.jenis == "com.google" }
            .thenByDescending { it.utama }.thenByDescending { it.tampil }.thenByDescending { it.jenis == "com.google" })
    }

    private fun rentang(ctx: Context, awal: Long, akhir: Long, maks: Int): List<String> {
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, awal)
        ContentUris.appendId(b, akhir)
        val proj = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY,
        )
        val hasil = ArrayList<String>()
        ctx.contentResolver.query(b.build(), proj, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
            while (c.moveToNext() && hasil.size < maks) {
                val judul = c.getString(1) ?: "(tanpa judul)"
                val mulai = c.getLong(2)
                val selesai = c.getLong(3)
                val lokasi = c.getString(4) ?: ""
                val seharian = c.getInt(5) == 1
                val waktu = if (seharian) {
                    synchronized(formatTgl) { formatTgl.format(Date(mulai)) } + " (seharian)"
                } else {
                    synchronized(format) { format.format(Date(mulai)) } + "-" + synchronized(formatJam) { formatJam.format(Date(selesai)) }
                }
                hasil.add("#" + c.getLong(0) + " " + judul + ", " + waktu + (if (lokasi.isNotBlank()) ", di $lokasi" else ""))
            }
        }
        return hasil
    }

    fun lihat(ctx: Context, dari: String, sampai: String): String {
        if (!izinBaca(ctx)) return BELUM
        val awal = if (dari.isBlank()) System.currentTimeMillis() else (Alarm.parseWaktu(dari) ?: return "Format waktu 'dari' tidak dikenali. Pakai yyyy-MM-ddTHH:mm.")
        val akhir = if (sampai.isBlank()) awal + 7L * 24 * 3600 * 1000 else (Alarm.parseWaktu(sampai) ?: return "Format waktu 'sampai' tidak dikenali. Pakai yyyy-MM-ddTHH:mm.")
        if (akhir <= awal) return "Rentang waktu tidak valid."
        val baris = try { rentang(ctx, awal, akhir, 30) } catch (e: Exception) { return "Gagal membaca kalender: " + e.javaClass.simpleName + " " + (e.message ?: "") }
        return if (baris.isEmpty()) "Tidak ada acara pada rentang itu." else baris.joinToString("\n")
    }

    fun tambah(ctx: Context, judul: String, mulai: String, selesai: String, lokasi: String, ingat: String): String {
        if (!izin(ctx)) return BELUM
        if (judul.isBlank()) return "Judul acara kosong."
        val ms = Alarm.parseWaktu(mulai) ?: return "Format waktu mulai tidak dikenali. Pakai yyyy-MM-ddTHH:mm."
        val me = if (selesai.isBlank()) ms + 3600_000L else (Alarm.parseWaktu(selesai) ?: return "Format waktu selesai tidak dikenali.")
        if (me <= ms) return "Waktu selesai harus setelah waktu mulai."
        val tulis = try { daftarKalender(ctx).filter { it.bisaTulis } } catch (e: Exception) { return "Gagal membaca daftar kalender: " + e.javaClass.simpleName + " " + (e.message ?: "") }
        if (tulis.isEmpty()) return "Tidak ada kalender yang bisa ditulis di ponsel ini. Pengguna perlu login akun Google dan mengaktifkan sinkron Kalender di Pengaturan Android, atau membuka aplikasi Google Kalender sekali."
        val bentrok = try { rentang(ctx, ms, me, 5) } catch (e: Exception) { emptyList() }

        var id = -1L
        var galat = ""
        for (k in tulis) {
            try {
                val v = ContentValues()
                v.put(CalendarContract.Events.CALENDAR_ID, k.id)
                v.put(CalendarContract.Events.TITLE, judul.trim())
                v.put(CalendarContract.Events.DTSTART, ms)
                v.put(CalendarContract.Events.DTEND, me)
                v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                if (lokasi.isNotBlank()) v.put(CalendarContract.Events.EVENT_LOCATION, lokasi.trim())
                val uri = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)
                if (uri != null) {
                    id = ContentUris.parseId(uri)
                    break
                }
                galat = "penyedia kalender menolak (kalender \"" + k.nama + "\")"
            } catch (e: Exception) {
                galat = e.javaClass.simpleName + " " + (e.message ?: "") + " (kalender \"" + k.nama + "\")"
            }
        }
        if (id < 0) return "Gagal menambah acara: $galat"

        val menit = ingat.trim().toIntOrNull() ?: 10
        if (menit > 0) {
            val r = ContentValues()
            r.put(CalendarContract.Reminders.EVENT_ID, id)
            r.put(CalendarContract.Reminders.MINUTES, menit)
            r.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            try { ctx.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, r) } catch (e: Exception) { }
        }
        Alat.catatUrung("acara \"" + judul.trim().take(30) + "\"") { hapusLangsung(ctx, id) }
        CatatanAsta.tulis(ctx, "Menambah acara kalender: " + judul.trim().take(60))
        val peringatan = if (bentrok.isNotEmpty()) " Perhatian, bentrok dengan: " + bentrok.joinToString("; ") else ""
        return "Acara #" + id + " \"" + judul.trim() + "\" ditambahkan pada " + synchronized(format) { format.format(Date(ms)) } + "." + peringatan
    }

    private fun hapusLangsung(ctx: Context, id: Long): Boolean =
        ctx.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) > 0

    fun hapus(ctx: Context, idTeks: String): String {
        if (!izin(ctx)) return BELUM
        val id = idTeks.trim().removePrefix("#").toLongOrNull() ?: return "Nomor acara tidak valid."
        val proj = arrayOf(
            CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.EVENT_TIMEZONE,
        )
        var judul = ""
        var mulai = 0L
        var selesai = 0L
        var lokasi = ""
        var kal = 0L
        var zona = TimeZone.getDefault().id
        var ada = false
        ctx.contentResolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), proj, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                ada = true
                judul = c.getString(0) ?: ""
                mulai = c.getLong(1)
                selesai = c.getLong(2)
                lokasi = c.getString(3) ?: ""
                kal = c.getLong(4)
                zona = c.getString(5) ?: zona
            }
        }
        if (!ada) return "Tidak ada acara dengan nomor $id."
        if (!Persetujuan.minta(ctx, "Hapus acara kalender", "Asta ingin menghapus acara \"$judul\" pada " + synchronized(format) { format.format(Date(mulai)) } + ".")) {
            return "Pengguna tidak menyetujui penghapusan acara."
        }
        if (!hapusLangsung(ctx, id)) return "Acara gagal dihapus."
        CatatanAsta.tulis(ctx, "Menghapus acara kalender: " + judul.take(60))
        val simpanJudul = judul
        Alat.catatUrung("hapus acara \"" + simpanJudul.take(30) + "\"") {
            val v = ContentValues()
            v.put(CalendarContract.Events.CALENDAR_ID, kal)
            v.put(CalendarContract.Events.TITLE, simpanJudul)
            v.put(CalendarContract.Events.DTSTART, mulai)
            v.put(CalendarContract.Events.DTEND, selesai)
            v.put(CalendarContract.Events.EVENT_TIMEZONE, zona)
            if (lokasi.isNotBlank()) v.put(CalendarContract.Events.EVENT_LOCATION, lokasi)
            ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)
        }
        return "Acara \"$judul\" dihapus."
    }
}
