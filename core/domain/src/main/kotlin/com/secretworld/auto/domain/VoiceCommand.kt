package com.secretworld.auto.domain

/**
 * Lo que el conductor puede pedir de viva voz.
 *
 * Es el **vocabulario cerrado del nivel 1** (SPEC §2.1): órdenes contadas y
 * respuestas previsibles. No es comprensión abierta, y la diferencia importa: un
 * vocabulario cerrado se puede auditar entero antes de publicar y **funciona sin
 * cobertura**, mientras que entender lenguaje libre exige el nivel 2, que depende
 * de un servicio que Pleos todavía no ha publicado.
 */
sealed interface VoiceCommand {

    /** "¿Qué es ese castillo?", "cuéntame el Foro" → narra ese lugar. */
    data class TellMeAbout(val place: Place) : VoiceCommand

    /** "¿Qué hay cerca?" → enumera los lugares del entorno. */
    data object WhatIsNearby : VoiceCommand

    /** "Cuéntame más" → segundo nivel de contenido sobre lo que suena. */
    data object TellMeMore : VoiceCommand

    /**
     * "Llévame ahí", "llévame al Foro" → traza la ruta y la sigue.
     *
     * @param place el destino nombrado, o `null` si es "ahí": el lugar en foco.
     */
    data class TakeMeThere(val place: Place?) : VoiceCommand

    /**
     * "Dónde puedo comer", "un bar cerca", "busca shawarma" → busca sitios de
     * ese tipo alrededor del coche. [query] es lo que se busca, en el idioma del
     * conductor.
     */
    data class FindPlaces(val query: String) : VoiceCommand

    /** "Sí", "vale", "claro" → confirma lo que el asistente acaba de ofrecer. */
    data object Yes : VoiceCommand

    /** "No", "déjalo" → rechaza lo ofrecido. */
    data object No : VoiceCommand

    /** "Sáltalo", "ahora no" → descarta y pasa al siguiente. */
    data object Skip : VoiceCommand

    /** "Calla", "para" → corta la narración. */
    data object Stop : VoiceCommand

    /**
     * Se entendió que pide algo, pero no a qué se refiere. La pregunta se le
     * dice al conductor y se abre el turno para que conteste.
     *
     * Solo la produce el modelo de lenguaje ([LlmIntentResolver]): la gramática
     * cerrada no tiene forma de saber que ha entendido a medias.
     */
    data class Clarify(val question: String) : VoiceCommand

    /**
     * Se oyó algo y no es ninguna de las órdenes.
     *
     * Existe como caso propio, y no como `null`, porque la respuesta correcta no
     * es el silencio: hay que decirle al conductor qué sí se entiende. Una app
     * que ignora lo que le dicen parece rota.
     */
    data class NotUnderstood(val heard: String) : VoiceCommand
}

/**
 * Traduce lo que se oyó a una orden.
 *
 * **Coincidencia sobre vocabulario cerrado, no comprensión.** El reconocedor
 * entrega texto sucio —sin puntuación fiable, con mayúsculas arbitrarias y a
 * veces con palabras de más—, así que se normaliza y se buscan las claves dentro
 * de la frase en vez de exigir que la frase sea exactamente una de ellas.
 *
 * El orden de comprobación no es casual: "para" y "sáltalo" van antes que nada
 * porque son órdenes de seguridad —quiero que se calle **ya**— y no deben
 * perderse porque la frase contenga además el nombre de un lugar.
 */
object VoiceGrammar {

    /**
     * @param heard lo que entendió el reconocedor.
     * @param candidates lugares entre los que resolver un nombre; normalmente la
     *   cola del recorrido, no el catálogo entero.
     * @param focus el lugar del que se está hablando ahora mismo. Es lo que
     *   permite que "¿qué es eso?" signifique algo: sin él, un deíctico no tiene
     *   a qué referirse.
     * @param listed lo último que el asistente enumeró en voz alta, en orden. Es
     *   lo que hace que "el segundo" signifique algo.
     */
    fun parse(
        heard: String,
        candidates: List<Place>,
        focus: Place? = null,
        listed: List<Place> = emptyList(),
    ): VoiceCommand {
        val words = normalise(heard).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return VoiceCommand.NotUnderstood(heard)

        if (words.hasAny(STOP)) return VoiceCommand.Stop
        if (words.hasAny(SKIP)) return VoiceCommand.Skip

        // Un "sí" o un "no" a secas solo tienen sentido como respuesta; con más
        // palabras detrás, lo que cuenta es el resto.
        if (words.size <= 2 && words.hasAny(YES)) return VoiceCommand.Yes
        if (words.size <= 2 && words.hasAny(NO)) return VoiceCommand.No

        // Buscar sitios: por categoría conocida ("comer" → restaurante) o por lo
        // que siga a "busca" / "dónde hay". Va antes que "cerca", porque "un bar
        // cerca" es buscar bares, no enumerar monumentos.
        findQuery(words)?.let { return VoiceCommand.FindPlaces(it) }
        if (words.hasAny(NEARBY)) return VoiceCommand.WhatIsNearby
        if (words.hasAny(MORE)) return VoiceCommand.TellMeMore
        // "Llévame al Foro" nombra el destino; "llévame ahí" es el lugar en foco.
        if (words.hasAny(NAVIGATE)) return VoiceCommand.TakeMeThere(resolve(heard, candidates) ?: focus)

        // Un lugar nombrado gana siempre al deíctico: si dice "cuéntame el Foro"
        // mientras suena otra cosa, quiere el Foro.
        resolve(heard, candidates)?.let { return VoiceCommand.TellMeAbout(it) }

        // "El segundo" solo significa algo si se acaba de enumerar.
        ordinalIn(words)?.let { index -> listed.getOrNull(index)?.let { return VoiceCommand.TellMeAbout(it) } }

        // "¿Qué es eso?" solo significa algo si hay un "eso": se exige el
        // deíctico, como palabra entera. Se buscaba como subcadena y "little"
        // contenía "it": cualquier frase mal oída acababa narrando el primer
        // lugar de la cola. Lo que no nombra nada ni señala nada se deja pasar,
        // para que lo mire el modelo.
        if (words.hasAny(DEICTIC)) {
            val target = focus ?: candidates.firstOrNull()
            if (target != null) return VoiceCommand.TellMeAbout(target)
        }

        return VoiceCommand.NotUnderstood(heard)
    }

    /**
     * Busca el lugar nombrado.
     *
     * Se puntúa por palabras significativas compartidas, no por igualdad: nadie
     * dice "the mosaics of the basilica of saint cecilia in trastevere", dice
     * "santa cecilia". Se exige una palabra propia del nombre —no un artículo—
     * para no disparar con cualquier frase.
     */
    fun resolve(heard: String, candidates: List<Place>): Place? {
        val words = normalise(heard).split(' ').filter { it.length > 2 && it !in FILLER }.toSet()
        if (words.isEmpty()) return null

        return candidates
            .map { place ->
                val name = normalise(place.name).split(' ')
                    .filter { it.length > 2 && it !in FILLER }
                    .toSet()
                place to name.count { it in words }
            }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private val ACCENTS = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n')

    /** ¿Contiene la frase alguna de estas expresiones, como palabras enteras y seguidas? */
    private fun List<String>.hasAny(phrases: List<String>): Boolean = phrases.any { phrase ->
        val needle = normalise(phrase).split(' ')
        indices.any { start -> start + needle.size <= size && subList(start, start + needle.size) == needle }
    }

    /**
     * Qué buscar, si la frase es una búsqueda de sitios.
     *
     * Dos caminos: una categoría conocida en cualquier parte de la frase
     * ("tengo hambre" → restaurante), o un verbo de búsqueda seguido de lo que
     * sea ("busca shawarma", "dónde hay un cajero" → "cajero").
     */
    private fun findQuery(words: List<String>): String? {
        CATEGORIES.forEach { (keys, query) -> if (words.hasAny(keys)) return query }
        SEARCH_VERBS.forEach { verb ->
            val needle = normalise(verb).split(' ')
            val at = words.indices.firstOrNull { i -> i + needle.size <= words.size && words.subList(i, i + needle.size) == needle }
                ?: return@forEach
            val rest = words.drop(at + needle.size).filter { it !in SEARCH_FILLER }
            if (rest.isNotEmpty()) return rest.joinToString(" ")
        }
        return null
    }

    /** Índice (desde 0) del ordinal que aparezca, o `null`. */
    private fun ordinalIn(words: List<String>): Int? =
        words.firstNotNullOfOrNull { ORDINALS[it] }

    private fun normalise(text: String): String =
        text.lowercase()
            // Sin acentos: el reconocedor los pone o no según le suene, y
            // "que hay cerca" y "qué hay cerca" tienen que ser lo mismo.
            .map { ACCENTS[it] ?: it }
            .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")

    /**
     * Las órdenes, en inglés y en español a la vez. No hace falta saber el
     * idioma para reconocerlas: las palabras no se pisan entre sí, y el
     * conductor puede mezclar. Lo que sí importa es no incluir palabras
     * corrientes: "para" es "stop" en español pero también "hacia", así que
     * cuenta solo como "para ya" o con la orden completa.
     */
    /** Órdenes de corte. Se comprueban las primeras, a propósito. */
    private val STOP = listOf(
        "stop", "quiet", "silence", "be quiet", "shut up", "enough",
        "para ya", "cállate", "callate", "silencio", "basta", "detente", "alto", "ya está", "ya esta",
    )
    private val SKIP = listOf(
        "skip", "not now", "next one", "next place", "later",
        "salta", "sáltalo", "saltalo", "siguiente", "ahora no", "pásalo", "pasalo",
    )
    /**
     * Incluye cómo lo oye Whisper: "new by", "newbie" y "near by" son "nearby"
     * dicho en un coche. Medido, no supuesto.
     */
    private val NEARBY = listOf(
        "nearby", "near by", "new by", "newbie", "near me", "close by", "around me",
        "around here", "what s around", "whats around", "what is around", "around",
        "cerca", "alrededor", "por aquí", "por aqui", "qué hay aquí", "que hay aqui", "cerca de mí", "cerca de mi",
    )
    private val MORE = listOf(
        "tell me more", "more about", "go on", "keep going", "continue",
        "cuéntame más", "cuentame mas", "dime más", "dime mas", "sigue", "continúa", "continua",
    )
    private val NAVIGATE = listOf(
        "take me there", "take me to", "navigate", "drive me", "go there", "directions",
        // "lleva mi" y "lleva me" son "llévame" partido por el reconocedor. Medido.
        "llévame", "llevame", "lleva mi", "lleva me", "llévanos", "llevanos", "lleva a", "vamos a", "vamos al", "vamos para", "vamos ahí", "vamos ahi", "vamos allí", "vamos alli", "vamos allá", "vamos alla", "cómo llego", "como llego", "ruta a", "ruta al", "ir a", "ir al", "navega",
    )

    private val YES = listOf("yes", "yeah", "sure", "okay", "ok", "please", "sí", "si", "vale", "claro", "dale", "bueno", "de acuerdo", "por favor")
    private val NO = listOf("no", "nope", "nah", "déjalo", "dejalo", "no gracias", "no thanks")

    /**
     * Categorías que se piden por su nombre corriente. La consulta es lo que
     * se le manda al buscador, en el idioma en que mejor devuelve: para
     * Colombia, español.
     */
    private val CATEGORIES: List<Pair<List<String>, String>> = listOf(
        listOf("comer", "comida", "hambre", "restaurante", "restaurantes", "almorzar", "cenar", "desayunar", "eat", "food", "hungry", "restaurant", "lunch", "dinner", "breakfast") to "restaurante",
        listOf("bar", "bares", "cerveza", "copa", "copas", "beer", "drink", "drinks", "pub") to "bar",
        listOf("café", "cafe", "cafetería", "cafeteria", "coffee") to "café",
        listOf("gasolina", "gasolinera", "combustible", "tanquear", "echar gasolina", "gas station", "fuel", "petrol") to "gasolinera",
        listOf("parquear", "parqueadero", "aparcar", "aparcamiento", "estacionar", "parking", "park the car") to "parqueadero",
        listOf("farmacia", "droguería", "drogueria", "pharmacy", "drugstore") to "farmacia",
        listOf("cajero", "banco", "atm", "bank") to "cajero automático",
        listOf("supermercado", "mercado", "supermarket", "grocery") to "supermercado",
        listOf("hotel", "hoteles", "dormir", "hostal") to "hotel",
        listOf("hospital", "urgencias", "clínica", "clinica", "emergency") to "hospital",
        listOf("baño", "baños", "toilet", "restroom", "bathroom") to "baño público",
        listOf("pizza", "pizzería", "pizzeria") to "pizza",
        listOf("hamburguesa", "hamburguesas", "burger", "burgers") to "hamburguesa",
        listOf("sushi") to "sushi",
        listOf("shawarma", "shawarmas", "kebab", "döner", "doner") to "shawarma",
        listOf("tacos", "taco") to "tacos",
        listOf("helado", "heladería", "heladeria", "ice cream") to "helado",
        listOf("panadería", "panaderia", "pan", "bakery") to "panadería",
        // Lo que hace falta en carretera, más allá de comer y dormir.
        listOf("taller", "mecánico", "mecanico", "llanta", "llantas", "pinchazo", "grúa", "grua", "mechanic", "garage", "tire", "tyre", "tow") to "taller mecánico",
        listOf("cargador", "cargar el coche", "carga eléctrica", "electrolinera", "ev charger", "charging station", "charge the car") to "estación de carga eléctrica",
        listOf("lavadero", "lavar el carro", "lavar el coche", "car wash") to "lavadero de carros",
        listOf("aeropuerto", "airport") to "aeropuerto",
        listOf("terminal", "estación de buses", "estacion de buses", "bus station") to "terminal de transporte",
        listOf("cambio", "casa de cambio", "cambiar dinero", "exchange", "money exchange") to "casa de cambio",
        listOf("policía", "policia", "police") to "policía",
        listOf("playa", "beach") to "playa",
        listOf("mirador", "viewpoint", "view point", "lookout") to "mirador",
        listOf("tienda", "minimarket", "convenience store", "shop") to "tienda",
        listOf("centro comercial", "mall", "shopping") to "centro comercial",
    )

    /** Verbos tras los que viene lo que se busca. */
    private val SEARCH_VERBS = listOf("busca", "buscar", "búscame", "buscame", "encuentra", "encuéntrame", "dónde hay", "donde hay", "hay algún", "hay alguna", "quiero", "necesito", "find", "search for", "look for", "where is there", "is there a", "i need", "i want")

    /** Lo que sobra entre el verbo y lo buscado. */
    private val SEARCH_FILLER = setOf("un", "una", "unos", "unas", "el", "la", "los", "las", "algún", "alguna", "algo", "de", "por", "aquí", "aqui", "cerca", "cercano", "cercana", "a", "an", "the", "some", "nearby", "near", "here", "me", "close", "around")

    /** Deícticos: solo tienen sentido con un lugar en foco. */
    private val DEICTIC = listOf(
        "that", "this", "it", "there", "that one", "this one",
        "eso", "esto", "ahí", "ahi", "ese", "esa", "aquello", "allí", "alli",
    )

    /** Sin "one", "two"…: "that one" y "next one" no son ordinales. */
    private val ORDINALS = mapOf(
        "first" to 0, "1st" to 0, "primero" to 0, "primera" to 0,
        "second" to 1, "2nd" to 1, "segundo" to 1, "segunda" to 1,
        "third" to 2, "3rd" to 2, "tercero" to 2, "tercera" to 2,
        "fourth" to 3, "4th" to 3, "cuarto" to 3, "cuarta" to 3,
        "fifth" to 4, "5th" to 4, "quinto" to 4, "quinta" to 4,
    )

    /**
     * Palabras que no distinguen un lugar de otro. Sin esta lista, "the" empareja
     * con la mitad del catálogo y cualquier frase resolvería al primer lugar.
     */
    private val FILLER = setOf(
        "the", "and", "for", "with", "from", "que", "los", "las",
        "tell", "about", "what", "who", "was", "were", "this", "that", "there",
        "please", "hey", "secret", "world",
        "del", "por", "para", "con", "una", "uno", "unos", "unas", "qué", "cuéntame", "cuentame",
        "sobre", "hay", "cerca", "dime", "háblame", "hablame", "lugar", "sitio", "vamos", "llévame", "llevame",
    )
}
