package com.secretworld.auto.core.data

import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    /** Un sitio con coordenadas en la Wikipedia de [wiki]. */
    private data class Hit(val wiki: VoiceLanguage, val pageId: String, val title: String, val position: Coordinates, val distance: Double)

    /**
     * **Los mismos sitios en los tres idiomas.** Cada Wikipedia tiene artículos
     * distintos sobre una ciudad —medido en Cartagena: la española traía la
     * Ciénaga de la Virgen y la inglesa el Castillo San Felipe—, y con solo la
     * del idioma elegido, cambiar de idioma cambiaba la lista. Se busca en las
     * tres, se junta por el elemento de Wikidata, que es el mismo en todas, y
     * del artículo del idioma elegido salen el nombre, el texto y la foto. Si
     * en ese idioma no hay, el de otro, y la narración lo traduce.
     */
    private suspend fun fetch(position: Coordinates, language: VoiceLanguage, radiusMeters: Int, limit: Int): Around = coroutineScope {
        val found = VoiceLanguage.entries
            .map { wiki -> async { wiki to runCatching { nearby(wiki, position, radiusMeters) }.getOrNull() } }
            .awaitAll()
            .mapNotNull { (wiki, result) -> result?.let { wiki to it } }
            .toMap()
        val city = found[language]?.second ?: found.values.firstNotNullOfOrNull { it.second }
        val hits = found.values.flatMap { it.first }
        if (hits.isEmpty()) return@coroutineScope Around(emptyList(), city)

        // Cada artículo, a su elemento de Wikidata; los que no tienen, sueltos.
        val entityOf = hits.groupBy { it.wiki }.flatMap { (wiki, ofWiki) ->
            entities(wiki, ofWiki.map { it.pageId }).map { (pageId, entity) -> (wiki to pageId) to entity }
        }.toMap()
        val chosen = hits
            .groupBy { hit -> entityOf[hit.wiki to hit.pageId] ?: "${hit.wiki.code}:${hit.pageId}" }
            .map { (key, same) -> key to same.minBy { it.distance } }
            .sortedBy { (_, hit) -> hit.distance }
            .take(limit)

        // El título de cada uno en cada idioma, según Wikidata.
        val titles = sitelinks(chosen.map { it.first }.filter { it.startsWith("Q") })
        val preference = listOf(language) + VoiceLanguage.entries.filter { it != language }

        // Texto y foto, primero en el idioma elegido; lo que falte o se quede
        // corto, en el siguiente.
        val pages = mutableMapOf<String, Pair<VoiceLanguage, Page>>()
        preference.forEach { wiki ->
            val wanted = chosen.mapNotNull { (key, hit) ->
                if (key in pages) return@mapNotNull null
                val title = titles[key]?.get(wiki) ?: hit.title.takeIf { hit.wiki == wiki } ?: return@mapNotNull null
                key to title
            }
            if (wanted.isEmpty()) return@forEach
            val byTitle = pagesFor(wiki, wanted.map { it.second })
            wanted.forEach { (key, title) ->
                byTitle[title]?.takeIf { it.extract.length >= MIN_TEXT_CHARS }?.let { pages[key] = wiki to it }
            }
        }

        // La foto, de cualquier idioma: el artículo español del Castillo San
        // Felipe no la tiene y el inglés sí.
        val missing = chosen.filter { (key, _) -> pages[key]?.second?.image == null }
        val images = mutableMapOf<String, String>()
        preference.forEach { wiki ->
            val wanted = missing.mapNotNull { (key, _) -> if (key in images) null else titles[key]?.get(wiki)?.let { key to it } }
            if (wanted.isEmpty()) return@forEach
            val byTitle = pagesFor(wiki, wanted.map { it.second })
            wanted.forEach { (key, title) -> byTitle[title]?.image?.let { images[key] = it } }
        }

        val places = chosen.mapNotNull { (key, hit) ->
            val (wiki, page) = pages[key] ?: return@mapNotNull null
            Place(
                id = "wiki:$key",
                name = spokenTitle(page.title),
                position = hit.position,
                narrationSeconds = (page.extract.split(' ').size / WORDS_PER_SECOND).toInt().coerceAtLeast(10),
                languages = setOf(wiki),
                city = city,
                imageUrl = page.image ?: images[key],
                summary = page.extract,
                contentLanguage = wiki,
            )
        }
        Around(places, city)
    }

    /** Lo que tiene coordenadas cerca en la Wikipedia de [wiki], ya filtrado por tipo, y la ciudad. */
    private fun nearby(wiki: VoiceLanguage, position: Coordinates, radiusMeters: Int): Pair<List<Hit>, String?> {
        val body = get(
            api(wiki).newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("list", "geosearch")
                .addQueryParameter("gscoord", "${position.latitude}|${position.longitude}")
                .addQueryParameter("gsradius", radiusMeters.coerceAtMost(10_000).toString())
                .addQueryParameter("gslimit", "50")
                .addQueryParameter("gsprop", "type")
                .addQueryParameter("format", "json")
                .build().toString(),
        )
        val all = json.parseToJsonElement(body).jsonObject["query"]?.jsonObject?.get("geosearch")?.jsonArray.orEmpty().map { it.jsonObject }
        val city = all.firstOrNull { it["type"]?.jsonPrimitive?.content == "city" }?.get("title")?.jsonPrimitive?.content
        val hits = all
            .filter { it["type"]?.jsonPrimitive?.content in PLACE_TYPES }
            .filterNot { hit -> NOT_A_STOP.any { hit["title"]!!.jsonPrimitive.content.contains(it, ignoreCase = true) } }
            .map { hit ->
                Hit(
                    wiki = wiki,
                    pageId = hit["pageid"]!!.jsonPrimitive.content,
                    title = hit["title"]!!.jsonPrimitive.content,
                    position = Coordinates(hit["lat"]!!.jsonPrimitive.content.toDouble(), hit["lon"]!!.jsonPrimitive.content.toDouble()),
                    distance = hit["dist"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: Double.MAX_VALUE,
                )
            }
        return hits to city
    }

    /** El elemento de Wikidata de cada página, por su id. */
    private fun entities(wiki: VoiceLanguage, pageIds: List<String>): Map<String, String> =
        pageIds.chunked(MAX_IDS).flatMap { chunk ->
            val body = get(
                api(wiki).newBuilder()
                    .addQueryParameter("action", "query")
                    .addQueryParameter("prop", "pageprops")
                    .addQueryParameter("ppprop", "wikibase_item")
                    .addQueryParameter("pageids", chunk.joinToString("|"))
                    .addQueryParameter("format", "json")
                    .build().toString(),
            )
            json.parseToJsonElement(body).jsonObject["query"]?.jsonObject?.get("pages")?.jsonObject.orEmpty().mapNotNull { (id, page) ->
                page.jsonObject["pageprops"]?.jsonObject?.get("wikibase_item")?.jsonPrimitive?.content?.let { id to it }
            }
        }.toMap()

    /** El título de cada elemento en cada una de las tres Wikipedias, si lo tiene. */
    private fun sitelinks(entities: List<String>): Map<String, Map<VoiceLanguage, String>> =
        entities.chunked(MAX_IDS).flatMap { chunk ->
            val body = runCatching {
                get(
                    WIKIDATA.toHttpUrl().newBuilder()
                        .addQueryParameter("action", "wbgetentities")
                        .addQueryParameter("ids", chunk.joinToString("|"))
                        .addQueryParameter("props", "sitelinks")
                        .addQueryParameter("sitefilter", VoiceLanguage.entries.joinToString("|") { "${it.code}wiki" })
                        .addQueryParameter("format", "json")
                        .build().toString(),
                )
            }.getOrNull() ?: return@flatMap emptyList()
            json.parseToJsonElement(body).jsonObject["entities"]?.jsonObject.orEmpty().map { (entity, data) ->
                val links = data.jsonObject["sitelinks"]?.jsonObject.orEmpty()
                entity to VoiceLanguage.entries.mapNotNull { wiki ->
                    links["${wiki.code}wiki"]?.jsonObject?.get("title")?.jsonPrimitive?.content?.let { wiki to it }
                }.toMap()
            }
        }.toMap()

    /** Lo que hace falta de un artículo: su título, la introducción y la foto. */
    private data class Page(val title: String, val extract: String, val image: String?)

    /** Introducciones y fotos de [titles] en la Wikipedia de [wiki], por título. */
    private fun pagesFor(wiki: VoiceLanguage, titles: List<String>): Map<String, Page> =
        titles.chunked(MAX_EXTRACTS).flatMap { chunk ->
            val body = runCatching {
                get(
                    api(wiki).newBuilder()
                        .addQueryParameter("action", "query")
                        .addQueryParameter("prop", "extracts|pageimages")
                        .addQueryParameter("exintro", "1")
                        .addQueryParameter("explaintext", "1")
                        .addQueryParameter("exlimit", MAX_EXTRACTS.toString())
                        .addQueryParameter("pithumbsize", "640")
                        .addQueryParameter("titles", chunk.joinToString("|"))
                        .addQueryParameter("format", "json")
                        .build().toString(),
                )
            }.getOrNull() ?: return@flatMap emptyList()
            json.parseToJsonElement(body).jsonObject["query"]?.jsonObject?.get("pages")?.jsonObject.orEmpty().values.mapNotNull { page ->
                val o = page.jsonObject
                val title = o["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                title to Page(
                    title = title,
                    extract = o["extract"]?.jsonPrimitive?.content?.trim().orEmpty(),
                    image = o["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content,
                )
            }
        }.toMap()

    private fun api(wiki: VoiceLanguage) = "https://${wiki.code}.wikipedia.org/w/api.php".toHttpUrl()

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

    /**
     * "Torre del Reloj (Cartagena de Indias)" se dice "Torre del Reloj", y
     * "Cartagena Cathedral, Colombia", "Cartagena Cathedral": lo que aclara
     * dónde está sobra, que ya se está ahí.
     */
    private fun spokenTitle(title: String): String = title.substringBefore(" (").substringBefore(", ").trim()

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val USER_AGENT = "SecretWorldAuto/0.1 (https://sworld.co.uk; bench build)"
        const val RADIUS_M = 10_000
        const val LIMIT = 20
        /** Páginas o elementos por petición, lo que admite la API. */
        const val MAX_IDS = 50
        /** Introducciones por petición: la API no da más de 20 de una vez. */
        const val MAX_EXTRACTS = 20
        const val WIKIDATA = "https://www.wikidata.org/w/api.php"
        const val MIN_TEXT_CHARS = 120
        /** Ritmo de lectura de la voz sintética, para estimar la duración. */
        const val WORDS_PER_SECOND = 2.4
        val PLACE_TYPES = setOf("landmark", "mountain", "forrest", "forest", "isle", "waterbody", "glacier", "river")
        /** Con artículo y coordenadas, pero no un sitio al que llevar a nadie. */
        val NOT_A_STOP = listOf(
            "Hotel", "Estadio", "Coliseo", "Aeropuerto", "Universidad", "Colegio", "Hospital", "Centro comercial", "Stadium", "Airport", "University", "Hospital", "Mall",
            // Pasó en un sitio, pero no es un sitio: la batalla tiene coordenadas.
            "Batalla", "Battle", "Battaglia", "Asedio", "Siege", "Assedio",
        )
    }
}
