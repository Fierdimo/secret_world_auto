package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItineraryDesignerTest {

    /** Centro histórico de Cartagena, más o menos. */
    private val here = Coordinates(10.4236, -75.5510)

    private fun place(id: String, name: String, dLat: Double, dLon: Double, summary: String = "", kind: String? = null) = Place(
        id = id,
        name = name,
        position = Coordinates(here.latitude + dLat, here.longitude + dLon),
        narrationSeconds = 60,
        languages = setOf(VoiceLanguage.SPANISH),
        summary = summary,
        kind = kind,
    )

    private val castle = place("1", "Castillo de San Felipe", 0.004, 0.012)
    private val cathedral = place("2", "Catedral de Santa Catalina", 0.001, 0.001)
    private val museum = place("wiki:es:3", "Museo del Oro Zenú", 0.002, 0.000)
    private val square = place("4", "Plaza de los Coches", 0.000, 0.002)
    private val wall = place("wiki:es:5", "Las Murallas", 0.003, -0.002)
    private val bar = place("6", "Bar La Esquina", 0.0005, 0.0005, kind = "bar")
    private val all = listOf(castle, cathedral, museum, square, wall, bar)

    private val threePm = 15 * 60
    private val tenAm = 10 * 60

    @Test
    fun `lo que pidio evitar no aparece`() {
        val proposal = ItineraryDesigner.plan(here, all, ItineraryRequest(minutes = 240, avoid = setOf(Interest.RELIGION)), TravellerProfile(), threePm)
        assertTrue(cathedral !in proposal.stops)
    }

    @Test
    fun `lo de una busqueda no es una parada`() {
        val proposal = ItineraryDesigner.plan(here, all, ItineraryRequest(minutes = 600), TravellerProfile(), threePm)
        assertTrue(bar !in proposal.stops)
    }

    @Test
    fun `con intereses va lo que encaja`() {
        val proposal = ItineraryDesigner.plan(here, all, ItineraryRequest(minutes = 240, interests = setOf(Interest.HISTORY)), TravellerProfile(), threePm)
        assertEquals(setOf(castle, wall), proposal.stops.toSet())
    }

    @Test
    fun `lo ya visitado no se repite`() {
        val profile = TravellerProfile().visited(listOf(square))
        val proposal = ItineraryDesigner.plan(here, all, ItineraryRequest(minutes = 600), profile, threePm)
        assertTrue(square !in proposal.stops)
    }

    @Test
    fun `cabe en el tiempo`() {
        val short = ItineraryDesigner.plan(here, all, ItineraryRequest(minutes = 60), TravellerProfile(), threePm)
        assertTrue(short.minutes <= 60, "dura ${short.minutes}")
        assertTrue(short.stops.isNotEmpty())
        val long = ItineraryDesigner.plan(here, all, ItineraryRequest(minutes = 600), TravellerProfile(), threePm)
        assertTrue(long.stops.size > short.stops.size)
    }

    @Test
    fun `aprovechar al maximo mete mas que ir con calma`() {
        val packed = ItineraryDesigner.plan(here, all, ItineraryRequest(minutes = 120, pace = Pace.PACKED), TravellerProfile(), threePm)
        val relaxed = ItineraryDesigner.plan(here, all, ItineraryRequest(minutes = 120, pace = Pace.RELAXED), TravellerProfile(), threePm)
        assertTrue(packed.stops.size >= relaxed.stops.size)
        assertTrue(packed.stops.size > relaxed.stops.size || packed.minutes < relaxed.minutes)
    }

    @Test
    fun `un museo dura mas que una plaza`() {
        assertTrue(ItineraryDesigner.stopMinutes(museum, Pace.NORMAL) > ItineraryDesigner.stopMinutes(square, Pace.NORMAL))
    }

    @Test
    fun `el dia que cruza la hora de comer para a comer`() {
        val day = ItineraryDesigner.plan(here, all, ItineraryRequest(dayPart = DayPart.FULL_DAY), TravellerProfile(), tenAm)
        assertTrue(day.mealAfter != null)
        val afternoon = ItineraryDesigner.plan(here, all, ItineraryRequest(dayPart = DayPart.AFTERNOON), TravellerProfile(), threePm)
        assertNull(afternoon.mealAfter)
    }

    @Test
    fun `comer pedido se respeta aunque no sea la hora`() {
        val proposal = ItineraryDesigner.plan(here, all, ItineraryRequest(minutes = 240, meal = true), TravellerProfile(), threePm)
        assertTrue(proposal.mealAfter != null)
    }

    @Test
    fun `quitar una parada acorta la duracion`() {
        val proposal = ItineraryDesigner.plan(here, all, ItineraryRequest(minutes = 600), TravellerProfile(), threePm)
        val shorter = ItineraryDesigner.without(proposal, proposal.stops.last())
        assertEquals(proposal.stops.dropLast(1), shorter.stops)
        assertTrue(shorter.minutes < proposal.minutes)
    }

    @Test
    fun `una guia de la ciudad no es una parada`() {
        val guide = place("9", "Cartagena de Indias, the Jewel of the Caribbean", 0.0, 0.0).copy(city = "Cartagena")
        val cathedral = place("10", "Cartagena Cathedral", 0.0, 0.0).copy(city = "Cartagena")
        assertTrue(PlaceKinds.isCityGuide(guide))
        assertTrue(!PlaceKinds.isCityGuide(cathedral))
        val proposal = ItineraryDesigner.plan(here, all + guide, ItineraryRequest(minutes = 600), TravellerProfile(), threePm)
        assertTrue(guide !in proposal.stops)
    }

    @Test
    fun `nombrar castillos trae el castillo`() {
        val request = requireNotNull(ItineraryRequests.parse("un itinerario esta tarde, me gustan los castillos"))
        assertEquals(setOf("castill"), request.mentions)
        val proposal = ItineraryDesigner.plan(here, all, request.copy(minutes = 60), TravellerProfile(), threePm)
        assertEquals(castle, proposal.stops.first())
    }

    @Test
    fun `lo que se evita no cuenta como mencion`() {
        assertTrue(requireNotNull(ItineraryRequests.parse("un itinerario sin iglesias")).mentions.isEmpty())
    }

    @Test
    fun `esta tarde pedida por la manana empieza a las dos y no come`() {
        assertEquals(14 * 60, ItineraryDesigner.startOf(ItineraryRequest(dayPart = DayPart.AFTERNOON), tenAm))
        assertEquals(5 * 60, ItineraryDesigner.budgetMinutes(ItineraryRequest(dayPart = DayPart.AFTERNOON), tenAm))
        assertNull(ItineraryDesigner.plan(here, all, ItineraryRequest(dayPart = DayPart.AFTERNOON), TravellerProfile(), tenAm).mealAfter)
    }

    @Test
    fun `la tarde llega hasta las siete y nunca es un rato`() {
        assertEquals(4 * 60, ItineraryDesigner.budgetMinutes(ItineraryRequest(dayPart = DayPart.AFTERNOON), threePm))
        assertEquals(90, ItineraryDesigner.budgetMinutes(ItineraryRequest(dayPart = DayPart.AFTERNOON), 18 * 60 + 30))
        assertEquals(120, ItineraryDesigner.budgetMinutes(ItineraryRequest(minutes = 120, dayPart = DayPart.AFTERNOON), threePm))
    }

    @Test
    fun `el perfil pone los gustos que no se dijeron`() {
        val profile = TravellerProfile(likes = setOf(Interest.HISTORY))
        val proposal = ItineraryDesigner.plan(here, all, ItineraryRequest(minutes = 240), profile, threePm)
        assertEquals(setOf(Interest.HISTORY), proposal.request.interests)
        assertEquals(setOf(castle, wall), proposal.stops.toSet())
    }
}
