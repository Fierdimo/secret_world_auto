package com.secretworld.auto.core.data

import android.util.Log
import com.secretworld.auto.domain.Area
import com.secretworld.auto.domain.AreaLocator
import com.secretworld.auto.domain.Bounds
import com.secretworld.auto.domain.NamedPlaceFinder
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.VoiceGrammar
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Dónde está una zona dicha de viva voz —un barrio, el centro—, por
 * OpenStreetMap.
 *
 * Por qué así, medido en Cartagena:
 * - **Mapbox no**: devolvía barrios homónimos de otros países ("Las Gaviotas" a
 *   2 600 km) o, limitado a la zona, sitios que se llaman igual (una cancha).
 * - **Photon** (el buscador de OpenStreetMap de Komoot) **primero**: tolera la
 *   ortografía del reconocedor —«las Gaviutas», «las Gabiotes» encuentran Las
 *   Gaviotas— en una sola petición de 1 a 2 s.
 * - **Nominatim** de respaldo, solo coincidencia exacta.
 * - Se probó bajar la lista de barrios de Overpass y compararla aquí: sus
 *   servidores públicos contestaban 504 o se colgaban, y la búsqueda se quedaba
 *   sin zona.
 *
 * Siempre dentro de un recuadro alrededor del coche: nadie pide "un
 * restaurante en el centro" de otra ciudad. Los servicios públicos sirven para
 * la demo; el producto necesita servidor propio o de pago.
 */
class NominatimAreas(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
) : AreaLocator, NamedPlaceFinder {

    /**
     * Sitios concretos por su nombre en OpenStreetMap. Complementa a Mapbox:
     * tiene los monumentos —el Castillo San Felipe de Barajas, que Mapbox no
     * devolvía— y los nombres tal como se dicen.
     */
    override suspend fun find(name: String, near: Coordinates, within: Bounds?, language: VoiceLanguage): List<Place> =
        photonPlaces(name, near, within, language, tag = null, limit = 6)

    override suspend fun findTagged(tag: String, query: String, near: Coordinates, within: Bounds?, language: VoiceLanguage): List<Place> =
        photonPlaces(query, near, within, language, tag = tag, limit = 15)

    private suspend fun photonPlaces(name: String, near: Coordinates, within: Bounds?, language: VoiceLanguage, tag: String?, limit: Int): List<Place> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://photon.komoot.io/api/".toHttpUrl().newBuilder()
                    .addQueryParameter("q", name)
                    .addQueryParameter("lat", near.latitude.toString())
                    .addQueryParameter("lon", near.longitude.toString())
                    .addQueryParameter("limit", limit.toString())
                    .apply { if (tag != null) addQueryParameter("osm_tag", tag) }
                    .addQueryParameter("bbox", within?.let { String.format(Locale.ROOT, "%f,%f,%f,%f", it.west, it.south, it.east, it.north) } ?: box(near))
                    .apply { if (language.code in PHOTON_LANGUAGES) addQueryParameter("lang", language.code) }
                    .build()
                val features = get(url.toString())?.let { json.parseToJsonElement(it).jsonObject["features"]?.jsonArray }.orEmpty()
                features.mapNotNull { element ->
                    val feature = element.jsonObject
                    val properties = feature["properties"]?.jsonObject ?: return@mapNotNull null
                    val label = properties["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val coordinates = feature["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
                    val lon = coordinates.getOrNull(0)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                    val lat = coordinates.getOrNull(1)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                    val street = listOfNotNull(properties["street"]?.jsonPrimitive?.content, properties["housenumber"]?.jsonPrimitive?.content)
                        .joinToString(" ").ifEmpty { null }
                    Place(
                        id = "osm:${properties["osm_type"]?.jsonPrimitive?.content}${properties["osm_id"]?.jsonPrimitive?.content}",
                        name = label,
                        position = Coordinates(lat, lon),
                        narrationSeconds = 0,
                        languages = setOf(language),
                        city = properties["city"]?.jsonPrimitive?.content,
                        kind = properties["osm_value"]?.jsonPrimitive?.content?.replace('_', ' '),
                        address = street,
                    )
                }
            }.onFailure { Log.w(TAG, "sitio «$name»: ${it.message}") }.getOrDefault(emptyList())
        }

    /** Lo encontrado se recuerda mientras la app vive: los barrios no se mueven. */
    private val found = HashMap<String, Area>()

    override suspend fun locate(area: String, near: Coordinates, language: VoiceLanguage): Area? {
        val key = String.format(Locale.ROOT, "%.1f,%.1f:%s", near.latitude, near.longitude, area)
        synchronized(found) { found[key]?.let { return it } }
        val located = withContext(Dispatchers.IO) {
            runCatching { fetch(area, near, language) }.onFailure { Log.w(TAG, "zona «$area»: ${it.message}") }.getOrNull()
        }
        Log.i(TAG, "zona «$area» → ${located?.let { "${it.name} (${it.position})" } ?: "no encontrada"}")
        // Solo lo encontrado se recuerda: un fallo de red no es "no existe".
        if (located != null) synchronized(found) { found[key] = located }
        return located
    }

    private fun fetch(area: String, near: Coordinates, language: VoiceLanguage): Area? {
        // "El centro" se busca como se llama el barrio en casi toda Latinoamérica
        // e Italia: "Centro". Si no lo hay, el centro histórico.
        val queries = if (area == VoiceGrammar.CITY_CENTRE) {
            listOf("Centro", "Centro Histórico", "Centro storico")
        } else {
            // «boca grande» es Bocagrande: si separado no aparece, junto.
            listOf(area, area.replace(" ", "")).distinct()
        }
        queries.forEach { query -> runCatching { photon(query, near, language) }.getOrNull()?.let { return it } }
        queries.forEach { query -> runCatching { nominatim(query, near, language) }.getOrNull()?.let { return it } }
        return null
    }

    private fun photon(query: String, near: Coordinates, language: VoiceLanguage): Area? {
        val url = "https://photon.komoot.io/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("lat", near.latitude.toString())
            .addQueryParameter("lon", near.longitude.toString())
            .addQueryParameter("limit", "5")
            .addQueryParameter("bbox", box(near))
            // Photon solo admite unas pocas lenguas; sin ella devuelve el nombre local.
            .apply { if (language.code in PHOTON_LANGUAGES) addQueryParameter("lang", language.code) }
            .build()
        val features = get(url.toString())?.let { json.parseToJsonElement(it).jsonObject["features"]?.jsonArray }.orEmpty()
            .map { it.jsonObject }
        // Un barrio antes que un restaurante que se llame igual.
        val best = features.firstOrNull { it.kind() in AREA_KINDS } ?: features.firstOrNull() ?: return null
        val coordinates = best["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return null
        val lon = coordinates.getOrNull(0)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val lat = coordinates.getOrNull(1)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val properties = best["properties"]?.jsonObject
        val name = properties?.get("name")?.jsonPrimitive?.content ?: query
        // Lo que ocupa el barrio: [oeste, norte, este, sur].
        val bounds = properties?.get("extent")?.jsonArray?.mapNotNull { it.jsonPrimitive.content.toDoubleOrNull() }
            ?.takeIf { it.size == 4 }
            ?.let { (west, north, east, south) -> Bounds(south = south, west = west, north = north, east = east) }
        return Area(name, Coordinates(lat, lon), bounds)
    }

    private fun JsonObject.kind(): String? = this["properties"]?.jsonObject?.get("osm_key")?.jsonPrimitive?.content

    private fun nominatim(query: String, near: Coordinates, language: VoiceLanguage): Area? {
        val url = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "1")
            .addQueryParameter("viewbox", box(near))
            .addQueryParameter("bounded", "1")
            .addQueryParameter("accept-language", language.code)
            .build()
        val hit = get(url.toString())?.let { json.parseToJsonElement(it).jsonArray.firstOrNull()?.jsonObject } ?: return null
        val lat = hit["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val lon = hit["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        // "Las Gaviotas, Cartagena de Indias, Dique, Bolívar…": el nombre es lo primero.
        val name = hit["display_name"]?.jsonPrimitive?.content?.substringBefore(',')?.trim()?.ifEmpty { null } ?: query
        return Area(name, Coordinates(lat, lon))
    }

    /**
     * El barrio y la ciudad de un punto, "Barrio (Ciudad)", por la geocodificación
     * inversa de OpenStreetMap. Es el respaldo de la del sistema, que falta donde
     * no hay servicios de Google —el emulador de Pleos—: sin ella, la cabecera
     * tomaba la primera página de Wikipedia con tipo ciudad, y en el centro de
     * Cartagena decía "Bocagrande".
     */
    suspend fun describe(at: Coordinates, language: VoiceLanguage): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://nominatim.openstreetmap.org/reverse".toHttpUrl().newBuilder()
                .addQueryParameter("lat", at.latitude.toString())
                .addQueryParameter("lon", at.longitude.toString())
                .addQueryParameter("format", "jsonv2")
                // Barrio: ni la calle ni solo la ciudad.
                .addQueryParameter("zoom", "16")
                .addQueryParameter("accept-language", language.code)
                .build()
            val address = get(url.toString())?.let { json.parseToJsonElement(it).jsonObject["address"]?.jsonObject } ?: return@runCatching null
            fun field(vararg keys: String) = keys.firstNotNullOfOrNull { key -> address[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } }
            val city = field("city", "town", "village", "municipality", "county") ?: return@runCatching null
            val area = field("suburb", "neighbourhood", "quarter", "city_district")?.takeIf { it != city }
            if (area == null) city else "$area ($city)"
        }.onFailure { Log.w(TAG, "geocodificación inversa", it) }.getOrNull()
    }

    /**
     * El recuadro de búsqueda. Locale.ROOT: con la app en italiano o español,
     * "%f" escribía "10,39" y el servidor contestaba 400.
     */
    private fun box(near: Coordinates): String = String.format(
        Locale.ROOT,
        "%f,%f,%f,%f",
        near.longitude - BOX_DEGREES, near.latitude - BOX_DEGREES,
        near.longitude + BOX_DEGREES, near.latitude + BOX_DEGREES,
    )

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            // Los servicios de OpenStreetMap exigen identificarse.
            .header("User-Agent", USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP ${response.code} de ${request.url.host}")
                return null
            }
            response.body.string()
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val TAG = "SWAreas"
        const val USER_AGENT = "SecretWorldAuto/0.1 (https://sworld.co.uk; demo)"
        /** ~33 km a cada lado: la ciudad y sus alrededores. */
        const val BOX_DEGREES = 0.3
        /** Barrios y divisiones, no calles ni negocios con el mismo nombre. */
        val AREA_KINDS = setOf("place", "boundary")
        val PHOTON_LANGUAGES = setOf("en", "de", "fr", "it")
    }
}
