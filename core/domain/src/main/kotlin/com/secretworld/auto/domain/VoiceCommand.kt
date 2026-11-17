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
     * "Llévame al Hotel Caribe", "portami al Castillo San Felipe", "take me to
     * Calle 30 número 5" → ir a un sitio **concreto** que no está en el
     * catálogo: se busca por su nombre y se traza la ruta. No es buscar un tipo
     * de sitio ("llévame a un hotel"), que es [FindPlaces].
     */
    data class NavigateToNamed(val name: String, val area: String? = null) : VoiceCommand

    /**
     * "Dónde puedo comer", "un bar cerca", "busca shawarma" → busca sitios de
     * ese tipo alrededor del coche. [query] es lo que se busca, en el idioma del
     * conductor.
     */
    data class FindPlaces(
        val query: String,
        /**
         * Dónde, si no es alrededor del coche: "en el barrio Las Gaviotas",
         * "in centro". [VoiceGrammar.CITY_CENTRE] es el centro de la ciudad.
         */
        val area: String? = null,
    ) : VoiceCommand

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

    /** "Habla en español", "parla italiano", "speak English" → cambia el idioma de la app. */
    data class SwitchLanguage(val language: VoiceLanguage) : VoiceCommand

    /** "Calla", "para" → corta la narración. */
    data object Stop : VoiceCommand

    /**
     * "Ármame un itinerario para esta tarde", "organizzami un giro a Roma" →
     * propone un recorrido a la medida (SPEC §2.4, RF-8). Lo que no se dijo lo
     * completa el perfil o una pregunta.
     */
    data class PlanItinerary(val request: ItineraryRequest) : VoiceCommand

    /** "Olvida mis preferencias" → borra el perfil (SPEC RF-8.6). */
    data object ForgetProfile : VoiceCommand

    /** "Empieza mi itinerario" → arranca el itinerario guardado. */
    data object StartItinerary : VoiceCommand

    /**
     * "Añade el castillo" → al itinerario a la vista o, sin ninguno, a uno nuevo
     * que empieza por él.
     */
    data class AddToItinerary(val place: Place) : VoiceCommand

    /** "Quiero armar mi propio itinerario" → el mapa para ir eligiendo, sin planificar nada. */
    data object BuildItinerary : VoiceCommand

    /** "Mis itinerarios" → la ventana con los guardados, para elegir uno. */
    data object ShowItineraries : VoiceCommand

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
        // "Para" a secas es callar; con más palabras suele ser la preposición.
        if (words.size == 1 && words.first() in STOP_ALONE) return VoiceCommand.Stop
        // Antes que las búsquedas: "parla italiano" no es buscar comida italiana.
        languageSwitchIn(words)?.let { return VoiceCommand.SwitchLanguage(it) }
        if (words.hasAny(SKIP)) return VoiceCommand.Skip

        // Un "sí" o un "no" a secas solo tienen sentido como respuesta; con más
        // palabras detrás, lo que cuenta es el resto.
        if (words.size <= 2 && words.hasAny(YES)) return VoiceCommand.Yes
        if (words.size <= 2 && words.hasAny(NO)) return VoiceCommand.No

        if (words.hasAny(FORGET_PROFILE)) return VoiceCommand.ForgetProfile
        // Armarlo a mano y añadir, antes que pedir uno: "añade el castillo al
        // itinerario" lleva "itinerario" y no es planificar otro.
        if (words.hasExactly(BUILD_ITINERARY)) return VoiceCommand.BuildItinerary
        if (ItineraryRequests.adds(heard)) {
            resolve(heard, (listed + candidates).distinctBy { it.id })?.let { return VoiceCommand.AddToItinerary(it) }
        }
        // Empezar antes que ver: "empieza mi itinerario" no es abrir la lista.
        if (words.hasAny(START_ITINERARY)) return VoiceCommand.StartItinerary
        // Al pie de la letra: "hazme mi itinerario" se parece a "mis itinerarios"
        // y es pedir uno nuevo.
        if (words.hasExactly(SHOW_ITINERARIES)) return VoiceCommand.ShowItineraries
        // Antes que las búsquedas: "un itinerario con comida" no es buscar comida.
        ItineraryRequests.parse(heard)?.let { return VoiceCommand.PlanItinerary(it) }

        // Recién enumerada una lista, un nombre de ella la elige, antes de buscar
        // nada: «Restaurante Sung Waa» —escrito «restaurantes son guas»— lanzaba
        // otra búsqueda de restaurantes y la lista cambiaba debajo del conductor.
        pickFromListed(words, listed)?.let { place ->
            // Lo que salió de una búsqueda no tiene historia: la pregunta era
            // "¿te llevo a alguno?", así que se lleva.
            return when {
                place.kind != null -> VoiceCommand.TakeMeThere(place)
                choosing -> VoiceCommand.ChooseAction(place)
                else -> VoiceCommand.TellMeAbout(place)
            }
        }

        // Buscar sitios: por categoría conocida ("comer" → restaurante) o por lo
        // que siga a "busca" / "dónde hay". Va antes que "cerca", porque "un bar
        // cerca" es buscar bares, no enumerar monumentos.
        // Pedir ir a un lugar conocido gana a buscar: "voglio andare a India
        // Catalina" lleva "voglio", que también es verbo de búsqueda, y acababa
        // buscando "andare india catalina" en vez de trazar la ruta.
        val navigate = words.hasAny(NAVIGATE) || words.any { word -> NAVIGATE_STEMS.any(word::startsWith) }
        if (navigate) {
            val named = namedDestination(words)
            // Un sitio del catálogo solo si contiene lo pedido: "llévame al centro
            // comercial San Fernando" iba a la Casa del fuerte de **San** Juan.
            resolve(heard, candidates)
                ?.takeIf { named == null || NameMatcher.matches(named.first, it.name) }
                ?.let { return VoiceCommand.TakeMeThere(it) }
            // Ir a un sitio concreto que no conocemos: se buscará por su nombre.
            // "Llévame a un hotel" o "al centro comercial más cercano" no: eso es
            // buscar un tipo de sitio, y sigue más abajo.
            named?.let { (name, area) -> return VoiceCommand.NavigateToNamed(name, area) }
        }

        // Qué se busca y dónde: "un restaurante | en el barrio Las Gaviotas".
        val (what, area) = splitArea(words, language)
        categoryIn(what, language)?.let { return VoiceCommand.FindPlaces(it, area) }
        // Lo que sigue a "busca" / "voglio" se busca en el mapa, salvo que sea
        // un lugar que ya conocemos: "voglio la India Catalina" es ese sitio.
        searchedFor(what, language)?.let { query ->
            if (matches(query, candidates).isEmpty()) return VoiceCommand.FindPlaces(query, area)
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
        // "The castle" o "il castello" es el "Castillo San Felipe": los nombres
        // del catálogo vienen en el idioma de su fuente, no en el de quien habla.
        if (Landmarks.same(heard, name)) return true
        if (heard.length < 5 || name.length < 5) return false
        val allowed = if (name.length >= 8) 2 else 1
        return Script.editDistance(heard, name) <= allowed
    }

    /**
     * El sitio de [listed] que se nombró, si alguno se parece lo bastante. Sin
     * las palabras genéricas ("restaurante", "hotel", la ciudad), que casan con
     * media lista, y con tolerancia a la ortografía del reconocedor: solo se
     * compara con los pocos sitios recién leídos, no con el mundo.
     */
    private fun pickFromListed(words: List<String>, listed: List<Place>): Place? {
        if (listed.isEmpty()) return null
        // Solo lo que distingue un sitio: fuera lo que describe una búsqueda
        // —categorías en singular o plural, zonas, artículos—. "Centros
        // comerciales en el centro", tras una lista de uno, se tomaba por ese uno.
        val said = words.filter { word ->
            word !in FILLER && word !in GENERIC_NAME_WORDS && word.length > 1 &&
                ALL_KEY_WORDS.none { sameForm(word, it) } && word !in AREA_WORDS
        }.joinToString("")
        if (said.length < 3) return null
        val scored = listed.map { place ->
            val core = normalise(place.name).split(' ').filter { it !in FILLER && it !in GENERIC_NAME_WORDS && it.length > 1 }.joinToString("")
            val distance = when {
                core.isEmpty() -> Int.MAX_VALUE
                core == said || (said.length >= 4 && (core.contains(said) || said.contains(core))) -> 0
                else -> Script.editDistance(said, core)
            }
            place to Pair(distance, core.length)
        }.sortedBy { it.second.first }
        val (best, bestScore) = scored.first()
        val (distance, length) = bestScore
        // Hasta la mitad del nombre: «sung guas» por "Sung Waa" son tres letras.
        // Se puede ser generoso porque solo se compara con los pocos sitios
        // recién leídos, y se exige que ninguno otro esté igual de cerca.
        // Y nunca más de la mitad de lo dicho: una frase larga no casa con un
        // nombre largo por acumulación de parecidos.
        if (said.length < 4 || distance > maxOf(1, minOf(length, said.length) / 2)) return null
        // Solo si no hay otro casi igual de cerca: ante la duda, que busque.
        val runnerUp = scored.getOrNull(1)?.second?.first ?: Int.MAX_VALUE
        return best.takeIf { runnerUp > distance }
    }

    /**
     * El destino nombrado tras "llévame", si es un sitio concreto y no un tipo
     * de sitio. Es concreto si queda algo que no sea categoría ni relleno —"el
     * Hotel **Caribe**"— y no empieza por un artículo indeterminado ni pide "el
     * más cercano".
     */
    private fun namedDestination(words: List<String>): Pair<String, String?>? {
        val verbAt = words.indices.firstOrNull { i ->
            NAVIGATE_STEMS.any(words[i]::startsWith) ||
                NAVIGATE.any { phrase -> val needle = normalise(phrase).split(' '); i + needle.size <= words.size && words.subList(i, i + needle.size) == needle }
        } ?: return null
        val verbLength = NAVIGATE.map { normalise(it).split(' ') }
            .filter { needle -> verbAt + needle.size <= words.size && words.subList(verbAt, verbAt + needle.size) == needle }
            .maxOfOrNull { it.size } ?: 1
        var rest = words.drop(verbAt + verbLength)
        while (rest.isNotEmpty() && rest.first() in DESTINATION_LEAD) rest = rest.drop(1)
        if (rest.isEmpty() || rest.first() in INDEFINITE || rest.any { it in NEAREST }) return null
        if (rest.size == 1 && rest.first() in DEICTIC_WORDS) return null
        val (named, area) = splitArea(listOf("x") + rest).let { (w, a) -> w.drop(1) to a }
        // Solo categoría y relleno ("la terminal", "a comer"): es buscar un tipo.
        val distinctive = named.filter { it !in SEARCH_FILLER && it !in ALL_KEY_WORDS && it !in DESTINATION_LEAD }
        if (distinctive.isEmpty()) return null
        return named.joinToString(" ") to area
    }

    /** Lo que va entre el verbo y el nombre: "a", "al", "hasta el", "to the". */
    private val DESTINATION_LEAD = setOf("a", "al", "hasta", "hacia", "para", "pa", "el", "la", "los", "las", "lo", "il", "alla", "allo", "ai", "agli", "verso", "fino", "per", "di", "to", "the", "at", "ad", "en",
        // Contracciones italianas: «all'India» se normaliza como "all india".
        "all", "dell", "nell", "sull", "l", "d")

    private val INDEFINITE = setOf("un", "una", "unos", "unas", "uno", "algun", "alguna", "qualche", "some", "any", "an")

    private val NEAREST = setOf("cercano", "cercana", "proximo", "proxima", "vicino", "vicina", "nearest", "closest", "near", "cerca")

    private val DEICTIC_WORDS = setOf("ahi", "alli", "alla", "li", "la", "there", "ci", "eso", "quello")

    /**
     * Categorías en las que el buscador de mapas mezcla lo que no es. Medido en
     * Cartagena: en Mapbox, las tiendas de **dentro** de un centro comercial
     * llevan la misma categoría que el centro comercial —una papelería, una
     * clínica dental, un puesto de comida rápida—. Para estas se exige que el
     * nombre lo diga, y se completa con OpenStreetMap, que sí los distingue.
     */
    data class Strict(
        /** La etiqueta de OpenStreetMap, "clave:valor". */
        val osmTag: String,
        /** Lo que busca OpenStreetMap: sin texto no devuelve nada. */
        val osmQueries: List<String>,
        /** Alguna de estas, en el nombre, para aceptar lo de Mapbox. */
        val nameSays: List<String>,
    )

    fun strictness(searchCategory: String?): Strict? = STRICT[searchCategory]

    private val STRICT = mapOf(
        "shopping_mall" to Strict(
            osmTag = "shop:mall",
            osmQueries = listOf("centro comercial", "mall", "centro commerciale"),
            nameSays = listOf("centro comercial", "c c", "cc", "mall", "plaza", "outlet", "shopping", "galeria", "galerias", "centro commerciale", "paseo"),
        ),
    )

    /** ¿Dice el nombre lo que es? Ver [Strict]. */
    fun nameSays(name: String, strict: Strict): Boolean {
        val words = normalise(name).split(' ').filter { it.isNotBlank() }
        return words.hasAny(strict.nameSays)
    }

    /** ¿Es una palabra de alguna categoría —"parqueadero", "cajero", "tienda"—? */
    fun isCategoryWord(word: String): Boolean = normalise(word) in ALL_KEY_WORDS

    /** Todas las palabras de todas las categorías, también las de claves de varias. */
    private val ALL_KEY_WORDS: Set<String> by lazy { CATEGORIES.flatMap { it.keys }.flatMap { normalise(it).split(' ') }.toSet() }

    /** Lo que dice dónde, no qué: no sirve para elegir un sitio de la lista. */
    private val AREA_WORDS = setOf(
        "en", "in", "nel", "nella", "near", "around", "cerca", "vicino", "centro", "center", "centre", "downtown",
        "barrio", "quartiere", "zona", "sector", "ciudad", "citta", "city", "aqui", "qui", "here",
    )

    /** Palabras de los nombres que no distinguen un sitio de otro. */
    private val GENERIC_NAME_WORDS = setOf(
        "restaurante", "restaurantes", "restaurant", "ristorante", "hotel", "hoteles", "hostal", "bar", "cafe",
        "cafeteria", "pizzeria", "casa", "de", "del", "la", "el", "cartagena", "indias", "sas", "s", "a",
    )

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
    private fun List<String>.hasExactly(phrases: List<String>): Boolean = phrases.any { phrase ->
        val needle = normalise(phrase).split(' ')
        indices.any { start -> start + needle.size <= size && subList(start, start + needle.size) == needle }
    }

    private fun List<String>.hasAny(phrases: List<String>): Boolean = phrases.any { phrase ->
        val needle = normalise(phrase).split(' ')
        indices.any { start ->
            start + needle.size <= size && needle.indices.all { i -> sameForm(this[start + i], needle[i]) }
        }
    }

    /**
     * La misma palabra, en singular o en plural. Las claves están en singular y
     * se dicen como se dicen: «centros comerciales» no casaba con "centro
     * comercial" y la frase acababa en "no te entendí". En español e inglés el
     * plural añade "s" o "es"; en italiano cambia la vocal final
     * (ristorante → ristoranti).
     */
    private fun sameForm(heard: String, key: String): Boolean {
        if (heard == key) return true
        if (heard == key + "s" || heard == key + "es" || key == heard + "s" || key == heard + "es") return true
        return heard.length >= 5 && key.length == heard.length &&
            heard.dropLast(1) == key.dropLast(1) && heard.last() in VOWELS && key.last() in VOWELS
    }

    private val VOWELS = setOf('a', 'e', 'i', 'o')

    /**
     * Una categoría conocida en cualquier parte de la frase: "tengo hambre" →
     * restaurante. El otro camino, un verbo seguido de lo que sea ("busca
     * shawarma", "dónde hay un cajero"), es [searchedFor].
     */
    private fun categoryIn(words: List<String>, language: VoiceLanguage): String? {
        val category = CATEGORIES.firstOrNull { words.hasAny(it.keys) }
            // Sin coincidencia exacta, con una o dos letras de margen en palabras
            // largas: el reconocedor escribe «picería» o «pilcería» por pizzería.
            ?: CATEGORIES.firstOrNull { category -> category.keys.any { key -> ' ' !in key && words.any { sameKey(it, normalise(key)) } } }
            ?: return null
        return when (language) {
            VoiceLanguage.ITALIAN -> category.italian
            // En inglés, con su nombre en inglés: contestaba «Looking for restaurante nearby».
            VoiceLanguage.ENGLISH -> ENGLISH_NAMES[category.searchId] ?: category.spanish
            VoiceLanguage.SPANISH -> category.spanish
        }
    }

    /** Cómo se dice en inglés lo que se busca, para contestarlo; lo demás se busca por categoría. */
    private val ENGLISH_NAMES = mapOf(
        "restaurant" to "restaurants", "gas_station" to "petrol stations", "parking_lot" to "parking",
        "hotel" to "hotels", "pharmacy" to "pharmacies", "atm" to "cash machines", "cafe" to "cafés", "bar" to "bars",
        "supermarket" to "supermarkets", "hospital" to "hospitals", "pizza_restaurant" to "pizza", "ice_cream" to "ice cream",
        "bakery" to "bakeries", "auto_repair" to "mechanics", "charging_station" to "charging stations", "car_wash" to "car washes",
        "airport" to "the airport", "bus_station" to "bus stations", "beach" to "beaches", "viewpoint" to "viewpoints",
        "shopping_mall" to "shopping centres", "convenience_store" to "shops", "cinema" to "cinemas", "police_station" to "police",
        "seafood_restaurant" to "seafood", "italian_restaurant" to "Italian food", "chinese_restaurant" to "Chinese food",
        "mexican_restaurant" to "Mexican food", "japanese_restaurant" to "Japanese food", "fast_food" to "fast food",
    )

    private fun sameKey(heard: String, key: String): Boolean {
        // Siete letras o más: con seis, "parque" pasaba por "parquear".
        if (heard.length < 7 || key.length < 7) return false
        val allowed = if (key.length >= 8) 2 else 1
        return Script.editDistance(heard, key) <= allowed
    }

    /** La zona "centro de la ciudad", se diga como se diga. */
    const val CITY_CENTRE = "centro"

    /**
     * Separa lo que se busca de dónde se busca. La zona es lo que sigue a "en",
     * "in", "nel", "cerca de"… siempre que no sea "aquí" —"cerca de mí" es lo
     * de siempre—. "El centro", "centro storico" o "downtown" son el centro.
     */
    private fun splitArea(words: List<String>, language: VoiceLanguage? = null): Pair<List<String>, String?> {
        if (words.hasAny(DOWNTOWN)) return words.filterNot { it == "downtown" } to CITY_CENTRE
        // En italiano "a" también dice dónde: "ristoranti a Bocagrande". En
        // español no, que "a" es casi siempre adónde.
        val markers = if (language == VoiceLanguage.ITALIAN) AREA_MARKERS + "a" else AREA_MARKERS
        // El primer marcador que deje algo detrás; se prueban los largos antes.
        for (start in 1 until words.size) {
            val marker = markers.firstOrNull { marker ->
                val needle = normalise(marker).split(' ')
                start + needle.size < words.size && words.subList(start, start + needle.size) == needle
            } ?: continue
            val after = withoutLead(words.drop(start + normalise(marker).split(' ').size))
            // La zona acaba donde empieza otra descripción: "en Bocagrande | de
            // comida oriental". Lo que sigue vuelve a contar para qué se busca;
            // si no, la zona era «boca grande comida oriental» y no existía.
            val end = after.indices.firstOrNull { i -> i > 0 && endsArea(after, i) } ?: after.size
            val rest = after.take(end)
            if (rest.isEmpty()) continue
            val area = rest.joinToString(" ")
            if (area in HERE) return words to null
            return (words.take(start) + after.drop(end)) to if (isCentre(rest)) CITY_CENTRE else area
        }
        return words to null
    }

    private val AREA_MARKERS = listOf(
        "en el barrio", "en la zona de", "en el sector", "por el barrio", "cerca de", "en", "por",
        "nel quartiere", "nella zona di", "in zona", "vicino a", "nel", "nella", "in",
        "in the", "near", "around",
    ).sortedByDescending { it.length }

    /**
     * Quita delante del nombre un artículo y una palabra genérica —"el barrio",
     * "la zona"—, pero no más: en "el barrio Las Gaviotas" el "Las" es del
     * nombre.
     */
    private fun withoutLead(area: List<String>): List<String> {
        var rest = area
        // "En el barrio de Las Gaviotas": la preposición no es del nombre.
        if (rest.size > 1 && rest[0] in setOf("de", "del", "di", "of")) rest = rest.drop(1)
        // El artículo solo se quita delante de "barrio", "zona" o "centro".
        if (rest.size > 1 && rest[0] in ARTICLES && (rest[1] in AREA_NOUNS || rest[1] == CITY_CENTRE)) rest = rest.drop(1)
        if (rest.size > 1 && rest.first() in AREA_NOUNS) rest = rest.drop(1)
        return rest
    }

    /** ¿Empieza en [i] otra descripción, y no más nombre de zona? */
    private fun endsArea(words: List<String>, i: Int): Boolean {
        val word = words[i]
        if (word in CATEGORY_WORDS || word in AREA_TAIL) return true
        // "de comida", "di cucina": la preposición abre la descripción.
        return word in setOf("de", "di", "of") && words.getOrNull(i + 1)?.let { it in CATEGORY_WORDS || it in DESCRIPTION_NOUNS } == true
    }

    /** Palabras sueltas de las categorías: "comida", "oriental", "pizzeria"… */
    private val CATEGORY_WORDS: Set<String> by lazy {
        CATEGORIES.flatMap { it.keys }.filter { ' ' !in it }.map(::normalise).filter { it.length > 3 }.toSet()
    }

    private val DESCRIPTION_NOUNS = setOf("comida", "cucina", "food", "cocina")
    private val AREA_TAIL = setOf("que", "con", "para", "per", "che", "with", "that", "donde", "dove", "where")

    private val ARTICLES = setOf("el", "la", "los", "las", "il", "lo", "the")
    private val AREA_NOUNS = setOf("barrio", "quartiere", "zona", "sector")

    /** "Cerca de mí", "qui vicino": no es otra zona, es aquí. */
    private val HERE = setOf("mi", "me", "aqui", "aca", "qui", "qua", "here", "this area", "esta", "questa", "este")

    /**
     * "El centro", "centro de la ciudad", "centro storico": cualquier zona que
     * empiece por "centro", salvo un centro comercial, que es un sitio.
     */
    private fun isCentre(area: List<String>): Boolean {
        val first = area.firstOrNull() ?: return false
        if (first in setOf("centre", "center") || area.joinToString(" ") in setOf("city centre", "city center", "old town")) return true
        return first == "centro" && area.getOrNull(1) !in setOf("comercial", "commerciale")
    }

    private val DOWNTOWN = listOf("downtown")

    /**
     * La categoría del buscador de mapas para lo que se busca, si la tiene:
     * "ristorante" → `restaurant`. Sirve también para lo que propone el modelo
     * de lenguaje, que busca con sus propias palabras ("coffee", "gasolina").
     */
    fun searchCategoryOf(query: String): String? {
        val words = normalise(query).split(' ').filter { it.isNotBlank() }
        val said = normalise(query)
        // Primero la etiqueta exacta —la que puso la propia gramática—, y solo
        // después palabras sueltas: "negozio di abbigliamento" lleva "negozio",
        // que también es tienda de conveniencia.
        CATEGORIES.firstOrNull { said == normalise(it.spanish) || said == normalise(it.italian) }?.let { return it.searchId }
        ENGLISH_NAMES.entries.firstOrNull { said == normalise(it.value) }?.let { return it.key }
        return CATEGORIES.firstOrNull { words.hasAny(it.keys) }?.searchId
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

    /** "El segundo", "la tercera", "il primo": el índice desde 0, o `null`. */
    fun ordinal(heard: String): Int? = ordinalIn(normalise(heard).split(' '))

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
        "para ya", "para de hablar", "deja de hablar", "cállate", "callate", "silencio", "basta", "detente", "alto", "ya está", "ya esta",
        "zitto", "zitta", "stai zitto", "stai zitta", "taci", "silenzio", "fermati", "smettila",
    )
    private val STOP_ALONE = setOf("para", "parar", "pare", "ferma", "stop")
    private val SKIP = listOf(
        "skip", "not now", "next one", "next place", "next stop", "later",
        "salta", "sáltalo", "saltalo", "siguiente", "ahora no", "pásalo", "pasalo",
        "prossimo", "prossima tappa", "non ora", "non adesso", "più tardi", "passa oltre",
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
        // Como lo escribe el reconocedor: «told me about Cerro de la Popa». Medido.
        "told me", "tel me", "tell us",
        "cuéntame", "cuentame", "cuéntamela", "cuentamela", "háblame", "hablame", "qué es", "que es", "quién fue", "quien fue", "historia",
        "raccontami", "raccontamela", "racconta", "parlami", "cos è", "cosa è", "che cos è", "chi era", "storia", "la storia",
    )

    /**
     * Pedir **la historia** sin nombrar el lugar: la respuesta a "¿te llevo o
     * te cuento?". Más estrecha que [TELL]: sin lugar nombrado, un "qué es"
     * suelto no basta.
     */
    private val STORY = listOf(
        "story", "history", "tell me", "tell me about it", "told me",
        "historia", "su historia", "cuéntamela", "cuentamela", "cuéntame", "cuentame", "háblame", "hablame",
        "storia", "la storia", "raccontamela", "raccontami", "racconta", "parlami",
        // Lo que invita a decir la pregunta "¿te llevo o te la cuento?".
        "raccontala", "raccontamelo", "raccontalo", "cuéntala", "cuentala", "cuéntalo", "cuentalo",
    )

    /**
     * Pedir otro idioma. Hace falta un verbo de cambio o una frase muy corta
     * ("in italiano", "en español"): el nombre del idioma suelto también es
     * una cocina, y "comida italiana" es buscar restaurantes.
     */
    private fun languageSwitchIn(words: List<String>): VoiceLanguage? {
        val language = LANGUAGE_NAMES.entries.firstOrNull { (_, names) -> words.hasAny(names) }?.key ?: return null
        val asked = words.hasAny(SWITCH_VERBS) || (words.size <= 3 && words.hasAny(LANGUAGE_PREPOSITIONS))
        return language.takeIf { asked }
    }

    private val LANGUAGE_NAMES = mapOf(
        VoiceLanguage.SPANISH to listOf("español", "espanol", "castellano", "spagnolo", "spanish"),
        VoiceLanguage.ITALIAN to listOf("italiano", "italian"),
        VoiceLanguage.ENGLISH to listOf("inglés", "ingles", "inglese", "english"),
    )

    private val SWITCH_VERBS = listOf(
        "habla", "háblame", "hablame", "hablemos", "cambia", "cambiar", "pasa a", "pon",
        "parla", "parlami", "parliamo", "cambia lingua", "passa a", "metti",
        "speak", "talk", "switch", "change", "use",
        "idioma", "lingua", "language",
    )

    private val LANGUAGE_PREPOSITIONS = listOf("en", "in")

    /** Cuántas opciones se leen en voz alta como mucho: más no se recuerdan. */
    private const val MAX_OPTIONS = 3

    /**
     * Raíces de "llévame" en italiano. El reconocedor multilingüe escribe
     * "portami" como le suena —«portammy», visto en un Huawei—, así que se mira
     * cómo empieza la palabra y no la palabra entera. Ninguna palabra corriente
     * empieza así salvo "portamonete", que no se le dice a un navegador.
     */
    private val NAVIGATE_STEMS = listOf("portam", "portar", "portan", "llevab", "llevam", "llevarm", "llevan")

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
        // El viaje entero, no solo la ruta: es lo que se dice al acabar el itinerario.
        "end the trip", "end trip", "end the tour", "end the itinerary", "finish the trip", "finish the tour",
        "we re done", "we are done", "trip is over",
        "acaba el viaje", "acabar el viaje", "acabamos el viaje", "fin del viaje", "fin del recorrido", "se acabo el viaje",
        "abbiamo finito", "fine del viaggio", "finisci il viaggio", "basta viaggio",
        "fine percorso", "fine navigazione", "fine del percorso", "stop navigazione", "basta navigazione", "esci dalla navigazione",
        "salir de la ruta", "salir de la navegación", "salir de la navegacion", "parar la ruta", "parar navegación", "parar navegacion",
    )

    private val START_ITINERARY = listOf(
        "empieza mi itinerario", "empieza el itinerario", "empezar el itinerario", "empecemos el itinerario", "arranca el itinerario",
        "inizia il mio itinerario", "inizia l itinerario", "iniziamo l itinerario", "parti con l itinerario",
        "start my itinerary", "start the itinerary", "begin my itinerary",
    )
    private val BUILD_ITINERARY = listOf(
        "mi propio itinerario", "itinerario a mano", "itinerario vacio", "itinerario a mi manera", "yo elijo los sitios",
        "armarlo yo", "lo armo yo", "itinerario personalizado",
        "il mio itinerario personale", "itinerario a mano", "itinerario vuoto", "scelgo io i posti",
        "my own itinerary", "build my own", "empty itinerary", "i ll pick the places",
    )
    private val SHOW_ITINERARIES = listOf(
        "mis itinerarios", "itinerarios guardados", "muestra los itinerarios", "muestrame los itinerarios", "ver los itinerarios",
        "que itinerarios tengo",
        "i miei itinerari", "itinerari salvati", "mostrami gli itinerari",
        "my itineraries", "saved itineraries", "show the itineraries", "show my itineraries",
    )
    private val FORGET_PROFILE = listOf(
        "olvida mis preferencias", "olvida mis gustos", "borra mis preferencias", "borra mis gustos",
        "dimentica le mie preferenze", "dimentica i miei gusti", "cancella le mie preferenze",
        "forget my preferences", "forget what i like", "delete my preferences", "clear my preferences",
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
    private class Category(
        val keys: List<String>,
        val spanish: String,
        val italian: String,
        /**
         * La categoría canónica del buscador de mapas. Con ella se piden **los
         * más cercanos de ese tipo**, sin depender del idioma de la consulta:
         * "distributore di benzina" en texto libre devolvía gasolineras de
         * Italia a 10 000 km de Cartagena; como categoría, la Terpel a 600 m.
         */
        val searchId: String? = null,
    )

    private val CATEGORIES: List<Category> = listOf(
        // Las cocinas van **antes** que "restaurante": se comprueba en orden, y
        // "dame restaurantes de comida mejicana" acababa buscando restaurantes
        // a secas porque "restaurantes" y "comida" casaban primero.
        Category(listOf("mexicana", "mexicano", "mexicanos", "mexicanas", "mejicana", "mejicano", "messicano", "messicana", "messicani", "mexican"), "restaurante mexicano", "ristorante messicano", "mexican_restaurant"),
        Category(listOf("comida italiana", "restaurante italiano", "italiana", "italiano", "italian food", "italian restaurant", "cucina italiana", "trattoria"), "restaurante italiano", "ristorante italiano", "italian_restaurant"),
        Category(listOf("china", "chino", "chinos", "cinese", "cinesi", "chinese"), "restaurante chino", "ristorante cinese", "chinese_restaurant"),
        Category(listOf("sushi"), "sushi", "sushi", "sushi_restaurant"),
        Category(listOf("japonesa", "japonés", "japones", "giapponese", "japanese", "ramen"), "restaurante japonés", "ristorante giapponese", "japanese_restaurant"),
        Category(listOf("peruana", "peruano", "peruviano", "peruviana", "peruvian", "ceviche", "cebiche"), "restaurante peruano", "ristorante peruviano", "peruvian_restaurant"),
        Category(listOf("marisco", "mariscos", "marisquería", "marisqueria", "pescado", "pescados", "pesce", "frutti di mare", "seafood", "fish"), "marisquería", "ristorante di pesce", "seafood_restaurant"),
        Category(listOf("asado", "parrilla", "carne asada", "steak", "steakhouse", "bistecca", "grigliata"), "asadero", "steakhouse", "steakhouse"),
        Category(listOf("comida india", "restaurante indio", "hindú", "hindu", "indiano", "indiana", "indian food", "indian restaurant", "curry"), "restaurante indio", "ristorante indiano", "indian_restaurant"),
        Category(listOf("tailandesa", "tailandés", "thai", "tailandese"), "restaurante tailandés", "ristorante thailandese", "thai_restaurant"),
        Category(listOf("oriental", "orientale", "asiática", "asiatica", "asiatico", "asian", "comida oriental", "cucina orientale"), "restaurante asiático", "ristorante asiatico", "asian_restaurant"),
        Category(listOf("comida rápida", "comida rapida", "fast food", "cibo veloce"), "comida rápida", "fast food", "fast_food"),
        Category(listOf("comida típica", "comida tipica", "comida colombiana", "comida criolla", "cucina tipica", "local food"), "comida típica", "cucina tipica", "latin_american_restaurant"),
        Category(listOf("comer", "comida", "hambre", "restaurante", "restaurantes", "almorzar", "cenar", "desayunar", "eat", "food", "hungry", "restaurant", "restaurants", "lunch", "dinner", "breakfast", "mangiare", "cibo", "fame", "ristorante", "ristoranti", "pranzo", "pranzare", "cena", "cenare", "colazione", "trattoria", "osteria"), "restaurante", "ristorante", "restaurant"),
        Category(listOf("bar", "bares", "cerveza", "copa", "copas", "beer", "drink", "drinks", "pub", "birra", "aperitivo", "bere"), "bar", "bar", "bar"),
        Category(listOf("café", "cafe", "cafetería", "cafeteria", "coffee", "caffè", "caffetteria", "cappuccino"), "café", "caffè", "cafe"),
        Category(listOf("gasolina", "gasolinera", "combustible", "tanquear", "echar gasolina", "gas station", "fuel", "petrol", "benzina", "benzinaio", "distributore", "carburante", "rifornimento"), "gasolinera", "distributore di benzina", "gas_station"),
        Category(listOf("parquear", "parqueadero", "aparcar", "aparcamiento", "estacionar", "parking", "park the car", "parcheggio", "parcheggiare", "posteggio"), "parqueadero", "parcheggio", "parking_lot"),
        Category(listOf("farmacia", "droguería", "drogueria", "pharmacy", "drugstore"), "farmacia", "farmacia", "pharmacy"),
        Category(listOf("cajero", "banco", "atm", "bank", "bancomat", "banca"), "cajero automático", "bancomat", "atm"),
        Category(listOf("supermercado", "mercado", "supermarket", "grocery", "supermercato", "alimentari"), "supermercado", "supermercato", "supermarket"),
        Category(listOf("hotel", "hoteles", "dormir", "hostal", "albergo", "dormire", "alloggio", "alojamiento", "alojamientos", "hospedaje", "posada", "accommodation", "lodging", "pernottare"), "hotel", "hotel", "hotel"),
        Category(listOf("hospital", "urgencias", "clínica", "clinica", "emergency", "ospedale", "pronto soccorso"), "hospital", "ospedale", "hospital"),
        Category(listOf("baño", "baños", "toilet", "restroom", "bathroom", "bagno", "bagni", "toilette"), "baño público", "bagno pubblico"),
        Category(listOf("pizza", "pizzería", "pizzeria", "pizería", "pizeria"), "pizza", "pizzeria", "pizza_restaurant"),
        Category(listOf("hamburguesa", "hamburguesas", "burger", "burgers", "hamburger"), "hamburguesa", "hamburger", "burger_restaurant"),
        Category(listOf("sushi"), "sushi", "sushi"),
        Category(listOf("shawarma", "shawarmas", "kebab", "döner", "doner"), "shawarma", "kebab", "middle_eastern_restaurant"),
        Category(listOf("tacos", "taco"), "tacos", "tacos", "taco_shop"),
        Category(listOf("helado", "heladería", "heladeria", "ice cream", "gelato", "gelati", "gelateria"), "helado", "gelateria", "ice_cream"),
        Category(listOf("panadería", "panaderia", "pan", "bakery", "panificio", "forno", "pasticceria"), "panadería", "panificio", "bakery"),
        // Lo que hace falta en carretera, más allá de comer y dormir.
        Category(listOf("taller", "mecánico", "mecanico", "llanta", "llantas", "pinchazo", "grúa", "grua", "mechanic", "garage", "tire", "tyre", "tow", "meccanico", "officina", "gommista", "gomma", "gomme", "foratura", "carro attrezzi"), "taller mecánico", "officina meccanica", "auto_repair"),
        Category(listOf("cargador", "cargar el coche", "carga eléctrica", "electrolinera", "ev charger", "charging station", "charge the car", "colonnina", "ricarica", "ricaricare", "stazione di ricarica"), "estación de carga eléctrica", "stazione di ricarica", "charging_station"),
        Category(listOf("lavadero", "lavar el carro", "lavar el coche", "car wash", "autolavaggio", "lavare la macchina"), "lavadero de carros", "autolavaggio", "car_wash"),
        Category(listOf("aeropuerto", "airport", "aeroporto"), "aeropuerto", "aeroporto", "airport"),
        Category(listOf("terminal", "estación de buses", "estacion de buses", "bus station", "stazione", "stazione dei treni", "treno", "autobus"), "terminal de transporte", "stazione", "bus_station"),
        Category(listOf("cambio", "casa de cambio", "cambiar dinero", "exchange", "money exchange", "cambio valuta", "cambiare soldi"), "casa de cambio", "cambio valuta", "currency_exchange"),
        Category(listOf("policía", "policia", "police", "polizia", "carabinieri", "questura"), "policía", "polizia", "police_station"),
        Category(listOf("playa", "beach", "spiaggia"), "playa", "spiaggia", "beach"),
        Category(listOf("mirador", "viewpoint", "view point", "lookout", "belvedere", "panorama"), "mirador", "belvedere", "viewpoint"),
        Category(listOf("tienda", "minimarket", "convenience store", "shop", "negozio", "tabaccheria", "tabacchi"), "tienda", "negozio", "convenience_store"),
        Category(listOf("centro comercial", "mall", "shopping", "centro commerciale"), "centro comercial", "centro commerciale", "shopping_mall"),
        // Servicios corrientes, de viaje y de todos los días.
        Category(listOf("cine", "cinema", "película", "pelicula", "film", "movie", "movies"), "cine", "cinema", "cinema"),
        Category(listOf("lavandería", "lavanderia", "laundry", "lavar ropa", "lavare i vestiti"), "lavandería", "lavanderia", "laundry"),
        Category(listOf("ropa", "vestiti", "abbigliamento", "clothes", "clothing", "tienda de ropa", "negozio di abbigliamento"), "tienda de ropa", "negozio di abbigliamento", "clothing_store"),
        Category(listOf("zapatos", "zapatería", "zapateria", "scarpe", "shoes", "shoe store"), "zapatería", "negozio di scarpe", "shoe_store"),
        Category(listOf("ferretería", "ferreteria", "ferramenta", "hardware store"), "ferretería", "ferramenta", "hardware_store"),
        Category(listOf("correo", "correos", "oficina de correos", "ufficio postale", "poste", "post office"), "oficina de correos", "ufficio postale", "post_office"),
        Category(listOf("alquiler de carros", "alquiler de coches", "rentar un carro", "autonoleggio", "noleggio auto", "car rental", "rent a car"), "alquiler de carros", "autonoleggio", "car_rental"),
        Category(listOf("taxi", "taxis"), "taxi", "taxi", "taxi"),
        Category(listOf("dentista", "odontólogo", "odontologo", "dentist"), "dentista", "dentista", "dentist"),
        Category(listOf("veterinario", "veterinaria", "vet", "veterinarian"), "veterinario", "veterinario", "veterinarian"),
        Category(listOf("discoteca", "rumba", "bailar", "ballare", "night club", "nightclub", "nightlife", "vida nocturna"), "discoteca", "discoteca", "nightlife"),
    )

    /** Verbos tras los que viene lo que se busca. */
    private val SEARCH_VERBS = listOf("busca", "buscar", "búscame", "buscame", "encuentra", "encuéntrame", "dónde hay", "donde hay", "hay algún", "hay alguna",
        // "¿Hay un OXXO cerca?": una cadena, por su nombre. Sin esto acababa en "¿qué hay cerca?".
        "hay un", "hay una", "quiero", "necesito", "find", "search for", "look for", "where is there", "is there a", "is there an", "i need", "i want")

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
        // Comunes a muchos nombres: por sí solas no identifican un sitio.
        "san", "santa", "santo", "saint", "casa", "llevabame", "llevarme", "llevanos",
    )
}
