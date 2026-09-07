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

    /** "Llévame ahí" → fija el destino en el navegador del vehículo. */
    data object TakeMeThere : VoiceCommand

    /** "Sáltalo", "ahora no" → descarta y pasa al siguiente. */
    data object Skip : VoiceCommand

    /** "Calla", "para" → corta la narración. */
    data object Stop : VoiceCommand

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
     */
    fun parse(heard: String, candidates: List<Place>, focus: Place? = null): VoiceCommand {
        val text = normalise(heard)
        if (text.isBlank()) return VoiceCommand.NotUnderstood(heard)

        if (STOP.any { text.contains(it) }) return VoiceCommand.Stop
        if (SKIP.any { text.contains(it) }) return VoiceCommand.Skip
        if (NEARBY.any { text.contains(it) }) return VoiceCommand.WhatIsNearby
        if (MORE.any { text.contains(it) }) return VoiceCommand.TellMeMore
        if (NAVIGATE.any { text.contains(it) }) return VoiceCommand.TakeMeThere

        // Un lugar nombrado gana siempre al deíctico: si dice "cuéntame el Foro"
        // mientras suena otra cosa, quiere el Foro.
        resolve(text, candidates)?.let { return VoiceCommand.TellMeAbout(it) }

        // "¿Qué es eso?" solo significa algo si hay un "eso".
        if (ABOUT.any { text.contains(it) } || DEICTIC.any { text.contains(it) }) {
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

    private fun normalise(text: String): String =
        text.lowercase()
            .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")

    /** Órdenes de corte. Se comprueban las primeras, a propósito. */
    private val STOP = listOf("stop", "quiet", "silence", "be quiet", "shut up", "enough")
    private val SKIP = listOf("skip", "not now", "next one", "next place", "later")
    private val NEARBY = listOf(
        "what s nearby", "whats nearby", "what is nearby", "nearby", "around me",
        "around here", "what s around", "whats around", "what is around",
    )
    private val MORE = listOf("tell me more", "more about", "go on", "keep going")
    private val NAVIGATE = listOf("take me there", "take me to", "navigate", "drive me", "go there")

    /** Formas de pedir que se cuente algo. */
    private val ABOUT = listOf("tell me about", "what is", "what s", "whats", "who was", "talk about")

    /** Deícticos: solo tienen sentido con un lugar en foco. */
    private val DEICTIC = listOf("that", "this", "it", "there")

    /**
     * Palabras que no distinguen un lugar de otro. Sin esta lista, "the" empareja
     * con la mitad del catálogo y cualquier frase resolvería al primer lugar.
     */
    private val FILLER = setOf(
        "the", "and", "for", "with", "from", "que", "los", "las",
        "tell", "about", "what", "who", "was", "were", "this", "that", "there",
        "please", "hey", "secret", "world",
    )
}
