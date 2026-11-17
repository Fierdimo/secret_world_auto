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
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El micrófono por el reconocedor **del sistema**.
 *
 * Es la implementación de [VoiceInput] que no necesita modelos instalados, y por
 * eso sigue existiendo: es lo que atiende mientras [OnDeviceVoiceInput] no tiene
 * sus modelos, y lo que hay en un dispositivo sin ellos. No detecta la palabra
 * de activación: [HandsFree] la reabre en cada frase y [WakeWord] la busca en el
 * texto, que es la parte ineficiente que el reconocedor en el dispositivo
 * sustituye.
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
class SystemVoiceInput(
    private val context: Context,
    /** En qué idioma se explican los fallos: es lo que se enseña al conductor. */
    private val language: VoiceLanguage = VoiceLanguage.ENGLISH,
) : VoiceInput {

    private val _state = MutableStateFlow<VoiceInput.State>(VoiceInput.State.Idle)
    override val state: StateFlow<VoiceInput.State> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var direct: Boolean = true

    /**
     * La app acaba de preguntar algo y espera la respuesta, sin palabra de
     * activación. Dura hasta que se oye algo o se agota la espera. Sin esto, el
     * bucle de [HandsFree] reabría el reconocedor en modo "esperar la palabra"
     * justo después de la pregunta, y la respuesta —"unas 4 horas"— se
     * descartaba por no llevar "Sícret".
     */
    private var answerTurn = false

    override val available: Boolean get() = !gaveUp && SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Fallos seguidos que no son silencio —sin red, sin servicio, idioma no
     * admitido—. A los [GIVE_UP_AFTER], el reconocedor no está y la app se usa
     * tocando: reabrirlo sin parar solo gasta y llena la pantalla de errores.
     */
    private var hardFailures = 0
    @Volatile private var gaveUp = false

    /** Cierra el micrófono en cada frase: la escucha permanente es reabrirlo. */
    override val continuous: Boolean = false

    override val hasPermission: Boolean
        get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Abre el micrófono. Vuelve a [VoiceInput.State.Idle] solo, pase lo que pase. */
    override fun listen(direct: Boolean) {
        if (direct) answerTurn = true
        // Vigilar la palabra no interrumpe un turno de respuesta en curso.
        if (!direct && answerTurn && _state.value is VoiceInput.State.Listening) return
        this.direct = direct || answerTurn
        // Sin reconocedor no se escucha, y no se dice en cada turno: el micrófono
        // tachado y el aviso del principio ya lo cuentan. Tras guardar un
        // itinerario salía "no hay reconocimiento" sin que nadie lo pidiera.
        if (!available) {
            answerTurn = false
            _state.value = VoiceInput.State.Idle
            return
        }
        if (!hasPermission) {
            _state.value = VoiceInput.State.Failed(reasonOf(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
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
            // El idioma de la app, no uno fijo: con "en-US" fijo, "termina el
            // itinerario" llegaba como «terminal 18 area».
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // En marcha no hay tiempo para frases largas, y una escucha que no se
            // cierra sola deja el micrófono abierto sin que nadie se entere.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_200L)
        }

        _state.value = VoiceInput.State.Listening
        runCatching { engine.startListening(intent) }
            .onFailure { _state.value = VoiceInput.State.Failed(it.message ?: "could not start listening") }
    }

    /**
     * Apaga la escucha para siempre en este arranque. Se usa cuando la app no
     * puede contestar —sin voz—: una conversación que solo va en un sentido no
     * sirve, y el micrófono abierto sin fin solo confunde.
     */
    fun turnOff() {
        gaveUp = true
        cancel()
        _state.value = VoiceInput.State.Idle
    }

    /** Aquí no hay vigilancia propia que conservar: cancelar y apagar es lo mismo. */
    override fun stopListening() = cancel()

    override fun cancel() {
        recognizer?.runCatching {
            cancel()
            destroy()
        }
        recognizer = null
        if (_state.value is VoiceInput.State.Listening) _state.value = VoiceInput.State.Idle
    }

    /** Vuelve al silencio tras haber mostrado lo que se oyó o el fallo. */
    override fun acknowledge() {
        if (_state.value !is VoiceInput.State.Listening) _state.value = VoiceInput.State.Idle
    }

    override fun inject(text: String) {
        _state.value = VoiceInput.State.Heard(text, direct = true)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            hardFailures = 0
            val heard = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            Log.i(TAG, "oido: «$heard»${if (direct) " (respuesta)" else ""}")
            answerTurn = false
            _state.value = if (heard.isBlank()) {
                VoiceInput.State.Failed(reasonOf(SpeechRecognizer.ERROR_NO_MATCH), quiet = !direct)
            } else {
                VoiceInput.State.Heard(heard, direct)
            }
        }

        override fun onError(error: Int) {
            Log.w(TAG, "error de reconocimiento: $error")
            if (error in HARD_FAILURES && ++hardFailures >= GIVE_UP_AFTER && !gaveUp) {
                gaveUp = true
                Log.w(TAG, "el reconocedor del sistema no responde: sin escucha, se usa tocando")
            }
            // El servicio que se desconecta entre una escucha y la siguiente —en
            // algunos móviles, cada pocas— tampoco es una avería: se reabre.
            val nothingHeard = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
            // Un fallo por estar ocupado —se reabrió encima— no agota el turno.
            if (error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY && error != SpeechRecognizer.ERROR_CLIENT) answerTurn = false
            _state.value = VoiceInput.State.Failed(reasonOf(error), quiet = nothingHeard && !direct)
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
     * La etiqueta de idioma para el reconocedor. Con el país del teléfono si
     * habla ese idioma —"es-CO" en Colombia reconoce mejor que "es-ES"—; si no,
     * la variante más común.
     */
    private fun recognitionTag(): String {
        val device = java.util.Locale.getDefault()
        if (device.language == language.code && device.country.isNotEmpty()) return device.toLanguageTag()
        return language.say(en = "en-US", es = "es-ES", it = "it-IT")
    }

    /**
     * Los códigos de error se traducen a algo que se pueda enseñar.
     *
     * `ERROR_NO_MATCH` y `ERROR_SPEECH_TIMEOUT` no son averías: son "no te he
     * oído", y merecen un texto distinto del de una avería de verdad.
     */
    private fun reasonOf(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> language.say(en = "I didn't catch that", es = "No te he oído", it = "Non ti ho sentito")
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> language.say(
            en = "Microphone permission not granted", es = "Falta el permiso del micrófono", it = "Manca il permesso del microfono",
        )
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> language.say(
            en = "Speech recognition needs a connection here",
            es = "Aquí el reconocimiento de voz necesita conexión",
            it = "Qui il riconoscimento vocale ha bisogno di connessione",
        )
        SpeechRecognizer.ERROR_AUDIO -> language.say(en = "No audio input on this device", es = "Este dispositivo no tiene entrada de audio", it = "Questo dispositivo non ha ingresso audio")
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> language.say(en = "Still listening, one moment", es = "Sigo escuchando, un momento", it = "Sto ancora ascoltando, un momento")
        else -> language.say(en = "Speech recognition failed ($error)", es = "Falló el reconocimiento de voz ($error)", it = "Riconoscimento vocale non riuscito ($error)")
    }

    private companion object {
        /** Lo que dice que el reconocedor no está, no que no se oyó nada. */
        private val HARD_FAILURES = setOf(
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        )
        private const val GIVE_UP_AFTER = 3
        const val TAG = "SWVoiceInput"
    }
}
