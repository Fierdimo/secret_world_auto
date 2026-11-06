package com.secretworld.auto.core.data

import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.NarrationTextSource
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * De dónde salen los lugares.
 *
 * La regla que gobierna esta clase: **nunca dejar la pantalla vacía**. Un coche
 * pierde cobertura en túneles, en montaña y en garajes, y una app de viaje que se
 * queda en blanco justo donde el usuario está viajando no sirve. Por eso, ante
 * cualquier fallo, se cae al catálogo de respaldo y **se dice** —degradar en
 * silencio sería peor (misma regla que SPEC RF-2.6).
 */
class CatalogueRepository(
    private val api: SecretWorldApi,
    /**
     * Lugares reales alrededor, de Wikipedia, con su texto. Se **suman** al
     * catálogo editorial: sin ellos una ciudad tiene tres fichas y poco que ver.
     */
    private val wikipedia: WikipediaPlaces? = null,
    /** Lo consultado, por zona, para cuando no hay cobertura. */
    private val cache: PlaceCache? = null,
) : NarrationTextSource {

    /**
     * Qué origen sirvió los últimos datos, para que la interfaz pueda ser honesta.
     *
     * [POI] significa que no hubo catálogo y lo que hay viene de Wikipedia.
     */
    enum class Source { LIVE, FALLBACK, POI }

    data class Result(
        val places: List<Place>,
        val source: Source,
        val failure: String? = null,
    )

    private val texts = mutableMapOf<String, String>()
    private var lastKey: String? = null
    private var lastResult: Result? = null

    suspend fun nearby(
        position: Coordinates,
        radiusKm: Int = DEFAULT_RADIUS_KM,
        language: VoiceLanguage = VoiceLanguage.ENGLISH,
    ): Result {
        // Redondear la clave evita repetir la llamada cada vez que el coche avanza
        // unos metros, que es constantemente.
        val key = "%.2f,%.2f,%d,%s".format(position.latitude, position.longitude, radiusKm, language.code)
        lastResult?.takeIf { key == lastKey }?.let { return it }

        val fetched = cached(position, "catalogue", language) {
            withContext(Dispatchers.IO) {
                api.placesNearby(
                    PlacesRequest(
                        lat = position.latitude,
                        long = position.longitude,
                        maxRange = radiusKm,
                        lang = language.code,
                    ),
                )
            }.data.mapNotNull { PlaceMapper.toDomain(it, language) }.deduplicate()
        }
        val result = fetched
            .map { places -> if (places.isEmpty()) fallback("el catálogo no devolvió lugares utilizables aquí") else Result(places, Source.LIVE) }
            .getOrElse { error -> fallback(error.message ?: error::class.simpleName ?: "error de red") }
            .let { catalogue -> withWikipedia(catalogue, position, language) }

        result.places.forEach { place -> place.summary?.let { texts[place.id] = it } }
        // El respaldo no se recuerda: si no, al volver la red se servía otra vez
        // él en vez de pedir el catálogo de verdad.
        if (result.source != Source.FALLBACK) {
            lastKey = key
            lastResult = result
        }
        return result
    }

    private fun fallback(reason: String) =
        Result(CuratedCatalogue.places.deduplicate(), Source.FALLBACK, reason)

    /**
     * Pasa por la caché de zona si la hay. Sin caché, la llamada tal cual, y
     * su fallo es el fallo.
     */
    private suspend fun cached(position: Coordinates, key: String, language: VoiceLanguage, fetch: suspend () -> List<Place>): kotlin.Result<List<Place>> {
        val store = cache ?: return runCatching { fetch() }
        var failure: Throwable? = null
        val places = store.around(position, key, language) {
            runCatching { fetch() }.onFailure { failure = it }.getOrThrow()
        }
        return if (places.isEmpty() && failure != null) kotlin.Result.failure(failure!!) else kotlin.Result.success(places)
    }

    /**
     * Suma lo de Wikipedia a lo del catálogo, sin repetir lo que ya está.
     *
     * Lo del catálogo va primero: es contenido editorial y es lo que el producto
     * vende. Si el catálogo no tenía nada de aquí —el respaldo es Roma, y el
     * coche puede estar en cualquier parte—, se queda solo Wikipedia y se dice.
     */
    private suspend fun withWikipedia(catalogue: Result, position: Coordinates, language: VoiceLanguage): Result {
        val wiki = wikipedia ?: return catalogue
        val around = cache?.let { store ->
            // Wikipedia también por zona: la ciudad no se mueve de sitio.
            val places = store.around(position, "wikipedia", language) { wiki.around(position, language).places }
            WikipediaPlaces.Around(places, places.firstNotNullOfOrNull { it.city })
        } ?: wiki.around(position, language)
        if (around.places.isEmpty()) return catalogue

        val nearEnough = catalogue.places.filter { position.distanceTo(it.position) < NEAR_ENOUGH_METERS }
        val known = nearEnough.map { normalise(it.name) }
        val extra = around.places
            .filterNot { wikiPlace -> known.any { it.contains(normalise(wikiPlace.name)) || normalise(wikiPlace.name).contains(it) } }
            // El catálogo trae ciudad; Wikipedia la sabe aparte. Que todos digan la misma.
            .map { if (it.city == null) it.copy(city = nearEnough.firstOrNull()?.city ?: around.city) else it }
        val merged = (nearEnough + extra).sortedBy { position.distanceTo(it.position) }
        return if (nearEnough.isEmpty()) {
            Result(merged, Source.POI, catalogue.failure)
        } else {
            Result(merged, catalogue.source, catalogue.failure)
        }
    }

    private fun normalise(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.split(' ').filter { it.length > 3 }.joinToString(" ")

    /**
     * El catálogo tiene **varias fichas del mismo monumento** —el Coliseo aparece
     * más de una vez con textos distintos—, algo natural en un catálogo editorial
     * y desastroso en una lista de coche: el conductor ve "Colosseum" dos veces y
     * cree que la app está rota.
     *
     * Se queda la primera de cada nombre, que es la más cercana porque el API
     * ordena por distancia.
     */
    private fun List<Place>.deduplicate(): List<Place> =
        distinctBy { it.name.lowercase().trim() }

    /**
     * El texto de narración es el mismo que muestra la ficha. Que sean el mismo no
     * es pereza: si lo que se oye y lo que se lee difieren, el usuario cree que la
     * app se ha equivocado.
     */
    override fun textFor(place: Place, language: VoiceLanguage): String? =
        place.summary ?: texts[place.id]

    private companion object {
        const val DEFAULT_RADIUS_KM = 25
        /** Más lejos que esto, un lugar no es "de alrededor" para nadie. */
        const val NEAR_ENOUGH_METERS = 50_000.0
    }
}
