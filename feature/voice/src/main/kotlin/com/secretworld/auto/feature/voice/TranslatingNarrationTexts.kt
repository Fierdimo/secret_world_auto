package com.secretworld.auto.feature.voice

import android.content.Context
import android.util.Log
import com.secretworld.auto.domain.LanguageModel
import com.secretworld.auto.domain.NarrationTextSource
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Traduce en el dispositivo el texto de un lugar cuando el catálogo no lo
 * tiene en el idioma de la voz.
 *
 * Comprobado contra el API: en Cartagena todo el contenido está en inglés
 * (`language_code: en`, `translations: null`). Antes que narrar en inglés a un
 * conductor en español, se traduce con el mismo modelo que entiende las
 * órdenes. Se hace **una vez por lugar y se guarda**, y ocurre en la fase de
 * preparación por adelantado, que ya corre en segundo plano: al narrar no se
 * espera. Un modelo de 1B tarda ~15 s por texto y traduce correcto, no
 * literario; la traducción editorial sigue siendo trabajo humano (ROADMAP).
 *
 * Si el modelo no está o falla, se narra el original: peor que traducido,
 * mejor que callarse.
 */
class TranslatingNarrationTexts(
    context: Context,
    private val base: NarrationTextSource,
    private val model: LanguageModel,
) : NarrationTextSource {

    private val cache = File(context.filesDir, "translations").apply { mkdirs() }

    override fun textFor(place: Place, language: VoiceLanguage): String? {
        val original = base.textFor(place, language) ?: return null
        val from = place.contentLanguage ?: return original
        if (from == language) return original

        val file = File(cache, "${place.id.replace(':', '_')}.${language.code}.txt")
        if (file.isFile) return file.readText()
        // El modelo tarda unos segundos en cargar al arrancar, y la preparación
        // por adelantado llega antes. Se espera: sintetizar el original ahora
        // dejaría en caché audio en inglés que nadie volvería a traducir.
        if (!awaitModel()) return original

        val started = System.nanoTime()
        val translated = runCatching {
            // Bloquea a propósito: se llama desde la preparación por adelantado,
            // en un hilo de fondo, nunca desde la interfaz.
            runBlocking { model.complete(prompt(language), original.trim() + "<end_of_turn>\n<start_of_turn>model\n", singleLine = false) }
        }.getOrNull()?.trim()?.takeIf { plausible(it, original) }

        if (translated == null) {
            Log.w(TAG, "no se pudo traducir ${place.id} a ${language.code}; se narra el original")
            return original
        }
        Log.i(TAG, "traducido ${place.id} a ${language.code} en ${(System.nanoTime() - started) / 1_000_000} ms")
        file.writeText(translated)
        return translated
    }

    private fun awaitModel(): Boolean = runBlocking {
        var waited = 0L
        while (!model.ready && !model.failed && waited < MODEL_WAIT_MS) {
            kotlinx.coroutines.delay(MODEL_POLL_MS)
            waited += MODEL_POLL_MS
        }
        model.ready
    }

    /**
     * Una traducción que es mucho más corta que el original, o que empieza
     * repitiendo la consigna, no es una traducción: es el modelo perdiéndose.
     */
    private fun plausible(translated: String, original: String): Boolean =
        translated.length > original.length / 3 && !translated.startsWith("Translate", ignoreCase = true)

    private fun prompt(language: VoiceLanguage): String {
        val name = when (language) {
            VoiceLanguage.SPANISH -> "Spanish"
            VoiceLanguage.ITALIAN -> "Italian"
            VoiceLanguage.ENGLISH -> "English"
        }
        // Con marcas de turno, aquí sí: para un texto largo el modelo se guía
        // mejor por el formato de chat, y el fin de turno es lo que lo para.
        return "<start_of_turn>user\nTranslate the following text into $name, for a spoken travel narration. " +
            "Keep place names. Output only the translation, nothing else.\n\n"
    }

    private companion object {
        const val TAG = "SWTranslate"
        const val MODEL_WAIT_MS = 40_000L
        const val MODEL_POLL_MS = 500L
    }
}
