package com.secretworld.auto.feature.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El micrófono: convierte lo que dice el conductor en texto.
 *
 * Usa el `SpeechRecognizer` de AOSP **también en el vehículo**, y eso es una
 * decisión con datos detrás: el `STT_SERVICE` de Pleos figura como *coming soon*
 * y su `onBind` devuelve `null` —está instalado y acepta la conexión, pero la
 * implementación no existe—, mientras que en esa misma imagen `startListening()`
 * arranca `ai.umos.ambientai/.AmbRecognitionService` y **activa el micrófono**.
 * Ver [docs/PLEOS-SDK-SURFACE.md]. Cuando el servicio se publique, entra por aquí
 * y no cambia nada por encima.
 *
 * **Todo se llama desde el hilo principal**: `SpeechRecognizer` lo exige.
 */
class VoiceInput(private val context: Context) {

    sealed interface State {
        data object Idle : State
        /** El micrófono está abierto. La pantalla tiene que decirlo. */
        data object Listening : State
        /**
         * @param direct si la escucha la abrió el conductor con el botón. Lo que
         * llega por la escucha permanente **no es directo** y exige la palabra de
         * activación; sin distinguirlo, cualquier conversación dentro del coche
         * sería una orden.
         */
        data class Heard(val text: String, val direct: Boolean) : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var direct: Boolean = true

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    val hasPermission: Boolean
        get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Abre el micrófono. Vuelve a [State.Idle] solo, pase lo que pase. */
    fun listen(direct: Boolean = true, language: String = "en-US") {
        this.direct = direct
        if (!available) {
            _state.value = State.Failed("this device has no speech recognition")
            return
        }
        if (!hasPermission) {
            _state.value = State.Failed("microphone permission not granted")
            return
        }

        cancel()
        val engine = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
        recognizer = engine

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // En marcha no hay tiempo para frases largas, y una escucha que no se
            // cierra sola deja el micrófono abierto sin que nadie se entere.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_200L)
        }

        _state.value = State.Listening
        runCatching { engine.startListening(intent) }
            .onFailure { _state.value = State.Failed(it.message ?: "could not start listening") }
    }

    fun cancel() {
        recognizer?.runCatching {
            cancel()
            destroy()
        }
        recognizer = null
        if (_state.value is State.Listening) _state.value = State.Idle
    }

    /** Vuelve al silencio tras haber mostrado lo que se oyó o el fallo. */
    fun acknowledge() {
        if (_state.value !is State.Listening) _state.value = State.Idle
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val heard = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            Log.i(TAG, "oido: «$heard»")
            _state.value = if (heard.isBlank()) {
                State.Failed("I didn't catch that")
            } else {
                State.Heard(heard, direct)
            }
        }

        override fun onError(error: Int) {
            Log.w(TAG, "error de reconocimiento: $error")
            _state.value = State.Failed(reasonOf(error))
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /**
     * Los códigos de error se traducen a algo que se pueda enseñar.
     *
     * `ERROR_NO_MATCH` y `ERROR_SPEECH_TIMEOUT` no son averías: son "no te he
     * oído", y merecen un texto distinto del de una avería de verdad.
     */
    private fun reasonOf(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission not granted"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "speech recognition needs a connection here"
        SpeechRecognizer.ERROR_AUDIO -> "no audio input on this device"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "still listening, one moment"
        else -> "speech recognition failed ($error)"
    }

    private companion object {
        const val TAG = "SWVoiceInput"
    }
}
