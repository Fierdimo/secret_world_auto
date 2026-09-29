package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItineraryPlannerTest {

    // En línea recta hacia el este desde el coche, a ~1, 2 y 3 km.
    private val here = Coordinates(10.40, -75.55)
    private val near = stop("near", -75.54, "Castillo de San Felipe, fortaleza colonial del siglo XVII")
    private val middle = stop("middle", -75.53, "Catedral de Santa Catalina, iglesia del siglo XVI")
    private val far = stop("far", -75.52, "Playa de Bocagrande, spiaggia sulla baia")

    @Test
    fun `el orden empieza por la mas cercana y sigue por la mas cercana a esa`() {
        assertEquals(listOf(near, middle, far), ItineraryPlanner.order(here, listOf(far, near, middle)))
    }

    @Test
    fun `deduce que es cada lugar de su nombre y su ficha`() {
        assertTrue(Interest.HISTORY in PlaceKinds.interestsOf(near))
        assertTrue(Interest.RELIGION in PlaceKinds.interestsOf(middle))
        assertTrue(Interest.NATURE in PlaceKinds.interestsOf(far))
        assertTrue(Interest.NATURE !in PlaceKinds.interestsOf(near))
    }

    @Test
    fun `genera segun los intereses`() {
        val religious = ItineraryPlanner.generate(here, listOf(far, near, middle), ItineraryPreferences(setOf(Interest.RELIGION)))
        assertEquals(listOf(middle), religious)
        val all = ItineraryPlanner.generate(here, listOf(far, near, middle), ItineraryPreferences())
        assertEquals(listOf(near, middle, far), all)
    }

    @Test
    fun `no se pasa del tiempo disponible`() {
        // Cada parada son ~17 minutos (visita, narración y un poco de coche).
        val short = ItineraryPlanner.generate(here, listOf(far, near, middle), ItineraryPreferences(minutes = 40))
        assertEquals(listOf(near, middle), short)
        assertTrue(ItineraryPlanner.estimateMinutes(here, short) <= 40)
    }

    @Test
    fun `lo que salio de una busqueda no es parada de tour`() {
        val bar = stop("bar", -75.545, "Bar en la plaza").copy(kind = "bar")
        assertEquals(listOf(near), ItineraryPlanner.generate(here, listOf(bar, near), ItineraryPreferences(minutes = 25)))
    }

    @Test
    fun `el tour avanza parada a parada y acaba en la ultima`() {
        val tour = Tour(listOf(near, middle))
        assertEquals(near, tour.current)
        assertEquals(middle, tour.next)
        val second = tour.advanced()!!
        assertEquals(middle, second.current)
        assertTrue(second.isLast)
        assertNull(second.advanced())
    }

    private fun stop(id: String, longitude: Double, summary: String) = Place(
        id = id,
        name = summary.substringBefore(','),
        position = Coordinates(10.40, longitude),
        narrationSeconds = 60,
        languages = setOf(VoiceLanguage.SPANISH),
        summary = summary,
    )
}
