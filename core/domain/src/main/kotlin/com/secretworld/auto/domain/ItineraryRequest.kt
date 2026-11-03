package com.secretworld.auto.domain

/** Cómo de apretado quiere el día quien viaja. */
enum class Pace { RELAXED, NORMAL, PACKED }

/** La franja que se nombra al pedir: "esta tarde", "todo el día". */
enum class DayPart { MORNING, AFTERNOON, EVENING, FULL_DAY }

/**
 * Lo que se pidió al pedir un itinerario (SPEC RF-8.2). Todo es opcional: lo
 * que falte lo completa el perfil o, si tampoco, una pregunta.
 *
 * @param minutes tiempo disponible dicho en horas o minutos ("dos horas").
 * @param dayPart la franja nombrada, si no se dijo un tiempo.
 * @param area una ciudad o zona con nombre; `null` es alrededor del vehículo.
 * @param avoid lo que no quiere ("sin iglesias", "menos museos").
 * @param meal si pidió parar a comer; `null` si no lo dijo.
 */
data class ItineraryRequest(
    val minutes: Int? = null,
    val dayPart: DayPart? = null,
    val area: String? = null,
    val interests: Set<Interest> = emptySet(),
    val avoid: Set<Interest> = emptySet(),
    val pace: Pace? = null,
    val meal: Boolean? = null,
    /**
     * Las raíces concretas que nombró ("castill", "muse", "playa"), además del
     * interés general. Sirven para preferir el sitio que se llama así.
     */
    val mentions: Set<String> = emptySet(),
) {
    /** Si dijo cuánto tiempo tiene, de una forma u otra. */
    val hasTime: Boolean get() = minutes != null || dayPart != null

    /**
     * Lo que hay que preguntar antes de proponer, como mucho una cosa
     * combinada (RF-8.3): el tiempo si no lo dijo, y los intereses si no los
     * dijo y el perfil no los sabe.
     */
    fun missing(profile: TravellerProfile): Set<Missing> = buildSet {
        if (!hasTime) add(Missing.TIME)
        if (interests.isEmpty() && profile.likes.isEmpty()) add(Missing.INTERESTS)
    }

    /** Lo que trae otra respuesta encima de esta: "dos horas, y me gusta la historia". */
    fun plus(other: ItineraryRequest): ItineraryRequest = copy(
        minutes = other.minutes ?: minutes,
        dayPart = other.dayPart ?: dayPart,
        area = other.area ?: area,
        interests = interests + other.interests - other.avoid,
        avoid = avoid + other.avoid - other.interests,
        pace = other.pace ?: pace,
        meal = other.meal ?: meal,
        mentions = mentions + other.mentions,
    )

    /** Con el perfil: lo que no se dijo se toma de lo que se sabe del usuario. */
    fun withProfile(profile: TravellerProfile): ItineraryRequest = copy(
        interests = interests.ifEmpty { profile.likes - avoid },
        avoid = avoid + (profile.dislikes - interests),
    )

    enum class Missing { TIME, INTERESTS }
}

/**
 * Lo que se sabe del usuario para armarle itinerarios (SPEC RF-8.5). Vive en el
 * dispositivo; quien lo guarda es la app.
 *
 * @param visited lugares ya recorridos en un itinerario: no se repiten.
 */
data class TravellerProfile(
    val likes: Set<Interest> = emptySet(),
    val dislikes: Set<Interest> = emptySet(),
    val visited: Set<String> = emptySet(),
) {
    /**
     * Lo dicho al pedir se aprende: quien pide castillos dos veces no tiene que
     * decirlo una tercera. Lo último dicho manda sobre lo anterior.
     */
    fun learn(request: ItineraryRequest): TravellerProfile = copy(
        likes = likes + request.interests - request.avoid,
        dislikes = dislikes + request.avoid - request.interests,
    )

    fun visited(places: List<Place>): TravellerProfile = copy(visited = visited + places.map { it.id })

    val isEmpty: Boolean get() = likes.isEmpty() && dislikes.isEmpty() && visited.isEmpty()
}

/** Un cambio sobre una propuesta ya hecha: el orden, una parada más, el tiempo en una. */
sealed interface ItineraryEdit {
    /** "Empieza por el castillo" ([first]) o "deja la catedral para el final". */
    data class Move(val place: Place, val first: Boolean) : ItineraryEdit

    /** "La India Catalina antes que el castillo" ([before]) o "después de". */
    data class Relative(val place: Place, val anchor: Place, val before: Boolean) : ItineraryEdit

    /** "Añade el Cerro de la Popa". */
    data class Add(val place: Place) : ItineraryEdit

    /** "Más tiempo en el castillo" ([longer]) o "menos tiempo en la plaza". */
    data class Stay(val place: Place, val longer: Boolean) : ItineraryEdit

    /** "Quita el museo". */
    data class Remove(val place: Place) : ItineraryEdit
}

/**
 * Qué se contesta a una propuesta, dicho como se diga: "guarda ese itinerario"
 * y "me gusta" la guardan; "inicia el itinerario" y "vamos" la guardan y
 * arrancan; "guárdalo para luego" la guarda y cierra la vista.
 */
enum class ItineraryReply { SAVE, START, LATER }

/**
 * Entiende una petición de itinerario dicha de viva voz, en los tres idiomas.
 *
 * Es gramática, no comprensión: busca palabras clave dentro de la frase, como
 * [VoiceGrammar]. Lo que no cubre lo intenta el modelo de lenguaje (RF-8.1).
 */
object ItineraryRequests {

    /**
     * La petición, si la frase pide un itinerario: "ármame un itinerario para
     * esta tarde", "organizzami un giro a Roma", "plan me a tour of the old town".
     * `null` si la frase no pide uno.
     */
    fun parse(heard: String): ItineraryRequest? {
        val words = words(heard)
        if (!asksForItinerary(words)) return null
        return details(words)
    }

    /**
     * Lo que se dice al contestar la pregunta o al pedir un ajuste: "unas tres
     * horas, me gustan los museos", "menos iglesias". Sin palabra de itinerario:
     * ya se sabe que se está hablando de uno.
     */
    fun details(heard: String): ItineraryRequest = details(words(heard))

    /**
     * Un "no" corto que no ajusta nada: "no, vale", "no gracias". "No
     * iglesias" no lo es: dice qué quitar.
     */
    fun refuses(heard: String): Boolean = words(heard).let {
        it.size <= 4 && it.any { word -> word in REFUSALS } && details(it) == ItineraryRequest()
    }

    /**
     * Un cambio de orden, de estancia o una parada más, sobre la propuesta.
     *
     * @param stops las paradas propuestas, entre las que se buscan los nombres.
     * @param named el lugar que la gramática encontró en la frase, para
     *   "añade el Cerro de la Popa": no está entre las paradas, sino cerca.
     */
    fun edit(heard: String, stops: List<Place>, named: Place?): ItineraryEdit? {
        val words = words(heard)
        if (words.hasAny(LONGER) || words.hasAny(SHORTER)) {
            val place = VoiceGrammar.resolve(heard, stops) ?: return null
            return ItineraryEdit.Stay(place, longer = words.hasAny(LONGER))
        }
        // "X antes que Y": lo de la izquierda y lo de la derecha, por separado.
        listOf(true to BEFORE, false to AFTER).forEach { (before, markers) ->
            markers.forEach { marker ->
                val needle = marker.split(' ')
                val at = words.indices.firstOrNull { i -> i + needle.size <= words.size && words.subList(i, i + needle.size) == needle }
                    ?: return@forEach
                val place = VoiceGrammar.resolve(words.take(at).joinToString(" "), stops)
                val anchor = VoiceGrammar.resolve(words.drop(at + needle.size).joinToString(" "), stops)
                if (place != null && anchor != null && place != anchor) return ItineraryEdit.Relative(place, anchor, before)
            }
        }
        if (words.hasAny(FIRST)) VoiceGrammar.resolve(heard, stops)?.let { return ItineraryEdit.Move(it, first = true) }
        if (words.hasAny(LAST)) VoiceGrammar.resolve(heard, stops)?.let { return ItineraryEdit.Move(it, first = false) }
        // Añadir comida es otro ajuste ("añade dónde comer"): aquí, un sitio.
        if (words.any { it in ADD_VERBS } && !words.hasAny(MEAL)) {
            val place = named?.takeIf { candidate -> stops.none { it.id == candidate.id } } ?: return null
            return ItineraryEdit.Add(place)
        }
        return null
    }

    /** "Quita el museo", "togli il castello", "remove the cathedral". */
    fun removes(heard: String): Boolean = words(heard).any { it in REMOVE_VERBS }

    /**
     * Lo que contesta a la propuesta, o `null` si no contesta: un ajuste
     * ("vamos a ver museos") no es una respuesta. Dejarlo para luego gana a
     * empezar ("empezamos mañana"), y empezar a guardar ("guárdalo y vamos").
     * Aprobar sin verbo —"sí", "me gusta"— solo en frases cortas, que en una
     * larga puede ser otra cosa.
     */
    fun reply(heard: String): ItineraryReply? {
        val words = words(heard)
        if (words.size > MAX_REPLY_WORDS) return null
        if (words.hasAffirmed(LATER)) return ItineraryReply.LATER
        if (details(words) != ItineraryRequest()) return null
        return when {
            words.hasAffirmed(START_VERBS) -> ItineraryReply.START
            words.hasAffirmed(SAVE_VERBS) -> ItineraryReply.SAVE
            words.size <= 4 && words.hasAffirmed(APPROVE) && words.none { it in REFUSALS } -> ItineraryReply.SAVE
            else -> null
        }
    }

    /**
     * Como [hasAny], pero sin una negación justo delante: "no empecemos
     * todavía", "don't start", "non partiamo". "No, guárdalo" sí guarda: el
     * imperativo con el pronombre pegado solo es afirmativo.
     */
    private fun List<String>.hasAffirmed(phrases: List<String>): Boolean = phrases.any { phrase ->
        val needle = phrase.split(' ')
        indices.any { i ->
            i + needle.size <= size && subList(i, i + needle.size) == needle &&
                (needle.first() in ENCLITIC || (1..2).none { back -> getOrNull(i - back) in REPLY_NEGATIONS })
        }
    }

    private fun details(words: List<String>): ItineraryRequest {
        val (liked, avoided, mentioned) = interests(words)
        return ItineraryRequest(
            minutes = minutesIn(words),
            dayPart = dayPartIn(words),
            area = areaIn(words),
            interests = liked,
            avoid = avoided,
            pace = paceIn(words),
            meal = if (words.hasAny(MEAL)) true else null,
            mentions = mentioned,
        )
    }

    private fun asksForItinerary(words: List<String>): Boolean =
        words.any { it in ITINERARY_NOUNS } || words.hasAny(ITINERARY_PHRASES)

    /** "Dos horas", "2 horas", "mezz'ora", "una hora y media". */
    private fun minutesIn(words: List<String>): Int? {
        words.forEachIndexed { i, word ->
            if (word in HALF_HOUR_WORDS) return 30
            val unit = when (word) {
                in HOUR_WORDS -> 60
                in MINUTE_WORDS -> 1
                else -> return@forEachIndexed
            }
            val before = words.getOrNull(i - 1)
            // "Media hora", "mezz'ora" (que llega partida: "mezz ora"), "half an hour".
            if (unit == 60 && (before in HALF_WORDS || before == "mezz" || (before in setOf("an", "a") && words.getOrNull(i - 2) in HALF_WORDS))) return 30
            val amount = before?.let(::numberOf) ?: if (unit == 60) 1 else return@forEachIndexed
            val half = words.getOrNull(i + 1) in AND_WORDS && words.getOrNull(i + 2) in HALF_WORDS
            return amount * unit + if (half) 30 else 0
        }
        return null
    }

    private fun numberOf(word: String): Int? = word.toIntOrNull()?.takeIf { it in 1..24 } ?: NUMBERS[word]

    /**
     * "Todo el día" explícito gana; después las franjas; y "hoy" a secas, al
     * final: "esta tarde de hoy" es la tarde.
     */
    private fun dayPartIn(words: List<String>): DayPart? = when {
        words.hasAny(FULL_DAY) -> DayPart.FULL_DAY
        words.hasAny(EVENING) -> DayPart.EVENING
        words.hasAny(AFTERNOON) -> DayPart.AFTERNOON
        words.hasAny(MORNING) -> DayPart.MORNING
        words.hasAny(TODAY) -> DayPart.FULL_DAY
        else -> null
    }

    private fun paceIn(words: List<String>): Pace? = when {
        words.hasAny(PACKED) -> Pace.PACKED
        words.hasAny(RELAXED) -> Pace.RELAXED
        else -> null
    }

    /**
     * Lo que le gusta y lo que no. Una negación delante ("sin iglesias",
     * "niente chiese", "no churches", "menos museos") manda a la lista de evitar
     * lo que la sigue de cerca.
     */
    private fun interests(words: List<String>): Triple<Set<Interest>, Set<Interest>, Set<String>> {
        val liked = mutableSetOf<Interest>()
        val avoided = mutableSetOf<Interest>()
        val mentioned = mutableSetOf<String>()
        words.forEachIndexed { i, word ->
            val interest = interestOf(word) ?: return@forEachIndexed
            // "No me gustan los museos": la negación puede ir cuatro palabras
            // antes. Un "pero" o un "sí" la cortan: "pero sí la historia".
            val before = (1..4).mapNotNull { back -> words.getOrNull(i - back) }.takeWhile { it !in CONTRAST }
            // "Quita la catedral" también: quitar es no querer.
            val negated = before.any { it in NEGATIONS || it in REMOVE_VERBS }
            if (negated) {
                avoided += interest
            } else {
                liked += interest
                // Lo concreto que le gusta, no lo que evita: "sin iglesias" no pide iglesias.
                SPECIFIC_STEMS.firstOrNull(word::startsWith)?.let { mentioned += it }
            }
        }
        return Triple(liked - avoided, avoided, mentioned)
    }

    /**
     * La ciudad o zona: lo que sigue a "en" / "in" / "a" / "di", si no es una
     * franja ni un interés. "Un itinerario en Cartagena" → "cartagena"; "en la
     * tarde" no es un sitio.
     */
    private fun areaIn(words: List<String>): String? {
        words.forEachIndexed { i, word ->
            if (word !in AREA_MARKERS) return@forEachIndexed
            val rest = words.drop(i + 1).dropWhile { it in ARTICLES }
                .takeWhile { it !in AREA_STOP && !isVocabulary(it) && numberOf(it) == null }
            if (rest.isNotEmpty()) return rest.joinToString(" ")
        }
        return null
    }

    private fun isVocabulary(word: String): Boolean =
        word in VOCABULARY || interestOf(word) != null || word in ITINERARY_NOUNS

    private fun interestOf(word: String): Interest? {
        if (NOT_INTERESTS.any(word::startsWith)) return null
        return INTEREST_WORDS.entries.firstOrNull { (_, stems) -> stems.any(word::startsWith) }?.key
    }

    /** Empiezan como un interés y no lo son: "parqueadero" es aparcar, no un parque. */
    private val NOT_INTERESTS = listOf("parquea", "parking", "parcheggi", "fortun", "artic", "arteri")

    private fun words(heard: String): List<String> =
        Script.latin(heard.lowercase())
            .map { ACCENTS[it] ?: it }
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }

    private fun List<String>.hasAny(phrases: List<String>): Boolean = phrases.any { phrase ->
        val needle = phrase.split(' ')
        indices.any { i -> i + needle.size <= size && subList(i, i + needle.size) == needle }
    }

    /** "Percorso" no: es la ruta que se está siguiendo ("termina el percorso"). */
    private val ITINERARY_NOUNS = setOf(
        "itinerario", "itinerarios", "itinerari", "itinerary", "itineraries",
        "tour", "giro", "recorrido", "ruta turistica", "excursion", "escapada", "gita",
    )
    private val ITINERARY_PHRASES = listOf(
        "que hacer", "que puedo hacer", "que visitar", "cosa fare", "cosa posso fare", "cosa visitare",
        "what to do", "what can i do", "what to visit", "plan my day", "plan the day", "plan my afternoon",
        "organiza el dia", "organizza la giornata", "aprovechar el dia", "sfruttare la giornata", "make the most of",
    )

    private val LONGER = listOf(
        "mas tiempo", "mas rato", "quedarme mas", "quedarnos mas", "estar mas",
        "piu tempo", "fermarmi di piu", "fermarci di piu", "restare di piu",
        "more time", "longer", "stay more",
    )
    private val SHORTER = listOf(
        "menos tiempo", "poco tiempo", "menos rato", "rapido en", "rapidito",
        "meno tempo", "poco tempo", "veloce",
        "less time", "short visit", "quick",
    )
    private val BEFORE = listOf("antes de", "antes que", "antes del", "prima di", "prima del", "prima della", "before")
    private val AFTER = listOf("despues de", "despues del", "luego de", "dopo", "dopo il", "dopo la", "after")
    private val FIRST = listOf(
        "primero", "primera", "empieza por", "empezar por", "empecemos por", "empezamos por", "comienza por",
        "prima il", "prima la", "per primo", "per prima", "inizia da", "iniziamo da", "comincia da",
        "first", "start with", "begin with",
    )
    private val LAST = listOf(
        "al final", "para el final", "de ultimo", "de ultima", "por ultimo", "el ultimo", "la ultima",
        "alla fine", "per ultimo", "per ultima", "per la fine",
        "last", "at the end", "for the end",
    )
    private val ADD_VERBS = setOf(
        "anade", "anadele", "agrega", "agregale", "incluye", "mete", "suma", "pon",
        "aggiungi", "includi", "metti",
        "add", "include", "put",
    )
    private val REMOVE_VERBS = setOf(
        "quita", "quitale", "quitar", "saca", "elimina", "borra", "fuera",
        "togli", "togliere", "levare", "leva", "cancella",
        "remove", "drop", "delete", "without",
    )
    /** "No, vale": la negación gana. */
    private val REFUSALS = setOf("no", "nope", "non", "nah", "mejor", "meglio")
    private const val MAX_REPLY_WORDS = 8
    private val SAVE_VERBS = listOf(
        "guarda", "guardalo", "guardala", "guardar", "guardame", "salva", "salvalo", "salvala", "apunta", "apuntalo",
        "memoriza", "memorizalo", "conserva", "conservalo", "quedate con", "me lo quedo",
        "salvare", "memorizza", "memorizzalo", "tienilo", "tienila",
        "save", "keep", "store", "remember it",
    )
    private val START_VERBS = listOf(
        "inicia", "inicialo", "iniciar", "iniciemos", "iniciamos", "empieza", "empiezalo", "empezar", "empecemos",
        "empezamos", "comienza", "comienzalo", "comencemos", "comenzamos", "arranca", "arrancalo", "arrancamos",
        "arranquemos", "vamos", "vamonos", "dale", "adelante", "en marcha", "a por ello",
        "inizia", "inizialo", "iniziamo", "comincia", "cominciamo", "partiamo", "parti", "andiamo", "avvia",
        "start", "begin", "lets go", "let s go", "go ahead", "go now", "let s start", "lets start",
    )
    private val LATER = listOf(
        "luego", "despues", "mas tarde", "otro dia", "otro rato", "otro momento", "manana", "en otro momento", "ahora no",
        "dopo", "piu tardi", "domani", "un altro giorno", "adesso no", "non ora",
        "later", "tomorrow", "another day", "not now",
    )
    private val APPROVE = listOf(
        "si", "vale", "venga", "perfecto", "listo", "me gusta", "me encanta", "esta bien", "de acuerdo", "genial", "bien",
        "perfetto", "va bene", "certo", "dai", "mi piace", "ottimo", "benissimo",
        "yes", "ok", "okay", "perfect", "sounds good", "sure", "great", "fine", "i like it", "love it",
    )
    /** Imperativos con el pronombre pegado: no hay forma negativa que se les parezca. */
    private val ENCLITIC = setOf(
        "guardalo", "guardala", "guardame", "salvalo", "salvala", "apuntalo", "memorizalo", "conservalo",
        "inicialo", "empiezalo", "comienzalo", "arrancalo", "inizialo", "memorizzalo", "tienilo", "tienila",
    )
    private val REPLY_NEGATIONS = setOf("no", "non", "dont", "don", "not", "never", "nunca", "mai")

    private val HOUR_WORDS = setOf("hora", "horas", "ora", "ore", "hour", "hours", "h")
    private val MINUTE_WORDS = setOf("minuto", "minutos", "minuti", "minute", "minutes", "min")
    private val HALF_HOUR_WORDS = setOf("mezzora")
    private val AND_WORDS = setOf("y", "e", "and")
    private val HALF_WORDS = setOf("media", "mezza", "mezzo", "half")
    private val NUMBERS = mapOf(
        "un" to 1, "una" to 1, "uno" to 1, "an" to 1, "a" to 1, "one" to 1,
        "dos" to 2, "due" to 2, "two" to 2, "couple" to 2,
        "tres" to 3, "tre" to 3, "three" to 3,
        "cuatro" to 4, "quattro" to 4, "four" to 4,
        "cinco" to 5, "cinque" to 5, "five" to 5,
        "seis" to 6, "sei" to 6, "six" to 6,
        "siete" to 7, "sette" to 7, "seven" to 7,
        "ocho" to 8, "otto" to 8, "eight" to 8,
    )

    private val FULL_DAY = listOf(
        "todo el dia", "el dia entero", "dia completo", "todo el dia de hoy",
        "tutto il giorno", "tutta la giornata", "giornata intera", "giornata",
        "all day", "whole day", "full day", "the day",
        "aprovechar el dia",
    )
    private val TODAY = listOf("hoy", "oggi", "today")
    private val MORNING = listOf("esta manana", "por la manana", "manana por la manana", "stamattina", "questa mattina", "mattina", "this morning", "morning")
    private val AFTERNOON = listOf("esta tarde", "por la tarde", "la tarde", "questo pomeriggio", "oggi pomeriggio", "pomeriggio", "this afternoon", "afternoon")
    private val EVENING = listOf("esta noche", "stasera", "questa sera", "sera", "this evening", "tonight", "evening")

    private val PACKED = listOf(
        "aprovechar al maximo", "al maximo", "lo mas posible", "todo lo que pueda",
        "sfruttare al massimo", "al massimo", "il piu possibile",
        "make the most", "as much as possible", "packed",
    )
    private val RELAXED = listOf("tranquilo", "tranquila", "con calma", "sin prisa", "relajado", "tranquillo", "con calma", "rilassato", "senza fretta", "relaxed", "slow", "easy", "no rush")
    private val MEAL = listOf(
        "comer", "almorzar", "almuerzo", "comida", "cenar",
        "mangiare", "pranzo", "pranzare", "cena",
        "lunch", "eat", "food", "dinner",
    )

    /**
     * Las raíces que nombran un tipo de sitio concreto, no un interés general:
     * "castillos" pide un castillo; "historia", no.
     */
    private val SPECIFIC_STEMS = listOf(
        "castill", "castell", "castle", "fort", "murall", "palaci", "palazz", "palace", "ruin", "rovin",
        "muse", "galeri", "galler", "teatr", "theat",
        "iglesi", "chies", "church", "catedral", "cattedral", "cathedral", "basilic", "convent", "monaster",
        "playa", "spiagg", "beach", "parque", "parco", "park", "jardin", "giardin", "garden", "mirador", "belvedere",
        "plaza", "piazz", "square", "mercado", "mercat", "market",
    )

    /** Por raíz: "castillo", "castillos", "castello", "castle" caen todos. */
    private val INTEREST_WORDS: Map<Interest, List<String>> = mapOf(
        Interest.HISTORY to listOf("histori", "storia", "storic", "castill", "castell", "castle", "fort", "murall", "ruin", "rovin", "monument", "palaci", "palazz", "palace", "colonial", "arqueolog", "archeolog", "archaeolog"),
        Interest.ART to listOf("arte", "art", "muse", "galeri", "galler", "pintur", "pittur", "painting", "escultur", "scultur"),
        Interest.RELIGION to listOf("iglesi", "chies", "church", "catedral", "cattedral", "cathedral", "basilic", "convent", "monaster", "religio"),
        Interest.NATURE to listOf("natur", "playa", "spiagg", "beach", "parque", "parco", "park", "jardin", "giardin", "garden", "mirador", "belvedere", "viewpoint", "paisaj", "paesagg", "landscape"),
        Interest.CITY to listOf("plaza", "piazz", "square", "barrio", "quartier", "neighbo", "mercado", "mercat", "market", "calles", "street", "ciudad", "citta", "city", "centro historico", "old town"),
    )
    private val CONTRAST = setOf("pero", "si", "ma", "but", "yes", "aunque", "though")
    private val NEGATIONS = setOf("sin", "no", "ni", "nada", "menos", "senza", "niente", "meno", "without", "less", "fewer", "not", "nothing")

    private val AREA_MARKERS = setOf("en", "in", "por", "per", "around", "de", "di", "a", "at", "to")
    private val ARTICLES = setOf("el", "la", "los", "las", "il", "lo", "the")
    private val AREA_STOP = setOf("y", "e", "and", "con", "with", "para", "per", "for", "que", "che", "that", "me", "mi", "porque", "perche", "because")
    /** Palabras que siguen a "en"/"a" sin ser un sitio. */
    private val VOCABULARY = setOf(
        "tarde", "manana", "noche", "dia", "pomeriggio", "mattina", "sera", "giornata", "afternoon", "morning", "evening", "day",
        "hora", "horas", "ora", "ore", "hour", "hours", "calma", "maximo", "massimo",
        "esta", "este", "questo", "questa", "this", "my", "mi", "me", "un", "una", "uno", "a", "an",
        "todo", "toda", "tutto", "tutta", "all", "whole", "medio", "mezza", "half",
        "comer", "mangiare", "eat", "pie", "piedi", "foot", "coche", "auto", "car",
    )

    private val ACCENTS = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
        'à' to 'a', 'è' to 'e', 'ì' to 'i', 'ò' to 'o', 'ù' to 'u',
    )
}

/**
 * Para lo que la gramática no entendió con una propuesta delante: el modelo
 * solo elige entre guardar, empezar, dejarlo para luego o nada. Una palabra, que
 * en un modelo de 1B es lo rápido, y cualquier otra cosa es "nada": ante la
 * duda no se guarda ni se arranca.
 */
object ItineraryReplyPrompt {

    val PREFIX: String = """
        |A driver was just offered a sightseeing itinerary and asked "shall I save it, or start now?". Classify the driver's answer with ONE word:
        |save — keep the itinerary for later or approve it ("save that itinerary", "I like it", "me lo apuntas", "tienilo da parte")
        |start — begin it now ("start the itinerary", "let's hit the road", "nos vamos ya", "dai, partiamo")
        |later — keep it but not now ("maybe tomorrow", "lo dejamos para otro rato", "magari stasera")
        |none — anything else: a change, a question, talking to someone else
        |
        |Driver: "perfecto, guárdamelo"
        |Reply: save
        |Driver: "venga, en marcha"
        |Reply: start
        |Driver: "lo hacemos otro rato"
        |Reply: later
        |Driver: "¿cuánto se tarda en llegar?"
        |Reply: none
        |Driver: "dai, partiamo"
        |Reply: start
        |Driver: "magari stasera"
        |Reply: later
        |
    """.trimMargin()

    fun request(heard: String): String = "Driver: \"${heard.replace('"', ' ')}\"\nReply:"

    /**
     * La primera de las cuatro palabras en lo que conteste, fuera de comillas:
     * a veces repite la frase del conductor antes de clasificarla.
     */
    fun parse(reply: String): ItineraryReply? {
        val words = reply.replace(Regex("\"[^\"]*\""), " ").lowercase().split(Regex("[^a-z]+"))
        return when (words.firstOrNull { it in CLASSES }) {
            "save" -> ItineraryReply.SAVE
            "start" -> ItineraryReply.START
            "later" -> ItineraryReply.LATER
            else -> null
        }
    }

    private val CLASSES = setOf("save", "start", "later", "none")
}
