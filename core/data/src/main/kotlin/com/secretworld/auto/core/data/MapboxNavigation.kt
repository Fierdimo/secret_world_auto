package com.secretworld.auto.core.data

import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.PlaceSearch
import com.secretworld.auto.domain.Route
import com.secretworld.auto.domain.Router
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rutas por Mapbox Directions. El puente del banco de pruebas; ver [MapboxApi].
 */
class MapboxRouter(
    private val api: MapboxApi,
    private val credentials: MapCredentials,
    /** Idioma de las instrucciones de maniobra, que se dicen en voz alta. */
    private val language: VoiceLanguage = VoiceLanguage.ENGLISH,
) : Router {

    override suspend fun route(from: Coordinates, to: Place): Route? {
        val token = credentials.mapboxToken() ?: return null
        val coordinates = "${from.longitude},${from.latitude};${to.position.longitude},${to.position.latitude}"
        val response = runCatching {
            withContext(Dispatchers.IO) { api.directions(coordinates, token, language = language.code) }
        }.getOrElse { return null }
        val best = response.routes.firstOrNull() ?: return null
        val points = best.geometry?.coordinates.orEmpty().mapNotNull { it.toCoordinates() }
        if (points.size < 2) return null

        val steps = best.legs.flatMap { leg ->
            leg.steps.mapNotNull { step ->
                val at = step.maneuver.location.toCoordinates() ?: return@mapNotNull null
                Route.Step(step.maneuver.instruction, at, step.distance)
            }
        }
        return Route(
            destination = to,
            points = points,
            distanceMeters = best.distance,
            durationSeconds = best.duration,
            steps = steps,
        )
    }
}

/**
 * Búsqueda de sitios por lo que son, por Mapbox. Ver [MapboxApi.forward].
 */
class MapboxPlaceSearch(
    private val api: MapboxApi,
    private val credentials: MapCredentials,
    private val language: VoiceLanguage = VoiceLanguage.SPANISH,
) : PlaceSearch {

    override suspend fun search(query: String, near: Coordinates, limit: Int): List<Place> {
        val token = credentials.mapboxToken() ?: return emptyList()
        val response = runCatching {
            withContext(Dispatchers.IO) { api.forward(query, "${near.longitude},${near.latitude}", token, limit = limit + 2, language = language.code) }
        }.getOrElse { return emptyList() }
        return response.features
            .mapNotNull { feature ->
                val at = feature.geometry.coordinates.toCoordinates() ?: return@mapNotNull null
                val name = feature.properties.name.trim().ifEmpty { return@mapNotNull null }
                Place(
                    id = "mapbox:${feature.properties.mapbox_id}",
                    name = name,
                    position = at,
                    narrationSeconds = 0,
                    languages = setOf(language),
                    city = feature.properties.place_formatted,
                    summary = null,
                    kind = feature.properties.poi_category.firstOrNull(),
                    address = feature.properties.address,
                )
            }
            .distinctBy { it.id }
            .sortedBy { near.distanceTo(it.position) }
            .take(limit)
    }
}

/** `[lon, lat]` de GeoJSON a coordenadas del dominio. */
private fun List<Double>.toCoordinates(): Coordinates? =
    if (size >= 2) Coordinates(latitude = this[1], longitude = this[0]) else null
