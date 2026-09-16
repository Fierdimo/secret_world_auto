package com.secretworld.auto.core.data

import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.PlaceSearch
import com.secretworld.auto.domain.VoiceLanguage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

/**
 * Lo que se consultó una vez, guardado por zona para cuando no hay cobertura.
 *
 * Un coche pierde la red en túneles, en la montaña y en media Colombia. Los
 * sitios no cambian de sitio: lo que se trajo de Wikipedia o del buscador hace
 * una hora sigue valiendo, y vale más que un "sin conexión". Cada respuesta se
 * guarda por **celda** —un cuadrado de unos 2 km— y consulta, y se sirve desde
 * disco si la red falla o si es reciente.
 *
 * No es un mapa sin conexión: las teselas y las rutas siguen necesitando red.
 * Eso lo resuelve el navegador del vehículo; aquí, con Mapbox por REST, no.
 */
class PlaceCache(root: File) {

    private val dir = File(root, "places").apply { mkdirs() }

    /**
     * Devuelve lo guardado para esta zona y consulta si es reciente; si no,
     * pide a [fetch] y guarda. Si [fetch] falla o devuelve vacío, lo guardado
     * aunque sea viejo: mejor un sitio de la semana pasada que ninguno.
     */
    suspend fun around(
        position: Coordinates,
        key: String,
        language: VoiceLanguage,
        fetch: suspend () -> List<Place>,
    ): List<Place> {
        val file = File(dir, "${cell(position)}_${language.code}_${key.slug()}.json")
        val stored = read(file)
        if (stored != null && System.currentTimeMillis() - file.lastModified() < FRESH_MS) return stored

        val fresh = runCatching { fetch() }.getOrDefault(emptyList())
        if (fresh.isNotEmpty()) {
            write(file, fresh)
            return fresh
        }
        return stored ?: emptyList()
    }

    /** Un [PlaceSearch] que pasa por aquí. */
    fun caching(delegate: PlaceSearch, language: VoiceLanguage): PlaceSearch = object : PlaceSearch {
        override suspend fun search(query: String, near: Coordinates, limit: Int): List<Place> =
            around(near, "q:${query.lowercase().trim()}", language) { delegate.search(query, near, limit) }.take(limit)
    }

    private fun read(file: File): List<Place>? = runCatching {
        if (!file.isFile) return null
        json.decodeFromString(ListSerializer(StoredPlace.serializer()), file.readText()).map { it.toPlace() }
    }.getOrNull()

    private fun write(file: File, places: List<Place>) {
        runCatching { file.writeText(json.encodeToString(ListSerializer(StoredPlace.serializer()), places.map { StoredPlace.of(it) })) }
    }

    /** Celda de ~2 km: dos decimales de grado son ~1,1 km; se redondea a 0,02. */
    private fun cell(position: Coordinates): String {
        val lat = (position.latitude / CELL_DEGREES).roundToInt()
        val lon = (position.longitude / CELL_DEGREES).roundToInt()
        return "${lat}_$lon"
    }

    private fun String.slug(): String = map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(60)

    /** La ficha como se guarda. Es el [Place] entero; el dominio no serializa. */
    @Serializable
    private data class StoredPlace(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val narrationSeconds: Int,
        val languages: List<String>,
        val city: String? = null,
        val imageUrl: String? = null,
        val summary: String? = null,
        val contentLanguage: String? = null,
        val kind: String? = null,
        val address: String? = null,
    ) {
        fun toPlace() = Place(
            id = id,
            name = name,
            position = Coordinates(lat, lon),
            narrationSeconds = narrationSeconds,
            languages = languages.mapNotNull { VoiceLanguage.fromTag(it) }.toSet(),
            city = city,
            imageUrl = imageUrl,
            summary = summary,
            contentLanguage = contentLanguage?.let { VoiceLanguage.fromTag(it) },
            kind = kind,
            address = address,
        )

        companion object {
            fun of(place: Place) = StoredPlace(
                id = place.id,
                name = place.name,
                lat = place.position.latitude,
                lon = place.position.longitude,
                narrationSeconds = place.narrationSeconds,
                languages = place.languages.map { it.code },
                city = place.city,
                imageUrl = place.imageUrl,
                summary = place.summary,
                contentLanguage = place.contentLanguage?.code,
                kind = place.kind,
                address = place.address,
            )
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val CELL_DEGREES = 0.02
        /** Una semana: los sitios no se mueven, los restaurantes a veces cierran. */
        const val FRESH_MS = 7L * 24 * 3600 * 1000
    }
}
