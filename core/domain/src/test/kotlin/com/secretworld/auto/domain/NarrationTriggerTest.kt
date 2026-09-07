package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NarrationTriggerTest {

    private val origin = Coordinates(41.9028, 12.4964)

    private fun placeAt(meters: Double, seconds: Int = 75) = Place(
        id = "p",
        name = "p",
        position = Coordinates(origin.latitude + meters / 111_320.0, origin.longitude),
        narrationSeconds = seconds,
        languages = setOf(VoiceLanguage.ENGLISH),
    )

    private fun driving(speed: Double) = VehicleState(origin, 0.0, speed, parked = false)

    @Test
    fun `a mas velocidad se dispara antes`() {
        val trigger = NarrationTrigger()
        val place = placeAt(1000.0)

        val lento = trigger.triggerDistanceMeters(place, speedMetersPerSecond = 8.0)
        val rapido = trigger.triggerDistanceMeters(place, speedMetersPerSecond = 33.0)

        assertTrue(rapido > lento, "a 120 km/h hay que empezar mucho antes que a 30")
    }

    @Test
    fun `dispara cuando el lugar entra en el alcance`() {
        val trigger = NarrationTrigger()
        // A 25 m/s con 75 s de narracion y 10 s de margen: 2125 m de alcance.
        val place = placeAt(2000.0)

        assertTrue(trigger.shouldStart(driving(25.0), place))
    }

    @Test
    fun `no dispara si todavia esta lejos`() {
        val trigger = NarrationTrigger()
        val place = placeAt(4000.0)

        assertFalse(trigger.shouldStart(driving(25.0), place))
    }

    @Test
    fun `no dispara con el vehiculo detenido`() {
        val trigger = NarrationTrigger()
        val place = placeAt(100.0)
        val parado = VehicleState(origin, null, 0.0, parked = true)

        assertFalse(trigger.shouldStart(parado, place))
    }

    @Test
    fun `detecta que ya no da tiempo`() {
        val trigger = NarrationTrigger()
        // A 30 m/s quedan 10 s hasta el lugar, pero la narracion dura 75.
        val place = placeAt(300.0, seconds = 75)

        assertTrue(trigger.isTooLate(driving(30.0), place))
    }

    @Test
    fun `si da tiempo no lo marca como tarde`() {
        val trigger = NarrationTrigger()
        val place = placeAt(3000.0, seconds = 75)

        assertFalse(trigger.isTooLate(driving(30.0), place))
    }
}
