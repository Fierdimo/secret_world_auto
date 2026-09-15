package com.secretworld.auto.core.data

import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Lugares reales alrededor, de Wikipedia, **con su texto en el idioma**.
 *
 * Es la fuente que hace que una ciudad tenga algo que ver más allá del
 * catálogo editorial de Secret World, que en la mayoría de ciudades tiene tres
 * fichas. Lo que tiene artículo en Wikipedia es, por definición, lo que merece
 * una parada; y el artículo trae la introducción ya escrita en el idioma de la
 * voz y una foto. Comprobado en Cartagena: 22 monumentos en 10 km, frente a 3
 * del catálogo.
 *
 * Se filtra por tipo —monumentos, montes, islas, bosques— para dejar fuera
 * batallas, provincias, aeropuertos y universidades, que también tienen
 * coordenadas y artículo pero no son sitios a los que llevar a nadie.
 *
 * Licencia: CC BY-SA. Los textos se atribuyen (docs/OPEN-QUESTIONS.md).
 */
class WikipediaPlaces(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
) {

    /** Lo que se encontró alrededor, y cómo se llama la ciudad según Wikipedia. */
    data class Around(val places: List<Place>, val city: String?)

    suspend fun around(position: Coordinates, language: VoiceLanguage, radiusMeters: Int = RADIUS_M, limit: Int = LIMIT): Around =
        withContext(Dispatchers.IO) {
            runCatching { fetch(position, language, radiusMeters, limit) }.getOrElse { Around(emptyList(), null) }
        }

    private fun fetch(position: Coordinates, language: VoiceLanguage, radiusMeters: Int, limit: Int): Around {
        val base = "https://${language.code}.wikipedia.org/w/api.php"
        val nearby = get(
            base.toHttpUrl().newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("list", "geosearch")
                .addQueryParameter("gscoord", "${position.latitude}|${position.longitude}")
                .addQueryParameter("gsradius", radiusMeters.coerceAtMost(10_000).toString())
                .addQueryParameter("gslimit", "50")
                .addQueryParameter("gsprop", "type")
                .addQueryParameter("format", "json")
                .build().toString(),
        )
        val hits = json.parseToJsonElement(nearby).jsonObject["query"]?.jsonObject?.get("geosearch")?.jsonArray.orEmpty()
            .map { it.jsonObject }

        val city = hits.firstOrNull { it["type"]?.jsonPrimitive?.content == "city" }?.get("title")?.jsonPrimitive?.content

        val candidates = hits
            .filter { it["type"]?.jsonPrimitive?.content in PLACE_TYPES }
            .filterNot { hit -> NOT_A_STOP.any { hit["title"]!!.jsonPrimitive.content.contains(it, ignoreCase = true) } }
            .take(limit)
        if (candidates.isEmpty()) return Around(emptyList(), city)

        // Introducciones y fotos, en un solo viaje: la API admite 20 páginas por
        // petición y eso son todas las que se van a usar.
        val ids = candidates.map { it["pageid"]!!.jsonPrimitive.content }
        val details = get(
            base.toHttpUrl().newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("prop", "extracts|pageimages")
                .addQueryParameter("exintro", "1")
                .addQueryParameter("explaintext", "1")
                .addQueryParameter("exlimit", "20")
                .addQueryParameter("pithumbsize", "640")
                .addQueryParameter("pageids", ids.take(20).joinToString("|"))
                .addQueryParameter("format", "json")
                .build().toString(),
        )
        val pages = json.parseToJsonElement(details).jsonObject["query"]?.jsonObject?.get("pages")?.jsonObject.orEmpty()

        val places = candidates.mapNotNull { hit ->
            val id = hit["pageid"]!!.jsonPrimitive.content
            val page = pages[id]?.jsonObject
            val extract = page?.get("extract")?.jsonPrimitive?.content?.trim().orEmpty()
            if (extract.length < MIN_TEXT_CHARS) return@mapNotNull null
            val title = hit["title"]!!.jsonPrimitive.content
            Place(
                id = "wiki:${language.code}:$id",
                name = spokenTitle(title),
                position = Coordinates(hit["lat"]!!.jsonPrimitive.content.toDouble(), hit["lon"]!!.jsonPrimitive.content.toDouble()),
                narrationSeconds = (extract.split(' ').size / WORDS_PER_SECOND).toInt().coerceAtLeast(10),
                languages = setOf(language),
                city = city,
                imageUrl = page?.get("thumbnail")?.jsonObject?.get("source")?.jsonPrimitive?.content,
                summary = extract,
                contentLanguage = language,
            )
        }
        return Around(places, city)
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            // Wikipedia pide identificarse; sin esto, limita o rechaza.
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} de Wikipedia" }
            return response.body.string()
        }
    }

    /** "Torre del Reloj (Cartagena de Indias)" se dice "Torre del Reloj". */
    private fun spokenTitle(title: String): String = title.substringBefore(" (").trim()

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val USER_AGENT = "SecretWorldAuto/0.1 (https://sworld.co.uk; bench build)"
        const val RADIUS_M = 10_000
        const val LIMIT = 20
        const val MIN_TEXT_CHARS = 120
        /** Ritmo de lectura de la voz sintética, para estimar la duración. */
        const val WORDS_PER_SECOND = 2.4
        val PLACE_TYPES = setOf("landmark", "mountain", "forrest", "forest", "isle", "waterbody", "glacier", "river")
        /** Con artículo y coordenadas, pero no un sitio al que llevar a nadie. */
        val NOT_A_STOP = listOf("Hotel", "Estadio", "Coliseo", "Aeropuerto", "Universidad", "Colegio", "Hospital", "Centro comercial", "Stadium", "Airport", "University", "Hospital", "Mall")
    }
}
