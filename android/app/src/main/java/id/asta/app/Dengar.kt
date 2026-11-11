package id.asta.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Suara masuk lewat pengenal suara Android. Semua panggilan diteruskan ke thread utama. */
object Dengar {
    private var pengenal: SpeechRecognizer? = null
    private val utama = Handler(Looper.getMainLooper())
    @Volatile var aktif = false

    fun mulai(ctx: Context, hasil: (String) -> Unit, galat: (String) -> Unit) {
        val app = ctx.applicationContext
        utama.post {
            if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                galat("izin")
                return@post
            }
            if (!SpeechRecognizer.isRecognitionAvailable(app)) {
                galat("tidak-tersedia")
                return@post
            }
            pengenal?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(app)
            pengenal = r
            aktif = true
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onError(error: Int) {
                    aktif = false
                    val nama = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "tidak-ada-suara"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "jaringan"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "izin"
                        SpeechRecognizer.ERROR_CLIENT -> "dibatalkan"
                        else -> "kode-$error"
                    }
                    galat(nama)
                }

                override fun onResults(results: Bundle?) {
                    aktif = false
                    val daftar = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    hasil(if (daftar != null && daftar.isNotEmpty()) daftar[0] else "")
                }
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            r.startListening(i)
        }
    }

    fun batal() {
        utama.post {
            try {
                pengenal?.cancel()
                pengenal?.destroy()
            } catch (e: Exception) {
                // abaikan
            }
            pengenal = null
            aktif = false
        }
    }
}
