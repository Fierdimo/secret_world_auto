package com.secretworld.auto.feature.voice

import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sin escucha: el dispositivo no tiene los modelos propios.
 *
 * El reconocedor del sistema era el respaldo, pero en marcha había que reabrirlo
 * en cada frase, y en algunos móviles se desconectaba cada pocos segundos y le
 * quitaba el audio a la narración. Mejor no escuchar y decirlo una vez: la app
 * se sigue usando tocando.
 */
class NoVoiceInput(private val language: VoiceLanguage) : VoiceInput {

    private val _state = MutableStateFlow<VoiceInput.State>(VoiceInput.State.Idle)
    override val state: StateFlow<VoiceInput.State> = _state.asStateFlow()

    override val available = false
    override val hasPermission = true
    override val continuous = false

    /** Pedir que escuche —el botón, una respuesta— solo recuerda que no se puede. */
    override fun listen(direct: Boolean) {
        if (!direct) return
        _state.value = VoiceInput.State.Failed(
            language.say(
                en = "This device can't recognise speech",
                es = "Este dispositivo no puede reconocer la voz",
                it = "Questo dispositivo non può riconoscere la voce",
            ),
        )
    }

    /** Un turno de respuesta abierto por la app, no pedido: sin aviso. */
    override fun listenForAnswer() = Unit

    override fun cancel() {
        _state.value = VoiceInput.State.Idle
    }

    override fun stopListening() = cancel()

    override fun acknowledge() = cancel()

    override fun inject(text: String) {
        _state.value = VoiceInput.State.Heard(text, direct = true)
    }
}
