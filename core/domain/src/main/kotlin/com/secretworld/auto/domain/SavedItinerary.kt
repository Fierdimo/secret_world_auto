package com.secretworld.auto.domain

/**
 * Un itinerario guardado en el dispositivo: se arma en el hotel y se empieza
 * otro día. Las horas de [proposal] son las de salir ahora; las de cuando se
 * guardó ya no valen.
 */
data class SavedItinerary(val id: String, val savedAt: Long, val proposal: ItineraryProposal)

/**
 * Qué se pide con la ventana de los guardados delante: "el segundo", "empieza el
 * del castillo", "borra el primero". Solo decide; abrir, arrancar o borrar es
 * cosa de quien lo usa.
 */
object ItineraryLibrary {

    enum class Action { OPEN, START, DELETE }

    data class Choice(val index: Int, val action: Action)

    /**
     * El guardado al que se refiere la frase y qué hacer con él, o `null` si no
     * nombra ninguno. Con uno solo, "empiézalo" o "bórralo" bastan.
     */
    fun choose(heard: String, saved: List<SavedItinerary>): Choice? {
        if (saved.isEmpty()) return null
        val action = when {
            ItineraryRequests.removes(heard) -> Action.DELETE
            ItineraryRequests.startsNow(heard) -> Action.START
            else -> Action.OPEN
        }
        val index = VoiceGrammar.ordinal(heard)?.takeIf { it in saved.indices }
            ?: byStop(heard, saved)
            ?: if (saved.size == 1 && action != Action.OPEN) 0 else return null
        return Choice(index, action)
    }

    /** "El del castillo": el primero que pase por un sitio nombrado. */
    private fun byStop(heard: String, saved: List<SavedItinerary>): Int? =
        saved.indexOfFirst { VoiceGrammar.resolve(heard, it.proposal.stops) != null }.takeIf { it >= 0 }
}
