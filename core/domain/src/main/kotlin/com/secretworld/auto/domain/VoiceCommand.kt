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

    /**
     * Se nombró un lugar sin decir qué hacer con él: "India Catalina", "quiero
     * la India Catalina". En vez de suponer, se pregunta: ¿te llevo o te cuento
     * su historia? Suponer narrar era lo que pasaba, y quien quería ir se
     * encontraba con una historia que no había pedido.
     */
    data class ChooseAction(val place: Place) : VoiceCommand

    /**
     * El nombre encaja con varios lugares por igual: se pregunta cuál. Los
     * candidatos quedan enumerados, así que "el segundo" contesta.
     */
    data class WhichOne(val places: List<Place>) : VoiceCommand

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

    /**
     * "Termina el percorso", "cancela la ruta" → deja de navegar. Sin ruta,
     * equivale a [Stop]: quien dice "termina" quiere que algo pare.
     */
    data object EndRoute : VoiceCommand

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
     * @param language el idioma del conductor. Las órdenes se reconocen en
     *   todos; decide en qué idioma sale lo que se busca y qué verbos que
     *   chocarían con otro idioma se admiten.
     */
    fun parse(
        heard: String,
        candidates: List<Place>,
        focus: Place? = null,
        listed: List<Place> = emptyList(),
        language: VoiceLanguage = VoiceLanguage.SPANISH,
        /** [listed] se enumeró para elegir entre ellos, no para contarlos. */
        choosing: Boolean = false,
    ): VoiceCommand {
        val words = normalise(heard).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return VoiceCommand.NotUnderstood(heard)

        // Terminar la ruta va antes que callar: "stop navigation" lleva "stop".
        if (words.hasAny(END_ROUTE) || words.any(::endsRoute)) return VoiceCommand.EndRoute
        if (words.hasAny(STOP)) return VoiceCommand.Stop
        if (words.hasAny(SKIP)) return VoiceCommand.Skip

        // Un "sí" o un "no" a secas solo tienen sentido como respuesta; con más
        // palabras detrás, lo que cuenta es el resto.
        if (words.size <= 2 && words.hasAny(YES)) return VoiceCommand.Yes
        if (words.size <= 2 && words.hasAny(NO)) return VoiceCommand.No

        // Buscar sitios: por categoría conocida ("comer" → restaurante) o por lo
        // que siga a "busca" / "dónde hay". Va antes que "cerca", porque "un bar
        // cerca" es buscar bares, no enumerar monumentos.
        // Pedir ir a un lugar conocido gana a buscar: "voglio andare a India
        // Catalina" lleva "voglio", que también es verbo de búsqueda, y acababa
        // buscando "andare india catalina" en vez de trazar la ruta.
        val navigate = words.hasAny(NAVIGATE) || words.any { word -> NAVIGATE_STEMS.any(word::startsWith) }
        if (navigate) resolve(heard, candidates)?.let { return VoiceCommand.TakeMeThere(it) }

        categoryIn(words, language)?.let { return VoiceCommand.FindPlaces(it) }
        // Lo que sigue a "busca" / "voglio" se busca en el mapa, salvo que sea
        // un lugar que ya conocemos: "voglio la India Catalina" es ese sitio.
        searchedFor(words, language)?.let { query ->
            if (matches(query, candidates).isEmpty()) return VoiceCommand.FindPlaces(query)
        }
        if (words.hasAny(NEARBY)) return VoiceCommand.WhatIsNearby
        if (words.hasAny(MORE)) return VoiceCommand.TellMeMore
        // "Llévame al Foro" nombra el destino; "llévame ahí" es el lugar en foco.
        if (navigate) return VoiceCommand.TakeMeThere(resolve(heard, candidates) ?: focus)

        // Un lugar nombrado gana siempre al deíctico: si dice "cuéntame el Foro"
        // mientras suena otra cosa, quiere el Foro. Con verbo de contar, se
        // cuenta; sin él, se pregunta qué quiere; y si el nombre encaja con
        // varios, cuál.
        val tell = words.hasAny(TELL)
        // Contestando a "¿cuál?", se busca solo entre las opciones leídas.
        val named = matches(heard, if (choosing && listed.isNotEmpty()) listed else candidates)
        if (named.size > 1 && !tell) return VoiceCommand.WhichOne(named.take(MAX_OPTIONS))
        named.firstOrNull()?.let { return if (tell) VoiceCommand.TellMeAbout(it) else VoiceCommand.ChooseAction(it) }

        // "La historia", "cuéntamela": contesta a "¿te llevo o te cuento?".
        // Solo con petición explícita: "what's the weather" no es "cuéntamelo".
        if (focus != null && words.hasAny(STORY)) return VoiceCommand.TellMeAbout(focus)

        // "El segundo" solo significa algo si se acaba de enumerar.
        ordinalIn(words)?.let { index ->
            listed.getOrNull(index)?.let { return if (choosing) VoiceCommand.ChooseAction(it) else VoiceCommand.TellMeAbout(it) }
        }

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
    fun resolve(heard: String, candidates: List<Place>): Place? = matches(heard, candidates).firstOrNull()

    /**
     * ¿Es la misma palabra, dicha como la escribe el reconocedor? Con ruido,
     * "Catalina" llega como «Cataline» o «Catala»: se admite una letra de
     * diferencia en palabras de cinco o más, y dos en las de ocho o más. En las
     * cortas no: "roma" y "rosa" son sitios distintos.
     */
    private fun sameWord(heard: String, name: String): Boolean {
        if (heard == name) return true
        if (heard.length < 5 || name.length < 5) return false
        val allowed = if (name.length >= 8) 2 else 1
        return Script.editDistance(heard, name) <= allowed
    }

    /** Los lugares que mejor encajan con lo oído, empatados en puntuación. */
    private fun matches(heard: String, candidates: List<Place>): List<Place> {
        val words = normalise(heard).split(' ').filter { it.length > 2 && it !in FILLER }.toSet()
        if (words.isEmpty()) return emptyList()

        // Primero cuántas palabras del nombre se dijeron; a igualdad, gana el
        // nombre que queda **entero**: "Cartagena" es el sitio llamado así, no
        // "Cartagena de Indias, the Jewel of the Indies", que solo la contiene.
        val scored = candidates
            .map { place ->
                val name = normalise(place.name).split(' ')
                    .filter { it.length > 2 && it !in FILLER }
                    .toSet()
                val said = name.count { part -> words.any { heardWord -> sameWord(heardWord, part) } }
                Triple(place, said, if (name.isEmpty()) 0.0 else said.toDouble() / name.size)
            }
            .filter { (_, said, _) -> said > 0 }
        val most = scored.maxOfOrNull { (_, said, _) -> said } ?: return emptyList()
        val tied = scored.filter { (_, said, _) -> said == most }
        val complete = tied.filter { (_, _, coverage) -> coverage >= 1.0 }
        return (complete.ifEmpty { tied }).map { it.first }
    }

    private val ACCENTS = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
        'à' to 'a', 'è' to 'e', 'ì' to 'i', 'ò' to 'o', 'ù' to 'u',
    )

    /** ¿Contiene la frase alguna de estas expresiones, como palabras enteras y seguidas? */
    private fun List<String>.hasAny(phrases: List<String>): Boolean = phrases.any { phrase ->
        val needle = normalise(phrase).split(' ')
        indices.any { start -> start + needle.size <= size && subList(start, start + needle.size) == needle }
    }

    /**
     * Una categoría conocida en cualquier parte de la frase: "tengo hambre" →
     * restaurante. El otro camino, un verbo seguido de lo que sea ("busca
     * shawarma", "dónde hay un cajero"), es [searchedFor].
     */
    private fun categoryIn(words: List<String>, language: VoiceLanguage): String? {
        CATEGORIES.forEach { if (words.hasAny(it.keys)) return if (language == VoiceLanguage.ITALIAN) it.italian else it.spanish }
        return null
    }

    /** Lo que sigue a un verbo de búsqueda ("busca shawarma" → "shawarma"). */
    private fun searchedFor(words: List<String>, language: VoiceLanguage): String? {
        val verbs = if (language == VoiceLanguage.ITALIAN) SEARCH_VERBS + SEARCH_VERBS_IT else SEARCH_VERBS
        verbs.forEach { verb ->
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
        Script.latin(text.lowercase())
            // Sin acentos: el reconocedor los pone o no según le suene, y
            // "que hay cerca" y "qué hay cerca" tienen que ser lo mismo.
            .map { ACCENTS[it] ?: it }
            .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")

    /**
     * Las órdenes, en inglés, español e italiano a la vez. No hace falta saber
     * el idioma para reconocerlas: las palabras no se pisan entre sí, y el
     * conductor puede mezclar. Lo que sí importa es no incluir palabras
     * corrientes: "para" es "stop" en español pero también "hacia", así que
     * cuenta solo como "para ya" o con la orden completa.
     */
    /** Órdenes de corte. Se comprueban las primeras, a propósito. */
    private val STOP = listOf(
        "stop", "quiet", "silence", "be quiet", "shut up", "enough",
        "para ya", "cállate", "callate", "silencio", "basta", "detente", "alto", "ya está", "ya esta",
        "zitto", "zitta", "stai zitto", "stai zitta", "taci", "silenzio", "fermati", "smettila",
    )
    private val SKIP = listOf(
        "skip", "not now", "next one", "next place", "later",
        "salta", "sáltalo", "saltalo", "siguiente", "ahora no", "pásalo", "pasalo",
        "prossimo", "non ora", "non adesso", "più tardi", "passa oltre",
    )
    /**
     * Incluye cómo lo oye Whisper: "new by", "newbie" y "near by" son "nearby"
     * dicho en un coche. Medido, no supuesto.
     */
    private val NEARBY = listOf(
        "nearby", "near by", "new by", "newbie", "near me", "close by", "around me",
        "around here", "what s around", "whats around", "what is around", "around",
        "cerca", "alrededor", "por aquí", "por aqui", "qué hay aquí", "que hay aqui", "cerca de mí", "cerca de mi",
        "vicino", "qui vicino", "vicino a me", "intorno", "qui intorno", "dintorni", "nei dintorni",
    )
    private val MORE = listOf(
        "tell me more", "more about", "go on", "keep going", "continue",
        "cuéntame más", "cuentame mas", "dime más", "dime mas", "sigue", "continúa", "continua",
        "dimmi di più", "raccontami di più", "di più", "vai avanti", "prosegui",
    )
    private val NAVIGATE = listOf(
        "take me there", "take me to", "navigate", "drive me", "go there", "directions",
        // "lleva mi" y "lleva me" son "llévame" partido por el reconocedor. Medido.
        "llévame", "llevame", "lleva mi", "lleva me", "llévanos", "llevanos", "lleva a", "vamos a", "vamos al", "vamos para", "vamos ahí", "vamos ahi", "vamos allí", "vamos alli", "vamos allá", "vamos alla", "cómo llego", "como llego", "ruta a", "ruta al", "ir a", "ir al", "navega",
        // "porta mi" es "portami" partido, igual que "lleva mi".
        "portami", "porta mi", "portaci", "andiamo a", "andiamo al", "andiamo alla", "andiamo lì", "andiamo là", "andiamoci",
        "come arrivo", "percorso per", "strada per", "vai a", "vai al", "naviga verso",
        // Cómo más se pide en italiano, y cómo lo parte el reconocedor.
        "porta me", "portame", "portarmi", "mi porti", "mi porta", "ci porti", "portaci a",
        "voglio andare", "vorrei andare", "andare a", "andare al", "andare alla", "come si arriva", "raggiungere",
        "naviga", "navigare", "indicazioni", "percorso",
    )

    /**
     * Pedir la historia de un lugar. Con esto un lugar nombrado se cuenta sin
     * preguntar; sin esto, se pregunta si llevar o contar.
     */
    private val TELL = listOf(
        "tell me", "tell me about", "what is", "what s", "whats", "who was", "story", "history",
        "cuéntame", "cuentame", "cuéntamela", "cuentamela", "háblame", "hablame", "qué es", "que es", "quién fue", "quien fue", "historia",
        "raccontami", "raccontamela", "racconta", "parlami", "cos è", "cosa è", "che cos è", "chi era", "storia", "la storia",
    )

    /**
     * Pedir **la historia** sin nombrar el lugar: la respuesta a "¿te llevo o
     * te cuento?". Más estrecha que [TELL]: sin lugar nombrado, un "qué es"
     * suelto no basta.
     */
    private val STORY = listOf(
        "story", "history", "tell me", "tell me about it",
        "historia", "su historia", "cuéntamela", "cuentamela", "cuéntame", "cuentame", "háblame", "hablame",
        "storia", "la storia", "raccontamela", "raccontami", "racconta", "parlami",
        // Lo que invita a decir la pregunta "¿te llevo o te la cuento?".
        "raccontala", "raccontamelo", "raccontalo", "cuéntala", "cuentala", "cuéntalo", "cuentalo",
    )

    /** Cuántas opciones se leen en voz alta como mucho: más no se recuerdan. */
    private const val MAX_OPTIONS = 3

    /**
     * Raíces de "llévame" en italiano. El reconocedor multilingüe escribe
     * "portami" como le suena —«portammy», visto en un Huawei—, así que se mira
     * cómo empieza la palabra y no la palabra entera. Ninguna palabra corriente
     * empieza así salvo "portamonete", que no se le dice a un navegador.
     */
    private val NAVIGATE_STEMS = listOf("portam", "portar", "portan")

    /**
     * Terminar la ruta. Por raíz, como "portami": el reconocedor escribe
     * «termina el percoso», y lo que cuenta es el verbo. "Terminal" no: es un
     * sitio al que se quiere ir.
     */
    private fun endsRoute(word: String): Boolean =
        END_ROUTE_STEMS.any(word::startsWith) && !word.startsWith("terminal")

    private val END_ROUTE_STEMS = listOf("termin", "annull", "cancel", "finaliz")

    private val END_ROUTE = listOf(
        "end route", "end navigation", "stop navigation", "stop the route", "cancel route",
        "fine percorso", "fine navigazione", "fine del percorso", "stop navigazione", "basta navigazione", "esci dalla navigazione",
        "salir de la ruta", "salir de la navegación", "salir de la navegacion", "parar la ruta", "parar navegación", "parar navegacion",
    )

    private val YES = listOf(
        "yes", "yeah", "sure", "okay", "ok", "please", "sí", "si", "vale", "claro", "dale", "bueno", "de acuerdo", "por favor",
        "certo", "va bene", "d'accordo", "perfetto", "volentieri", "per favore", "sì grazie",
    )
    private val NO = listOf("no", "nope", "nah", "déjalo", "dejalo", "no gracias", "no thanks", "no grazie", "lascia stare", "lascia perdere")

    /**
     * Una categoría que se pide por su nombre corriente.
     *
     * La consulta es lo que se le manda al buscador, en el idioma en que mejor
     * devuelve y en el que luego se le dice al conductor: español para
     * Colombia, italiano para Italia.
     */
    private class Category(val keys: List<String>, val spanish: String, val italian: String)

    private val CATEGORIES: List<Category> = listOf(
        Category(listOf("comer", "comida", "hambre", "restaurante", "restaurantes", "almorzar", "cenar", "desayunar", "eat", "food", "hungry", "restaurant", "lunch", "dinner", "breakfast", "mangiare", "cibo", "fame", "ristorante", "ristoranti", "pranzo", "pranzare", "cena", "cenare", "colazione", "trattoria", "osteria"), "restaurante", "ristorante"),
        Category(listOf("bar", "bares", "cerveza", "copa", "copas", "beer", "drink", "drinks", "pub", "birra", "aperitivo", "bere"), "bar", "bar"),
        Category(listOf("café", "cafe", "cafetería", "cafeteria", "coffee", "caffè", "caffetteria", "cappuccino"), "café", "caffè"),
        Category(listOf("gasolina", "gasolinera", "combustible", "tanquear", "echar gasolina", "gas station", "fuel", "petrol", "benzina", "benzinaio", "distributore", "carburante", "rifornimento"), "gasolinera", "distributore di benzina"),
        Category(listOf("parquear", "parqueadero", "aparcar", "aparcamiento", "estacionar", "parking", "park the car", "parcheggio", "parcheggiare", "posteggio"), "parqueadero", "parcheggio"),
        Category(listOf("farmacia", "droguería", "drogueria", "pharmacy", "drugstore"), "farmacia", "farmacia"),
        Category(listOf("cajero", "banco", "atm", "bank", "bancomat", "banca"), "cajero automático", "bancomat"),
        Category(listOf("supermercado", "mercado", "supermarket", "grocery", "supermercato", "alimentari"), "supermercado", "supermercato"),
        Category(listOf("hotel", "hoteles", "dormir", "hostal", "albergo", "dormire", "alloggio"), "hotel", "hotel"),
        Category(listOf("hospital", "urgencias", "clínica", "clinica", "emergency", "ospedale", "pronto soccorso"), "hospital", "ospedale"),
        Category(listOf("baño", "baños", "toilet", "restroom", "bathroom", "bagno", "bagni", "toilette"), "baño público", "bagno pubblico"),
        Category(listOf("pizza", "pizzería", "pizzeria"), "pizza", "pizzeria"),
        Category(listOf("hamburguesa", "hamburguesas", "burger", "burgers", "hamburger"), "hamburguesa", "hamburger"),
        Category(listOf("sushi"), "sushi", "sushi"),
        Category(listOf("shawarma", "shawarmas", "kebab", "döner", "doner"), "shawarma", "kebab"),
        Category(listOf("tacos", "taco"), "tacos", "tacos"),
        Category(listOf("helado", "heladería", "heladeria", "ice cream", "gelato", "gelati", "gelateria"), "helado", "gelateria"),
        Category(listOf("panadería", "panaderia", "pan", "bakery", "panificio", "forno", "pasticceria"), "panadería", "panificio"),
        // Lo que hace falta en carretera, más allá de comer y dormir.
        Category(listOf("taller", "mecánico", "mecanico", "llanta", "llantas", "pinchazo", "grúa", "grua", "mechanic", "garage", "tire", "tyre", "tow", "meccanico", "officina", "gommista", "gomma", "gomme", "foratura", "carro attrezzi"), "taller mecánico", "officina meccanica"),
        Category(listOf("cargador", "cargar el coche", "carga eléctrica", "electrolinera", "ev charger", "charging station", "charge the car", "colonnina", "ricarica", "ricaricare", "stazione di ricarica"), "estación de carga eléctrica", "stazione di ricarica"),
        Category(listOf("lavadero", "lavar el carro", "lavar el coche", "car wash", "autolavaggio", "lavare la macchina"), "lavadero de carros", "autolavaggio"),
        Category(listOf("aeropuerto", "airport", "aeroporto"), "aeropuerto", "aeroporto"),
        Category(listOf("terminal", "estación de buses", "estacion de buses", "bus station", "stazione", "stazione dei treni", "treno", "autobus"), "terminal de transporte", "stazione"),
        Category(listOf("cambio", "casa de cambio", "cambiar dinero", "exchange", "money exchange", "cambio valuta", "cambiare soldi"), "casa de cambio", "cambio valuta"),
        Category(listOf("policía", "policia", "police", "polizia", "carabinieri", "questura"), "policía", "polizia"),
        Category(listOf("playa", "beach", "spiaggia"), "playa", "spiaggia"),
        Category(listOf("mirador", "viewpoint", "view point", "lookout", "belvedere", "panorama"), "mirador", "belvedere"),
        Category(listOf("tienda", "minimarket", "convenience store", "shop", "negozio", "tabaccheria", "tabacchi"), "tienda", "negozio"),
        Category(listOf("centro comercial", "mall", "shopping", "centro commerciale"), "centro comercial", "centro commerciale"),
    )

    /** Verbos tras los que viene lo que se busca. */
    private val SEARCH_VERBS = listOf("busca", "buscar", "búscame", "buscame", "encuentra", "encuéntrame", "dónde hay", "donde hay", "hay algún", "hay alguna", "quiero", "necesito", "find", "search for", "look for", "where is there", "is there a", "i need", "i want")

    /**
     * Verbos de búsqueda italianos, solo en italiano: "cerca" es "busca" en
     * italiano y "cerca" en español, y en español debe seguir significando
     * "¿qué hay cerca?".
     */
    private val SEARCH_VERBS_IT = listOf("cerca", "cercami", "trova", "trovami", "dove c è", "dov è", "c è un", "c è una", "voglio", "ho bisogno di", "mi serve")

    /** Lo que sobra entre el verbo y lo buscado. */
    private val SEARCH_FILLER = setOf(
        "il", "lo", "gli", "le", "uno", "qualche", "qualcosa", "da", "qui", "qua", "vicino", "per", "favore", "mi","un", "una", "unos", "unas", "el", "la", "los", "las", "algún", "alguna", "algo", "de", "por", "aquí", "aqui", "cerca", "cercano", "cercana", "a", "an", "the", "some", "nearby", "near", "here", "me", "close", "around")

    /** Deícticos: solo tienen sentido con un lugar en foco. */
    private val DEICTIC = listOf(
        "that", "this", "it", "there", "that one", "this one",
        "eso", "esto", "ahí", "ahi", "ese", "esa", "aquello", "allí", "alli",
        // Sin "lì" ni "là": sin acento son "li" y "la", un pronombre y un
        // artículo, y cualquier frase con "la" señalaría al lugar en foco.
        "quello", "quella", "questo", "questa",
    )

    /** Sin "one", "two"…: "that one" y "next one" no son ordinales. */
    private val ORDINALS = mapOf(
        "first" to 0, "1st" to 0, "primero" to 0, "primera" to 0,
        "second" to 1, "2nd" to 1, "segundo" to 1, "segunda" to 1,
        "third" to 2, "3rd" to 2, "tercero" to 2, "tercera" to 2,
        "fourth" to 3, "4th" to 3, "cuarto" to 3, "cuarta" to 3,
        "fifth" to 4, "5th" to 4, "quinto" to 4, "quinta" to 4,
        "primo" to 0, "prima" to 0, "secondo" to 1, "seconda" to 1, "terzo" to 2, "terza" to 2, "quarto" to 3, "quarta" to 3,
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
        "che", "cosa", "cos", "dimmi", "parlami", "raccontami", "della", "dello", "degli", "delle", "sul", "sulla",
        "portami", "andiamo", "luogo", "posto", "qui", "vicino", "quello", "questo", "com",
        "porti", "portarmi", "andare", "voglio", "vorrei", "percorso", "naviga", "portammy", "portamy",
    )
}
