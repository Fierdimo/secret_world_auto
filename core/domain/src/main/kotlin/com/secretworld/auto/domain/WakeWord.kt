package com.secretworld.auto.domain

/**
 * La palabra de activación: **quien conduce no puede pulsar un botón**.
 *
 * Es "Sícret" —la marca, dicha como suena— a secas: un "eh" o un "hey" delante
 * se admite pero no se exige. Se probó "Siky", el nombre del asistente, y el
 * reconocedor lo escribió de nueve formas en nueve intentos ("Sake", "See Kin",
 * "Cyk", "seeking", "sí qui"): un nombre inventado no tiene ortografía. "Secret"
 * es una palabra real de su vocabulario y la escribe siempre igual. Se detecta
 * **por transcripción** en todos
 * los idiomas: el detector de voz corta la frase, el reconocedor la escribe y
 * se mira si empieza por la palabra. El detector de palabras clave se probó y
 * se descartó: está entrenado solo con inglés, con un nombre inventado dispara
 * a medias y con una palabra española no dispara nunca. Con el reconocedor
 * actual (~110 ms por frase) transcribir todo lo que se oye sale a cuenta.
 *
 * Las transcripciones aceptadas son una **lista explícita** más dos letras de
 * tolerancia sobre la forma canónica: una lista se audita y crece con lo que
 * se ve en el registro; la tolerancia cubre lo que el reconocedor inventa con
 * un nombre que no está en su diccionario ("sicky", "seeky", "psyche").
 */
object WakeWord {

    /** La palabra, que no cambia con el idioma. El asistente se sigue llamando Siky. */
    const val NAME = "Sícret"

    /** Cómo se activa el asistente en un idioma. */
    data class Spec(
        /** Lo que se pronuncia, para enseñarlo en pantalla. */
        val spoken: String,
        /**
         * Tokens BPE para el detector de palabras clave, si algún día vuelve a
         * usarse. Hoy `null` en todos los idiomas: se detecta por transcripción.
         */
        val keywordTokens: String?,
        /** Transcripciones que el reconocedor devuelve al decir [spoken]. */
        val aliases: Set<String>,
        /** Formas canónicas, sin espacios ni acentos, para la comparación con tolerancia. */
        val canonical: Set<String> = emptySet(),
    )

    /**
     * Cómo escribe el reconocedor la palabra: medido, y ampliable con el
     * registro. Primero las formas de "secret"; después, por si alguna cuela,
     * las de "siky".
     */
    private val NAME_FORMS = listOf(
        "secret", "sicret", "sícret", "secreto", "sekret",
        // Con ruido de fondo (música, voces), medido en una cafetería.
        "sigred", "sigret", "sicred", "secred", "segret", "segred",
        // También en la cafetería, y la forma más repetida: 8 veces en dos
        // minutos. "Tigre" es una palabra real, pero al principio de una frase
        // dicha en un coche es mucho más probable que sea la llamada.
        "tigre", "tygre", "tigri", "tigret", "tigred", "ticret", "igre", "igri", "igry",
        // Más formas del registro, demasiado cortas o lejos para la comparación por sonido.
        "icre", "cicre", "dicre", "hicre", "picret", "picred", "tickred", "ticre", "sicred", "cigret", "seacret", "sécret", "secrets", "sicre", "secre", "sicrit", "secrit", "siquet", "secreth",
        "siky", "sicky", "seeky", "sikhi", "psyche", "silky", "sikey", "siki", "sicri", "sikee", "ciky",
    )

    /** El nombre solo, y con cualquiera de las llamadas delante. */
    private fun forms(vararg calls: String): Set<String> =
        (NAME_FORMS + calls.flatMap { call -> NAME_FORMS.map { "$call $it" } }).toSet()

    private val ENGLISH = Spec(
        spoken = NAME,
        keywordTokens = null,
        aliases = forms("hey", "hay", "he", "a", "eh", "hi"),
        canonical = setOf("secret", "heysecret", "asecret"),
    )

    private val SPANISH = Spec(
        spoken = NAME,
        keywordTokens = null,
        aliases = forms("eh", "ey", "ei", "e", "hey", "a", "ay", "oye", "he", "el"),
        canonical = setOf("secret", "sicret", "ehsecret", "eysicret", "elsecreto"),
    )

    private val ITALIAN = Spec(
        spoken = NAME,
        keywordTokens = null,
        // "segreto" es lo que un reconocedor italiano puede escribir al oír
        // "secret", igual que el español escribe "secreto". Sin medir todavía
        // con voz real: ampliar con el registro de la demo.
        aliases = forms("ehi", "ei", "hey", "e", "a", "oh", "ciao") + setOf("segreto", "ehi segreto", "sicreto"),
        canonical = setOf("secret", "sicret", "sekret", "sigret", "ehisecret", "eisecret", "segreto"),
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
        val candidate = Script.latin(phrase.lowercase())
            .map { ACCENTS[it] ?: it }
            .filter { it.isLetterOrDigit() || it == ' ' }
            .joinToString("")
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        if (candidate.isEmpty()) return false
        val aliases = spec.aliases.map { alias -> alias.map { ACCENTS[it] ?: it }.joinToString("") }
        if (candidate in aliases) return true
        val squashed = candidate.replace(" ", "")
        // La palabra sola son seis letras: ahí una de tolerancia; con la
        // llamada delante, dos. Menos de cinco letras no es nada.
        if (squashed.length < 5) return false
        val fuzz = if (squashed.length <= 6) 1 else FUZZ
        if (spec.canonical.any { Script.editDistance(squashed, it) <= fuzz }) return true
        return soundsLikeTheName(candidate)
    }

    /**
     * ¿Suena como la palabra, aunque se escriba distinto? El reconocedor escribe
     * "Sícret" de formas sin fin —«Zikre», «Sikre», «Tigret», «Cigres»,
     * «Digret», «Segret», todas en diez minutos en un móvil—, y una lista no
     * alcanza. Se compara **cómo suena**: z→s, c/g/q→k, d→t, e/y→i. Así todas
     * quedan cerca de "sikrit", y las palabras corrientes ("sigue", "segura")
     * lejos. Solo para una palabra suelta de cinco letras o más.
     */
    private fun soundsLikeTheName(candidate: String): Boolean {
        if (' ' in candidate || candidate.length < 5) return false
        val sound = phonetic(candidate)
        val allowed = if (sound.length <= 5) 1 else 2
        return Script.editDistance(sound, NAME_SOUND) <= allowed
    }

    private fun phonetic(word: String): String = word.map {
        when (it) {
            'z' -> 's'
            'c', 'g', 'q' -> 'k'
            'd' -> 't'
            'e', 'y' -> 'i'
            else -> it
        }
    }.joinToString("")

    /** "Sícret" dicho: s-i-k-r-i-t. */
    private const val NAME_SOUND = "sikrit"


    private const val FUZZ = 2


    private val ACCENTS = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
        'à' to 'a', 'è' to 'e', 'ì' to 'i', 'ò' to 'o', 'ù' to 'u',
    )
}
