package com.secretworld.auto.domain

/**
 * Decide **cuándo** empezar a narrar (SPEC RF-3.3).
 *
 * Narrar un lugar que el conductor ya ha pasado se considera un fallo, no una
 * degradación. Así que el disparo se calcula hacia atrás desde el momento en que
 * el vehículo llegará al lugar: hay que empezar con la antelación suficiente para
 * que la narración termine —o esté bien avanzada— cuando se pase por delante.
 */
class NarrationTrigger(
    /** Margen extra para que el usuario aún vea el sitio al terminar de oírlo. */
    private val leadSeconds: Int = 10,
    /** Por debajo de esta velocidad no se anticipa: el vehículo casi no avanza. */
    private val minimumSpeedMetersPerSecond: Double = 1.0,
) {

    /**
     * Distancia a la que hay que empezar a hablar, en metros. Depende de la
     * velocidad: a 120 km/h hace falta empezar mucho antes que a 30.
     */
    fun triggerDistanceMeters(place: Place, speedMetersPerSecond: Double): Double {
        val speed = speedMetersPerSecond.coerceAtLeast(minimumSpeedMetersPerSecond)
        return speed * (place.narrationSeconds + leadSeconds)
    }

    /** ¿Toca empezar a narrar [place] ahora mismo? */
    fun shouldStart(vehicle: VehicleState, place: Place): Boolean {
        if (vehicle.parked) return false
        val distance = vehicle.position.distanceTo(place.position)
        return distance <= triggerDistanceMeters(place, vehicle.speedMetersPerSecond)
    }

    /**
     * ¿Ya no llegamos? Si a la velocidad actual la narración no cabe antes de
     * pasar por delante, es mejor no empezarla.
     */
    fun isTooLate(vehicle: VehicleState, place: Place): Boolean {
        if (vehicle.speedMetersPerSecond < minimumSpeedMetersPerSecond) return false
        val secondsToArrival = vehicle.position.distanceTo(place.position) / vehicle.speedMetersPerSecond
        return secondsToArrival < place.narrationSeconds
    }
}
