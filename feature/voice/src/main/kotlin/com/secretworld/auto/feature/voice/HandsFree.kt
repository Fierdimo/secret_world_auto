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
 * Es lo que hace utilizable el asistente en marcha. Cómo se consigue depende del
 * micrófono:
 *
 * - Con [OnDeviceVoiceInput] es trivial: el detector de palabra de activación
 *   ya escucha en continuo, así que basta con pedírselo una vez.
 * - Con [SystemVoiceInput] el reconocedor de AOSP cierra el micrófono en cada
 *   frase, y "permanente" significa *volver a abrirlo en cuanto se cierra*. La
 *   orden llega marcada como no directa y quien la interpreta exige la palabra
 *   de activación en el texto. Funciona, pero gasta más y, si el reconocedor
 *   tira de red, depende de ella. Es el respaldo, no el producto.
 */
class HandsFree(
    private val input: VoiceInput,
    private val scope: CoroutineScope,
    /**
     * Si la app está hablando. Con el reconocedor del sistema no se escucha
     * mientras tanto: al abrir el micrófono se queda el audio, y la narración
     * se cortaba nada más empezar.
     */
    private val speaking: StateFlow<Boolean> = MutableStateFlow(false),
) {

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var loop: Job? = null

    /** ¿Puede siquiera intentarse aquí? Sin micrófono ni reconocedor, no. */
    val possible: Boolean get() = input.available && input.hasPermission

    fun enable() {
        if (_enabled.value || !possible) return
        _enabled.value = true
        if (input.continuous) {
            // El detector de palabra de activación ya vigila solo.
            input.listen(direct = false)
            return
        }
        loop = scope.launch {
            var consecutiveErrors = 0
            while (_enabled.value) {
                // Sin reconocedor —no lo hay o dejó de responder—, se deja de escuchar.
                if (!input.available) {
                    Log.i(TAG, "sin reconocedor: escucha permanente apagada")
                    _enabled.value = false
                    break
                }
                speaking.first { !it }
                input.listen(direct = false)
                // Si empieza a hablar con el micrófono abierto, se cierra: primero se oye.
                val hush = scope.launch {
                    speaking.first { it }
                    input.stopListening()
                }
                // Se espera a que el ciclo termine: escuchando no hay nada que hacer.
                val outcome = input.state.awaitEndOfListening()
                hush.cancel()
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
        input.stopListening()
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
