package com.secretworld.auto.feature.voice

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import com.secretworld.auto.domain.LanguageModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gemma 3 1B en el dispositivo, por MediaPipe LLM Inference.
 *
 * Solo sabe completar texto: qué se le pregunta y cómo se lee la respuesta es
 * cosa de [com.secretworld.auto.domain.LlmIntentResolver], que vive en el
 * dominio y se prueba sin modelo.
 *
 * **Lo caro es el prefijo, no la respuesta.** Las instrucciones y las
 * herramientas son unos cuatrocientos tokens y no cambian nunca; la respuesta
 * son diez. Por eso el prefijo se procesa **una vez** en una sesión base y cada
 * petición trabaja sobre un *clon* de esa sesión, que hereda la caché ya
 * calculada. Sin esto cada frase del conductor costaría varios segundos en la
 * CPU de un infotainment.
 *
 * El modelo no va en el APK (~530 MB): lo resuelve [ModelStore] (SPEC RF-1.8.7).
 */
class GemmaLanguageModel(
    private val context: Context,
    private val models: ModelStore,
) : LanguageModel {

    @Volatile
    private var engine: LlmInference? = null

    /** Sesión con el prefijo ya procesado, y qué prefijo es. */
    private var base: LlmInferenceSession? = null
    private var basePrefix: String? = null

    /**
     * Si el backend sabe clonar sesiones. El de GPU de MediaPipe **no**
     * ("UNIMPLEMENTED: The current backend does not support cloning"), y en ese
     * caso cada petición paga el prefijo entero. Se descubre en la primera
     * llamada y no se vuelve a intentar.
     */
    @Volatile
    private var canClone = true

    /** MediaPipe no admite dos generaciones a la vez sobre el mismo motor. */
    private val lock = Mutex()

    override val ready: Boolean get() = engine != null

    val available: Boolean get() = models.isAvailable(ModelCatalog.LLM)

    /**
     * Carga el modelo. Cuesta segundos y cerca de 1 GB de RAM; se llama una vez,
     * en segundo plano. Idempotente.
     */
    suspend fun load(): Result<Unit> = withContext(Dispatchers.IO) {
        if (engine != null) return@withContext Result.success(Unit)
        runCatching {
            val dir = requireNotNull(models.dirFor(ModelCatalog.LLM)) { "falta ${ModelCatalog.LLM.id}" }
            val model = File(dir, ModelCatalog.LLM.marker)
            val started = System.nanoTime()
            // CPU antes que GPU, y no por rendimiento bruto: el backend de GPU de
            // MediaPipe no clona sesiones, así que cada orden vuelve a leer el
            // prompt entero (medido: 3,7 s por orden). El de CPU sí clona y solo
            // paga la frase nueva.
            val built = create(model, PREFERRED_BACKEND).recoverCatching { failure ->
                Log.w(TAG, "$PREFERRED_BACKEND no disponible para el LLM, se usa $FALLBACK_BACKEND: ${failure.message}")
                create(model, FALLBACK_BACKEND).getOrThrow()
            }.getOrThrow()
            Log.i(TAG, "LLM cargado en ${(System.nanoTime() - started) / 1_000_000} ms")
            lock.withLock {
                if (engine == null) engine = built else built.close()
            }
        }
    }

    /**
     * Deja el prefijo procesado antes de la primera orden. Medido en CPU: 3,8 s
     * que, sin esto, paga la primera frase del conductor.
     */
    suspend fun warm(prefix: String) = withContext(Dispatchers.IO) {
        val llm = engine ?: return@withContext
        lock.withLock {
            if (!canClone) return@withLock
            val started = System.nanoTime()
            runCatching {
                // Añadir el prefijo no lo procesa: el motor lo deja pendiente
                // hasta la primera generación. Se fuerza con una orden de prueba
                // sobre un clon, que es lo que hará cada frase real.
                val root = baseSession(llm, prefix)
                val probe = root.cloneSession()
                try {
                    probe.addQueryChunk("Driver: \"stop\"\n")
                    firstLine(probe)
                } finally {
                    closeWhenIdle(probe)
                }
            }
                .onSuccess { Log.i(TAG, "prefijo del LLM preparado en ${(System.nanoTime() - started) / 1_000_000} ms") }
                .onFailure { Log.w(TAG, "no se pudo preparar el prefijo del LLM", it) }
        }
    }

    private fun create(model: File, backend: LlmInference.Backend): Result<LlmInference> = runCatching {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(model.absolutePath)
            // Prefijo + lugares + frase + respuesta: sobra con esto, y cuanto
            // menos contexto se reserve, menos memoria.
            .setMaxTokens(MAX_TOKENS)
            .setPreferredBackend(backend)
            .build()
        LlmInference.createFromOptions(context, options)
    }

    override suspend fun complete(prefix: String, request: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "petición al LLM: ${request.lines().lastOrNull { it.isNotBlank() }}")
        val llm = engine ?: error("el LLM no está cargado")
        lock.withLock {
            // Que un fallo del modelo no sea invisible: sin esto se confunde con
            // "no entendí" y nadie sabe que el modelo no llegó a hablar.
            try {
                val started = System.nanoTime()
                val session = if (canClone) {
                    val root = baseSession(llm, prefix)
                    runCatching { root.cloneSession() }.getOrElse {
                        Log.w(TAG, "este backend no clona sesiones; cada petición procesará el prefijo entero")
                        canClone = false
                        freshSession(llm).also { it.addQueryChunk(prefix) }
                    }
                } else {
                    freshSession(llm).also { it.addQueryChunk(prefix) }
                }
                val prefixMs = (System.nanoTime() - started) / 1_000_000

                try {
                    session.addQueryChunk(request)
                    val reply = firstLine(session)
                    val totalMs = (System.nanoTime() - started) / 1_000_000
                    Log.i(TAG, "LLM: ${totalMs} ms (prefijo ${prefixMs} ms, ${if (canClone) "clonado" else "sin caché"}) → ${reply.trim().take(120)}")
                    reply
                } finally {
                    closeWhenIdle(session)
                }
            } catch (e: Exception) {
                Log.e(TAG, "el LLM falló", e)
                throw e
            }
        }
    }

    /**
     * La sesión base con el prefijo procesado. Se rehace solo si el prefijo
     * cambia, que en la práctica es nunca.
     */
    private fun baseSession(llm: LlmInference, prefix: String): LlmInferenceSession {
        base?.takeIf { basePrefix == prefix }?.let { return it }
        base?.close()
        val session = freshSession(llm)
        session.addQueryChunk(prefix)
        base = session
        basePrefix = prefix
        return session
    }

    /**
     * Genera hasta la primera línea completa y **corta**.
     *
     * Medido: sin esto el modelo contestaba en una línea y seguía inventándose
     * el diálogo —"Driver: …", otra respuesta, otra pregunta— hasta agotar los
     * tokens: 25 s por orden, de los que la respuesta útil eran los primeros
     * 200 ms. La respuesta es una línea; en cuanto llega el salto se cancela.
     * Se espera a que el motor confirme el fin antes de soltar el candado:
     * cerrar la sesión con la generación aún viva es un cierre nativo.
     */
    private suspend fun firstLine(session: LlmInferenceSession): String {
        val text = StringBuilder()
        val line = CompletableDeferred<String>()
        val finished = CompletableDeferred<Unit>()
        session.generateResponseAsync { partial, done ->
            text.append(partial)
            val soFar = text.toString()
            val cut = soFar.indexOf('\n', startIndex = soFar.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0))
            if (cut >= 0 && !line.isCompleted) {
                line.complete(soFar.substring(0, cut))
                runCatching { session.cancelGenerateResponseAsync() }
            }
            if (done) {
                if (!line.isCompleted) line.complete(soFar)
                finished.complete(Unit)
            }
        }
        val result = withTimeoutOrNull(GENERATION_TIMEOUT_MS) { line.await() } ?: run {
            runCatching { session.cancelGenerateResponseAsync() }
            text.toString()
        }
        withTimeoutOrNull(CANCEL_GRACE_MS) { finished.await() }
        Log.d(TAG, "generado en bruto: «${text.toString().replace("\n", "⏎").take(200)}»")
        return result
    }

    /**
     * Cierra una sesión cuya generación se acaba de cancelar.
     *
     * MediaPipe avisa de `done` antes de haber terminado del todo, y cerrar en
     * ese instante lanza "Previous invocation still processing". Se reintenta
     * un momento; si no cede, se deja sin cerrar y se anota: una sesión huérfana
     * es un poco de memoria, un cierre que lanza es una orden sin respuesta.
     */
    private suspend fun closeWhenIdle(session: LlmInferenceSession) {
        repeat(CLOSE_RETRIES) { attempt ->
            val closed = runCatching { session.close() }.isSuccess
            if (closed) return
            kotlinx.coroutines.delay(CLOSE_RETRY_MS)
            if (attempt == CLOSE_RETRIES - 1) Log.w(TAG, "la sesión del LLM no se dejó cerrar; se abandona")
        }
    }

    private fun freshSession(llm: LlmInference): LlmInferenceSession {
        val options = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            // Determinista: elegir una herramienta no es tarea creativa.
            .setTopK(1)
            .setTemperature(0f)
            // Sin plantilla de chat, a propósito: en modo conversación el modelo
            // contesta como una persona ("What's nearby: the Basilica…") e
            // ignora el patrón de ejemplos. En modo continuación sigue el patrón
            // y escribe el nombre de la herramienta. Que no pare solo lo
            // resuelve el corte a la primera línea de [firstLine].
            .build()
        return LlmInferenceSession.createFromOptions(llm, options)
    }

    /**
     * Suelta el modelo. Es el consumidor de RAM más grande de la app.
     *
     * No espera al candado a propósito: se llama al destruir la actividad, cuando
     * ya no hay escucha y por tanto no hay generación en curso.
     */
    fun release() {
        val llm = engine
        engine = null
        runCatching { base?.close() }
        base = null
        basePrefix = null
        runCatching { llm?.close() }
    }

    private companion object {
        const val TAG = "SWVoiceLlm"
        const val MAX_TOKENS = 1024
        val PREFERRED_BACKEND = LlmInference.Backend.CPU
        val FALLBACK_BACKEND = LlmInference.Backend.GPU
        /** Más que esto y algo va mal: la respuesta son cinco tokens. */
        const val GENERATION_TIMEOUT_MS = 8_000L
        const val CANCEL_GRACE_MS = 3_000L
        const val CLOSE_RETRIES = 10
        const val CLOSE_RETRY_MS = 100L
    }
}
