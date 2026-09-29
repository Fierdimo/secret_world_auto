package com.secretworld.auto.core.data

import com.secretworld.auto.domain.Coordinates
import java.util.Locale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import com.secretworld.auto.domain.Bounds
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

    override suspend fun search(query: String, near: Coordinates, limit: Int, category: String?, within: Bounds?): List<Place> {
        val token = credentials.mapboxToken() ?: return emptyList()
        val proximity = "${near.longitude},${near.latitude}"
        // En un barrio grande, por cuartos: una sola petición devuelve como mucho
        // 25, y se amontonan en una parte. Así salen repartidos por todo él.
        val boxes = when {
            within == null -> listOf(null)
            within.sizeMeters > SPLIT_3_ABOVE_M -> within.split(3)
            within.sizeMeters > TILE_ABOVE_M -> within.split(2)
            else -> listOf(within)
        }
        val perBox = if (within == null) limit + 2 else minOf(MAX_PER_REQUEST, limit / boxes.size + 2)
        val features = coroutineScope {
            boxes.map { box ->
                async(Dispatchers.IO) {
                    val bbox = box?.let { String.format(Locale.ROOT, "%f,%f,%f,%f", it.west, it.south, it.east, it.north) }
                    // Cada parte, con su propio centro de referencia: con el del
                    // barrio, cada una devolvía su esquina más cercana a él y todo
                    // se amontonaba alrededor del mismo punto.
                    val proximity = box?.centre?.let { "${it.longitude},${it.latitude}" } ?: proximity
                    runCatching {
                        // Con categoría, los más cercanos de ese tipo; sin ella, el texto.
                        if (category != null) {
                            api.category(category, proximity, token, limit = perBox, language = language.code, bbox = bbox)
                        } else {
                            api.forward(query, proximity, token, limit = perBox, language = language.code, bbox = bbox)
                        }
                    }.getOrNull()?.features.orEmpty()
                }
            }.awaitAll()
                // Por turnos, una de cada parte: si luego se recorta al límite, se
                // recorta por igual en todo el barrio y no siempre por los bordes.
                .let { perPart -> (0 until (perPart.maxOfOrNull { it.size } ?: 0)).flatMap { i -> perPart.mapNotNull { it.getOrNull(i) } } }
        }
        val ordered = features
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
            .filter { near.distanceTo(it.position) <= PlaceSearch.MAX_DISTANCE_M }
        // Alrededor de un punto, lo más cercano; en un barrio, repartido por todo él.
        return if (within == null) ordered.sortedBy { near.distanceTo(it.position) }.take(limit) else ordered.take(limit)
    }

    override suspend fun findByName(name: String, near: Coordinates, within: Bounds?): List<Place> {
        val token = credentials.mapboxToken() ?: return emptyList()
        val bbox = (within ?: around(near)).let { String.format(Locale.ROOT, "%f,%f,%f,%f", it.west, it.south, it.east, it.north) }
        val response = runCatching {
            withContext(Dispatchers.IO) {
                // Negocios, direcciones y calles: "Hotel Caribe" y "Calle 30 # 5".
                api.forward(name, "${near.longitude},${near.latitude}", token, types = "poi,address,street", limit = 5, language = language.code, bbox = bbox)
            }
        }.getOrElse { return emptyList() }
        // En el orden del buscador, que es el de parecido.
        return response.features.mapNotNull { feature ->
            val at = feature.geometry.coordinates.toCoordinates() ?: return@mapNotNull null
            val label = feature.properties.name.trim().ifEmpty { return@mapNotNull null }
            Place(
                id = "mapbox:${feature.properties.mapbox_id}",
                name = label,
                position = at,
                narrationSeconds = 0,
                languages = setOf(language),
                city = feature.properties.place_formatted,
                summary = null,
                kind = feature.properties.poi_category.firstOrNull() ?: feature.properties.feature_type,
                address = feature.properties.address,
            )
        }.distinctBy { it.id }
    }

    /** El recuadro donde se busca un nombre sin zona: la ciudad y alrededores. */
    private fun around(near: Coordinates) = Bounds(
        south = near.latitude - NAME_BOX_DEGREES, west = near.longitude - NAME_BOX_DEGREES,
        north = near.latitude + NAME_BOX_DEGREES, east = near.longitude + NAME_BOX_DEGREES,
    )
}

/** Un nombre sin zona se busca en ~40 km alrededor: la ciudad, no el país. */
private const val NAME_BOX_DEGREES = 0.35

/** Por encima de esto un barrio se busca por partes: 2 × 2, y 3 × 3 si es grande. */
private const val TILE_ABOVE_M = 1_200.0
private const val SPLIT_3_ABOVE_M = 2_000.0
/** Lo más que devuelve el buscador por petición. */
private const val MAX_PER_REQUEST = 25

/** `[lon, lat]` de GeoJSON a coordenadas del dominio. */
private fun List<Double>.toCoordinates(): Coordinates? =
    if (size >= 2) Coordinates(latitude = this[1], longitude = this[0]) else null
