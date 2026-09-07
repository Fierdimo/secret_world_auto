package com.secretworld.auto.feature.voice

import android.content.Context
import android.util.Log
import com.secretworld.auto.domain.NarrationPrefetcher
import com.secretworld.auto.domain.NarrationTextSource
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.CoroutineScope
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

    /** Donde se deja la respuesta hablada del momento. Se sobrescribe siempre. */
    private val replyPath = File(context.cacheDir, "reply.wav").absolutePath

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
            installer.install(language) { progress ->
                when (progress) {
                    is VoiceInstaller.Progress.Downloading ->
                        _state.value = State.InstallingVoice((progress.fraction * 100).toInt())
                    VoiceInstaller.Progress.Verifying,
                    VoiceInstaller.Progress.Extracting ->
                        _state.value = State.InstallingVoice(100)
                    is VoiceInstaller.Progress.Done -> {
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

    /**
     * Narra [place]. Si ya estaba sintetizado suena al instante; si no, lo
     * sintetiza primero y lo dice mientras tanto.
     */
    fun listen(place: Place) {
        Log.i(TAG, "listen: ${place.id} ${place.name}")
        scope.launch(Dispatchers.IO) {
            if (!installer.isInstalled(language)) {
                _state.value = State.Failed("todavia no hay voz instalada")
                return@launch
            }
            val path = store.pathFor(place.id, language)
            if (!store.has(place.id, language)) {
                val text = textSource.textFor(place, language)
                if (text.isNullOrBlank()) {
                    _state.value = State.Failed("no hay texto para ${place.name}")
                    return@launch
                }
                _state.value = State.Synthesizing(place.name)
                val result = runCatching { synthesizer.synthesizeToFile(text, language, path) }
                if (result.isFailure) {
                    _state.value = State.Failed(result.exceptionOrNull()?.message ?: "fallo al sintetizar")
                    return@launch
                }
                Log.i(TAG, "sintetizado al vuelo: ${place.id}, RTF %.2f".format(result.getOrThrow().realTimeFactor))
            } else {
                store.touch(place.id, language)
            }

            withContext(Dispatchers.Main) {
                val duration = player.play(File(path)) { _state.value = State.Idle }
                _state.value = if (duration != null) {
                    State.Playing(place.name, duration)
                } else {
                    State.Failed("no se pudo reproducir")
                }
            }
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
    fun say(text: String) {
        scope.launch(Dispatchers.IO) {
            if (!installer.isInstalled(language)) {
                _state.value = State.Failed("todavia no hay voz instalada")
                return@launch
            }
            withContext(Dispatchers.Main) { player.stop() }
            _state.value = State.Speaking(text, preparing = true)

            val result = runCatching { synthesizer.synthesizeToFile(text, language, replyPath) }
            if (result.isFailure) {
                _state.value = State.Failed(result.exceptionOrNull()?.message ?: "fallo al sintetizar")
                return@launch
            }

            withContext(Dispatchers.Main) {
                val duration = player.play(File(replyPath)) { _state.value = State.Idle }
                _state.value = if (duration != null) {
                    State.Speaking(text, preparing = false)
                } else {
                    State.Failed("no se pudo reproducir")
                }
            }
        }
    }

    fun stop() {
        player.stop()
        _state.value = State.Idle
    }

    /** Posicion actual de la reproduccion, para que la pantalla no mienta. */
    fun positionMs(): Int = player.positionMs

    val isPlaying: Boolean get() = player.isPlaying

    private companion object {
        const val TAG = "SWNarration"
    }
}
