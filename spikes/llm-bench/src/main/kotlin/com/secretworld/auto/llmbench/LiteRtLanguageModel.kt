package com.secretworld.auto.llmbench

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.ResponseCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Session
import com.google.ai.edge.litertlm.SessionConfig
import com.secretworld.auto.domain.LanguageModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Un `.litertlm` en el dispositivo, por LiteRT-LM. Pensado para Qwen3 0.6B.
 *
 * Imita a [com.secretworld.auto.feature.voice.GemmaLanguageModel] en lo que
 * importa para comparar: **continuación en crudo, sin plantilla de chat** —la
 * [Session] de LiteRT-LM no la aplica; la `Conversation` sí—, muestreo
 * determinista y corte en la primera línea.
 *
 * Lo que **no** puede imitar: LiteRT-LM 0.18 no clona sesiones, así que cada
 * petición vuelve a leer el prefijo entero. Es justo una de las cosas que mide
 * el banco: si el modelo pequeño compensa eso con velocidad.
 */
class LiteRtLanguageModel(
    private val model: File,
    private val cacheDir: File,
) : LanguageModel {

    @Volatile
    private var engine: Engine? = null

    /** LiteRT-LM tampoco admite dos generaciones a la vez sobre el mismo motor. */
    private val lock = Mutex()

    override val ready: Boolean get() = engine != null

    @Volatile
    override var failed: Boolean = false
        private set

    suspend fun load(): Result<Unit> = withContext(Dispatchers.IO) {
        if (engine != null) return@withContext Result.success(Unit)
        runCatching {
            require(model.isFile) { "falta ${model.absolutePath}" }
            val started = System.nanoTime()
            // CPU, como Gemma en producción, para comparar lo mismo.
            val built = Engine(EngineConfig(model.absolutePath, Backend.CPU(), maxNumTokens = MAX_TOKENS, cacheDir = cacheDir.absolutePath))
            built.initialize()
            Log.i(TAG, "LLM cargado en ${(System.nanoTime() - started) / 1_000_000} ms")
            engine = built
        }.onFailure { failed = true }
    }

    override suspend fun complete(prefix: String, request: String, singleLine: Boolean): String = withContext(Dispatchers.IO) {
        val llm = engine ?: error("el LLM no está cargado")
        lock.withLock {
            val started = System.nanoTime()
            val session = llm.createSession(SessionConfig(SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)))
            try {
                val reply = generate(session, prefix + request, singleLine)
                Log.i(TAG, "LLM: ${(System.nanoTime() - started) / 1_000_000} ms → ${reply.trim().take(120)}")
                reply
            } finally {
                runCatching { session.close() }
            }
        }
    }

    /** Hasta la primera línea completa, y corta: el modelo seguiría inventando diálogo. */
    private suspend fun generate(session: Session, prompt: String, singleLine: Boolean): String {
        val text = StringBuilder()
        val line = CompletableDeferred<String>()
        val finished = CompletableDeferred<Unit>()
        session.generateContentStream(listOf(InputData.Text(prompt)), object : ResponseCallback {
            override fun onNext(response: String) {
                synchronized(text) { text.append(response) }
                if (!singleLine || line.isCompleted) return
                val soFar = synchronized(text) { text.toString() }
                val start = soFar.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val cut = soFar.indexOf('\n', startIndex = start)
                if (cut >= 0) {
                    line.complete(soFar.substring(0, cut))
                    runCatching { session.cancelProcess() }
                }
            }

            override fun onDone() {
                line.complete(synchronized(text) { text.toString() })
                finished.complete(Unit)
            }

            override fun onError(throwable: Throwable) {
                // Cancelar a propósito también llega por aquí.
                line.complete(synchronized(text) { text.toString() })
                finished.complete(Unit)
            }
        })
        val result = withTimeoutOrNull(GENERATION_TIMEOUT_MS) { line.await() } ?: run {
            runCatching { session.cancelProcess() }
            synchronized(text) { text.toString() }
        }
        // Cerrar la sesión con la generación viva es un cierre nativo.
        withTimeoutOrNull(CANCEL_GRACE_MS) { finished.await() }
        Log.d(TAG, "generado en bruto: «${synchronized(text) { text.toString() }.replace("\n", "⏎").take(200)}»")
        return result
    }

    fun release() {
        runCatching { engine?.close() }
        engine = null
    }

    private companion object {
        const val TAG = "SWLlmBench"
        const val MAX_TOKENS = 1024
        const val GENERATION_TIMEOUT_MS = 15_000L
        const val CANCEL_GRACE_MS = 3_000L
    }
}
