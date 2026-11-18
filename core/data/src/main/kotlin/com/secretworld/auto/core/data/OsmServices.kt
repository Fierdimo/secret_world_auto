package com.secretworld.auto.core.data

import android.util.Log
import com.secretworld.auto.domain.Bounds
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.PlaceSearch
import com.secretworld.auto.domain.Route
import com.secretworld.auto.domain.Router
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/*
 * Rutas y búsqueda con OpenStreetMap, sin Mapbox: no lo tenemos licenciado
 * (ver docs/NAVIGATION-AND-MAP-DECISIONS.md). Los servidores públicos
 * —OSRM, Nominatim, Photon— sirven para probar; el producto necesita servidor
 * propio o de pago, y sus normas de uso piden pocas peticiones e identificarse.
 */

private val osmClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
}

private val osmJson = Json { ignoreUnknownKeys = true }

private const val OSM_TAG = "SWOsm"
private const val OSM_USER_AGENT = "SecretWorldAuto/0.1 (https://sworld.co.uk; demo)"

private fun osmGet(url: String): String? {
    val request = Request.Builder().url(url).header("User-Agent", OSM_USER_AGENT).build()
    return osmClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            Log.w(OSM_TAG, "HTTP ${response.code} de ${request.url.host}")
            return null
        }
        response.body.string()
    }
}

/**
 * Rutas por OSRM. Lo que se usa de ellas es el trazado y la duración —para
 * dibujar el itinerario y repartir el tiempo—. **Sin maniobras**: la guía es del
 * navegador instalado, ni se dicen ni se enseñan, así que ni se piden.
 */
class OsmRouter : Router {

    override suspend fun route(from: Coordinates, to: Place): Route? = withContext(Dispatchers.IO) {
        runCatching {
            val url = String.format(
                Locale.ROOT,
                "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson&steps=false",
                from.longitude, from.latitude, to.position.longitude, to.position.latitude,
            )
            val body = osmGet(url) ?: return@runCatching null
            val best = osmJson.parseToJsonElement(body).jsonObject["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@runCatching null
            val points = best["geometry"]?.jsonObject?.get("coordinates")?.jsonArray.orEmpty().mapNotNull { it.jsonArray.toCoordinates() }
            if (points.size < 2) return@runCatching null
            Route(
                destination = to,
                points = points,
                distanceMeters = best.number("distance") ?: 0.0,
                durationSeconds = best.number("duration") ?: 0.0,
            )
        }.onFailure { Log.w(OSM_TAG, "ruta a ${to.name}: ${it.message}") }.getOrNull()
    }
}

/**
 * Búsqueda de sitios por lo que son —"restaurantes", "gasolina"— en
 * OpenStreetMap.
 *
 * - Con una categoría que OSM tiene como tipo, **Nominatim** con su búsqueda
 *   estructurada (`amenity=restaurant`) dentro del recuadro: en el centro de
 *   Cartagena devolvía 40 restaurantes, siete gasolineras, 23 farmacias.
 * - Sin ella —"pizza", "shawarma"—, texto libre en **Photon**, que tolera la
 *   ortografía del reconocedor.
 * - Un nombre concreto —"Hotel Caribe", "Calle 30"—, Nominatim en texto libre;
 *   a la vez la app pregunta a Photon ([NominatimAreas.find]) y elige el mejor.
 */
class OsmPlaceSearch(
    private val areas: NominatimAreas,
    private val language: VoiceLanguage = VoiceLanguage.SPANISH,
) : PlaceSearch {

    override suspend fun search(query: String, near: Coordinates, limit: Int, category: String?, within: Bounds?): List<Place> {
        val box = within ?: around(near, CATEGORY_BOX_DEGREES)
        val type = category?.let { OSM_TYPES[it] }
        val found = if (type != null) {
            nominatim(type, box, limit)
        } else {
            // Una categoría sin tipo en OSM se busca por cómo se dice: "pizza".
            val text = category?.let { TEXT_FOR[it] } ?: query
            areas.find(text, near, box, language)
        }
        val ordered = found
            .distinctBy { it.id }
            .filter { near.distanceTo(it.position) <= PlaceSearch.MAX_DISTANCE_M }
        return if (within == null) ordered.sortedBy { near.distanceTo(it.position) }.take(limit) else ordered.take(limit)
    }

    override suspend fun findByName(name: String, near: Coordinates, within: Bounds?): List<Place> =
        nominatim(within ?: around(near, NAME_BOX_DEGREES), NAME_LIMIT, what = name) { addQueryParameter("q", name) }

    /** Una categoría: la búsqueda estructurada por tipo. */
    private suspend fun nominatim(type: String, box: Bounds, limit: Int): List<Place> =
        nominatim(box, limit, what = type) { addQueryParameter("amenity", type) }

    private suspend fun nominatim(box: Bounds, limit: Int, what: String, query: okhttp3.HttpUrl.Builder.() -> Unit): List<Place> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
                .apply(query)
                .addQueryParameter("viewbox", String.format(Locale.ROOT, "%f,%f,%f,%f", box.west, box.north, box.east, box.south))
                .addQueryParameter("bounded", "1")
                .addQueryParameter("format", "jsonv2")
                .addQueryParameter("addressdetails", "1")
                .addQueryParameter("limit", limit.coerceIn(1, NOMINATIM_MAX).toString())
                .addQueryParameter("accept-language", language.code)
                .build()
            val body = osmGet(url.toString()) ?: return@runCatching emptyList()
            (osmJson.parseToJsonElement(body) as? JsonArray).orEmpty().mapNotNull { element ->
                val hit = element.jsonObject
                val lat = hit.text("lat")?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = hit.text("lon")?.toDoubleOrNull() ?: return@mapNotNull null
                val address = hit["address"] as? JsonObject
                val street = listOfNotNull(address?.text("road"), address?.text("house_number")).joinToString(" ").ifEmpty { null }
                // Un aparcamiento o un cajero sin nombre: la calle donde está.
                val name = hit.text("name")?.takeIf { it.isNotBlank() }
                    ?: street
                    ?: hit.text("display_name")?.substringBefore(',')?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                Place(
                    id = "osm:${hit.text("osm_type")}${hit.text("osm_id")}",
                    name = name,
                    position = Coordinates(lat, lon),
                    narrationSeconds = 0,
                    languages = setOf(language),
                    city = address?.let { it.text("city") ?: it.text("town") ?: it.text("village") },
                    summary = null,
                    kind = hit.text("type")?.replace('_', ' '),
                    address = street,
                )
            }
        }.onFailure { Log.w(OSM_TAG, "búsqueda «$what»: ${it.message}") }.getOrDefault(emptyList())
    }

    private fun around(near: Coordinates, degrees: Double) = Bounds(
        south = near.latitude - degrees, west = near.longitude - degrees,
        north = near.latitude + degrees, east = near.longitude + degrees,
    )

    private companion object {
        /** Lo más que devuelve Nominatim por petición. */
        const val NOMINATIM_MAX = 40
        /** Sin zona, una categoría se busca en ~5 km alrededor. */
        const val CATEGORY_BOX_DEGREES = 0.045
        /** Un nombre sin zona, en ~40 km: la ciudad, no el país. */
        const val NAME_BOX_DEGREES = 0.35
        /** Por nombre, los primeros por parecido bastan. */
        const val NAME_LIMIT = 5

        /** Las categorías de la app, con su tipo en OpenStreetMap. */
        val OSM_TYPES = mapOf(
            "restaurant" to "restaurant",
            "gas_station" to "fuel",
            "parking_lot" to "parking",
            "hotel" to "hotel",
            "pharmacy" to "pharmacy",
            "atm" to "atm",
            "bar" to "bar",
            "bakery" to "bakery",
            "cinema" to "cinema",
            "hospital" to "hospital",
            "police_station" to "police",
            "supermarket" to "supermarket",
            "viewpoint" to "viewpoint",
            "auto_repair" to "car_repair",
            "beach" to "beach",
            "cafe" to "cafe",
        )

        /** Las que OSM no tiene como tipo propio: se buscan por cómo se llaman. */
        val TEXT_FOR = mapOf(
            "pizza_restaurant" to "pizza",
            "seafood_restaurant" to "mariscos",
            "convenience_store" to "tienda",
        )
    }
}

private fun JsonObject.text(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content

private fun JsonObject.number(key: String): Double? = text(key)?.toDoubleOrNull()

/** `[lon, lat]` de GeoJSON a coordenadas del dominio. */
private fun JsonArray.toCoordinates(): Coordinates? {
    val lon = getOrNull(0)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
    val lat = getOrNull(1)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
    return Coordinates(latitude = lat, longitude = lon)
}
