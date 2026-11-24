package com.secretworld.auto.feature.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * La voz on-device de la caas sin el SDK, con la llamada `speak` de siempre. El
 * SDK 3.3.1 pide `speakWithAudioConfig`, y la caas 2.2.7.1 (v2.1.5) no la
 * atiende: sin esto, al verificar no se sabe si falla la imagen o el SDK.
 */
class PleosRawTts(private val context: Context) {

    /** Dice [text] en [tag] y devuelve el resultado; [onEvent], cada mensaje que llega. */
    suspend fun speak(text: String, tag: String, startMs: Long, endMs: Long, onEvent: (String) -> Unit): String = withContext(Dispatchers.IO) {
        val connected = CompletableDeferred<IBinder?>()
        val began = CompletableDeferred<String?>()
        val done = CompletableDeferred<String?>()
        val callback = PleosBinder.callback(CALLBACK, 2) { (cmd, data) ->
            onEvent("$cmd ${data.orEmpty().take(120)}")
            when (cmd) {
                "UpdateTtsStart" -> began.complete(null)
                // Un fin sin haber empezado es de la frase anterior.
                "UpdateTtsDone" -> if (began.isCompleted) done.complete(null)
                "UpdateTtsStop" -> if (began.isCompleted) done.complete("stopped")
                "UpdateTtsError" -> {
                    began.complete("error ${data.orEmpty().take(120)}")
                    done.complete("error ${data.orEmpty().take(120)}")
                }
            }
        }
        val connection = PleosBinder.bind(context, SERVICE, ACTION) { connected.complete(it) }
            ?: return@withContext "FAIL could not bind"
        var target: IBinder? = null
        val app = context.packageName
        try {
            val binder = withTimeoutOrNull(BIND_MS) { connected.await() } ?: return@withContext "FAIL no binder"
            target = binder
            PleosBinder.call(binder, DESCRIPTOR, REGISTER, callback, app)
            PleosBinder.call(binder, DESCRIPTOR, SET_LOCALE, tag, app)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            PleosBinder.call(binder, DESCRIPTOR, SPEAK, text, true, app, attributes)
            val started = withTimeoutOrNull(startMs) { began.await() to true } ?: return@withContext "FAIL no start in ${startMs / 1000}s"
            started.first?.let { return@withContext "FAIL $it" }
            val ended = withTimeoutOrNull(endMs) { done.await() to true } ?: return@withContext "PARTIAL started, no end"
            ended.first?.let { "FAIL $it" } ?: "OK, engine reports it spoke: confirm by ear"
        } finally {
            target?.let { b ->
                runCatching { PleosBinder.call(b, DESCRIPTOR, STOP, app) }
                runCatching { PleosBinder.call(b, DESCRIPTOR, UNREGISTER, callback) }
            }
            runCatching { context.unbindService(connection) }
        }
    }

    private companion object {
        const val SERVICE = "ai.pleos.playground.caas.service.speech.SpeechService"
        const val ACTION = "ai.pleos.playground.caas.intent.action.TTS_STREAM_ON_DEVICE_START"
        const val DESCRIPTOR = "ai.pleos.playground.caas.service.speech.aidl.ITtsOnDeviceStream"
        const val CALLBACK = "ai.pleos.playground.caas.service.speech.aidl.ITtsCallback"
        const val REGISTER = 1
        const val UNREGISTER = 2
        const val SPEAK = 4
        const val STOP = 5
        const val SET_LOCALE = 6
        const val BIND_MS = 8_000L
    }
}
