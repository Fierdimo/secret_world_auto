package com.secretworld.auto.core.data

import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Una imagen para cada sitio que no traiga la suya: es lo que da vida a la
 * lista y al mapa, y lo que deja reconocer el sitio al llegar.
 *
 * Por orden de fiabilidad:
 * 1. Un sitio para ver: la foto de **su** artículo de Wikipedia —buscado por
 *    el nombre y comprobado que está ahí mismo, no un homónimo de otra
 *    ciudad—, en el idioma de la app y si no en inglés.
 * 2. Si no hay artículo, la foto de Wikimedia Commons hecha más cerca del
 *    punto, a unos pasos.
 * 3. Un negocio, o lo que no tenga foto: la vista de satélite del sitio. No es
 *    una foto del restaurante, pero enseña el edificio y lo que lo rodea, que
 *    es lo que orienta; una foto de lo que hay al lado diría algo falso.
 *
 * Lo encontrado se recuerda en [memory]: no se busca dos veces.
 */
class PlaceImages(
    private val memory: Memory,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {

    /** Dónde se guardan las imágenes encontradas, por id del sitio. */
    interface Memory {
        fun get(id: String): String?
        fun put(id: String, url: String)
    }

    /**
     * La imagen de [place], o `null` si no hay ninguna y tampoco satélite. La que
     * traiga se comprueba antes: las del catálogo a veces no existen, y entonces
     * el sitio se quedaba con su inicial.
     */
    suspend fun find(place: Place, language: VoiceLanguage, mapboxToken: String?): String? = withContext(Dispatchers.IO) {
        memory.get(KEY + place.id)?.let { return@withContext it }
        place.imageUrl?.takeIf(::loads)?.let { own ->
            memory.put(KEY + place.id, own)
            return@withContext own
        }
        val sight = place.kind == null
        val found = (if (sight) photoOf(place, language) else null) ?: satellite(place.position, mapboxToken)
        found?.also { memory.put(KEY + place.id, it) }
    }

    private fun photoOf(place: Place, language: VoiceLanguage): String? =
        runCatching { articlePhoto(place, language.code) }.getOrNull()
            ?: runCatching { articlePhoto(place, "en") }.getOrNull().takeIf { language != VoiceLanguage.ENGLISH }
            ?: runCatching { commonsNear(place.position) }.getOrNull()

    /** La foto del artículo que se llama así y está ahí: a menos de [ARTICLE_RADIUS_M]. */
    private fun articlePhoto(place: Place, lang: String): String? {
        val url = "https://$lang.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrsearch", place.name)
            .addQueryParameter("gsrlimit", "5")
            .addQueryParameter("prop", "pageimages|coordinates|pageprops")
            .addQueryParameter("ppprop", "wikibase_item")
            .addQueryParameter("pithumbsize", THUMB.toString())
            .addQueryParameter("format", "json")
            .build().toString()
        val pages = json.parseToJsonElement(get(url)).jsonObject["query"]?.jsonObject?.get("pages")?.jsonObject ?: return null
        val page = pages.values.map { it.jsonObject }
            .sortedBy { it["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: Int.MAX_VALUE }
            .firstOrNull { page -> page.near(place.position, ARTICLE_RADIUS_M) }
            ?: return null
        // Primero la imagen que Wikidata da como la del sitio: suele ser una foto
        // *de* él. La del artículo es a menudo una vista *desde* él —"Cartagena
        // desde el cerro de La Popa"—, que no deja reconocerlo al llegar.
        val item = page["pageprops"]?.jsonObject?.get("wikibase_item")?.jsonPrimitive?.content
        return item?.let { runCatching { wikidataImage(it) }.getOrNull() } ?: page.thumbnail()
    }

    /** La imagen (P18) del elemento de Wikidata, ya en miniatura. */
    private fun wikidataImage(item: String): String? {
        val url = "https://www.wikidata.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "wbgetclaims")
            .addQueryParameter("entity", item)
            .addQueryParameter("property", "P18")
            .addQueryParameter("format", "json")
            .build().toString()
        val file = json.parseToJsonElement(get(url)).jsonObject["claims"]?.jsonObject?.get("P18")?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("mainsnak")?.jsonObject?.get("datavalue")?.jsonObject?.get("value")
            ?.jsonPrimitive?.content ?: return null
        return "https://commons.wikimedia.org/wiki/Special:FilePath/".toHttpUrl().newBuilder()
            .addPathSegment(file.replace(' ', '_'))
            .addQueryParameter("width", THUMB.toString())
            .build().toString()
    }

    /** La foto de Commons hecha más cerca del punto, que no sea un mapa ni un logo. */
    private fun commonsNear(at: Coordinates): String? {
        val url = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("generator", "geosearch")
            .addQueryParameter("ggsnamespace", "6")
            .addQueryParameter("ggscoord", "${at.latitude}|${at.longitude}")
            .addQueryParameter("ggsradius", COMMONS_RADIUS_M.toString())
            .addQueryParameter("ggslimit", "8")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url")
            .addQueryParameter("iiurlwidth", THUMB.toString())
            .addQueryParameter("format", "json")
            .build().toString()
        val pages = json.parseToJsonElement(get(url)).jsonObject["query"]?.jsonObject?.get("pages")?.jsonObject ?: return null
        return pages.values.map { it.jsonObject }
            .sortedBy { it["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: Int.MAX_VALUE }
            .filterNot { page -> NOT_A_PHOTO.any { page["title"]?.jsonPrimitive?.content.orEmpty().contains(it, ignoreCase = true) } }
            .firstNotNullOfOrNull { page ->
                page["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject?.get("thumburl")?.jsonPrimitive?.content
            }
    }

    /** La vista de satélite del sitio, cuadrada, sin logos encima. */
    private fun satellite(at: Coordinates, token: String?): String? {
        if (token.isNullOrBlank()) return null
        return "https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v12/static/" +
            "${at.longitude},${at.latitude},$SATELLITE_ZOOM,0/${SATELLITE_PX}x$SATELLITE_PX@2x" +
            "?access_token=$token&attribution=false&logo=false"
    }

    /** Si la imagen responde: el primer byte basta. */
    private fun loads(url: String): Boolean = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Range", "bytes=0-0").build()
        client.newCall(request).execute().use { it.code == 200 || it.code == 206 }
    }.getOrDefault(false)

    private fun JsonObject.thumbnail(): String? = this["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content

    private fun JsonObject.near(at: Coordinates, meters: Double): Boolean {
        val point = this["coordinates"]?.jsonArray?.firstOrNull()?.jsonObject ?: return false
        val lat = point["lat"]?.jsonPrimitive?.doubleOrNull ?: return false
        val lon = point["lon"]?.jsonPrimitive?.doubleOrNull ?: return false
        return at.distanceTo(Coordinates(lat, lon)) <= meters
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return response.body.string()
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val USER_AGENT = "SecretWorldAuto/0.1 (https://sworld.co.uk; bench build)"
        const val THUMB = 480
        /** Cambia cuando cambia cómo se eligen: lo guardado con el criterio anterior no vale. */
        const val KEY = "v2:"
        const val ARTICLE_RADIUS_M = 1_500.0
        const val COMMONS_RADIUS_M = 120
        const val SATELLITE_ZOOM = 17.5
        const val SATELLITE_PX = 240
        /** Ficheros de Commons que no son una foto del sitio. */
        val NOT_A_PHOTO = listOf(".svg", "map", "mapa", "logo", "plano", "escudo", "flag", "bandera")
    }
}
