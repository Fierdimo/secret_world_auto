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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * El LLM no tiene SDK —Pleos lo retiró en la v2.1.0—: se le habla a mano, con el
 * contrato leído del bytecode de la caas 4.2.9. Se conserva para **verificar**
 * si alguna imagen lo trae funcionando. Ver docs/PLEOS-SDK-SURFACE.md.
 */
internal object PleosBinder {
    const val LLM_SERVICE = "ai.pleos.playground.caas.service.llm.LLMService"
    const val LLM_ON_DEVICE_ACTION = "ai.pleos.playground.caas.intent.action.LLM_ON_DEVICE_START"

    /** Vincula y devuelve el binder cuando llega; `null` si el servicio no lo da. */
    fun bind(context: Context, service: String, action: String, onBinder: (IBinder?) -> Unit): ServiceConnection? {
        val intent = Intent(action).setComponent(ComponentName(PleosCaas.PACKAGE, service))
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) = onBinder(binder)
            override fun onServiceDisconnected(name: ComponentName?) = onBinder(null)
            override fun onNullBinding(name: ComponentName?) = onBinder(null)
        }
        val bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
            .onFailure { Log.e(PleosCaas.TAG, "no se pudo vincular $action", it) }
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
                    runCatching { onResponse(values) }.onFailure { Log.e(PleosCaas.TAG, "callback", it) }
                    reply?.writeNoException()
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        }.apply { attachInterface(null, descriptor) }
}

/**
 * El LLM de Pleos (`IOnDeviceLLM`): un modelo pequeño (0,5B en la v2.1.5). La
 * gramática sigue atendiendo casi todo; esto es para lo que ella no entiende.
 * La respuesta llega a trozos por `ILLMCallback.onResponse(cmd, requestId, data)`.
 */
class PleosLanguageModel(
    private val context: Context,
    /** Lo que se espera a la prueba y a cada respuesta. Al verificar, mucho más que conduciendo. */
    private val probeTimeoutMs: Long = PROBE_TIMEOUT_MS,
    private val timeoutMs: Long = TIMEOUT_MS,
    /** Cada mensaje que llega de la caas, para verla por dentro. */
    private val onMessage: (String, String) -> Unit = { _, _ -> },
) : LanguageModel {

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

    private val callback = PleosBinder.callback(CALLBACK, 3) { (cmd, id, data) ->
        onMessage(cmd.orEmpty(), data.orEmpty())
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
        connection = PleosBinder.bind(context, PleosBinder.LLM_SERVICE, PleosBinder.LLM_ON_DEVICE_ACTION) { b ->
            binder = b
            if (b == null) {
                ready = false
                failed = true
                return@bind
            }
            val app = context.packageName
            runCatching {
                PleosBinder.call(b, DESCRIPTOR, REGISTER, callback, app)
                PleosBinder.call(b, DESCRIPTOR, INITIALIZE, app)
            }.onSuccess {
                Log.i(PleosCaas.TAG, "LLM de Pleos vinculado: se comprueba que conteste")
                probe(b)
            }.onFailure {
                failed = true
                Log.e(PleosCaas.TAG, "LLM initialize", it)
            }
        }
        if (connection == null) failed = true
    }

    /**
     * Vincularse no es tener modelo: en el emulador la caas acepta las
     * peticiones y no contesta nunca, y cada orden y cada traducción esperaba
     * 30 s para nada. Solo cuenta como listo si responde a una prueba.
     */
    private fun probe(b: IBinder) {
        probing = CoroutineScope(Dispatchers.IO).launch {
            val started = System.nanoTime()
            // Entera y a tiempo: a medias al acabarse el plazo es un modelo que
            // genera, pero tan despacio que cada orden esperaría por él.
            val answered = runCatching { generate(b, "Reply with: OK", probeTimeoutMs, partial = false) }.getOrNull()
            val took = (System.nanoTime() - started) / 1_000_000
            if (answered.isNullOrBlank()) {
                failed = true
                Log.w(PleosCaas.TAG, "el LLM de Pleos no contesta en ${probeTimeoutMs / 1000} s: se sigue sin IA")
            } else {
                ready = true
                Log.i(PleosCaas.TAG, "LLM de Pleos listo: contestó en $took ms")
            }
        }
    }

    private var probing: Job? = null

    override suspend fun complete(prefix: String, request: String, singleLine: Boolean): String = withContext(Dispatchers.IO) {
        val b = binder?.takeIf { ready } ?: error("el LLM de Pleos no está disponible")
        val text = runCatching { generate(b, prefix + request, timeoutMs) }.getOrNull()
        if (text.isNullOrBlank()) {
            // Un modelo que deja de contestar no se vuelve a esperar en cada frase.
            ready = false
            failed = true
            error("el LLM de Pleos no contestó a tiempo: se sigue sin IA")
        }
        if (singleLine) text.trim().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() else text
    }

    /** Lo que conteste a [prompt], o lo que llevara escrito al acabarse el tiempo. */
    private suspend fun generate(b: IBinder, prompt: String, timeoutMs: Long, partial: Boolean = true): String? {
        val id = "sw-" + UUID.randomUUID().toString().take(8)
        val pending = Request()
        requests[id] = pending
        try {
            PleosBinder.call(b, DESCRIPTOR, GENERATE_STREAM, context.packageName, id, prompt)
            return withTimeoutOrNull(timeoutMs) { pending.done.await() }
                ?: if (partial) synchronized(pending.text) { pending.text.toString() } else null
        } finally {
            requests.remove(id)
        }
    }

    fun release() {
        probing?.cancel()
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
        /** Lo que se espera a la prueba: un modelo que sirva contesta "OK" en mucho menos. */
        const val PROBE_TIMEOUT_MS = 10_000L
    }
}
