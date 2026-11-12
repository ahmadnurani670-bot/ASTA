package id.asta.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Membuat berkas dari teks: txt, md, csv, html, pdf, docx, xlsx. Disimpan di Unduhan/Asta. */
object Berkas {
    private val MIME = mapOf(
        "txt" to "text/plain", "md" to "text/markdown", "csv" to "text/csv", "html" to "text/html",
        "pdf" to "application/pdf",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )

    fun buat(ctx: Context, namaAsal: String, isi: String, formatAsal: String): String {
        val fmt = formatAsal.trim().lowercase(Locale.ROOT).removePrefix(".")
        val mime = MIME[fmt] ?: return "Format tidak didukung. Pilih: " + MIME.keys.joinToString(", ") + "."
        if (isi.isBlank()) return "Isi berkas kosong."
        if (isi.length > 200_000) return "Isi terlalu panjang untuk satu berkas."
        val dasar = namaAsal.trim().substringBeforeLast('.', namaAsal.trim()).replace(Regex("[^\\w .-]"), "_").trim().take(60)
        val nama = (if (dasar.isEmpty()) "asta" else dasar) + "." + fmt

        val bytes: ByteArray = try {
            when (fmt) {
                "csv" -> ("﻿" + isi).toByteArray(Charsets.UTF_8)
                "html" -> (if (isi.contains("<html", true)) isi else htmlDasar(nama, isi)).toByteArray(Charsets.UTF_8)
                "pdf" -> buatPdf(isi)
                "docx" -> buatDocx(isi)
                "xlsx" -> buatXlsx(isi)
                else -> isi.toByteArray(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            return "Gagal membuat berkas: " + (e.message ?: fmt)
        }

        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues()
            v.put(MediaStore.Downloads.DISPLAY_NAME, nama)
            v.put(MediaStore.Downloads.MIME_TYPE, mime)
            v.put(MediaStore.Downloads.RELATIVE_PATH, "Download/Asta")
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return "Gagal menyimpan berkas."
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            } catch (e: Exception) {
                ctx.contentResolver.delete(uri, null, null)
                return "Gagal menulis berkas."
            }
            Alat.catatUrung("berkas $nama") { ctx.contentResolver.delete(uri, null, null) }
            CatatanAsta.tulis(ctx, "Membuat berkas $nama")
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, mime)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return "Berkas $nama tersimpan di Unduhan/Asta. " + Pembuka.buka(ctx, i, nama)
        }
        val folder = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return "Penyimpanan tidak tersedia."
        folder.mkdirs()
        val f = File(folder, nama)
        f.writeBytes(bytes)
        Alat.catatUrung("berkas $nama") { f.delete() }
        CatatanAsta.tulis(ctx, "Membuat berkas $nama")
        return "Berkas $nama tersimpan di " + f.absolutePath + "."
    }

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun htmlDasar(judul: String, isi: String): String {
        val badan = isi.split("\n").joinToString("\n") { b ->
            val t = b.trim()
            when {
                t.isEmpty() -> ""
                t.startsWith("### ") -> "<h3>" + esc(t.removePrefix("### ")) + "</h3>"
                t.startsWith("## ") -> "<h2>" + esc(t.removePrefix("## ")) + "</h2>"
                t.startsWith("# ") -> "<h1>" + esc(t.removePrefix("# ")) + "</h1>"
                else -> "<p>" + esc(t) + "</p>"
            }
        }
        return "<!DOCTYPE html>\n<html lang=\"id\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>" + esc(judul) + "</title></head><body style=\"font-family:sans-serif;max-width:720px;margin:2em auto;padding:0 1em;line-height:1.5\">\n" +
            badan + "\n</body></html>"
    }

    private fun bersihMd(s: String): String = s.replace("**", "").replace("`", "").replace(Regex("(?<!\\w)\\*(?!\\s)|(?<!\\s)\\*(?!\\w)"), "")

    // ---------- PDF ----------

    private fun bungkus(teks: String, cat: Paint, lebar: Float): List<String> {
        val hasil = ArrayList<String>()
        var baris = ""
        for (kata in teks.split(" ")) {
            val coba = if (baris.isEmpty()) kata else "$baris $kata"
            if (cat.measureText(coba) <= lebar || baris.isEmpty()) baris = coba else { hasil.add(baris); baris = kata }
        }
        if (baris.isNotEmpty()) hasil.add(baris)
        return hasil
    }

    private fun buatPdf(teks: String): ByteArray {
        val doc = PdfDocument()
        val lebar = 595
        val tinggi = 842
        val margin = 48f
        val biasa = Paint()
        biasa.textSize = 12f
        biasa.color = Color.BLACK
        biasa.isAntiAlias = true
        val judul = Paint(biasa)
        judul.textSize = 18f
        judul.isFakeBoldText = true
        val sub = Paint(biasa)
        sub.textSize = 14f
        sub.isFakeBoldText = true

        var no = 1
        var hal = doc.startPage(PdfDocument.PageInfo.Builder(lebar, tinggi, no).create())
        var y = margin
        fun pastikan(butuh: Float) {
            if (y + butuh > tinggi - margin) {
                doc.finishPage(hal)
                no++
                hal = doc.startPage(PdfDocument.PageInfo.Builder(lebar, tinggi, no).create())
                y = margin
            }
        }
        for (mentah in teks.split("\n")) {
            val t = mentah.trimEnd()
            if (t.isBlank()) { y += 8f; continue }
            val cat: Paint
            val isi: String
            when {
                t.startsWith("# ") -> { cat = judul; isi = t.removePrefix("# ") }
                t.startsWith("## ") -> { cat = sub; isi = t.removePrefix("## ") }
                t.startsWith("### ") -> { cat = sub; isi = t.removePrefix("### ") }
                else -> { cat = biasa; isi = t }
            }
            val tb = cat.textSize * 1.35f
            for (b in bungkus(bersihMd(isi), cat, lebar - 2 * margin)) {
                pastikan(tb)
                y += tb
                hal.canvas.drawText(b, margin, y, cat)
            }
        }
        doc.finishPage(hal)
        val keluar = ByteArrayOutputStream()
        doc.writeTo(keluar)
        doc.close()
        return keluar.toByteArray()
    }

    // ---------- DOCX dan XLSX (paket OOXML minimal) ----------

    private fun zip(bagian: List<Pair<String, String>>): ByteArray {
        val keluar = ByteArrayOutputStream()
        ZipOutputStream(keluar).use { z ->
            for ((nama, isi) in bagian) {
                z.putNextEntry(ZipEntry(nama))
                z.write(isi.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return keluar.toByteArray()
    }

    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val NS_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val NS_TIPE = "http://schemas.openxmlformats.org/package/2006/content-types"
    private const val REL_DOK = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun docxBagian(teks: String): List<Pair<String, String>> {
        val sb = StringBuilder()
        for (mentah in teks.split("\n")) {
            val t = mentah.trimEnd()
            if (t.isBlank()) { sb.append("<w:p/>"); continue }
            var ukuran = 0
            var isi = t
            when {
                t.startsWith("# ") -> { ukuran = 34; isi = t.removePrefix("# ") }
                t.startsWith("## ") -> { ukuran = 28; isi = t.removePrefix("## ") }
                t.startsWith("### ") -> { ukuran = 24; isi = t.removePrefix("### ") }
            }
            val rpr = if (ukuran > 0) "<w:rPr><w:b/><w:sz w:val=\"$ukuran\"/></w:rPr>" else ""
            sb.append("<w:p><w:r>").append(rpr).append("<w:t xml:space=\"preserve\">").append(esc(bersihMd(isi))).append("</w:t></w:r></w:p>")
        }
        val dok = XML + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + sb + "</w:body></w:document>"
        val tipe = XML + "<Types xmlns=\"$NS_TIPE\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>"
        val rel = XML + "<Relationships xmlns=\"$NS_REL\"><Relationship Id=\"rId1\" Type=\"$REL_DOK/officeDocument\" Target=\"word/document.xml\"/></Relationships>"
        return listOf("[Content_Types].xml" to tipe, "_rels/.rels" to rel, "word/document.xml" to dok)
    }

    private fun buatDocx(teks: String): ByteArray = zip(docxBagian(teks))

    private fun pecahCsv(teks: String): List<List<String>> {
        val baris = teks.replace("\r\n", "\n").split("\n").filter { it.isNotBlank() }
        if (baris.isEmpty()) return emptyList()
        val pemisah = listOf(',', ';', '\t').maxByOrNull { p -> baris[0].count { it == p } } ?: ','
        val hasil = ArrayList<List<String>>()
        for (b in baris) {
            val sel = ArrayList<String>()
            val cur = StringBuilder()
            var kutip = false
            var i = 0
            while (i < b.length) {
                val c = b[i]
                if (kutip) {
                    if (c == '"' && i + 1 < b.length && b[i + 1] == '"') { cur.append('"'); i++ }
                    else if (c == '"') kutip = false
                    else cur.append(c)
                } else if (c == '"') kutip = true
                else if (c == pemisah) { sel.add(cur.toString()); cur.setLength(0) }
                else cur.append(c)
                i++
            }
            sel.add(cur.toString())
            hasil.add(sel)
        }
        return hasil
    }

    private fun kolomHuruf(n: Int): String {
        var x = n
        val sb = StringBuilder()
        while (x >= 0) {
            sb.insert(0, ('A' + (x % 26)))
            x = x / 26 - 1
        }
        return sb.toString()
    }

    fun xlsxBagian(teks: String): List<Pair<String, String>> {
        val data = StringBuilder()
        for ((r, baris) in pecahCsv(teks).withIndex()) {
            data.append("<row r=\"").append(r + 1).append("\">")
            for ((k, nilai) in baris.withIndex()) {
                val ref = kolomHuruf(k) + (r + 1)
                val angka = nilai.trim().toDoubleOrNull()
                if (angka != null && Regex("^-?\\d+(\\.\\d+)?$").matches(nilai.trim())) {
                    data.append("<c r=\"").append(ref).append("\"><v>").append(nilai.trim()).append("</v></c>")
                } else {
                    data.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(esc(nilai)).append("</t></is></c>")
                }
            }
            data.append("</row>")
        }
        val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val sheet = XML + "<worksheet xmlns=\"$ns\"><sheetData>" + data + "</sheetData></worksheet>"
        val buku = XML + "<workbook xmlns=\"$ns\" xmlns:r=\"$REL_DOK\"><sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
        val relBuku = XML + "<Relationships xmlns=\"$NS_REL\"><Relationship Id=\"rId1\" Type=\"$REL_DOK/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>"
        val tipe = XML + "<Types xmlns=\"$NS_TIPE\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>"
        val rel = XML + "<Relationships xmlns=\"$NS_REL\"><Relationship Id=\"rId1\" Type=\"$REL_DOK/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
        return listOf(
            "[Content_Types].xml" to tipe, "_rels/.rels" to rel,
            "xl/workbook.xml" to buku, "xl/_rels/workbook.xml.rels" to relBuku, "xl/worksheets/sheet1.xml" to sheet,
        )
    }

    private fun buatXlsx(teks: String): ByteArray = zip(xlsxBagian(teks))
}
