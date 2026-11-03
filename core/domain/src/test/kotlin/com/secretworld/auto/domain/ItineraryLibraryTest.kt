package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItineraryLibraryTest {

    private val castle = place("1", "Castillo de San Felipe")
    private val museum = place("2", "Museo del Oro")
    private val square = place("3", "Plaza de los Coches")
    private val popa = place("4", "Cerro de la Popa")

    private fun saved(id: String, vararg stops: Place) =
        SavedItinerary(id, 0, ItineraryProposal(stops.toList(), 120, mealAfter = null, request = ItineraryRequest()))

    private val two = listOf(saved("a", museum, square), saved("b", castle, popa))

    @Test
    fun `ver los guardados se pide de viva voz`() {
        listOf("mis itinerarios", "muéstrame los itinerarios", "i miei itinerari", "show my itineraries")
            .forEach { assertEquals(VoiceCommand.ShowItineraries, VoiceGrammar.parse(it, emptyList()), "«$it»") }
        assertEquals(VoiceCommand.StartItinerary, VoiceGrammar.parse("empieza mi itinerario", emptyList()))
    }

    @Test
    fun `por numero, abrir o empezar`() {
        assertEquals(ItineraryLibrary.Choice(1, ItineraryLibrary.Action.OPEN), ItineraryLibrary.choose("el segundo", two))
        assertEquals(ItineraryLibrary.Choice(0, ItineraryLibrary.Action.START), ItineraryLibrary.choose("empieza el primero", two))
        assertEquals(ItineraryLibrary.Choice(1, ItineraryLibrary.Action.START), ItineraryLibrary.choose("iniziamo il secondo", two))
    }

    @Test
    fun `por una de sus paradas`() {
        assertEquals(ItineraryLibrary.Choice(1, ItineraryLibrary.Action.START), ItineraryLibrary.choose("empieza el del castillo", two))
        assertEquals(ItineraryLibrary.Choice(0, ItineraryLibrary.Action.OPEN), ItineraryLibrary.choose("el del museo del oro", two))
    }

    @Test
    fun `borrar`() {
        assertEquals(ItineraryLibrary.Choice(0, ItineraryLibrary.Action.DELETE), ItineraryLibrary.choose("borra el primero", two))
    }

    @Test
    fun `sin nombrar ninguno, solo si hay uno`() {
        assertNull(ItineraryLibrary.choose("empieza", two))
        assertEquals(ItineraryLibrary.Choice(0, ItineraryLibrary.Action.START), ItineraryLibrary.choose("empiézalo", two.take(1)))
        assertNull(ItineraryLibrary.choose("qué hay cerca", two))
    }

    private fun place(id: String, name: String) = Place(
        id = id,
        name = name,
        position = Coordinates(10.42, -75.55),
        narrationSeconds = 60,
        languages = setOf(VoiceLanguage.SPANISH),
    )
}
