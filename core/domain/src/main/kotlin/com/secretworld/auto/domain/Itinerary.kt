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

    /**
     * Una guía de la ciudad entera y no un sitio: "Cartagena de Indias, the
     * Jewel of the Caribbean". Se llama como la ciudad y sigue con una coletilla.
     * En un itinerario no es una parada: está en el centro y no hay nada que ver.
     */
    fun isCityGuide(place: Place): Boolean {
        val city = place.city?.let(::normalise)?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val name = place.name.lowercase()
        val head = name.substringBefore(',').substringBefore(':')
        return head != name && normalise(head).trim().startsWith(city)
    }

    /** Si el nombre del lugar contiene alguna de estas raíces ("castill", "muse"). */
    fun nameMentions(place: Place, stems: Set<String>): Boolean {
        val name = normalise(place.name)
        return stems.any { stem -> " $stem" in name }
    }

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
 * Qué tipo de parada es, para saber **cuánto se queda uno**. Un itinerario no es
 * un paseo en coche: en un castillo se está una hora larga y en una estatua
 * diez minutos. Los intereses ([Interest]) dicen qué le gusta; esto, cuánto dura.
 *
 * @param minutes la estancia típica, con ritmo normal.
 */
enum class StopKind(val minutes: Int) {
    FORTRESS(75), MUSEUM(75), BEACH(90), PALACE(45), PARK(45), MARKET(45),
    VIEWPOINT(40), NEIGHBOURHOOD(45), CHURCH(25), SQUARE(20), MONUMENT(10), OTHER(30);

    companion object {
        /**
         * El nombre manda —"Castillo San Felipe" es un castillo aunque su ficha
         * hable de iglesias—; si no dice nada, el principio de la ficha.
         */
        fun of(place: Place): StopKind {
            val name = normalise(place.name)
            KEYWORDS.firstOrNull { (_, words) -> words.any { it in name } }?.let { return it.first }
            val summary = normalise(place.summary.orEmpty().take(SUMMARY_CHARS))
            return KEYWORDS.firstOrNull { (_, words) -> words.any { it in summary } }?.first ?: OTHER
        }

        private fun normalise(text: String): String =
            " " + text.lowercase().map { ACCENTS[it] ?: it }.map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("") + " "

        private const val SUMMARY_CHARS = 200

        /** En orden: lo más específico primero ("museo del castillo" es museo). */
        private val KEYWORDS: List<Pair<StopKind, List<String>>> = listOf(
            MUSEUM to listOf(" museo", " museum", " musei", " galeria", " gallery", " galleria", " pinacoteca"),
            FORTRESS to listOf(" castillo", " castello", " castle", " fuerte ", " forte ", " fort ", " fortaleza", " fortezza", " fortress", " ciudadela", " murallas", " mura "),
            BEACH to listOf(" playa", " spiaggia", " beach", " isla ", " isola ", " island"),
            PALACE to listOf(" palacio", " palazzo", " palace", " villa ", " teatro", " theatre", " theater"),
            CHURCH to listOf(" iglesia", " chiesa", " church", " catedral", " cattedrale", " cathedral", " basilica", " capilla", " cappella", " chapel", " convento", " convent", " monasterio", " monastero", " santuario", " duomo"),
            PARK to listOf(" parque", " parco", " park", " jardin", " giardino", " garden", " cienaga", " lago", " lake", " reserva"),
            MARKET to listOf(" mercado", " mercato", " market", " bazar"),
            VIEWPOINT to listOf(" mirador", " belvedere", " viewpoint", " cerro", " collina", " hill", " torre ", " tower"),
            NEIGHBOURHOOD to listOf(" barrio", " quartiere", " neighbourhood", " neighborhood", " centro historico", " centro storico", " old town", " ciudad amurallada"),
            SQUARE to listOf(" plaza", " piazza", " square", " plazuela", " calle ", " via ", " street"),
            MONUMENT to listOf(" estatua", " statua", " statue", " monumento", " monument", " busto", " fuente", " fontana", " fountain", " obelisco", " india catalina", " zapatos viejos"),
        )

        private val ACCENTS = mapOf(
            'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
            'à' to 'a', 'è' to 'e', 'ì' to 'i', 'ò' to 'o', 'ù' to 'u',
        )
    }
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
 * Lo que se propone al conductor antes de empezar (SPEC RF-8.4).
 *
 * @param minutes duración estimada de todo, conducción y comida incluidas.
 * @param mealAfter tras qué parada (índice) se para a comer, o `null` si no se para.
 * @param from desde dónde se sale: con él se recalcula al quitar una parada.
 * @param startMinute a qué hora se empieza, en minutos desde medianoche.
 * @param arrivals a qué hora se llega a cada parada, en el mismo orden que [stops].
 * @param stays cuántos minutos se queda uno en cada parada: visitarla y oírla.
 */
data class ItineraryProposal(
    val stops: List<Place>,
    val minutes: Int,
    val mealAfter: Int?,
    val request: ItineraryRequest,
    val from: Coordinates? = null,
    val startMinute: Int = 0,
    val arrivals: List<Int> = emptyList(),
    val stays: List<Int> = emptyList(),
)

/**
 * Arma el itinerario a la medida de una petición (SPEC RF-8.7–8.9).
 *
 * Frente a [ItineraryPlanner.generate], que solo mira la cercanía, aquí pesa
 * **qué merece la pena**: lo editorial de Secret World antes que Wikipedia, lo
 * que encaja con lo que le gusta antes que lo que no, y nunca lo que pidió
 * evitar ni lo ya visitado. Sigue eligiendo parada a parada, para que el
 * recorrido salga en orden y sin idas y vueltas.
 */
object ItineraryDesigner {

    /** Tiempo para comer, reservado dentro del total. */
    const val MEAL_MINUTES = 60

    /** La hora de comer: si el plan la cruza, se para. Minutos desde medianoche. */
    const val LUNCH_FROM = 13 * 60

    /** Sin decir cuánto: lo que dura una tarde corriente. */
    const val DEFAULT_MINUTES = 180

    /**
     * @param startMinute la hora de empezar, en minutos desde medianoche. Decide
     *   cuánto dura "esta tarde" y si toca comer.
     */
    fun plan(
        from: Coordinates,
        candidates: List<Place>,
        request: ItineraryRequest,
        profile: TravellerProfile,
        startMinute: Int,
    ): ItineraryProposal {
        val wanted = request.withProfile(profile)
        val pace = wanted.pace ?: Pace.NORMAL
        // "Esta tarde" pedida a las once empieza a las dos, no ahora.
        val start = startOf(wanted, startMinute)
        val budget = budgetMinutes(wanted, startMinute)
        val meal = wanted.meal ?: crossesLunch(start, budget)
        val available = budget - if (meal) MEAL_MINUTES else 0

        val pool = candidates
            .distinctBy { it.id }
            // Lo que salió de una búsqueda —un bar, una gasolinera— no es una parada de tour.
            .filter { it.kind == null && it.id !in profile.visited && !PlaceKinds.isCityGuide(it) }
            .filter { place -> PlaceKinds.interestsOf(place).none(wanted.avoid::contains) }
        // Con intereses, solo lo que encaja; si encaja muy poco, se completa con
        // lo demás antes que proponer un itinerario de una parada.
        val matching = pool.filter { matches(it, wanted.interests) }
        val chosenFrom = if (wanted.interests.isEmpty() || matching.size >= MIN_MATCHING) matching.ifEmpty { pool } else pool

        val remaining = chosenFrom.toMutableList()
        val chosen = ArrayList<Place>()
        var here = from
        var minutes = 0
        while (remaining.isNotEmpty() && chosen.size < maxStops(pace)) {
            // La más cercana, pero la distancia pesa menos cuanto más vale el sitio.
            val next = remaining.minBy { here.distanceTo(it.position) / weight(it, wanted) }
            remaining.remove(next)
            val cost = Phrasebook.drivingMinutes(here.distanceTo(next.position)) + stopMinutes(next, pace)
            if (minutes + cost > available) continue
            chosen += next
            minutes += cost
            here = next.position
        }

        val mealAfter = if (meal && chosen.isNotEmpty()) mealIndex(from, chosen, pace, start) else null
        return ItineraryProposal(
            stops = chosen,
            minutes = minutes + if (mealAfter != null) MEAL_MINUTES else 0,
            mealAfter = mealAfter,
            request = wanted,
            from = from,
            startMinute = start,
            arrivals = arrivals(from, chosen, pace, start, mealAfter),
            stays = chosen.map { stopMinutes(it, pace) },
        )
    }

    /**
     * A qué hora se llega a cada parada: conducir hasta ella, y antes visitar
     * las anteriores y comer donde toque. Es lo que enseña la línea de tiempo.
     */
    fun arrivals(from: Coordinates, stops: List<Place>, pace: Pace, startMinute: Int, mealAfter: Int?): List<Int> {
        var clock = startMinute
        var here = from
        return stops.mapIndexed { index, stop ->
            clock += Phrasebook.drivingMinutes(here.distanceTo(stop.position))
            val arrival = clock
            clock += stopMinutes(stop, pace) + if (index == mealAfter) MEAL_MINUTES else 0
            here = stop.position
            arrival
        }
    }

    /** La misma propuesta sin una parada, con la duración rehecha (RF-8.4: "quita el museo"). */
    fun without(proposal: ItineraryProposal, place: Place): ItineraryProposal {
        val index = proposal.stops.indexOf(place)
        if (index < 0) return proposal
        return rebuild(proposal, proposal.stops - place, staysOf(proposal).filterIndexed { i, _ -> i != index })
    }

    /** Una parada pasa a otro puesto del orden: "empieza por el castillo", "la catedral al final". */
    fun moved(proposal: ItineraryProposal, place: Place, to: Int): ItineraryProposal {
        val from = proposal.stops.indexOf(place)
        if (from < 0) return proposal
        val stays = staysOf(proposal).toMutableList()
        val stops = proposal.stops.toMutableList()
        val stay = stays.removeAt(from)
        stops.removeAt(from)
        val target = to.coerceIn(0, stops.size)
        stops.add(target, place)
        stays.add(target, stay)
        return rebuild(proposal, stops, stays)
    }

    /**
     * Una parada más, donde menos rodeo suponga: se prueba en cada hueco y se
     * queda el que deja el recorrido más corto.
     */
    fun added(proposal: ItineraryProposal, place: Place): ItineraryProposal {
        if (proposal.stops.any { it.id == place.id }) return proposal
        val pace = proposal.request.pace ?: Pace.NORMAL
        val stays = staysOf(proposal)
        return (0..proposal.stops.size)
            .map { slot ->
                rebuild(
                    proposal,
                    proposal.stops.toMutableList().apply { add(slot, place) },
                    stays.toMutableList().apply { add(slot, stopMinutes(place, pace)) },
                )
            }
            .minBy { it.minutes }
    }

    /** Más o menos tiempo en una parada: "más tiempo en el castillo". */
    fun restayed(proposal: ItineraryProposal, place: Place, factor: Double): ItineraryProposal {
        val index = proposal.stops.indexOf(place)
        if (index < 0) return proposal
        val stays = staysOf(proposal).toMutableList()
        stays[index] = (stays[index] * factor).toInt().coerceAtLeast(MIN_STAY)
        return rebuild(proposal, proposal.stops, stays)
    }

    /**
     * Rehace horas y duración con estas paradas y estancias, en este orden. La
     * comida se queda tras la misma posición, si sigue habiendo paradas.
     */
    fun rebuild(proposal: ItineraryProposal, stops: List<Place>, stays: List<Int>): ItineraryProposal {
        val mealAfter = proposal.mealAfter?.let { meal -> if (stops.isEmpty()) null else meal.coerceAtMost(stops.size - 1) }
        val from = proposal.from ?: return proposal.copy(stops = stops, stays = stays, mealAfter = mealAfter)
        var clock = proposal.startMinute
        var here = from
        val arrivals = stops.mapIndexed { index, stop ->
            clock += Phrasebook.drivingMinutes(here.distanceTo(stop.position))
            val arrival = clock
            clock += stays.getOrElse(index) { DEFAULT_STAY } + if (index == mealAfter) MEAL_MINUTES else 0
            here = stop.position
            arrival
        }
        return proposal.copy(
            stops = stops,
            stays = stays,
            mealAfter = mealAfter,
            arrivals = arrivals,
            minutes = clock - proposal.startMinute,
        )
    }

    /** Las estancias de la propuesta; si es antigua y no las trae, las de siempre. */
    private fun staysOf(proposal: ItineraryProposal): List<Int> {
        val pace = proposal.request.pace ?: Pace.NORMAL
        return proposal.stops.mapIndexed { index, stop -> proposal.stays.getOrNull(index) ?: stopMinutes(stop, pace) }
    }

    /**
     * Cuándo empieza: ahora, o el principio de la franja si aún no ha llegado.
     * "Esta tarde" dicho a las once es a partir de las dos.
     */
    fun startOf(request: ItineraryRequest, nowMinute: Int): Int {
        if (request.minutes != null) return nowMinute
        val opens = when (request.dayPart) {
            DayPart.MORNING, DayPart.FULL_DAY -> 9 * 60
            DayPart.AFTERNOON -> 14 * 60
            DayPart.EVENING -> 18 * 60
            null -> nowMinute
        }
        return maxOf(nowMinute, opens)
    }

    /**
     * Cuánto tiempo hay. Lo dicho manda; si no, la franja: la mañana hasta la
     * una, la tarde de dos a siete, la noche tres horas. Nunca menos de hora y
     * media: pedir una tarde a las seis y media no es pedir veinte minutos.
     */
    fun budgetMinutes(request: ItineraryRequest, nowMinute: Int): Int {
        request.minutes?.let { return it }
        val start = startOf(request, nowMinute)
        val until = when (request.dayPart) {
            DayPart.MORNING -> 13 * 60
            DayPart.AFTERNOON, DayPart.FULL_DAY -> 19 * 60
            DayPart.EVENING, null -> start + DEFAULT_MINUTES
        }
        val floor = if (request.dayPart == DayPart.FULL_DAY) 4 * 60 else 90
        return (until - start).coerceAtLeast(floor)
    }

    /** Visita y narración de una parada, según qué es y el ritmo pedido. */
    fun stopMinutes(place: Place, pace: Pace): Int {
        val visit = StopKind.of(place).minutes
        val factor = when (pace) {
            Pace.RELAXED -> 1.4
            Pace.NORMAL -> 1.0
            Pace.PACKED -> 0.75
        }
        return (visit * factor).toInt() + (place.narrationSeconds + 59) / 60
    }

    /** Empezar a las dos ya es haber comido: hace falta empezar antes de la una y media. */
    private fun crossesLunch(startMinute: Int, budget: Int): Boolean =
        budget >= 3 * 60 && startMinute < LUNCH_FROM + 30 && startMinute + budget > LUNCH_FROM

    /** Tras la parada en la que el reloj pasa de la una; si nunca pasa, a mitad. */
    private fun mealIndex(from: Coordinates, stops: List<Place>, pace: Pace, startMinute: Int): Int {
        var clock = startMinute
        var here = from
        stops.forEachIndexed { index, stop ->
            clock += Phrasebook.drivingMinutes(here.distanceTo(stop.position)) + stopMinutes(stop, pace)
            here = stop.position
            if (clock >= LUNCH_FROM) return index
        }
        return (stops.size - 1) / 2
    }

    private fun matches(place: Place, interests: Set<Interest>): Boolean =
        interests.isNotEmpty() && PlaceKinds.interestsOf(place).any(interests::contains)

    /**
     * Cuánto vale un sitio, para elegir entre dos parecidos de distancia. Lo
     * editorial vale el doble; lo que encaja con sus gustos, más; y en
     * Wikipedia un artículo largo suele ser un sitio importante.
     */
    private fun weight(place: Place, request: ItineraryRequest): Double {
        val editorial = if (place.id.startsWith(WIKIPEDIA_PREFIX)) 0.0 else 1.0
        val liked = PlaceKinds.interestsOf(place).count(request.interests::contains) * 0.5
        val depth = (place.summary?.length ?: 0) / 4000.0
        // Lo que nombró: quien pide castillos quiere el castillo, no "algo de historia".
        val named = if (request.mentions.isNotEmpty() && PlaceKinds.nameMentions(place, request.mentions)) 4.0 else 0.0
        return 1.0 + editorial + liked + depth.coerceAtMost(0.5) + named
    }

    private fun maxStops(pace: Pace): Int = when (pace) {
        Pace.RELAXED -> 5
        Pace.NORMAL -> NORMAL_STOPS
        Pace.PACKED -> 10
    }

    private const val MIN_MATCHING = 2
    /** Por muy poco tiempo que se pida, una parada no baja de esto. */
    private const val MIN_STAY = 5
    private const val DEFAULT_STAY = 30
    /** Más paradas no se recorren con gusto en una tarde, ni se recuerdan. */
    private const val NORMAL_STOPS = 6
    private const val WIKIPEDIA_PREFIX = "wiki:"

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
