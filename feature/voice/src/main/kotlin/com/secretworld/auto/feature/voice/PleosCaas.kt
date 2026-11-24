package com.secretworld.auto.feature.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.util.Log
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.stt.listener.OnServerConnectionListener as SttServer
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import ai.pleos.playground.tts.listener.OnServerConnectionListener as TtsServer

/**
 * La voz y la escucha de Pleos, con sus SDK oficiales (`TextToSpeech` y
 * `SpeechToText` 3.3.1, del Maven de Pleos). Los dos hablan con la caas
 * (`ai.pleos.playground.caas`).
 *
 * Sin credenciales, solo en el dispositivo (`ON_DEVICE`). Con el Client ID y el
 * Client Secret de *My Project* —ver [PleosCredentials]—, `HYBRID`: el servidor
 * cuando hay red y el dispositivo si no.
 *
 * Idiomas según la documentación: `en-US` y `es-US`/`es-MX`, entre otros. **Sin
 * italiano.** Ver docs/PLEOS-WATERFALL-PC.md.
 */
object PleosCaas {
    const val PACKAGE = "ai.pleos.playground.caas"

    /** Si la caas está instalada: fuera de Pleos, no. */
    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** El idioma que entiende Pleos: español de América, no de España. */
    fun localeOf(language: VoiceLanguage): Locale = when (language) {
        VoiceLanguage.SPANISH -> Locale.forLanguageTag("es-US")
        VoiceLanguage.ITALIAN -> Locale.forLanguageTag("it-IT")
        else -> Locale.forLanguageTag("en-US")
    }

    const val TAG = "SWPleos"

    /** El tiempo que se espera a que el SDK se conecte a la caas. */
    internal const val INIT_TIMEOUT_MS = 8_000L
}

/**
 * Las credenciales de la app en Pleos (*My Project*). Sin ellas, solo lo que
 * funciona en el dispositivo.
 */
data class PleosCredentials(val clientId: String, val clientSecret: String) {
    companion object {
        fun of(clientId: String, clientSecret: String): PleosCredentials? =
            PleosCredentials(clientId, clientSecret).takeIf { clientId.isNotBlank() && clientSecret.isNotBlank() }
    }
}

/**
 * La voz de Pleos: habla ella misma, no devuelve audio. Se sabe que terminó por
 * `onDone`. Si da error —en Waterfall, `ON_DEVICE_ENGINE_ERROR` por falta del
 * modelo—, se registra y la frase queda escrita en pantalla.
 */
class PleosSpeaker(
    private val context: Context,
    private val credentials: PleosCredentials? = null,
) : LiveSpeaker {

    private var tts: TextToSpeech? = null
    private var initialized: CompletableDeferred<Boolean>? = null
    private var locale: Locale? = null

    @Volatile private var pending: CompletableDeferred<Boolean>? = null

    /** Si la frase en curso llegó a sonar: `false` si dio error antes. */
    @Volatile private var starting: CompletableDeferred<Boolean>? = null

    private val events = object : EventListener {
        override fun onStart() {
            Log.i(PleosCaas.TAG, "TTS empieza")
            starting?.complete(true)
        }

        override fun onReady() = Unit

        override fun onDone() {
            starting?.complete(true)
            pending?.complete(true)
        }

        override fun onStop() = onDone()

        override fun onError(error: TtsError) {
            Log.w(PleosCaas.TAG, "TTS error: $error")
            starting?.complete(false)
            pending?.complete(false)
        }

        override fun onUpdatedRms(rms: Double) = Unit
    }

    override val available: Boolean get() = PleosCaas.installed(context)

    /** Crea el cliente y espera a que conecte. Todo en el hilo principal: el SDK lo da por hecho. */
    private suspend fun ready(): TextToSpeech? = withContext(Dispatchers.Main) {
        val waiting = initialized ?: CompletableDeferred<Boolean>().also { done ->
            initialized = done
            val mode = if (credentials != null) TtsMode.HYBRID else TtsMode.ON_DEVICE
            val client = runCatching { TextToSpeech(context, mode) }
                .onFailure { Log.e(PleosCaas.TAG, "TTS: no se pudo crear", it) }
                .getOrNull()
            tts = client
            if (client == null) {
                done.complete(false)
                return@also
            }
            runCatching {
                client.initialize(object : TtsInit {
                    override fun onSuccess() {
                        Log.i(PleosCaas.TAG, "TTS de Pleos listo ($mode)")
                        // Solo ahora: antes de inicializar, el SDK no tiene motor y se cae.
                        val wired = runCatching {
                            client.addEventListener(events)
                            client.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build(),
                            )
                        }.onFailure { Log.e(PleosCaas.TAG, "TTS: no se pudo preparar", it) }.isSuccess
                        if (!wired) {
                            done.complete(false)
                            return
                        }
                        credentials?.let { keys ->
                            client.registerApp(keys.clientId, keys.clientSecret, object : TtsServer {
                                override fun onConnected() {
                                    Log.i(PleosCaas.TAG, "TTS: app registrada en el servidor")
                                }

                                override fun onFailed(error: TtsError) {
                                    Log.w(PleosCaas.TAG, "TTS: registro en el servidor: $error")
                                }
                            })
                        }
                        // El registro no bloquea: en HYBRID, sin servidor, habla el dispositivo.
                        done.complete(true)
                    }

                    override fun onFailure(error: TtsError) {
                        Log.w(PleosCaas.TAG, "TTS: no inicializa: $error")
                        done.complete(false)
                    }
                })
            }.onFailure {
                Log.e(PleosCaas.TAG, "TTS initialize", it)
                done.complete(false)
            }
        }
        val ok = withTimeoutOrNull(PleosCaas.INIT_TIMEOUT_MS) { waiting.await() } ?: false
        tts.takeIf { ok }
    }

    override suspend fun speak(text: String, language: VoiceLanguage): Boolean {
        val client = ready() ?: return false.also { Log.w(PleosCaas.TAG, "TTS: sin servicio") }
        val done = CompletableDeferred<Boolean>()
        val began = CompletableDeferred<Boolean>()
        pending = done
        starting = began
        val sent = withContext(Dispatchers.Main) {
            runCatching {
                val wanted = PleosCaas.localeOf(language)
                if (locale != wanted) {
                    client.setLocale(wanted)
                    locale = wanted
                }
                client.speak(text, true)
            }.onFailure { Log.e(PleosCaas.TAG, "TTS speak", it) }.isSuccess
        }
        if (!sent) return false
        // Primero, que empiece a sonar: un error de idioma o de motor puede
        // llegar tarde, y la app no debe esperar la frase entera para saberlo.
        val sounding = withTimeoutOrNull(START_TIMEOUT_MS) { began.await() } ?: false
        if (!sounding) {
            Log.w(PleosCaas.TAG, "TTS: no empezó a sonar en ${START_TIMEOUT_MS / 1000} s")
            stop()
            return false
        }
        // Lo que tarde en decirse, con margen; si no llega el final, se sigue.
        return withTimeoutOrNull(text.length * MS_PER_CHAR + SPEAK_MARGIN_MS) { done.await() } ?: true
    }

    override fun stop() {
        pending?.complete(true)
        tts?.let { client -> runCatching { client.stop() } }
    }

    override fun release() {
        stop()
        tts?.let { client ->
            runCatching {
                client.removeEventListener(events)
                client.release()
            }
        }
        tts = null
        initialized = null
    }

    private companion object {
        /** Lo más que se espera a que una frase empiece a sonar. */
        const val START_TIMEOUT_MS = 10_000L
        const val MS_PER_CHAR = 90L
        const val SPEAK_MARGIN_MS = 5_000L
    }
}

/**
 * La escucha de Pleos. Como [SystemVoiceInput], se cierra en cada frase y
 * [HandsFree] la reabre; la palabra de activación se busca en el texto.
 */
class PleosVoiceInput(
    private val context: Context,
    private val language: VoiceLanguage,
    private val scope: CoroutineScope,
    private val credentials: PleosCredentials? = null,
) : VoiceInput {

    private val _state = MutableStateFlow<VoiceInput.State>(VoiceInput.State.Idle)
    override val state: StateFlow<VoiceInput.State> = _state.asStateFlow()

    private var stt: SpeechToText? = null
    private var initialized: CompletableDeferred<Boolean>? = null
    private var direct = true
    private var answerTurn = false

    /** Lo último que se oyó en esta frase: si no llega el final, vale esto. */
    private var lastHeard = ""
    private var delivered = false

    /** El turno de escucha en curso: si se cancela mientras el SDK conecta, no se abre el micrófono. */
    private var turn = 0
    private var requested = false

    private var hardFailures = 0
    @Volatile private var gaveUp = false

    override val available: Boolean get() = !gaveUp && PleosCaas.installed(context)
    override val continuous: Boolean = false

    override val hasPermission: Boolean
        get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private val results = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (text.isNotBlank()) lastHeard = text
            if (isFinal) deliver(text)
        }

        override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) = Unit
        override fun onStartedRecognition() = Unit

        override fun onEndedRecognition() {
            if (!delivered) deliver(lastHeard)
        }

        override fun onError(error: SttError) {
            Log.w(TAG, "error de reconocimiento: $error")
            delivered = true
            if (error != SttError.TIMEOUT && ++hardFailures >= GIVE_UP_AFTER && !gaveUp) {
                gaveUp = true
                Log.w(TAG, "la escucha de Pleos no responde: sin escucha, se usa tocando")
            }
            answerTurn = false
            _state.value = VoiceInput.State.Failed(reasonOf(error), quiet = error == SttError.TIMEOUT && !direct)
        }

        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private fun deliver(text: String) {
        if (delivered) return
        delivered = true
        hardFailures = 0
        Log.i(TAG, "oido: «$text»${if (direct) " (respuesta)" else ""}")
        answerTurn = false
        _state.value = if (text.isBlank()) {
            VoiceInput.State.Failed(reasonOf(SttError.TIMEOUT), quiet = !direct)
        } else {
            VoiceInput.State.Heard(text, direct)
        }
    }

    /** Crea el cliente y espera a que conecte, en el hilo principal. */
    private suspend fun ready(): SpeechToText? = withContext(Dispatchers.Main) {
        val waiting = initialized ?: CompletableDeferred<Boolean>().also { done ->
            initialized = done
            val mode = if (credentials != null) SttMode.HYBRID else SttMode.ON_DEVICE
            val client = runCatching { SpeechToText(context, mode) }
                .onFailure { Log.e(TAG, "STT: no se pudo crear", it) }
                .getOrNull()
            stt = client
            if (client == null) {
                done.complete(false)
                return@also
            }
            runCatching {
                client.initialize(object : SttInit {
                    override fun onSuccess() {
                        Log.i(TAG, "STT de Pleos listo ($mode)")
                        // Solo ahora: antes de inicializar, el SDK no tiene motor y se cae.
                        val wired = runCatching {
                            client.addListener(results)
                            client.setLocale(PleosCaas.localeOf(language))
                        }.onFailure { Log.e(TAG, "STT: no se pudo preparar", it) }.isSuccess
                        if (!wired) {
                            done.complete(false)
                            return
                        }
                        credentials?.let { keys ->
                            client.registerApp(keys.clientId, keys.clientSecret, object : SttServer {
                                override fun onConnected() {
                                    Log.i(TAG, "STT: app registrada en el servidor")
                                }

                                override fun onFailed(error: SttError) {
                                    Log.w(TAG, "STT: registro en el servidor: $error")
                                }
                            })
                        }
                        done.complete(true)
                    }

                    override fun onFailure(error: SttError) {
                        Log.w(TAG, "STT: no inicializa: $error")
                        done.complete(false)
                    }
                })
            }.onFailure {
                Log.e(TAG, "STT initialize", it)
                done.complete(false)
            }
        }
        val ok = withTimeoutOrNull(PleosCaas.INIT_TIMEOUT_MS) { waiting.await() } ?: false
        stt.takeIf { ok }
    }

    override fun listen(direct: Boolean) {
        if (direct) answerTurn = true
        // Vigilar la palabra no interrumpe un turno de respuesta en curso.
        if (!direct && answerTurn && _state.value is VoiceInput.State.Listening) return
        this.direct = direct || answerTurn
        if (!available) {
            answerTurn = false
            _state.value = VoiceInput.State.Idle
            return
        }
        if (!hasPermission) {
            _state.value = VoiceInput.State.Failed(
                language.say(en = "Microphone permission not granted", es = "Falta el permiso del micrófono", it = "Manca il permesso del microfono"),
            )
            return
        }
        cancel()
        lastHeard = ""
        delivered = false
        _state.value = VoiceInput.State.Listening
        val mine = turn
        scope.launch {
            val client = ready()
            if (mine != turn) return@launch
            if (client == null) {
                gaveUp = true
                Log.w(TAG, "sin escucha de Pleos: se usa tocando")
                answerTurn = false
                _state.value = VoiceInput.State.Idle
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (mine != turn) return@withContext
                requested = true
                runCatching { client.request() }.onFailure {
                    Log.e(TAG, "STT request", it)
                    requested = false
                    delivered = true
                    _state.value = VoiceInput.State.Failed(it.message ?: "could not start listening")
                }
            }
        }
    }

    /** Apaga la escucha para siempre en este arranque: sin voz que conteste, no sirve. */
    fun turnOff() {
        gaveUp = true
        cancel()
        _state.value = VoiceInput.State.Idle
    }

    override fun stopListening() = cancel()

    override fun cancel() {
        turn++
        delivered = true
        // Solo si se pidió: parar lo que no empezó avisa de un final que no existe.
        if (requested) stt?.let { client -> runCatching { client.stop() } }
        requested = false
        if (_state.value is VoiceInput.State.Listening) _state.value = VoiceInput.State.Idle
    }

    override fun acknowledge() {
        if (_state.value !is VoiceInput.State.Listening) _state.value = VoiceInput.State.Idle
    }

    override fun inject(text: String) {
        _state.value = VoiceInput.State.Heard(text, direct = true)
    }

    fun release() {
        cancel()
        stt?.let { client ->
            runCatching {
                client.removeListener(results)
                client.release()
            }
        }
        stt = null
        initialized = null
    }

    private fun reasonOf(error: SttError): String = when (error) {
        SttError.TIMEOUT -> language.say(en = "I didn't catch that", es = "No te he oído", it = "Non ti ho sentito")
        SttError.MIC_ERROR, SttError.PERMISSION_DENIED -> language.say(
            en = "The microphone is not available", es = "El micrófono no está disponible", it = "Il microfono non è disponibile",
        )
        SttError.NETWORK_ERROR -> language.say(
            en = "Speech recognition needs a connection here",
            es = "Aquí el reconocimiento de voz necesita conexión",
            it = "Qui il riconoscimento vocale ha bisogno di connessione",
        )
        SttError.UNAVAILABLE_LANGUAGE -> language.say(
            en = "Speech recognition doesn't support this language",
            es = "El reconocimiento de voz no admite este idioma",
            it = "Il riconoscimento vocale non supporta questa lingua",
        )
        else -> language.say(en = "Speech recognition failed ($error)", es = "Falló el reconocimiento de voz ($error)", it = "Riconoscimento vocale non riuscito ($error)")
    }

    private companion object {
        const val GIVE_UP_AFTER = 3
        const val TAG = "SWVoiceInput"
    }
}

/**
 * Un motor que habla él mismo, sin dejar el audio en un fichero: el de la
 * plataforma. [NarrationController] lo usa en lugar de sintetizar y reproducir.
 */
interface LiveSpeaker {
    val available: Boolean

    /** Dice [text] y vuelve cuando terminó. `false` si no pudo decirlo. */
    suspend fun speak(text: String, language: VoiceLanguage): Boolean

    fun stop()

    fun release()
}
