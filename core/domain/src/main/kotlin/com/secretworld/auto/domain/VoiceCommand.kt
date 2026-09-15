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
