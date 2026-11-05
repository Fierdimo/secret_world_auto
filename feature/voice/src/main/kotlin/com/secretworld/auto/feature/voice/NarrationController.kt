package com.secretworld.auto.feature.voice

import android.content.Context
import android.util.Log
import com.secretworld.auto.domain.NarrationPrefetcher
import com.secretworld.auto.domain.NarrationTextSource
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Une las piezas que hasta ahora funcionaban por separado: instalar la voz,
 * sintetizar por delante, y **sonar**.
 *
 * Todo el estado sale por [state] para que la pantalla pueda decir la verdad en
 * cada momento en lugar de fingir que algo ocurre.
 */
class NarrationController(
    context: Context,
    private val textSource: NarrationTextSource,
    private val scope: CoroutineScope,
    private val language: VoiceLanguage = VoiceLanguage.ENGLISH,
) {

    sealed interface State {
        data object Idle : State
        data class InstallingVoice(val percent: Int) : State
        data class Synthesizing(val placeName: String) : State
        data class Playing(val placeName: String, val durationMs: Int) : State
        /** Una respuesta del asistente, no un lugar del catálogo. */
        data class Speaking(val text: String, val preparing: Boolean) : State
        data class Failed(val reason: String) : State
    }

    /**
     * Respuestas ya sintetizadas, por texto. Las frases del asistente son pocas y
     * se repiten —"Yes?", "Stopped."—, y sintetizarlas cada vez es lo que hace
     * que conteste con medio segundo de retraso. Se sintetizan una vez y se
     * reproducen al instante.
     */
    private val replies = File(context.cacheDir, "replies").apply { mkdirs() }

    private val installer = VoiceInstaller(context)
    private val synthesizer = PiperSpeechSynthesizer(installer)
    private val store = DiskNarrationStore(context.filesDir)
    private val player = NarrationPlayer(context)

    private val prefetcher = NarrationPrefetcher(
        store = store,
        synthesizer = synthesizer,
        textSource = textSource,
        budgetBytes = 30L * 1024 * 1024,
        lookahead = 2,
    )

    /** Evita que varias recomposiciones disparen prefetches simultaneos. */
    private val prefetching = AtomicBoolean(false)

    /**
     * Ultima cola conocida. Sin esto, el primer `prefetch` llega antes de que la
     * voz este instalada, sale por la puerta de atras y **nadie lo vuelve a
     * intentar**: la app se queda sin narraciones preparadas hasta que el usuario
     * pulsa algo.
     */
    @Volatile
    private var lastQueue: List<Place> = emptyList()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val voiceInstalled: Boolean get() = installer.isInstalled(language)

    /** Instala la voz si falta. Idempotente. */
    fun ensureVoice() {
        if (installer.isInstalled(language)) return
        scope.launch(Dispatchers.IO) {
            // El avance de verdad, fase a fase: antes saltaba a 100 % al empezar a
            // descomprimir y se quedaba ahí veinte segundos.
            installer.install(language, onFraction = { f -> _state.value = State.InstallingVoice((f * 100).toInt().coerceAtMost(99)) }) { progress ->
                when (progress) {
                    is VoiceInstaller.Progress.Downloading,
                    VoiceInstaller.Progress.Verifying,
                    VoiceInstaller.Progress.Extracting -> Unit
                    is VoiceInstaller.Progress.Done -> {
                        // Voz nueva: lo sintetizado con la anterior sonaría
                        // con otra voz. Se tira y se vuelve a preparar.
                        store.clear()
                        replies.listFiles()?.forEach { it.delete() }
                        _state.value = State.Idle
                        // Ya hay voz: preparar lo que estuviera esperando.
                        prefetch(lastQueue)
                    }
                    is VoiceInstaller.Progress.Failed ->
                        _state.value = State.Failed(progress.reason)
                }
            }
        }
    }

    /** Prepara por delante los siguientes lugares de la cola. */
    fun prefetch(queue: List<Place>) {
        if (queue.isEmpty()) return
        lastQueue = queue
        if (!installer.isInstalled(language)) return
        if (!prefetching.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                val report = prefetcher.prefetch(queue, language)
                if (report.synthesized.isNotEmpty()) {
                    Log.i(TAG, "preparado por delante: ${report.synthesized.joinToString()}")
                }
            } finally {
                prefetching.set(false)
            }
        }
    }

    /** Si la narración de [place] ya está sintetizada y suena al instante. */
    fun isPrepared(place: Place): Boolean = store.has(place.id, language)

    /**
     * Narra [place]. Si ya estaba sintetizado suena al instante; si no, se
     * sintetiza **por frases** y la primera suena en cuanto está: una
     * narración de un minuto tardaba veinte segundos en empezar.
     */
    fun listen(place: Place) {
        Log.i(TAG, "listen: ${place.id} ${place.name}")
        saying?.cancel()
        saying = scope.launch(Dispatchers.IO) {
            if (!installer.isInstalled(language)) {
                _state.value = State.Failed("todavia no hay voz instalada")
                return@launch
            }
            val path = store.pathFor(place.id, language)
            if (store.has(place.id, language)) {
                store.touch(place.id, language)
                withContext(Dispatchers.Main) {
                    val duration = player.play(File(path)) { _state.value = State.Idle }
                    _state.value = if (duration != null) {
                        State.Playing(place.name, duration)
                    } else {
                        State.Failed("no se pudo reproducir")
                    }
                }
                return@launch
            }

            _state.value = State.Synthesizing(place.name)
            val text = textSource.textFor(place, language)
            if (text.isNullOrBlank()) {
                _state.value = State.Failed("no hay texto para ${place.name}")
                return@launch
            }
            withContext(Dispatchers.Main) { player.stop() }
            val parts = sentences(text)
            var next = async { synthesizeReply(parts[0]) }
            for (index in parts.indices) {
                val file = next.await() ?: run {
                    _state.value = State.Failed("fallo al sintetizar")
                    return@launch
                }
                if (index + 1 < parts.size) next = async { synthesizeReply(parts[index + 1]) }
                _state.value = State.Playing(place.name, durationMs = 0)
                if (!playAndAwait(file)) {
                    _state.value = State.Failed("no se pudo reproducir")
                    return@launch
                }
            }
            _state.value = State.Idle
            // Para la próxima vez, entera y en la caché de narraciones.
            runCatching { synthesizer.synthesizeToFile(text, language, path) }
        }
    }

    /**
     * Dice [text] con la misma voz que narra.
     *
     * Es lo que permite **contestar** a una orden hablada y no solo obedecerla en
     * silencio: "hay tres sitios cerca", "eso todavía no lo sé hacer". El motor es
     * el mismo de la narración, así que una respuesta no depende de la red ni de
     * que Pleos publique su `TTS_SERVICE`.
     *
     * Se corta lo que hubiera sonando **antes** de sintetizar: el fichero de
     * respuesta es único y reescribirlo mientras se reproduce es pedir un chasquido.
     */
    fun say(text: String, onDone: (() -> Unit)? = null) {
        saying?.cancel()
        saying = scope.launch(Dispatchers.IO) {
            if (!installer.isInstalled(language)) {
                _state.value = State.Failed("todavia no hay voz instalada")
                return@launch
            }
            withContext(Dispatchers.Main) { player.stop() }

            // Por frases, no de una vez. Medido: una respuesta de once segundos
            // tardaba siete en sintetizarse antes de sonar. Así la primera frase
            // suena en cuanto está y las demás se preparan mientras tanto.
            val parts = sentences(text)
            var next = async { synthesizeReply(parts[0]) }
            for (index in parts.indices) {
                val file = next.await()
                if (file == null) {
                    _state.value = State.Failed("fallo al sintetizar")
                    return@launch
                }
                if (index + 1 < parts.size) next = async { synthesizeReply(parts[index + 1]) }
                _state.value = State.Speaking(text, preparing = false)
                val played = playAndAwait(file)
                if (!played) {
                    _state.value = State.Failed("no se pudo reproducir")
                    return@launch
                }
            }
            _state.value = State.Idle
            // Lo que haya que hacer **después** de hablar —abrir el turno, por
            // ejemplo— va aquí y no antes: abrir el micrófono mientras suena la
            // respuesta es transcribirse a uno mismo.
            withContext(Dispatchers.Main) { onDone?.invoke() }
        }
    }

    /** La frase en curso, para poder cortarla si llega otra o un "para". */
    private var saying: Job? = null

    /** Sintetiza una frase a la caché de respuestas, o la toma de ella. */
    private fun synthesizeReply(sentence: String): File? {
        val file = replyFile(sentence)
        if (file.isFile) return file
        _state.value = State.Speaking(sentence, preparing = true)
        return runCatching { synthesizer.synthesizeToFile(sentence, language, file.absolutePath) }
            .map { file }
            .onFailure { file.delete() }
            .getOrNull()
    }

    /** Reproduce y espera a que termine. `false` si no llegó a sonar. */
    private suspend fun playAndAwait(file: File): Boolean {
        val finished = CompletableDeferred<Unit>()
        val started = withContext(Dispatchers.Main) {
            player.play(file) { finished.complete(Unit) } != null
        }
        if (!started) return false
        try {
            finished.await()
        } catch (e: CancellationException) {
            withContext(Dispatchers.Main + NonCancellable) { player.stop() }
            throw e
        }
        return true
    }

    /**
     * Parte un texto en frases que se puedan sintetizar por separado. Corta en
     * puntuación fuerte y en punto y coma, que es como se enumera; no corta
     * en comas, que dejarían trozos sin sentido para la prosodia.
     */
    private fun sentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?;])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(text) }

    /**
     * Sintetiza por adelantado las frases fijas del asistente, para que la
     * primera vez que hagan falta ya estén. Se llama cuando la voz está instalada.
     */
    fun prewarm(texts: List<String>) {
        if (!installer.isInstalled(language)) return
        scope.launch(Dispatchers.IO) {
            // La voz, cargada ya: la primera frase que no esté preparada no
            // debe pagar además la carga del modelo.
            runCatching { synthesizer.warm(language) }
            texts.flatMap { sentences(it) }.distinct().forEach { sentence ->
                val file = replyFile(sentence)
                if (file.isFile) return@forEach
                runCatching { synthesizer.synthesizeToFile(sentence, language, file.absolutePath) }
                    .onFailure { file.delete() }
            }
            Log.i(TAG, "respuestas fijas preparadas: ${texts.size}")
        }
    }

    private fun replyFile(text: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
            .digest("${language.code}:$text".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(replies, "$digest.wav")
    }

    fun stop() {
        saying?.cancel()
        player.stop()
        _state.value = State.Idle
    }

    /**
     * Suelta la voz al cerrar la actividad. Sin esto, cada vez que se reabre
     * —al cambiar de idioma— se quedaba otro motor de síntesis en memoria.
     */
    fun release() {
        stop()
        synthesizer.release()
    }

    /** Posicion actual de la reproduccion, para que la pantalla no mienta. */
    fun positionMs(): Int = player.positionMs

    val isPlaying: Boolean get() = player.isPlaying

    private companion object {
        const val TAG = "SWNarration"
    }
}
