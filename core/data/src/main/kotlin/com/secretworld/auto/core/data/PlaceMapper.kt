package com.secretworld.auto.core.data

import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.VoiceLanguage

/**
 * De la respuesta del backend al dominio.
 *
 * Tres reglas duras:
 *
 * - **Sin coordenadas no hay lugar**: no se puede narrar algo que no se sabe dónde está.
 * - **Sin texto tampoco**, porque el producto entero es la narración.
 * - **Sin ciudad tampoco.** Esta no es obvia y vale la pena explicarla: el
 *   catálogo mezcla lugares con artículos de marketing —"Secret World vs
 *   Wanderlog", "Best AI Trip Planner in Rome"— que llegan por el mismo endpoint.
 *   Medido sobre una muestra real de Roma, **todos los artículos venían sin
 *   ciudad** y 28 de 30 lugares con ella. Narrarle a un conductor la comparativa
 *   de un producto sería absurdo, así que la ciudad hace de filtro.
 */
object PlaceMapper {

    fun toDomain(dto: PlaceDto, language: VoiceLanguage): Place? {
        val lat = dto.address?.lat ?: return null
        val lon = dto.address.lon ?: return null
        val city = dto.address.city?.name?.takeIf { it.isNotBlank() } ?: return null
        val text = narrationTextOf(dto) ?: return null

        return Place(
            id = dto.oid.toString(),
            name = dto.name.cleaned().ifBlank { return null },
            position = Coordinates(lat, lon),
            narrationSeconds = estimateSeconds(text),
            languages = setOf(language),
            city = city,
            imageUrl = imageUrlOf(dto),
            summary = text,
            contentLanguage = VoiceLanguage.fromTag(dto.languageCode),
        )
    }

    /**
     * El texto del catálogo viene en Markdown y con longitud de artículo. El SPEC
     * (RF-1.5) pide segmentos de 60 a 90 segundos escritos para ser oídos, y eso
     * es trabajo editorial que no se puede automatizar.
     *
     * Mientras tanto: se limpia el marcado y se recorta a las primeras frases.
     * **Es un apaño consciente**, no la solución.
     */
    fun narrationTextOf(dto: PlaceDto): String? {
        val raw = dto.description?.takeIf { it.isNotBlank() }
            ?: dto.historicalDescription?.takeIf { it.isNotBlank() }
            ?: return null
        return raw.stripMarkdown().firstSentences(MAX_CHARS).takeIf { it.length > MIN_CHARS }
    }

    /**
     * La foto **no se saca de `image_path`**: ese campo devuelve 404 en todo lo
     * comprobado. La URL buena se compone desde `place_media`, igual que hace la
     * app móvil (`SharedContentHandler.extractFirstItineraryImage`), con un caso
     * especial: cuando el fichero se llama `0`, la imagen vive bajo `previews/`.
     */
    fun imageUrlOf(dto: PlaceDto): String? {
        val media = dto.media.firstOrNull()?.media ?: return null
        val path = media.filePath?.takeIf { it.isNotBlank() } ?: return null
        val name = media.fileName?.takeIf { it.isNotBlank() } ?: return null
        val extension = media.fileExtension?.takeIf { it.isNotBlank() } ?: return null

        return if (name == "0") {
            "$IMAGE_BASE${path.replace("originals", "")}/previews/$name.$extension"
        } else {
            "$IMAGE_BASE$path$name.$extension"
        }
    }

    /** ~150 palabras por minuto es el ritmo cómodo de una narración hablada. */
    private fun estimateSeconds(text: String): Int {
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        return (words * 60 / WORDS_PER_MINUTE).coerceIn(20, 180)
    }

    /**
     * Los nombres del catálogo vienen con coletilla de posicionamiento:
     * *"Colosseum : the largest amphitheatre in Rome - Secret World"*. En una
     * pantalla de coche, mirada de reojo, eso no es un nombre: es un párrafo.
     * Se corta por el primer separador y se queda lo que un copiloto diría.
     */
    private fun String.cleaned(): String {
        var name = replace(Regex("\\s*[-–|]\\s*Secret World\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        for (separator in listOf(" : ", ": ", " | ", " — ")) {
            val head = name.substringBefore(separator)
            if (head.length in MIN_NAME..name.length && head != name) {
                name = head.trim()
                break
            }
        }
        // "Discover India Catalina" es la India Catalina: el reclamo sobra, y
        // dicho en voz alta suena a anuncio.
        LEADS.firstOrNull { name.startsWith(it, ignoreCase = true) && name.length > it.length + MIN_NAME }
            ?.let { name = name.substring(it.length).trim() }
        return name.trimEnd(':', '-', '–', '|').trim()
    }

    private val LEADS = listOf("Discover ", "Explore ", "Visit ", "Descubre ", "Descubra ", "Explora ", "Visita ", "Scopri ", "Esplora ")

    private fun String.stripMarkdown(): String = this
        .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
        .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("[*_`#>]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Corta por final de frase, no a mitad de palabra: se va a leer en voz alta. */
    private fun String.firstSentences(limit: Int): String {
        if (length <= limit) return this
        val cut = substring(0, limit)
        val lastStop = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (lastStop > limit / 2) cut.substring(0, lastStop + 1) else "${cut.trimEnd()}…"
    }

    private const val IMAGE_BASE = "https://sworld.co.uk/img/img/"
    private const val MIN_NAME = 4
    private const val WORDS_PER_MINUTE = 150
    private const val MAX_CHARS = 700
    private const val MIN_CHARS = 80
}
