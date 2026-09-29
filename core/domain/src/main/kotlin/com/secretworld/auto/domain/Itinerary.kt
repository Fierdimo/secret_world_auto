package com.secretworld.auto.domain

/**
 * Lo que le puede interesar a quien viaja. Es la forma de pedir un itinerario
 * sin elegir parada por parada.
 */
enum class Interest { HISTORY, ART, RELIGION, NATURE, CITY }

/** Lo que se pide al generar un itinerario. Sin intereses, vale todo. */
data class ItineraryPreferences(
    val interests: Set<Interest> = emptySet(),
    /** Tiempo disponible, conducción incluida. */
    val minutes: Int = 120,
)

/**
 * Qué es un lugar, deducido de cómo se llama y de lo que cuenta su ficha.
 *
 * Los lugares del catálogo no traen categoría: solo nombre y texto. Se leen
 * palabras clave en los tres idiomas, y un lugar puede ser varias cosas a la
 * vez (una catedral es historia y religión). Es aproximado a propósito: se
 * equivoca en algún caso, y a cambio funciona sin red y sin modelo.
 */
object PlaceKinds {

    fun interestsOf(place: Place): Set<Interest> {
        val text = normalise("${place.name} ${place.summary.orEmpty().take(SUMMARY_CHARS)}")
        return KEYWORDS.filterValues { words -> words.any { it in text } }.keys
    }

    private fun normalise(text: String): String =
        " " + text.lowercase().map { ACCENTS[it] ?: it }.map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("") + " "

    /** Del resumen basta el principio: es donde se dice qué es el sitio. */
    private const val SUMMARY_CHARS = 300

    private val KEYWORDS: Map<Interest, List<String>> = mapOf(
        Interest.RELIGION to listOf(
            "church", "cathedral", "basilica", "chapel", "convent", "monastery", "sanctuary", "abbey",
            "iglesia", "catedral", "capilla", "convento", "monasterio", "santuario",
            "chiesa", "cattedrale", "cappella", "duomo", "monastero", "abbazia",
        ),
        Interest.HISTORY to listOf(
            "castle", "fort", "fortress", "wall", "palace", "ruins", "historic", "colonial", "century", "battle", "monument",
            "castillo", "fuerte", "fortaleza", "muralla", "palacio", "ruinas", "historico", "siglo", "batalla", "monumento",
            "castello", "forte", "fortezza", "mura", "palazzo", "rovine", "storico", "secolo", "battaglia",
        ),
        Interest.ART to listOf(
            "museum", "gallery", "art ", "theatre", "theater", "sculpture", "mosaic", "fresco", "painting",
            "museo", "galeria", "arte ", "teatro", "escultura", "mosaico", "pintura",
            "galleria", "scultura", "affresco", "dipinto",
        ),
        Interest.NATURE to listOf(
            "beach", "park", "garden", "island", "bay", "hill", "lake", "river", "nature", "viewpoint", "reserve",
            "playa", "parque", "jardin", "isla", "bahia", "cerro", "lago", "rio ", "naturaleza", "mirador", "reserva",
            "spiaggia", "parco", "giardino", "isola", "baia", "collina", "fiume", "natura", "belvedere", "riserva",
        ),
        Interest.CITY to listOf(
            "square", "plaza", "street", "market", "neighbourhood", "neighborhood", "district", "old town", "bridge",
            "calle", "mercado", "barrio", "centro historico", "puente",
            "piazza", "via ", "mercato", "quartiere", "centro storico", "ponte",
        ).map { " $it" },
    )

    private val ACCENTS = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
        'à' to 'a', 'è' to 'e', 'ì' to 'i', 'ò' to 'o', 'ù' to 'u',
    )
}

/**
 * Arma itinerarios: en qué orden ir, cuánto se tarda y, si se pide, qué
 * paradas elegir.
 */
object ItineraryPlanner {

    /** Lo que se estima que se queda uno en cada parada, además de oírla. */
    const val VISIT_MINUTES = 15

    /** Como mucho: más paradas no se recorren en un día, ni se recuerdan. */
    const val MAX_STOPS = 8

    /**
     * El orden de recorrido: la más cercana primero y, desde cada una, la más
     * cercana de las que quedan. No es el óptimo exacto, pero con ocho paradas
     * como mucho la diferencia es de minutos, y no hay que explicarlo.
     */
    fun order(from: Coordinates, stops: List<Place>): List<Place> {
        val remaining = stops.distinctBy { it.id }.toMutableList()
        val ordered = ArrayList<Place>(remaining.size)
        var here = from
        while (remaining.isNotEmpty()) {
            val next = remaining.minBy { here.distanceTo(it.position) }
            remaining.remove(next)
            ordered += next
            here = next.position
        }
        return ordered
    }

    /** Minutos de todo el recorrido: conducir entre paradas, oírlas y visitarlas. */
    fun estimateMinutes(from: Coordinates, stops: List<Place>): Int {
        var here = from
        var minutes = 0
        stops.forEach { stop ->
            minutes += Phrasebook.drivingMinutes(here.distanceTo(stop.position))
            minutes += (stop.narrationSeconds + 59) / 60 + VISIT_MINUTES
            here = stop.position
        }
        return minutes
    }

    /**
     * Un itinerario según lo que le interesa a quien viaja y el tiempo que
     * tiene. Se va eligiendo siempre la parada más cercana que encaje, mientras
     * quepa en el tiempo: así sale ya en orden y sin idas y vueltas.
     */
    fun generate(from: Coordinates, candidates: List<Place>, preferences: ItineraryPreferences): List<Place> {
        val matching = candidates
            .distinctBy { it.id }
            // Lo que salió de una búsqueda —un bar, una gasolinera— no es una parada de tour.
            .filter { it.kind == null }
            .filter { preferences.interests.isEmpty() || PlaceKinds.interestsOf(it).any(preferences.interests::contains) }
            .toMutableList()
        val chosen = ArrayList<Place>()
        var here = from
        while (matching.isNotEmpty() && chosen.size < MAX_STOPS) {
            val next = matching.minBy { here.distanceTo(it.position) }
            matching.remove(next)
            if (estimateMinutes(from, chosen + next) > preferences.minutes) break
            chosen += next
            here = next.position
        }
        return chosen
    }
}

/**
 * Un itinerario en marcha: las paradas y por cuál se va.
 *
 * @param index la parada a la que se está yendo, o que se está oyendo.
 */
data class Tour(val stops: List<Place>, val index: Int = 0) {
    val current: Place get() = stops[index]
    val next: Place? get() = stops.getOrNull(index + 1)
    val isLast: Boolean get() = index == stops.lastIndex

    fun advanced(): Tour? = if (isLast) null else copy(index = index + 1)
}
