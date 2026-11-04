package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ManualItineraryTest {

    private val castle = place("1", "Castillo de San Felipe", 10.4225, -75.5395)
    private val popa = place("2", "Cerro de la Popa", 10.4167, -75.5320)
    private val tower = place("3", "Torre del Reloj", 10.4226, -75.5486)
    private val here = Coordinates(10.42, -75.55)

    @Test
    fun `armarlo a mano y anadir se piden de viva voz`() {
        listOf("quiero armar mi propio itinerario", "un itinerario a mano", "my own itinerary")
            .forEach { assertEquals(VoiceCommand.BuildItinerary, VoiceGrammar.parse(it, emptyList()), "«$it»") }
        assertEquals(VoiceCommand.AddToItinerary(popa), VoiceGrammar.parse("añade el cerro de la popa", listOf(castle, popa)))
        assertEquals(VoiceCommand.AddToItinerary(castle), VoiceGrammar.parse("agrega el castillo al itinerario", listOf(castle, popa)))
        assertIs<VoiceCommand.PlanItinerary>(VoiceGrammar.parse("ármame un itinerario de dos horas", emptyList()))
    }

    @Test
    fun `empieza por lo elegido y lo tocado va al final`() {
        val one = ItineraryDesigner.startedWith(tower, here, 600)
        assertEquals(listOf(tower), one.stops)
        val two = ItineraryDesigner.appended(one, popa)
        val three = ItineraryDesigner.appended(two, castle)
        assertEquals(listOf(tower, popa, castle), three.stops)
        assertEquals(3, three.stays.size)
        assertEquals(three, ItineraryDesigner.appended(three, popa))
    }

    @Test
    fun `anadir con la vista abierta es un cambio`() {
        val conversation = ItineraryConversation().apply { proposed(ItineraryDesigner.startedWith(tower, here, 600)) }
        val step = conversation.hear("añade el cerro de la popa", VoiceCommand.AddToItinerary(popa), TravellerProfile())
        assertIs<ItineraryConversation.Step.Repropose>(step)
        assertEquals(listOf(tower, popa).toSet(), step.proposal.stops.toSet())
        val tapped = conversation.edited(ItineraryEdit.Add(castle, atEnd = true))
        assertIs<ItineraryConversation.Step.Repropose>(tapped)
        assertEquals(castle, tapped.proposal.stops.last())
    }

    private fun place(id: String, name: String, lat: Double, lon: Double) =
        Place(id = id, name = name, position = Coordinates(lat, lon), narrationSeconds = 60, languages = setOf(VoiceLanguage.SPANISH))
}
