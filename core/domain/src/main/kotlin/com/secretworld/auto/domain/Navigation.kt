package com.secretworld.auto.domain

/**
 * Una ruta calculada hasta un lugar.
 *
 * Es dominio puro: quién la calcula —Mapbox en el banco de pruebas, el
 * navegador del vehículo cuando Pleos publique su librería— es cosa de la capa
 * de datos. Aquí solo hay geometría, pasos y números.
 */
data class Route(
    val destination: Place,
    /** La línea a dibujar, de principio a fin. */
    val points: List<Coordinates>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    /** Las maniobras, en orden. Puede estar vacía si el proveedor no las da. */
    val steps: List<Step> = emptyList(),
) {
    /** Una maniobra: qué hacer y dónde. */
    data class Step(
        /** Frase para decir en voz alta: "Turn left onto Via del Corso". */
        val instruction: String,
        /** Dónde se ejecuta la maniobra. */
        val at: Coordinates,
        /** Longitud del tramo que **sigue** a la maniobra, hasta la siguiente. */
        val distanceMeters: Double,
    )
}

/**
 * Quien sabe buscar sitios por lo que son: "shawarma", "gasolinera", "bar".
 *
 * Es otra fuente distinta del catálogo y de Wikipedia: aquellos son lo que
 * merece una parada; esto es lo que hace falta en el camino. Sin texto que
 * narrar, con dirección y categoría, y siempre alrededor del coche.
 */
interface PlaceSearch {
    /**
     * @param category la categoría canónica del buscador ("restaurant",
     *   "gas_station"), si se sabe: con ella se piden los más cercanos de ese
     *   tipo en vez de buscar el texto. Ver [VoiceGrammar.searchCategoryOf].
     */
    suspend fun search(query: String, near: Coordinates, limit: Int = 5, category: String? = null): List<Place>

    companion object {
        /**
         * Nada más lejos que esto se ofrece nunca. El buscador de texto puede
         * devolver coincidencias en otro continente, y "a 10 112 km" no es una
         * respuesta para quien va conduciendo.
         */
        const val MAX_DISTANCE_M = 50_000.0
    }
}

/** Quien sabe calcular rutas. */
interface Router {
    /** `null` si no hay ruta o no hay con qué calcularla (sin red, sin clave). */
    suspend fun route(from: Coordinates, to: Place): Route?
}

/**
 * Sigue la ruta según se mueve el vehículo.
 *
 * Lo que un navegador hace por debajo, en lo mínimo que el producto necesita:
 * cuánto queda, cuál es la siguiente maniobra y a qué distancia, si se ha
 * llegado, y si el vehículo se ha salido —que es cuando hay que recalcular—.
 * Todo por geometría simple sobre la línea de la ruta; no hay mapa aquí.
 */
class NavigationGuide(private val route: Route) {

    data class Progress(
        /** Índice del punto de la ruta más cercano al vehículo. */
        val nearestIndex: Int,
        /** Distancia del vehículo a la línea de la ruta. */
        val offRouteMeters: Double,
        val remainingMeters: Double,
        val remainingSeconds: Double,
        /** La siguiente maniobra por delante, si hay pasos. */
        val nextStep: Route.Step?,
        val metersToNextStep: Double,
    ) {
        val arrived: Boolean get() = remainingMeters < ARRIVAL_METERS
        val offRoute: Boolean get() = offRouteMeters > OFF_ROUTE_METERS

        /** Si toca anunciar la maniobra: cerca de ella y aún no pasada. */
        val shouldAnnounce: Boolean get() = nextStep != null && metersToNextStep in 0.0..ANNOUNCE_METERS
    }

    /** Longitud acumulada hasta cada punto, para no recorrer la línea en cada posición. */
    private val cumulative: DoubleArray = DoubleArray(route.points.size).also { acc ->
        for (i in 1 until route.points.size) {
            acc[i] = acc[i - 1] + route.points[i - 1].distanceTo(route.points[i])
        }
    }

    private val total: Double get() = cumulative.lastOrNull() ?: 0.0

    fun progress(vehicle: VehicleState): Progress {
        val position = vehicle.position
        if (route.points.isEmpty()) {
            return Progress(0, 0.0, route.distanceMeters, route.durationSeconds, route.steps.firstOrNull(), 0.0)
        }

        var nearest = 0
        var best = Double.MAX_VALUE
        route.points.forEachIndexed { index, point ->
            val d = position.distanceTo(point)
            if (d < best) {
                best = d
                nearest = index
            }
        }

        val travelled = cumulative[nearest]
        val remaining = (total - travelled).coerceAtLeast(0.0)
        // El tiempo que queda, proporcional a la distancia: sin tráfico ni
        // límites por tramo, es lo más honesto que se puede decir.
        val remainingSeconds = if (total > 0) route.durationSeconds * remaining / total else 0.0

        // La siguiente maniobra es la primera cuyo punto está por delante de
        // donde va el vehículo, con un margen para no "pasarla" por la
        // imprecisión del GPS.
        val next = route.steps.firstOrNull { step ->
            distanceAlong(step.at) >= travelled - STEP_SLACK_METERS
        }
        val toNext = next?.let { (distanceAlong(it.at) - travelled).coerceAtLeast(0.0) } ?: 0.0

        return Progress(nearest, best, remaining, remainingSeconds, next, toNext)
    }

    /** Distancia recorrida por la ruta hasta el punto de la línea más cercano a [target]. */
    private fun distanceAlong(target: Coordinates): Double {
        var nearest = 0
        var best = Double.MAX_VALUE
        route.points.forEachIndexed { index, point ->
            val d = target.distanceTo(point)
            if (d < best) {
                best = d
                nearest = index
            }
        }
        return cumulative[nearest]
    }

    companion object {
        /** A menos de esto del final, se ha llegado. */
        const val ARRIVAL_METERS = 40.0
        /** A más de esto de la línea, el vehículo va por otro sitio: recalcular. */
        const val OFF_ROUTE_METERS = 60.0
        /** Se anuncia una maniobra cuando queda menos de esto. */
        const val ANNOUNCE_METERS = 250.0
        private const val STEP_SLACK_METERS = 15.0
    }
}
