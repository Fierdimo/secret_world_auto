package com.secretworld.auto.feature.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.secretworld.auto.domain.LanguageModel
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Los servicios de voz e IA de Pleos (la caas, `ai.pleos.playground.caas`).
 *
 * **Sin librería oficial**: Pleos no publica todavía los clientes de
 * `sdk.tts`, `sdk.stt` ni el del LLM. El contrato —descriptores, códigos de
 * transacción y orden de los argumentos— está leído del bytecode de la caas
 * 4.2.9 (imagen Connect-W de agosto de 2026) y coincide con el medido en la
 * 2.2.7.1 (v2.1.5). Puede romperse con otra versión: cuando lleguen las
 * librerías, esto se sustituye por ellas. Ver docs/PLEOS-SDK-SURFACE.md.
 *
 * Se vincula con el componente explícito: las acciones que `onBind` reconoce
 * no están en el intent-filter del servicio.
 */
internal object PleosCaas {
    const val PACKAGE = "ai.pleos.playground.caas"
    const val SPEECH_SERVICE = "ai.pleos.playground.caas.service.speech.SpeechService"
    const val LLM_SERVICE = "ai.pleos.playground.caas.service.llm.LLMService"
    const val TTS_ON_DEVICE_ACTION = "ai.pleos.playground.caas.intent.action.TTS_STREAM_ON_DEVICE_START"
    const val LLM_ON_DEVICE_ACTION = "ai.pleos.playground.caas.intent.action.LLM_ON_DEVICE_START"

    /** Si la caas está instalada: fuera de Pleos, no. */
    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** Vincula y devuelve el binder cuando llega; `null` si el servicio no lo da. */
    fun bind(context: Context, service: String, action: String, onBinder: (IBinder?) -> Unit): ServiceConnection? {
        val intent = Intent(action).setComponent(ComponentName(PACKAGE, service))
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) = onBinder(binder)
            override fun onServiceDisconnected(name: ComponentName?) = onBinder(null)
            override fun onNullBinding(name: ComponentName?) = onBinder(null)
        }
        val bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
            .onFailure { Log.e(TAG, "no se pudo vincular $action", it) }
            .getOrDefault(false)
        return connection.takeIf { bound }
    }

    /** Una llamada AIDL a mano: el descriptor y, en orden, los argumentos. */
    fun call(target: IBinder, descriptor: String, code: Int, vararg args: Any?) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(descriptor)
            args.forEach { arg ->
                when (arg) {
                    is String -> data.writeString(arg)
                    is Boolean -> data.writeInt(if (arg) 1 else 0)
                    is Int -> data.writeInt(arg)
                    is IBinder -> data.writeStrongBinder(arg)
                    // AudioAttributesParcel de la caas: marca de no nulo y, dentro, el AudioAttributes.
                    is AudioAttributes -> {
                        data.writeInt(1)
                        data.writeParcelable(arg, 0)
                    }
                    null -> data.writeString(null)
                    else -> error("argumento no soportado: ${arg::class}")
                }
            }
            target.transact(code, data, reply, 0)
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Un callback AIDL con un solo método, `onResponse`, de [strings] cadenas.
     * Se devuelve el binder: hay que guardarlo mientras se use, o se lo lleva el GC.
     */
    fun callback(descriptor: String, strings: Int, onResponse: (List<String?>) -> Unit): Binder =
        object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == INTERFACE_TRANSACTION) {
                    reply?.writeString(descriptor)
                    return true
                }
                if (code == FIRST_CALL_TRANSACTION) {
                    data.enforceInterface(descriptor)
                    val values = (0 until strings).map { data.readString() }
                    runCatching { onResponse(values) }.onFailure { Log.e(TAG, "callback", it) }
                    reply?.writeNoException()
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        }.apply { attachInterface(null, descriptor) }

    /** El código de idioma que espera la caas. */
    fun localeOf(language: VoiceLanguage): String = when (language) {
        VoiceLanguage.SPANISH -> "es-ES"
        VoiceLanguage.ITALIAN -> "it-IT"
        else -> "en-US"
    }

    const val TAG = "SWPleos"
}

/**
 * La voz de Pleos (`ITtsOnDeviceStream`): habla ella misma, no devuelve audio.
 * `speak` es asíncrono; se sabe que terminó por `UpdateTtsDone`. Medido en la
 * v2.1.5: on-device solo sintetiza coreano, y con es, it o en responde
 * `UpdateTtsError` ("Unsupported locale"). Aquí no se esconde: se registra y la
 * frase queda escrita en pantalla.
 */
class PleosSpeaker(private val context: Context) : LiveSpeaker {

    private var binder: IBinder? = null
    private var connection: ServiceConnection? = null
    private val connected = CompletableDeferred<IBinder?>()
    private var locale: String? = null
    private var pending: CompletableDeferred<Boolean>? = null

    /** Si la frase en curso llegó a sonar: `false` si dio error antes. */
    private var starting: CompletableDeferred<Boolean>? = null

    private val callback = PleosCaas.callback(CALLBACK, 2) { (cmd, data) ->
        Log.i(PleosCaas.TAG, "TTS <= $cmd ${data.orEmpty().take(160)}")
        when (cmd) {
            "UpdateTtsStart" -> starting?.complete(true)
            "UpdateTtsDone", "UpdateTtsStop" -> {
                starting?.complete(true)
                pending?.complete(true)
            }
            "UpdateTtsError" -> {
                starting?.complete(false)
                pending?.complete(false)
            }
        }
    }

    override val available: Boolean get() = PleosCaas.installed(context)

    private suspend fun ready(): IBinder? {
        if (connection == null) {
            connection = PleosCaas.bind(context, PleosCaas.SPEECH_SERVICE, PleosCaas.TTS_ON_DEVICE_ACTION) { b ->
                binder = b
                if (b != null) {
                    runCatching { PleosCaas.call(b, DESCRIPTOR, REGISTER, callback, context.packageName) }
                        .onFailure { Log.e(PleosCaas.TAG, "TTS registerCallback", it) }
                }
                connected.complete(b)
            }
            if (connection == null) connected.complete(null)
        }
        return withTimeoutOrNull(BIND_TIMEOUT_MS) { connected.await() } ?: binder
    }

    override suspend fun speak(text: String, language: VoiceLanguage): Boolean = withContext(Dispatchers.IO) {
        val b = ready() ?: return@withContext false.also { Log.w(PleosCaas.TAG, "TTS: sin servicio") }
        val app = context.packageName
        val wanted = PleosCaas.localeOf(language)
        val done = CompletableDeferred<Boolean>()
        val began = CompletableDeferred<Boolean>()
        pending = done
        starting = began
        runCatching {
            // El idioma se guarda por app: solo se cambia si hace falta.
            if (locale != wanted) {
                PleosCaas.call(b, DESCRIPTOR, SET_LOCALE, wanted, app)
                locale = wanted
            }
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            PleosCaas.call(b, DESCRIPTOR, SPEAK, text, true, app, attrs)
        }.onFailure {
            Log.e(PleosCaas.TAG, "TTS speak", it)
            return@withContext false
        }
        // Primero, que empiece a sonar. En la v2.1.5 acepta la frase, dice
        // "lista" y el error de idioma llega veinte segundos después, al
        // reproducir: esperando así, la app tardaba 80 s en saber que no hay voz.
        val sounding = withTimeoutOrNull(START_TIMEOUT_MS) { began.await() } ?: false
        if (!sounding) {
            Log.w(PleosCaas.TAG, "TTS: no empezó a sonar en ${START_TIMEOUT_MS / 1000} s")
            runCatching { PleosCaas.call(b, DESCRIPTOR, STOP, app) }
            return@withContext false
        }
        // Lo que tarde en decirse, con margen; si no llega el final, se sigue.
        withTimeoutOrNull(text.length * MS_PER_CHAR + SPEAK_MARGIN_MS) { done.await() } ?: true
    }

    override fun stop() {
        pending?.complete(true)
        binder?.let { b -> runCatching { PleosCaas.call(b, DESCRIPTOR, STOP, context.packageName) } }
    }

    override fun release() {
        stop()
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
    }

    private companion object {
        /** Lo más que se espera a que una frase empiece a sonar. */
        const val START_TIMEOUT_MS = 10_000L
        const val DESCRIPTOR = "ai.pleos.playground.caas.service.speech.aidl.ITtsOnDeviceStream"
        const val CALLBACK = "ai.pleos.playground.caas.service.speech.aidl.ITtsCallback"
        const val REGISTER = 1
        const val SPEAK = 4
        const val STOP = 5
        const val SET_LOCALE = 6
        const val BIND_TIMEOUT_MS = 5_000L
        const val MS_PER_CHAR = 90L
        const val SPEAK_MARGIN_MS = 5_000L
    }
}

/**
 * El LLM de Pleos (`IOnDeviceLLM`): un modelo pequeño (0,5B en la v2.1.5). La
 * gramática sigue atendiendo casi todo; esto es para lo que ella no entiende.
 * La respuesta llega a trozos por `ILLMCallback.onResponse(cmd, requestId, data)`.
 */
class PleosLanguageModel(private val context: Context) : LanguageModel {

    @Volatile
    private var binder: IBinder? = null
    private var connection: ServiceConnection? = null
    private val requests = ConcurrentHashMap<String, Request>()

    @Volatile
    override var ready: Boolean = false
        private set

    @Volatile
    override var failed: Boolean = false
        private set

    private class Request(val text: StringBuilder = StringBuilder(), val done: CompletableDeferred<String> = CompletableDeferred())

    private val callback = PleosCaas.callback(CALLBACK, 3) { (cmd, id, data) ->
        val request = id?.let(requests::get) ?: return@callback
        when (cmd) {
            "GenerateContentStreamPartialResponse" -> synchronized(request.text) { request.text.append(data.orEmpty()) }
            "GenerateContentStreamFullResponse", "GenerateContentResponse" -> {
                val partial = synchronized(request.text) { request.text.toString() }
                request.done.complete(partial.ifBlank { data.orEmpty() })
            }
            else -> Log.i(PleosCaas.TAG, "LLM <= $cmd ${data.orEmpty().take(120)}")
        }
    }

    /** Vincula e inicializa. El modelo carga en segundo plano: no se espera aquí. */
    fun load() {
        if (!PleosCaas.installed(context)) {
            failed = true
            return
        }
        connection = PleosCaas.bind(context, PleosCaas.LLM_SERVICE, PleosCaas.LLM_ON_DEVICE_ACTION) { b ->
            binder = b
            if (b == null) {
                ready = false
                failed = true
                return@bind
            }
            val app = context.packageName
            runCatching {
                PleosCaas.call(b, DESCRIPTOR, REGISTER, callback, app)
                PleosCaas.call(b, DESCRIPTOR, INITIALIZE, app)
            }.onSuccess {
                ready = true
                Log.i(PleosCaas.TAG, "LLM de Pleos vinculado")
            }.onFailure {
                failed = true
                Log.e(PleosCaas.TAG, "LLM initialize", it)
            }
        }
        if (connection == null) failed = true
    }

    override suspend fun complete(prefix: String, request: String, singleLine: Boolean): String = withContext(Dispatchers.IO) {
        val b = binder ?: error("el LLM de Pleos no está vinculado")
        val id = "sw-" + UUID.randomUUID().toString().take(8)
        val pending = Request()
        requests[id] = pending
        try {
            PleosCaas.call(b, DESCRIPTOR, GENERATE_STREAM, context.packageName, id, prefix + request)
            val text = withTimeoutOrNull(TIMEOUT_MS) { pending.done.await() }
                ?: synchronized(pending.text) { pending.text.toString() }.ifBlank { error("el LLM de Pleos no contestó a tiempo") }
            if (singleLine) text.trim().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() else text
        } finally {
            requests.remove(id)
        }
    }

    fun release() {
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
        binder = null
        ready = false
    }

    private companion object {
        const val DESCRIPTOR = "ai.pleos.playground.caas.data.llm.aidl.IOnDeviceLLM"
        const val CALLBACK = "ai.pleos.playground.caas.data.llm.aidl.ILLMCallback"
        const val REGISTER = 1
        const val INITIALIZE = 3
        const val GENERATE_STREAM = 5
        const val TIMEOUT_MS = 30_000L
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
