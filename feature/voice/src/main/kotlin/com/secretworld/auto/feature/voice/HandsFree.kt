package com.secretworld.auto.feature.voice

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Escucha permanente: mantiene el micrófono abierto para que **no haya que pulsar
 * nada**.
 *
 * Es lo que hace utilizable el asistente en marcha. La orden llega igual que
 * antes, por [VoiceInput], pero marcada como no directa: quien la interpreta
 * exige la palabra de activación y descarta el resto, así que una conversación
 * entre ocupantes no dispara nada.
 *
 * **Cómo está hecho, y qué no es.** El reconocedor de AOSP cierra el micrófono en
 * cada frase, así que "permanente" aquí significa *volver a abrirlo en cuanto se
 * cierra*. Funciona y no necesita nada que Pleos no haya publicado, pero no es un
 * detector de palabra clave de verdad: gasta más y, si el reconocedor tira de red,
 * depende de ella. El camino bueno es un detector *offline* —sherpa-onnx, que ya
 * está en el proyecto para la síntesis, trae uno—, y es la siguiente pieza. Ver
 * [docs/VOICE-OPTIONS.md].
 */
class HandsFree(
    private val input: VoiceInput,
    private val scope: CoroutineScope,
) {

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var loop: Job? = null

    /** ¿Puede siquiera intentarse aquí? Sin micrófono ni reconocedor, no. */
    val possible: Boolean get() = input.available && input.hasPermission

    fun enable() {
        if (_enabled.value || !possible) return
        _enabled.value = true
        loop = scope.launch {
            var consecutiveErrors = 0
            while (_enabled.value) {
                input.listen(direct = false)
                // Se espera a que el ciclo termine: escuchando no hay nada que hacer.
                val outcome = input.state.awaitEndOfListening()
                consecutiveErrors = if (outcome is VoiceInput.State.Failed) {
                    consecutiveErrors + 1
                } else {
                    0
                }
                // Reintentar sin pausa contra un reconocedor que falla es la forma
                // más rápida de que el sistema nos limite y de vaciar la batería.
                delay(backoff(consecutiveErrors))
            }
        }
    }

    fun disable() {
        _enabled.value = false
        loop?.cancel()
        loop = null
        input.cancel()
    }

    private fun backoff(errors: Int): Long = when {
        errors == 0 -> QUIET_PAUSE_MS
        else -> minOf(QUIET_PAUSE_MS * (1L shl minOf(errors, 3)), MAX_BACKOFF_MS)
    }

    /**
     * Espera a que el ciclo de escucha se cierre, con lo que sea que haya salido.
     *
     * Un `StateFlow` no termina nunca, así que esto **tiene** que ser un `first`:
     * recolectarlo entero dejaría el bucle colgado para siempre y el micrófono
     * abierto sin que nada lo vuelva a cerrar.
     */
    private suspend fun StateFlow<VoiceInput.State>.awaitEndOfListening(): VoiceInput.State =
        first { it !is VoiceInput.State.Listening }
            .also { Log.d(TAG, "ciclo de escucha terminado: $it") }

    private companion object {
        const val TAG = "SWHandsFree"
        const val QUIET_PAUSE_MS = 600L
        const val MAX_BACKOFF_MS = 5_000L
    }
}
