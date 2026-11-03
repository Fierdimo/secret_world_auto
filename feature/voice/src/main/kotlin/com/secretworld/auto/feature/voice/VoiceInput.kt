package com.secretworld.auto.feature.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * El micrófono: convierte lo que dice el conductor en texto.
 *
 * Es una interfaz porque hay dos formas de oír y la pantalla no tiene que saber
 * cuál está actuando:
 *
 * - [OnDeviceVoiceInput]: palabra de activación, detector de voz y reconocedor
 *   **en el dispositivo**, sin red. Es el producto.
 * - [SystemVoiceInput]: el reconocedor del sistema, reabierto en cada frase. Es
 *   el respaldo mientras los modelos no están instalados.
 */
interface VoiceInput {

    sealed interface State {
        data object Idle : State
        /** El micrófono está abierto esperando una orden. La pantalla tiene que decirlo. */
        data object Listening : State
        /**
         * @param direct si la orden ya viene **dirigida al asistente**: la abrió
         * el conductor con el botón, o la palabra de activación se detectó antes
         * de oírla. Lo que no es directo exige la palabra dentro del texto; sin
         * distinguirlo, cualquier conversación dentro del coche sería una orden.
         */
        /**
         * @param alternatives otras lecturas de la misma frase —la del audio sin
         *   limpiar, si hubo reductor de ruido—. Si [text] no se entiende, se
         *   prueban antes de rendirse.
         */
        data class Heard(val text: String, val direct: Boolean, val alternatives: List<String> = emptyList()) : State
        /**
         * @param quiet si no es noticia: vigilando la palabra de activación, que
         *   el reconocedor no oiga nada es lo normal, y enseñarlo en cada vuelta
         *   llenaba la pantalla de "no te he oído".
         */
        data class Failed(val reason: String, val quiet: Boolean = false) : State
    }

    val state: StateFlow<State>

    /**
     * Si ahora mismo está oyendo hablar a alguien y todavía no sabe qué dijo:
     * desde que el detector nota voz hasta que la frase queda transcrita. Es lo
     * que permite enseñar "te oigo" antes de saber si iba con nosotros; sin
     * ello, el conductor no distingue "no me oyó" de "me está oyendo".
     */
    val hearing: StateFlow<Boolean> get() = NEVER

    /** ¿Puede oír en este dispositivo? Sin reconocedor ni modelos, no. */
    val available: Boolean

    val hasPermission: Boolean

    private companion object {
        val NEVER: StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    /**
     * Si al terminar una frase sigue escuchando solo. Un reconocedor que se cierra
     * en cada frase necesita que alguien lo reabra ([HandsFree]); uno con palabra
     * de activación no.
     */
    val continuous: Boolean

    /**
     * Abre el micrófono. Con [direct] la siguiente frase es una orden sin más;
     * sin él, espera a la palabra de activación.
     */
    fun listen(direct: Boolean = true)

    /**
     * Abre el turno **después de una pregunta** del asistente. Como [listen]
     * directo, pero con más paciencia y sin reproche: si el conductor no
     * contesta, no ha pasado nada, se vuelve a vigilar.
     */
    fun listenForAnswer() = listen(direct = true)

    /**
     * Cancela la orden en curso. Si había vigilancia de la palabra de activación,
     * **sigue**: pulsar el botón para cortar una frase no es pedir que se apague.
     */
    fun cancel()

    /** Apaga todo: orden en curso y vigilancia. Es lo que hace [HandsFree.disable]. */
    fun stopListening()

    /** Vuelve al silencio tras haber mostrado lo que se oyó o el fallo. */
    fun acknowledge()

    /**
     * Hace como si se hubiera oído [text], dirigido al asistente. **Solo para
     * probar** la conversación sin micrófono —adb shell am start ... --es say
     * "..."—: el resto del camino es el de una frase de verdad.
     */
    fun inject(text: String) {}
}
