package id.asta.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            K.AKSI_BUNYI -> bunyi(ctx, intent)
            K.AKSI_MATIKAN -> ctx.stopService(Intent(ctx, BunyiService::class.java))
            K.AKSI_TUNDA -> {
                val jenis = intent.getStringExtra("jenis") ?: "pengingat"
                val pesan = intent.getStringExtra("pesan") ?: ""
                val waktu = System.currentTimeMillis() + K.TUNDA_MENIT * 60_000L
                val p = Gudang.tambah(ctx, jenis, pesan, waktu, emptyList(), 0, 0)
                Penjadwal.pasang(ctx, p)
                ctx.stopService(Intent(ctx, BunyiService::class.java))
            }
        }
    }

    private fun bunyi(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        val p = Gudang.cari(ctx, id) ?: return
        if (p.hari.isNotEmpty()) {
            val berikut = p.copy(waktuMs = Penjadwal.waktuBerikutnya(p.hari, p.jam, p.menit, System.currentTimeMillis()))
            Gudang.perbarui(ctx, berikut)
            Penjadwal.pasang(ctx, berikut)
        } else {
            Gudang.hapus(ctx, id)
        }
        val layanan = Intent(ctx, BunyiService::class.java)
        layanan.putExtra("jenis", p.jenis)
        layanan.putExtra("pesan", p.pesan)
        try {
            ctx.startForegroundService(layanan)
        } catch (e: Exception) {
            Notifikasi.tampilkanSaja(ctx, p.jenis, p.pesan)
        }
    }
}
