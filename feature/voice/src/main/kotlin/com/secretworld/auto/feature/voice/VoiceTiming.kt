package com.secretworld.auto.feature.voice

import android.os.Process
import android.os.SystemClock
import android.util.Log

/**
 * Los tiempos que importan de la voz, con una etiqueta propia para leerlos de
 * un vistazo: `adb logcat -s SWTiming`.
 *
 * - **Arranque en frío**: cuándo está lista cada pieza, contado desde que nace
 *   el proceso —interfaz, escucha, voz, IA—.
 * - **Cada frase**: desde que se deja de hablar hasta que la app empieza a
 *   contestar, partido en reconocer, entender y empezar a hablar.
 *
 * Se midió a mano en un Snapdragon 685 y la espera estaba donde nadie miraba:
 * con esto se ve sin tener que reconstruirlo línea a línea.
 */
object VoiceTiming {

    private const val TAG = "SWTiming"

    /** Más que esto entre dejar de hablar y contestar, y la frase ya no se cuenta como la misma. */
    private const val STALE_MS = 120_000L

    private val reported = mutableSetOf<String>()

    /** Milisegundos desde que arrancó el proceso. */
    fun sinceStart(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()

    /** Una pieza del arranque, una sola vez por proceso. */
    @Synchronized
    fun ready(piece: String, took: Long? = null) {
        if (!reported.add(piece)) return
        Log.i(TAG, "arranque · $piece a los ${sinceStart()} ms" + (took?.let { " (tardó $it ms)" } ?: ""))
    }

    @Volatile private var speechEnded = 0L
    @Volatile private var recognizeMs = 0L
    @Volatile private var understandMs = -1L
    @Volatile private var said = ""

    /** Se dejó de hablar: empieza a contar la frase. [decodeStartedAt] en `nanoTime`. */
    fun speechEnded(decodeStartedAt: Long) {
        speechEnded = decodeStartedAt
        understandMs = -1L
    }

    /** El reconocedor terminó; [ms] es lo que tardó y [text], lo que entendió. */
    fun recognized(ms: Long, text: String) {
        recognizeMs = ms
        said = text
    }

    /** La frase se entendió como [command] en [ms]. */
    fun understood(command: String, ms: Long) {
        if (speechEnded == 0L) return
        understandMs = ms
        Log.i(TAG, "frase «$said» · reconocer $recognizeMs ms · entender $ms ms ($command)")
    }

    /** Empezó a sonar la respuesta: cierra la cuenta de la frase. */
    fun startedSpeaking() {
        val ended = speechEnded
        if (ended == 0L || understandMs < 0) return
        speechEnded = 0L
        val total = (System.nanoTime() - ended) / 1_000_000
        if (total > STALE_MS) return
        val speak = (total - recognizeMs - understandMs).coerceAtLeast(0)
        Log.i(TAG, "frase «$said» · reconocer $recognizeMs ms · entender $understandMs ms · empezar a hablar $speak ms · total $total ms")
    }
}
