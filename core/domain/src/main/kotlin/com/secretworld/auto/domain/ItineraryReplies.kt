package com.secretworld.auto.domain

/**
 * Las respuestas a lo que pregunta el itinerario, para **tocarlas** en vez de
 * decirlas: cuánto tiempo hay y qué le gusta. Sin voz —en el emulador de Pleos,
 * en un coche cuyo reconocedor no habla el idioma— era la única forma de que
 * "Sugerido" pasara de la pregunta.
 *
 * Cada una lleva la frase que se diría: tocarla es decirla, y pasa por la misma
 * conversación que la voz. Que cada frase se entienda como debe lo comprueba
 * ItineraryRepliesTest.
 */
object ItineraryReplies {

    /** Lo que se enseña en el botón y lo que equivale a decir. */
    data class Reply(val label: String, val says: String)

    /** Lo que se ofrece para lo que falta: primero el tiempo, luego los gustos. */
    fun forMissing(missing: Set<ItineraryRequest.Missing>, language: VoiceLanguage): List<Reply> = when {
        ItineraryRequest.Missing.TIME in missing -> time(language)
        ItineraryRequest.Missing.INTERESTS in missing -> interests(language)
        else -> emptyList()
    }

    /** La pregunta, corta, para el título de la tarjeta. */
    fun question(missing: Set<ItineraryRequest.Missing>, language: VoiceLanguage): String = when {
        ItineraryRequest.Missing.TIME in missing ->
            language.say(en = "How much time do you have?", es = "¿Cuánto tiempo tienes?", it = "Quanto tempo hai?")
        else -> language.say(en = "What do you fancy most?", es = "¿Qué te apetece más?", it = "Cosa ti va di più?")
    }

    /** Los textos de la tarjeta cuando ya hay un itinerario sobre el mapa. */
    object Labels {
        fun ready(language: VoiceLanguage) = language.say(en = "Your itinerary is ready", es = "Tu itinerario está listo", it = "Il tuo itinerario è pronto")
        fun saved(language: VoiceLanguage) = language.say(en = "Saved. Shall we go?", es = "Guardado. ¿Empezamos?", it = "Salvato. Partiamo?")
        fun start(language: VoiceLanguage) = language.say(en = "Start", es = "Empezar", it = "Inizia")
        fun save(language: VoiceLanguage) = language.say(en = "Save", es = "Guardar", it = "Salva")
        fun discard(language: VoiceLanguage) = language.say(en = "Discard", es = "Descartar", it = "Scarta")
        fun later(language: VoiceLanguage) = language.say(en = "Later", es = "Más tarde", it = "Più tardi")
        fun cancel(language: VoiceLanguage) = language.say(en = "Cancel", es = "Cancelar", it = "Annulla")
    }

    fun time(language: VoiceLanguage): List<Reply> = listOf(
        Reply("1 h", language.say(en = "one hour", es = "una hora", it = "un'ora")),
        Reply("2 h", language.say(en = "two hours", es = "dos horas", it = "due ore")),
        Reply("3 h", language.say(en = "three hours", es = "tres horas", it = "tre ore")),
        language.say(en = "All day", es = "Todo el día", it = "Tutto il giorno").let { Reply(it, it.lowercase()) },
    )

    fun interests(language: VoiceLanguage): List<Reply> = listOf(
        language.say(en = "History", es = "Historia", it = "Storia"),
        language.say(en = "Art", es = "Arte", it = "Arte"),
        language.say(en = "Churches", es = "Iglesias", it = "Chiese"),
        language.say(en = "Nature", es = "Naturaleza", it = "Natura"),
    ).map { Reply(it, it.lowercase()) } +
        Reply(
            language.say(en = "The city", es = "La ciudad", it = "La città"),
            language.say(en = "strolling the city", es = "pasear por la ciudad", it = "passeggiare in città"),
        )
}
