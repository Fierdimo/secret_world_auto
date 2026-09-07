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
) : NarrationTextSource {

    /** Qué origen sirvió los últimos datos, para que la interfaz pueda ser honesta. */
    enum class Source { LIVE, FALLBACK }

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

        val result = runCatching {
            withContext(Dispatchers.IO) {
                api.placesNearby(
                    PlacesRequest(
                        lat = position.latitude,
                        long = position.longitude,
                        maxRange = radiusKm,
                        lang = language.code,
                    ),
                )
            }
        }.map { response ->
            val places = response.data
                .mapNotNull { PlaceMapper.toDomain(it, language) }
                .deduplicate()
            if (places.isEmpty()) {
                fallback("el catálogo no devolvió lugares utilizables aquí")
            } else {
                Result(places, Source.LIVE)
            }
        }.getOrElse { error ->
            fallback(error.message ?: error::class.simpleName ?: "error de red")
        }

        result.places.forEach { place -> place.summary?.let { texts[place.id] = it } }
        lastKey = key
        lastResult = result
        return result
    }

    private fun fallback(reason: String) =
        Result(CuratedCatalogue.places.deduplicate(), Source.FALLBACK, reason)

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
    }
}
