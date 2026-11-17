package com.secretworld.auto.domain

/**
 * Decide qué se escucha a continuación (PLAN §3.1).
 *
 * Reglas, en este orden:
 *  - no repetir lo ya escuchado en la sesión;
 *  - descartar lo que no tenga narración en el idioma activo (SPEC RF-1.6),
 *    salvo que se pueda traducir;
 *  - preferir lo que está por delante del vehículo sobre lo que ya se pasó;
 *  - descartar lo que queda demasiado lejos para importar;
 *  - **preferir el silencio al relleno**: si nada cualifica, la cola va vacía.
 */
class TourQueue(
    private val maxDistanceMeters: Double = 5_000.0,
    /** Semiángulo del cono "por delante". Fuera de él, el lugar se considera pasado. */
    private val forwardConeDegrees: Double = 100.0,
) {

    fun next(
        vehicle: VehicleState,
        catalogue: List<Place>,
        alreadyHeard: Set<String>,
        language: VoiceLanguage,
        limit: Int = 5,
        /**
         * Si la narración se traduce al idioma activo. Entonces un sitio con
         * texto solo en otro idioma también vale: sin esto, en inglés la voz no
         * veía la Ciénaga de la Virgen, que sí estaba en la pantalla.
         */
        translates: Boolean = false,
    ): List<Place> {
        require(limit > 0) { "limit debe ser positivo" }

        return catalogue
            .asSequence()
            .filter { it.id !in alreadyHeard }
            .filter { translates || it.hasNarrationIn(language) }
            .map { it to vehicle.position.distanceTo(it.position) }
            .filter { (_, distance) -> distance <= maxDistanceMeters }
            .filter { (place, _) -> isAhead(vehicle, place) }
            .sortedBy { (_, distance) -> distance }
            .map { (place, _) -> place }
            .take(limit)
            .toList()
    }

    /**
     * Sin rumbo fiable —vehículo parado— todo cuenta como "por delante": no hay
     * dirección respecto a la que descartar nada.
     */
    private fun isAhead(vehicle: VehicleState, place: Place): Boolean {
        val heading = vehicle.headingDegrees ?: return true
        val bearing = vehicle.position.bearingTo(place.position)
        val diff = Math.abs(((bearing - heading + 540.0) % 360.0) - 180.0)
        return diff <= forwardConeDegrees
    }
}
