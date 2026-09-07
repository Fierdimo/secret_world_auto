package com.secretworld.auto.domain

/**
 * La palabra de activación: **quien conduce no puede pulsar un botón**.
 *
 * Un nombre propio es lo peor que se le puede dar a un reconocedor de voz: no
 * está en su diccionario, así que nunca lo transcribe igual. "Siky" vuelve como
 * *sicky*, *sikhi*, *psyche* o *sky*. Por eso se compara contra una **lista
 * explícita** de las formas en que puede volver.
 *
 * Se probó a admitir cualquier palabra a un error de edición de distancia, y es
 * peor: "sticky" y "sickly" son palabras corrientes y quedan a un error. Una lista
 * se puede auditar y **crece con lo que se ve en el registro**; una distancia
 * acepta lo que nadie ha mirado.
 *
 * Dos reglas que quitan casi todos los disparos falsos:
 *
 * - La palabra tiene que ir **al principio**. Nadie dice "¿qué hay cerca, Siky?"
 *   antes de nombrarla; y en cambio "sky" aparece en medio de cualquier frase.
 * - Detrás puede venir la orden en la misma frase. "Siky, ¿qué hay cerca?" es una
 *   sola cosa dicha de un tirón, y tratarla como dos —despertar y luego escuchar—
 *   obliga al conductor a repetirse.
 */
object WakeWord {

    /** Lo que se pronuncia. Un nombre corto y con consonantes duras se reconoce mejor. */
    const val SPOKEN = "siky"

    /**
     * Transcripciones que el reconocedor devuelve para [SPOKEN].
     *
     * No son suposiciones: son las formas plausibles de un nombre inventado en un
     * motor entrenado en inglés. Se amplía con lo que se vea en el registro.
     */
    private val ALIASES = setOf(
        "siky", "sikky", "sicky", "sikey", "sikee", "sikhi", "sikh",
        "psyche", "psychy", "sky", "ski", "seeky", "seekee", "ciky", "cyky",
        // Medido: al decir "siky" al teléfono, el reconocedor devuelve «silky».
        // Se descartó por ser palabra corriente y el dispositivo dijo lo
        // contrario. Manda la medida.
        "silky", "silkie", "sylky",
    )

    /**
     * Devuelve la orden que sigue a la palabra de activación, o `null` si la frase
     * no empieza por ella.
     *
     * Una cadena **vacía** no es lo mismo que `null`: significa que la despertaron
     * sin decirle nada más, y eso merece respuesta ("dime"), no silencio.
     */
    fun commandIn(heard: String): String? {
        val trimmed = heard.trim()
        val cut = trimmed.indexOfFirst { it.isWhitespace() }
        val first = if (cut < 0) trimmed else trimmed.substring(0, cut)
        if (!matches(first)) return null

        // El resto se devuelve **tal cual llegó**: quien lo interpreta ya
        // normaliza, y para enseñarlo en pantalla la frase original se lee mejor
        // que una versión sin apóstrofos ni comas.
        if (cut < 0) return ""
        return trimmed.substring(cut).trimStart(' ', ',', '.', ':', ';').trim()
    }

    /** ¿Es esta palabra un intento de decir [SPOKEN]? */
    fun matches(word: String): Boolean {
        val candidate = word.lowercase().filter { it.isLetterOrDigit() }
        if (candidate.isEmpty()) return false
        return candidate in ALIASES
    }
}
