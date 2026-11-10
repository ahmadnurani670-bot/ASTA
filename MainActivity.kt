package id.asta.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/** Cangkang native yang menampilkan PWA Asta dan menyediakan fitur Android lewat jembatan. */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private var pemilihBerkas: ValueCallback<Array<Uri>>? = null
    private var pengenal: SpeechRecognizer? = null

    companion object {
        private const val KODE_BERKAS = 100
        private const val KODE_NOTIF = 101
        private const val KODE_MIK = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifikasi.siapkanSaluran(this)
        web = WebView(this)
        web.setBackgroundColor(Warna.LATAR)
        setContentView(web)
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        atur()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), KODE_NOTIF)
        }

        val alamat = Pengaturan.alamat(this)
        if (alamat.isBlank()) {
            Pengaturan.tanyaAlamat(this) { web.loadUrl(Pengaturan.alamat(this)) }
        } else {
            web.loadUrl(alamat)
        }
    }

    private fun atur() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = false
        s.setSupportMultipleWindows(false)
        s.javaScriptCanOpenWindowsAutomatically = false

        web.addJavascriptInterface(JembatanAsta(this), "AstaNative")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val tujuan = request.url
                val dasar = Uri.parse(Pengaturan.alamat(this@MainActivity))
                if (tujuan.scheme == "https" && tujuan.host == dasar.host) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, tujuan))
                } catch (e: Exception) {
                    // tidak ada aplikasi yang bisa membuka tautan itu
                }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) tampilkanGalat(view)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) tampilkanGalat(view)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                pemilihBerkas?.onReceiveValue(null)
                pemilihBerkas = callback
                return try {
                    startActivityForResult(params.createIntent(), KODE_BERKAS)
                    true
                } catch (e: Exception) {
                    pemilihBerkas = null
                    false
                }
            }
        }
    }

    private fun tampilkanGalat(view: WebView) {
        val alamat = Pengaturan.alamat(this)
        val html = "<body style='background:#030a14;color:#d6ebff;font-family:sans-serif;padding:24px'>" +
            "<h2>Asta tidak bisa dibuka</h2>" +
            "<p>Alamat: " + alamat + "</p>" +
            "<p>Periksa koneksi internet dan alamatnya, lalu coba lagi.</p>" +
            "<p><a style='color:#33d6ff' href='" + alamat + "'>Coba lagi</a></p>" +
            "<p><button style='font-size:16px;padding:10px 16px' onclick='AstaNative.ubahAlamat()'>Ubah alamat Asta</button></p></body>"
        view.loadData(html, "text/html; charset=utf-8", null)
    }

    fun muatUlang() {
        web.loadUrl(Pengaturan.alamat(this))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == KODE_BERKAS) {
            pemilihBerkas?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            pemilihBerkas = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // ---------- Suara masuk (WebView tidak mendukung Web Speech, jadi dipakai pengenal Android) ----------

    private fun js(kode: String) {
        web.evaluateJavascript(kode, null)
    }

    private fun kirimGalat(kode: String) {
        js("window.astaGalatDengar(" + JSONObject.quote(kode) + ")")
    }

    fun mulaiDengar() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), KODE_MIK)
            kirimGalat("izin")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            kirimGalat("tidak-tersedia")
            return
        }
        pengenal?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        pengenal = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                val nama = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "tidak-ada-suara"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "jaringan"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "izin"
                    SpeechRecognizer.ERROR_CLIENT -> "dibatalkan"
                    else -> "kode-" + error
                }
                kirimGalat(nama)
            }

            override fun onResults(results: Bundle?) {
                val daftar = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val teks = if (daftar != null && daftar.isNotEmpty()) daftar[0] else ""
                js("window.astaHasilDengar(" + JSONObject.quote(teks) + ")")
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        r.startListening(i)
    }

    fun berhentiDengar() {
        pengenal?.stopListening()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        pengenal?.destroy()
        pengenal = null
        web.destroy()
        super.onDestroy()
    }
}
