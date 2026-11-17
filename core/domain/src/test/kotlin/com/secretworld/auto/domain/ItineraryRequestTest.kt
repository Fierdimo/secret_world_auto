package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItineraryRequestTest {

    private fun parse(heard: String) = requireNotNull(ItineraryRequests.parse(heard)) { "«$heard» no se entendió como itinerario" }

    @Test
    fun `lo generico se entiende y deja huecos`() {
        val request = parse("ármame un itinerario para esta tarde")
        assertEquals(DayPart.AFTERNOON, request.dayPart)
        assertTrue(request.interests.isEmpty())
        assertNull(request.area)
        assertEquals(setOf(ItineraryRequest.Missing.INTERESTS), request.missing(TravellerProfile()))
    }

    @Test
    fun `la historia se entiende en los tres idiomas`() {
        // "histori" valía para "historia" pero no para "history": en inglés la
        // respuesta no se entendía y la conversación se cerraba.
        listOf("history", "historia", "storia").forEach { said ->
            assertEquals(setOf(Interest.HISTORY), ItineraryRequests.details(said).interests, said)
        }
    }

    @Test
    fun `sin tiempo ni gustos se pregunta por los dos`() {
        assertEquals(
            setOf(ItineraryRequest.Missing.TIME, ItineraryRequest.Missing.INTERESTS),
            parse("hazme un itinerario").missing(TravellerProfile()),
        )
    }

    @Test
    fun `con perfil no se pregunta por los gustos`() {
        val profile = TravellerProfile(likes = setOf(Interest.HISTORY))
        assertTrue(parse("ármame un itinerario para esta tarde").missing(profile).isEmpty())
    }

    @Test
    fun `lo especifico trae todo en la frase`() {
        val request = parse("quiero un itinerario de todo el día en Cartagena, me encantan los castillos y quiero aprovechar al máximo")
        assertEquals(DayPart.FULL_DAY, request.dayPart)
        assertEquals("cartagena", request.area)
        assertEquals(setOf(Interest.HISTORY), request.interests)
        assertEquals(Pace.PACKED, request.pace)
        assertTrue(request.missing(TravellerProfile()).isEmpty())
    }

    @Test
    fun `en italiano`() {
        val request = parse("organizzami un giro a Roma per tre ore, niente chiese, e con pranzo")
        assertEquals(180, request.minutes)
        assertEquals("roma", request.area)
        assertEquals(setOf(Interest.RELIGION), request.avoid)
        assertEquals(true, request.meal)
    }

    @Test
    fun `en ingles`() {
        val request = parse("plan me a relaxed tour this afternoon, I like museums and parks")
        assertEquals(DayPart.AFTERNOON, request.dayPart)
        assertEquals(Pace.RELAXED, request.pace)
        assertEquals(setOf(Interest.ART, Interest.NATURE), request.interests)
    }

    @Test
    fun `duraciones`() {
        assertEquals(120, parse("un itinerario de dos horas").minutes)
        assertEquals(90, parse("un tour de una hora y media").minutes)
        assertEquals(30, parse("un giro di mezz'ora").minutes)
        assertEquals(240, parse("a 4 hours tour").minutes)
    }

    @Test
    fun `la tarde de hoy es la tarde`() {
        assertEquals(DayPart.AFTERNOON, parse("un itinerario para esta tarde de hoy").dayPart)
        assertEquals(DayPart.FULL_DAY, parse("un itinerario para hoy").dayPart)
    }

    @Test
    fun `la negacion manda a evitar`() {
        val request = parse("un itinerario sin iglesias, no me gustan los museos, pero sí la historia")
        assertEquals(setOf(Interest.RELIGION, Interest.ART), request.avoid)
        assertEquals(setOf(Interest.HISTORY), request.interests)
    }

    @Test
    fun `aparcar no es naturaleza`() {
        assertTrue(Interest.NATURE !in parse("un itinerario con parqueadero cerca").interests)
    }

    @Test
    fun `por la tarde no es un sitio`() {
        assertNull(parse("un itinerario por la tarde").area)
        assertNull(parse("un itinerario de historia").area)
    }

    @Test
    fun `lo que no pide itinerario no lo es`() {
        assertNull(ItineraryRequests.parse("qué hay cerca"))
        assertNull(ItineraryRequests.parse("llévame al castillo"))
        assertNull(ItineraryRequests.parse("termina el percorso"))
    }

    @Test
    fun `la respuesta a la pregunta completa la peticion`() {
        val asked = parse("hazme un itinerario")
        val answered = asked.plus(ItineraryRequests.details("unas tres horas, me gustan los museos"))
        assertEquals(180, answered.minutes)
        assertEquals(setOf(Interest.ART), answered.interests)
        assertTrue(answered.missing(TravellerProfile()).isEmpty())
    }

    @Test
    fun `un ajuste quita lo que ya gustaba`() {
        val first = parse("un itinerario de historia y religión esta tarde")
        val adjusted = first.plus(ItineraryRequests.details("menos iglesias"))
        assertEquals(setOf(Interest.HISTORY), adjusted.interests)
        assertEquals(setOf(Interest.RELIGION), adjusted.avoid)
    }

    @Test
    fun `el perfil aprende y lo ultimo manda`() {
        val profile = TravellerProfile()
            .learn(ItineraryRequest(interests = setOf(Interest.RELIGION, Interest.HISTORY)))
            .learn(ItineraryRequest(avoid = setOf(Interest.RELIGION)))
        assertEquals(setOf(Interest.HISTORY), profile.likes)
        assertEquals(setOf(Interest.RELIGION), profile.dislikes)
    }

    @Test
    fun `lo dicho gana al perfil`() {
        val profile = TravellerProfile(likes = setOf(Interest.ART), dislikes = setOf(Interest.NATURE))
        val request = ItineraryRequest(interests = setOf(Interest.NATURE)).withProfile(profile)
        assertEquals(setOf(Interest.NATURE), request.interests)
        assertTrue(Interest.NATURE !in request.avoid)
    }
}
