package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La guía se prueba sobre una ruta recta hacia el norte de dos kilómetros, con
 * una maniobra a mitad: es geometría, y con geometría simple se ve si acierta.
 */
class NavigationGuideTest {

    private val origin = Coordinates(41.8800, 12.4800)
    private val forum = Place("1", "The Roman Forum", north(2000.0), 60, setOf(VoiceLanguage.ENGLISH))

    /** Un punto cada 100 m hacia el norte. */
    private val line = (0..20).map { north(it * 100.0) }
    private val route = Route(
        destination = forum,
        points = line,
        distanceMeters = 2000.0,
        durationSeconds = 240.0,
        steps = listOf(
            Route.Step("Turn right onto Via Sacra", north(1000.0), 1000.0),
            Route.Step("You have arrived", north(2000.0), 0.0),
        ),
    )
    private val guide = NavigationGuide(route)

    @Test
    fun `al principio queda todo y la siguiente maniobra es la primera`() {
        val p = guide.progress(at(north(0.0)))
        assertEquals(2000.0, p.remainingMeters, 5.0)
        assertEquals(240.0, p.remainingSeconds, 1.0)
        assertEquals("Turn right onto Via Sacra", p.nextStep?.instruction)
        assertEquals(1000.0, p.metersToNextStep, 5.0)
        assertFalse(p.arrived)
        assertFalse(p.offRoute)
    }

    @Test
    fun `cerca de la maniobra toca anunciarla, y pasada ya no`() {
        val before = guide.progress(at(north(800.0)))
        assertEquals(200.0, before.metersToNextStep, 5.0)

        val after = guide.progress(at(north(1100.0)))
        assertEquals("You have arrived", after.nextStep?.instruction, "la primera ya quedó atrás")
    }

    @Test
    fun `se llega cuando queda menos que el umbral`() {
        assertTrue(guide.progress(at(north(1990.0))).arrived)
        assertFalse(guide.progress(at(north(1900.0))).arrived)
    }

    @Test
    fun `fuera de la linea, hay que recalcular`() {
        val east = Coordinates(north(500.0).latitude, north(500.0).longitude + 0.002) // ~165 m al este
        val p = guide.progress(at(east))
        assertTrue(p.offRoute)
        assertTrue(p.offRouteMeters > NavigationGuide.OFF_ROUTE_METERS)

        val nearLine = Coordinates(north(500.0).latitude, north(500.0).longitude + 0.0003) // ~25 m
        assertFalse(guide.progress(at(nearLine)).offRoute)
    }

    @Test
    fun `una ruta sin pasos no anuncia nada`() {
        val bare = NavigationGuide(route.copy(steps = emptyList()))
        val p = bare.progress(at(north(900.0)))
        assertNull(p.nextStep)
        assertEquals(1100.0, p.remainingMeters, 5.0)
    }

    private fun north(meters: Double) = Coordinates(origin.latitude + meters / 111_320.0, origin.longitude)

    private fun at(position: Coordinates) = VehicleState(position, headingDegrees = 0.0, speedMetersPerSecond = 10.0, parked = false)
}
