package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TourQueueTest {

    private val origin = Coordinates(41.9028, 12.4964) // Roma

    private fun place(
        id: String,
        northMeters: Double = 0.0,
        eastMeters: Double = 0.0,
        languages: Set<VoiceLanguage> = setOf(VoiceLanguage.ENGLISH, VoiceLanguage.ITALIAN),
        seconds: Int = 75,
    ): Place {
        val dLat = northMeters / 111_320.0
        val dLon = eastMeters / (111_320.0 * Math.cos(Math.toRadians(origin.latitude)))
        return Place(id, id, Coordinates(origin.latitude + dLat, origin.longitude + dLon), seconds, languages)
    }

    private fun driving(headingDegrees: Double? = 0.0, speed: Double = 25.0) =
        VehicleState(origin, headingDegrees, speed, parked = false)

    @Test
    fun `ordena por cercania`() {
        val queue = TourQueue()
        val lejos = place("lejos", northMeters = 3000.0)
        val cerca = place("cerca", northMeters = 500.0)

        val result = queue.next(driving(), listOf(lejos, cerca), emptySet(), VoiceLanguage.ITALIAN)

        assertEquals(listOf("cerca", "lejos"), result.map { it.id })
    }

    @Test
    fun `no repite lo ya escuchado`() {
        val queue = TourQueue()
        val a = place("a", northMeters = 400.0)
        val b = place("b", northMeters = 800.0)

        val result = queue.next(driving(), listOf(a, b), setOf("a"), VoiceLanguage.ENGLISH)

        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun `descarta lo que no tiene narracion en el idioma activo`() {
        val queue = TourQueue()
        val soloIngles = place("en", northMeters = 300.0, languages = setOf(VoiceLanguage.ENGLISH))

        val result = queue.next(driving(), listOf(soloIngles), emptySet(), VoiceLanguage.ITALIAN)

        assertTrue(result.isEmpty(), "un lugar sin voz en el idioma activo no entra en la cola")
    }

    @Test
    fun `descarta lo que queda detras del vehiculo`() {
        val queue = TourQueue()
        val delante = place("delante", northMeters = 1000.0)
        val detras = place("detras", northMeters = -1000.0)

        // Rumbo norte: lo que está al sur ya se ha pasado.
        val result = queue.next(driving(headingDegrees = 0.0), listOf(delante, detras), emptySet(), VoiceLanguage.ENGLISH)

        assertEquals(listOf("delante"), result.map { it.id })
    }

    @Test
    fun `sin rumbo fiable no descarta por direccion`() {
        val queue = TourQueue()
        val norte = place("norte", northMeters = 900.0)
        val sur = place("sur", northMeters = -900.0)

        val parado = VehicleState(origin, headingDegrees = null, speedMetersPerSecond = 0.0, parked = true)
        val result = queue.next(parado, listOf(norte, sur), emptySet(), VoiceLanguage.ENGLISH)

        assertEquals(2, result.size)
    }

    @Test
    fun `prefiere el silencio al relleno`() {
        val queue = TourQueue()
        val demasiadoLejos = place("lejisimos", northMeters = 50_000.0)

        val result = queue.next(driving(), listOf(demasiadoLejos), emptySet(), VoiceLanguage.ENGLISH)

        assertTrue(result.isEmpty(), "si nada cualifica, la cola va vacia")
    }
}
