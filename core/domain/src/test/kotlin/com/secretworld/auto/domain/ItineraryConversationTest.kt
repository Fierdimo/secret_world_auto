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
    fun `guardar se dice de muchas formas`() {
        listOf("sí", "guarda ese itinerario", "guárdalo", "me gusta", "perfecto", "está bien", "salvalo", "save it", "no, guárdalo")
            .forEach { assertIs<ItineraryConversation.Step.Save>(hear(proposed(), it), "«$it»") }
        assertEquals(ItineraryConversation.Step.Discarded, hear(proposed(), "no"))
        assertEquals(ItineraryConversation.Step.Discarded, hear(proposed(), "no, vale"))
    }

    @Test
    fun `empezar a la propuesta la guarda y arranca`() {
        listOf("inicia el itinerario", "empecemos", "venga, vamos", "vámonos ya", "dale", "partiamo", "iniziamo", "let's go", "start it now")
            .forEach { heard ->
                val conversation = proposed()
                assertIs<ItineraryConversation.Step.Start>(hear(conversation, heard), "«$heard»")
                assertEquals(ItineraryConversation.State.Idle, conversation.state)
            }
    }

    @Test
    fun `dejarlo para luego lo guarda sin arrancar`() {
        listOf("guárdalo para luego", "mejor guárdalo para más tarde", "empezamos mañana", "ahora no, después", "later", "lo salvo per dopo")
            .forEach { assertIs<ItineraryConversation.Step.Shelved>(hear(proposed(), it), "«$it»") }
    }

    @Test
    fun `una negacion delante no arranca`() {
        assertIs<ItineraryConversation.Step.Discarded>(hear(proposed(), "no empecemos"))
        assertNull(ItineraryRequests.reply("don't start"))
        assertNull(ItineraryRequests.reply("non partiamo"))
    }

    @Test
    fun `empezar por una parada es un cambio, no empezar`() {
        val step = hear(proposed(), "empieza por la plaza de los coches")
        assertIs<ItineraryConversation.Step.Repropose>(step)
        assertEquals(ItineraryEdit.Move(square, first = true), step.change)
    }

    @Test
    fun `vamos con un ajuste es un ajuste`() {
        assertNull(ItineraryRequests.reply("vamos a ver más museos"))
        assertIs<ItineraryConversation.Step.Plan>(hear(proposed(), "venga, pero con más museos"))
    }

    @Test
    fun `la respuesta del modelo contesta a lo pendiente`() {
        val conversation = proposed()
        val pending = conversation.state
        assertNull(hear(conversation, "eso suena interesante"))
        conversation.restore(pending)
        assertIs<ItineraryConversation.Step.Start>(conversation.answer(ItineraryReply.START))
        assertEquals(ItineraryReply.SAVE, ItineraryReplyPrompt.parse(" save\n"))
        assertEquals(ItineraryReply.LATER, ItineraryReplyPrompt.parse("later."))
        assertNull(ItineraryReplyPrompt.parse("none"))
        assertEquals(ItineraryReply.START, ItineraryReplyPrompt.parse("\"nos ponemos en camino\" -> start"))
        assertEquals(ItineraryReply.START, ItineraryReplyPrompt.parse("Reply: start"))
        assertNull(ItineraryReplyPrompt.parse("\"start later\" none"))
    }

    @Test
    fun `tras un cambio solo se dice el cambio`() {
        val c = Phrasebook.Context(Coordinates(10.4, -75.5), emptyList(), ConversationMemory(), VoiceLanguage.SPANISH)
        val step = hear(proposed(), "quita el museo del oro")
        assertIs<ItineraryConversation.Step.Repropose>(step)
        val said = Phrasebook.itineraryChanged(c, step.proposal, step.change)
        assertTrue(said.startsWith("Fuera Museo del Oro."), said)
        assertTrue("propongo" !in said && "Castillo" !in said, said)
    }

    @Test
    fun `contar un sitio o callar no pierde la propuesta`() {
        val conversation = proposed()
        assertNull(conversation.hear("cuéntame del castillo", VoiceCommand.TellMeAbout(castle), empty))
        assertIs<ItineraryConversation.State.Proposed>(conversation.state)
        assertEquals(VoiceCommand.Stop, VoiceGrammar.parse("para", emptyList()))
        assertNull(hear(conversation, "para"))
        assertIs<ItineraryConversation.State.Proposed>(conversation.state)
        assertIs<ItineraryConversation.Step.Save>(hear(conversation, "guárdalo"))
        assertNull(conversation.hear("cuéntame del museo", VoiceCommand.TellMeAbout(museum), empty))
        assertIs<ItineraryConversation.State.Saved>(conversation.state)
    }

    @Test
    fun `lo que no se entiende no aparta del itinerario, otra orden si`() {
        val conversation = proposed()
        assertNull(hear(conversation, "y entonces le dije que tal"))
        assertIs<ItineraryConversation.State.Proposed>(conversation.state)
        assertNull(conversation.hear("qué hay cerca", VoiceCommand.WhatIsNearby, empty))
        assertEquals(ItineraryConversation.State.Idle, conversation.state)

        val saved = proposed().also { hear(it, "guárdalo") }
        assertNull(hear(saved, "y entonces le dije que tal"))
        assertIs<ItineraryConversation.State.Saved>(saved.state)
    }

    @Test
    fun `guardado, el segundo si empieza y el no lo deja para despues`() {
        val conversation = proposed()
        assertIs<ItineraryConversation.Step.Save>(hear(conversation, "sí"))
        assertIs<ItineraryConversation.Step.Start>(hear(conversation, "sí, empecemos"))
        assertIs<ItineraryConversation.Step.Start>(hear(proposed().also { hear(it, "guárdalo") }, "inicia el itinerario"))
        assertIs<ItineraryConversation.Step.Shelved>(hear(proposed().also { hear(it, "guárdalo") }, "más tarde"))

        val later = proposed().also { hear(it, "sí") }
        assertIs<ItineraryConversation.Step.Shelved>(hear(later, "no"))
        assertEquals(ItineraryConversation.State.Idle, later.state)
    }

    @Test
    fun `cambiar lo guardado vuelve a pedir confirmacion`() {
        val conversation = proposed().also { hear(it, "sí") }
        assertIs<ItineraryConversation.Step.Repropose>(hear(conversation, "deja el castillo para el final"))
        assertIs<ItineraryConversation.State.Proposed>(conversation.state)
    }

    @Test
    fun `empezar el itinerario guardado`() {
        assertEquals(VoiceCommand.StartItinerary, VoiceGrammar.parse("empieza mi itinerario", emptyList()))
        assertEquals(VoiceCommand.StartItinerary, VoiceGrammar.parse("inizia il mio itinerario", emptyList()))
        assertIs<VoiceCommand.PlanItinerary>(VoiceGrammar.parse("hazme mi itinerario para esta tarde", emptyList()))
    }

    private fun reordered(heard: String, command: VoiceCommand? = null): List<Place> {
        val conversation = proposed()
        val step = conversation.hear(heard, command ?: VoiceGrammar.parse(heard, emptyList()), empty)
        assertIs<ItineraryConversation.Step.Repropose>(step, "«$heard»")
        return step.proposal.stops
    }

    @Test
    fun `empezar por una parada la pone la primera`() {
        assertEquals(listOf(square, castle, museum), reordered("empieza por la plaza de los coches"))
        assertEquals(listOf(museum, castle, square), reordered("primero el museo del oro"))
    }

    @Test
    fun `dejar una parada para el final`() {
        assertEquals(listOf(museum, square, castle), reordered("deja el castillo para el final"))
        assertEquals(listOf(castle, square, museum), reordered("il museo alla fine"))
    }

    @Test
    fun `una parada antes o despues de otra`() {
        assertEquals(listOf(castle, square, museum), reordered("la plaza antes que el museo"))
        assertEquals(listOf(museum, castle, square), reordered("el castillo después del museo"))
    }

    @Test
    fun `mas o menos tiempo en una parada`() {
        val base = proposal()
        val longer = proposed().hear("más tiempo en el castillo", VoiceCommand.NotUnderstood(""), empty)
        assertIs<ItineraryConversation.Step.Repropose>(longer)
        assertTrue(longer.proposal.stays[0] > ItineraryDesigner.stopMinutes(castle, Pace.NORMAL))
        val shorter = proposed().hear("menos tiempo en la plaza", VoiceCommand.NotUnderstood(""), empty)
        assertIs<ItineraryConversation.Step.Repropose>(shorter)
        assertTrue(shorter.proposal.stays[2] < ItineraryDesigner.stopMinutes(square, Pace.NORMAL))
        assertEquals(base.stops, shorter.proposal.stops)
    }

    @Test
    fun `anadir un sitio que no estaba`() {
        val hill = place("9", "Cerro de La Popa")
        val stops = reordered("añade el cerro de la popa", VoiceCommand.ChooseAction(hill))
        assertEquals(4, stops.size)
        assertTrue(hill in stops)
    }

    @Test
    fun `anadir donde comer sigue siendo pedir comida`() {
        val step = hear(proposed(), "añade dónde comer")
        assertIs<ItineraryConversation.Step.Plan>(step)
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
