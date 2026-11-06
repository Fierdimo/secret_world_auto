package com.secretworld.auto.feature.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * Reproduce una narracion ya sintetizada.
 *
 * Pide **foco de audio** antes de sonar y lo suelta al terminar: en un coche
 * compartimos altavoz con la radio, la navegacion y las llamadas, y una app que
 * no negocia el foco es una app que pisa a las demas.
 */
class NarrationPlayer(private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    val isPlaying: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    val positionMs: Int get() = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    val durationMs: Int get() = runCatching { player?.duration ?: 0 }.getOrDefault(0)

    /** Reproduce [file]. Devuelve la duracion en ms, o null si no se pudo. */
    fun play(file: File, onCompletion: () -> Unit = {}): Int? {
        if (!file.isFile || file.length() == 0L) {
            Log.w(TAG, "no hay audio que reproducir en $file")
            return null
        }
        stop()

        if (!requestFocus()) {
            Log.w(TAG, "foco de audio denegado: no se reproduce")
            return null
        }

        return runCatching {
            val mp = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    Log.i(TAG, "narracion terminada")
                    releaseFocus()
                    onCompletion()
                }
                start()
            }
            player = mp
            Log.i(TAG, "reproduciendo ${file.name}, ${mp.duration} ms")
            mp.duration
        }.onFailure {
            Log.e(TAG, "fallo reproduciendo ${file.name}", it)
            releaseFocus()
        }.getOrNull()
    }

    fun stop() {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
        releaseFocus()
    }

    private fun requestFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        Log.i(TAG, "foco perdido, se para la narracion")
                        stop()
                    }
                    // Un aviso, el navegador o el micrófono un momento: se espera y se sigue.
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        Log.i(TAG, "foco perdido un momento, se pausa la narracion")
                        player?.runCatching { if (isPlaying) pause() }
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> player?.runCatching { start() }
                }
            }
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun releaseFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private companion object {
        const val TAG = "SWPlayer"
    }
}
