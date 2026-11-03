package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItineraryConversationTest {

    private val castle = place("1", "Castillo de San Felipe")
    private val museum = place("2", "Museo del Oro")
    private val square = place("3", "Plaza de los Coches")

    private val empty = TravellerProfile()

    private fun proposal(request: ItineraryRequest = ItineraryRequest(minutes = 180)) =
        ItineraryProposal(listOf(castle, museum, square), 170, mealAfter = null, request = request)

    private fun proposed(request: ItineraryRequest = ItineraryRequest(minutes = 180)) =
        ItineraryConversation().apply { proposed(proposal(request)) }

    /** Lo que la gramática entiende de la frase, como en la app. */
    private fun hear(conversation: ItineraryConversation, heard: String, profile: TravellerProfile = empty) =
        conversation.hear(heard, VoiceGrammar.parse(heard, emptyList()), profile)

    @Test
    fun `la gramatica reconoce la peticion`() {
        val command = VoiceGrammar.parse("ármame un itinerario para esta tarde", emptyList())
        assertIs<VoiceCommand.PlanItinerary>(command)
        assertEquals(DayPart.AFTERNOON, command.request.dayPart)
        assertEquals(VoiceCommand.ForgetProfile, VoiceGrammar.parse("olvida mis preferencias", emptyList()))
    }

    @Test
    fun `terminar el itinerario no se confunde con una terminal`() {
        assertEquals(VoiceCommand.EndRoute, VoiceGrammar.parse("termina el itinerario", emptyList()))
        assertEquals(VoiceCommand.EndRoute, VoiceGrammar.parse("terminamos el tour", emptyList()))
        val terminal = place("9", "Terminal de Transportes")
        assertEquals(VoiceCommand.TakeMeThere(terminal), VoiceGrammar.parse("llévame a la terminal de transportes", listOf(terminal)))
        assertTrue(VoiceGrammar.parse("cuéntame de la terminal de transportes", listOf(terminal)) != VoiceCommand.EndRoute)
    }

    @Test
    fun `un itinerario con comida no es buscar comida`() {
        assertIs<VoiceCommand.PlanItinerary>(VoiceGrammar.parse("un itinerario de dos horas con comida", emptyList()))
    }

    @Test
    fun `sin datos pregunta y la respuesta lleva a planificar`() {
        val conversation = ItineraryConversation()
        val ask = conversation.request(ItineraryRequest(), empty)
        assertIs<ItineraryConversation.Step.Ask>(ask)
        assertEquals(setOf(ItineraryRequest.Missing.TIME, ItineraryRequest.Missing.INTERESTS), ask.missing)

        val plan = hear(conversation, "unas tres horas, me gustan los museos")
        assertIs<ItineraryConversation.Step.Plan>(plan)
        assertEquals(180, plan.request.minutes)
        assertEquals(setOf(Interest.ART), plan.request.interests)
    }

    @Test
    fun `contestar con un nombre de lugar sigue siendo contestar`() {
        val conversation = ItineraryConversation()
        conversation.request(ItineraryRequest(dayPart = DayPart.AFTERNOON), empty)
        val heard = "me gustan los castillos y la historia"
        // La gramática, con el castillo en la lista, entiende "cuéntame el castillo".
        val grammarSays = VoiceGrammar.parse(heard, listOf(castle))
        assertIs<VoiceCommand.TellMeAbout>(grammarSays)
        val plan = conversation.hear(heard, grammarSays, empty)
        assertIs<ItineraryConversation.Step.Plan>(plan)
        assertEquals(setOf(Interest.HISTORY), plan.request.interests)
    }

    @Test
    fun `responder a medias vuelve a preguntar solo lo que falta`() {
        val conversation = ItineraryConversation()
        conversation.request(ItineraryRequest(), empty)
        val ask = hear(conversation, "un par de horas")
        assertIs<ItineraryConversation.Step.Ask>(ask)
        assertEquals(setOf(ItineraryRequest.Missing.INTERESTS), ask.missing)
    }

    @Test
    fun `con todo dicho se planifica sin preguntar`() {
        val step = ItineraryConversation().request(ItineraryRequest(dayPart = DayPart.AFTERNOON, interests = setOf(Interest.HISTORY)), empty)
        assertIs<ItineraryConversation.Step.Plan>(step)
    }

    @Test
    fun `lo que no contesta a la pregunta se atiende como orden`() {
        val conversation = ItineraryConversation()
        conversation.request(ItineraryRequest(), empty)
        assertNull(hear(conversation, "llévame al castillo"))
        assertEquals(ItineraryConversation.State.Idle, conversation.state)
    }

    @Test
    fun `si empieza y no descarta`() {
        assertIs<ItineraryConversation.Step.Start>(hear(proposed(), "sí"))
        assertIs<ItineraryConversation.Step.Start>(hear(proposed(), "partiamo"))
        assertIs<ItineraryConversation.Step.Start>(hear(proposed(), "venga, vamos"))
        assertEquals(ItineraryConversation.Step.Discarded, hear(proposed(), "no"))
        assertEquals(ItineraryConversation.Step.Discarded, hear(proposed(), "no, vale"))
    }

    @Test
    fun `quitar una parada repropone sin ella`() {
        val step = hear(proposed(), "quita el museo")
        assertIs<ItineraryConversation.Step.Repropose>(step)
        assertEquals(listOf(castle, square), step.proposal.stops)
        assertIs<ItineraryConversation.State.Proposed>(proposed().also { hear(it, "quita el museo") }.state)
    }

    @Test
    fun `quitar algo que no esta en la propuesta es evitarlo`() {
        val step = hear(proposed(), "quita la catedral")
        assertIs<ItineraryConversation.Step.Plan>(step)
        assertEquals(setOf(Interest.RELIGION), step.request.avoid)
        assertTrue(step.request.mentions.isEmpty())
    }

    @Test
    fun `un ajuste replanifica sumando a lo pedido`() {
        val step = hear(proposed(ItineraryRequest(minutes = 180, interests = setOf(Interest.HISTORY, Interest.RELIGION))), "menos iglesias")
        assertIs<ItineraryConversation.Step.Plan>(step)
        assertEquals(setOf(Interest.HISTORY), step.request.interests)
        assertEquals(setOf(Interest.RELIGION), step.request.avoid)
        assertEquals(180, step.request.minutes)
    }

    @Test
    fun `pedir comida es un ajuste`() {
        val step = hear(proposed(), "añade dónde comer")
        assertIs<ItineraryConversation.Step.Plan>(step)
        assertEquals(true, step.request.meal)
    }

    @Test
    fun `con la propuesta pendiente otra orden la olvida`() {
        val conversation = proposed()
        assertNull(hear(conversation, "qué hay cerca"))
        assertTrue(!conversation.busy)
    }

    private fun place(id: String, name: String) = Place(
        id = id,
        name = name,
        position = Coordinates(10.42, -75.55),
        narrationSeconds = 60,
        languages = setOf(VoiceLanguage.SPANISH),
    )
}
