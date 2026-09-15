package com.secretworld.auto.domain

/**
 * La palabra de activación: **quien conduce no puede pulsar un botón**.
 *
 * Es **distinta en cada idioma**, y no por capricho. Se midió con voz sintética
 * sobre el detector de palabras clave, que está entrenado solo con inglés: un
 * nombre inventado ("Siky") dispara a medias, y una palabra española —"Hola
 * Secreto", "Oye Mundo", lo que sea— no dispara nunca. Así que cada idioma lleva
 * su palabra **y su forma de detectarla**:
 *
 * - Inglés: "Hey Secret", por el detector de palabras clave. Dos palabras
 *   corrientes que el modelo conoce de sobra: 3 de 3 en la prueba, y dos
 *   palabras dan el doble de evidencia que una contra los falsos disparos.
 * - Español: "Ey Sícret" —la marca dicha como suena—, por transcripción: el
 *   detector de voz corta la frase, Whisper la escribe y se mira si empieza
 *   por la palabra. Más caro, pero funciona en cualquier idioma y acento.
 *
 * Lo que no cambia es la marca: siempre es *Secret*.
 *
 * Las transcripciones aceptadas son una **lista explícita**, no una distancia de
 * edición: una lista se audita y crece con lo que se ve en el registro; una
 * distancia acepta lo que nadie ha mirado.
 */
object WakeWord {

    /** Cómo se activa el asistente en un idioma. */
    data class Spec(
        /** Lo que se pronuncia, para enseñarlo en pantalla. */
        val spoken: String,
        /**
         * Tokens BPE del detector de palabras clave (modelo gigaspeech), o
         * `null` si en este idioma el detector no sirve y se detecta por
         * transcripción.
         */
        val keywordTokens: String?,
        /** Transcripciones que el reconocedor devuelve al decir [spoken]. */
        val aliases: Set<String>,
        /** Formas canónicas, sin espacios ni acentos, para la comparación con tolerancia. */
        val canonical: Set<String> = emptySet(),
    )

    private val ENGLISH = Spec(
        spoken = "Hey Secret",
        keywordTokens = "▁HE Y ▁SE C RE T",
        aliases = setOf("hey secret", "hey secrets", "hey secrete", "a secret", "hey secretly", "hey sea crit", "hey seagret"),
        canonical = setOf("heysecret"),
    )

    /**
     * En español la marca se dice como suena: "Ey Sícret". Whisper lo escribe
     * como le parece —a veces en inglés, a veces fonético—, así que la lista
     * cubre las dos ortografías y crece con lo que se vea en el registro.
     */
    private val SPANISH = Spec(
        spoken = "Ey Sícret",
        keywordTokens = null,
        aliases = setOf(
            "ey sicret", "ey sícret", "ey secret", "ey sicreth", "ey siquet", "ey secreto",
            "hey sicret", "hey sícret", "hey secret", "hey siquet", "hey secreto", "hey secrets",
            "ei sicret", "ei secret", "ay sicret", "ay secret", "eh sicret", "eh secret",
            "e sicret", "e secret", "a secret", "he secret", "hei secret", "hei sicret",
        ),
        canonical = setOf("eysicret", "heysecret", "eysecret", "esecret"),
    )

    private val ITALIAN = Spec(
        spoken = "Ciao Secret",
        keywordTokens = null,
        aliases = setOf("ciao secret", "ciao secreto", "ciao segreto", "ciao secrets"),
        canonical = setOf("ciaosecret"),
    )

    fun forLanguage(language: VoiceLanguage): Spec = when (language) {
        VoiceLanguage.ENGLISH -> ENGLISH
        VoiceLanguage.SPANISH -> SPANISH
        VoiceLanguage.ITALIAN -> ITALIAN
    }

    /** Lo que se pronuncia en el idioma dado. */
    fun spoken(language: VoiceLanguage): String = forLanguage(language).spoken

    /**
     * Devuelve la orden que sigue a la palabra de activación, o `null` si la frase
     * no empieza por ella.
     *
     * Una cadena **vacía** no es lo mismo que `null`: significa que la despertaron
     * sin decirle nada más, y eso merece respuesta ("dime"), no silencio.
     *
     * La palabra son una o dos palabras; se prueban las dos longitudes porque el
     * reconocedor a veces junta ("heysecret") y a veces separa.
     */
    fun commandIn(heard: String, language: VoiceLanguage): String? {
        val spec = forLanguage(language)
        val trimmed = heard.trim()
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null

        for (take in 2 downTo 1) {
            if (words.size < take) continue
            val head = words.take(take).joinToString(" ")
            if (!matches(head, spec)) continue
            val rest = words.drop(take).joinToString(" ")
            // El resto se devuelve **tal cual llegó**: quien lo interpreta ya
            // normaliza, y para enseñarlo en pantalla la frase original se lee
            // mejor que una versión sin apóstrofos ni comas.
            return rest.trimStart(' ', ',', '.', ':', ';', '!', '?').trim()
        }
        return null
    }

    /**
     * ¿Son estas palabras un intento de decir la palabra de activación?
     *
     * Primero la lista; si no, **dos letras de tolerancia** sobre las formas
     * canónicas, sin espacios. Es lo que hace falta cuando la palabra se detecta
     * por transcripción y el reconocedor escribe "e secret", "a seacret" o "el
     * secreto" por "Ey Sícret" —todo visto en el registro—. Solo se compara la
     * cabeza de la frase, así que una palabra parecida en medio no despierta.
     */
    fun matches(phrase: String, spec: Spec): Boolean {
        val candidate = phrase.lowercase()
            .map { ACCENTS[it] ?: it }
            .filter { it.isLetterOrDigit() || it == ' ' }
            .joinToString("")
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        if (candidate.isEmpty()) return false
        val aliases = spec.aliases.map { alias -> alias.map { ACCENTS[it] ?: it }.joinToString("") }
        if (candidate in aliases) return true
        val squashed = candidate.replace(" ", "")
        return spec.canonical.any { editDistance(squashed, it) <= FUZZ }
    }

    private fun editDistance(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev.indices.forEach { prev[it] = cur[it] }
        }
        return prev[b.length]
    }

    private const val FUZZ = 2

    private val ACCENTS = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n')
}
