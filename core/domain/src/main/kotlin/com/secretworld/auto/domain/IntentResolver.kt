package com.secretworld.auto.domain

/**
 * Convierte lo que se oyó en una orden.
 *
 * Existe como interfaz porque hay **dos formas de entender**, y conviene que el
 * resto de la app no sepa cuál está actuando: la gramática cerrada de
 * [VoiceGrammar], instantánea y auditable, y un modelo de lenguaje pequeño en el
 * dispositivo que atiende lo que la gramática no previó. Las dos devuelven lo
 * mismo, [VoiceCommand], y ninguna puede inventarse una acción nueva.
 */
interface IntentResolver {

    /**
     * @param heard lo que devolvió el reconocedor, tal cual.
     * @param candidates lugares entre los que resolver un nombre o un "eso".
     * @param memory lo que se recuerda de la conversación: el lugar en foco, lo
     *   último enumerado y las últimas frases.
     */
    suspend fun resolve(heard: String, candidates: List<Place>, memory: ConversationMemory): Resolution
}

/**
 * Lo que se entendió y, si quien lo entendió sabe redactar, lo que hay que decir.
 *
 * @param reply la frase para el conductor, o `null` para que la ponga el
 *   [Phrasebook]. La gramática no redacta; el modelo sí, con el contexto que
 *   se le dio.
 */
data class Resolution(val command: VoiceCommand, val reply: String? = null)

/** La gramática cerrada del nivel 1, envuelta. No hace nada que no hiciera ya. */
class GrammarIntentResolver : IntentResolver {
    override suspend fun resolve(heard: String, candidates: List<Place>, memory: ConversationMemory): Resolution =
        Resolution(VoiceGrammar.parse(heard, candidates, memory.focus, memory.listed))
}

/**
 * Primero la gramática; si no entiende, el modelo.
 *
 * El orden importa y no es negociable: la gramática cuesta microsegundos y no se
 * equivoca en lo que sabe, así que lo que ya funciona hoy **no pasa por el
 * modelo** y no pierde velocidad. El modelo solo ve las frases que hasta ahora
 * acababan en "no te he entendido".
 */
class LayeredIntentResolver(
    private val grammar: IntentResolver,
    private val fallback: IntentResolver?,
) : IntentResolver {

    override suspend fun resolve(heard: String, candidates: List<Place>, memory: ConversationMemory): Resolution {
        val first = grammar.resolve(heard, candidates, memory)
        if (first.command !is VoiceCommand.NotUnderstood || fallback == null) return first
        return fallback.resolve(heard, candidates, memory)
    }
}

/**
 * Un modelo de lenguaje que completa texto. Nada más.
 *
 * Se separa en **prefijo** y **petición** a propósito: el prefijo —las
 * instrucciones y las herramientas— es igual en todas las llamadas, y la
 * implementación puede dejarlo procesado y reutilizarlo. En un modelo pequeño lo
 * caro no es generar diez tokens, es leer cuatrocientos; sin esta separación cada
 * frase del conductor costaría segundos.
 */
interface LanguageModel {
    /** Si se puede llamar ahora. Un modelo que aún se está cargando no lo está. */
    val ready: Boolean

    /**
     * @param singleLine si la respuesta es una línea y se corta en el primer
     *   salto —lo normal: elegir una herramienta—, o un texto entero, como una
     *   traducción.
     */
    suspend fun complete(prefix: String, request: String, singleLine: Boolean = true): String
}

/**
 * Entiende con un modelo de lenguaje lo que la gramática no previó.
 *
 * **El modelo no decide, elige.** Se le da la lista de herramientas —que son
 * exactamente las órdenes del nivel 1— y los lugares cercanos numerados, y se le
 * pide una sola línea JSON. Lo que no sea una herramienta conocida con
 * argumentos válidos se descarta: un modelo de 1B parámetros se equivoca, y la
 * respuesta a un error suyo es "no te he entendido", nunca una acción que nadie
 * pidió.
 *
 * Los lugares se numeran en vez de pasar sus identificadores: copiar un id largo
 * es justo lo que un modelo pequeño hace mal, y un número del 1 al 10 no.
 */
class LlmIntentResolver(
    private val model: LanguageModel,
    /** Desde dónde se mide lo que está cerca, para que el modelo pueda decirlo. */
    private val position: () -> Coordinates? = { null },
    private val language: VoiceLanguage = VoiceLanguage.ENGLISH,
    /**
     * A quién contarle que el modelo falló. El dominio no tiene registro; sin
     * esto, un fallo del modelo es indistinguible de "no entendí" y nadie se
     * entera de que el modelo no llegó a hablar.
     */
    private val onFailure: (Throwable) -> Unit = {},
) : IntentResolver {

    override suspend fun resolve(heard: String, candidates: List<Place>, memory: ConversationMemory): Resolution {
        if (!model.ready || heard.isBlank()) return Resolution(VoiceCommand.NotUnderstood(heard))
        // Lo último enumerado va delante: si el conductor dice "el segundo", ese
        // es el segundo. Después, lo cercano que no estuviera ya.
        val shortlist = (memory.listed + candidates).distinctBy { it.id }.take(MAX_CANDIDATES)
        val reply = try {
            model.complete(IntentPrompt.PREFIX, IntentPrompt.request(heard, shortlist, memory, position(), language))
        } catch (e: Exception) {
            onFailure(e)
            return Resolution(VoiceCommand.NotUnderstood(heard))
        }
        return IntentPrompt.parse(reply, heard, shortlist, memory.focus)
    }

    private companion object {
        const val MAX_CANDIDATES = 6
    }
}

/**
 * El texto que se le da al modelo y cómo se lee lo que devuelve.
 *
 * Es un objeto aparte, en Kotlin puro, para poder probarlo entero sin cargar un
 * modelo: la mitad de los fallos de un asistente así están en el parseo, no en
 * el modelo.
 */
object IntentPrompt {

    /** Nombres de herramienta, tal y como las ve el modelo. */
    const val TELL_ME_ABOUT = "tell_me_about"
    const val WHAT_IS_NEARBY = "what_is_nearby"
    const val TELL_ME_MORE = "tell_me_more"
    const val TAKE_ME_THERE = "take_me_there"
    const val SKIP = "skip"
    const val STOP = "stop"
    const val CLARIFY = "clarify"
    const val NONE = "none"

    /**
     * Instrucciones y herramientas. **No cambia entre llamadas**, y por eso va
     * separado: es lo que la implementación puede dejar procesado.
     *
     * La respuesta pedida es **una línea con el nombre de la herramienta**, no
     * JSON: son cinco tokens en vez de veinte, y en un modelo de 1B cada token
     * generado son decenas de milisegundos. No hay nada que analizar salvo
     * partir por espacios.
     */
    val PREFIX: String = """
        |You are the voice of a driving companion app that narrates places near the car. You map what the driver says to ONE tool and say one short, warm sentence back. The driver may speak English or Italian: answer in the driver's language. The speech recogniser makes mistakes: judge by sound and meaning, not spelling.
        |
        |Tools:
        |$WHAT_IS_NEARBY — list what is around ("what's nearby", "anything around here", misheard as "what's new by", "glass new buy"). Name the places from the list with their distance and end with a question.
        |$TELL_ME_ABOUT N — narrate place N from the list. "That", "this one", "there" = the place playing now. "The second one" = 2. Say a few words of introduction, the narration follows.
        |$TELL_ME_MORE — more about the place playing now.
        |$TAKE_ME_THERE N — drive to place N ("take me to", "directions to", "let's go to"). Without N: the place playing now. Say you're routing there.
        |$SKIP — skip the current place.
        |$STOP — stop talking ("enough", "quiet"). Say one word.
        |$CLARIFY — you understood a request but not which place: ask which one.
        |$NONE — the driver was not talking to the assistant. Say nothing.
        |
        |Answer with ONE line: the tool, the number if needed, then " | " and what to say: one or two short sentences, under 25 words. Nothing else.
        |
        |Places: 1. The Roman Forum, 300 metres 2. Circus Maximus, 650 metres 3. Basilica of St. Cecilia, 1.2 kilometres
        |Driver: "can you tell me something about that church over there"
        |$TELL_ME_ABOUT 3 | Of course — the Basilica of St. Cecilia, just over a kilometre away.
        |Driver: "glass new buy"
        |$WHAT_IS_NEARBY | Right around you there's the Roman Forum, 300 metres ahead, and Circus Maximus a bit further. Want to hear about either?
        |Driver: "ok that's enough thanks"
        |$STOP | Sure.
        |Driver: "the second one please"
        |$TELL_ME_ABOUT 2 | Circus Maximus it is.
        |Driver: "let's drive to the forum"
        |$TAKE_ME_THERE 1 | Routing you to the Roman Forum, 300 metres from here.
        |Driver: "cosa c'è qui vicino"
        |$WHAT_IS_NEARBY | Qui vicino ci sono il Foro Romano, a 300 metri, e il Circo Massimo. Ti racconto uno dei due?
        |Driver: "which one is the old market"
        |$CLARIFY | I'm not sure which market you mean — the one near the Forum?
        |
    """.trimMargin()

    /** La parte que cambia: los lugares de ahora, la conversación reciente y lo que se acaba de oír. */
    fun request(
        heard: String,
        candidates: List<Place>,
        memory: ConversationMemory,
        here: Coordinates? = null,
        language: VoiceLanguage = VoiceLanguage.ENGLISH,
    ): String = buildString {
        // El idioma se dice explícitamente: con una frase corta el modelo no
        // siempre lo adivina, y contestar en el idioma equivocado es lo que
        // más rompe la ilusión de que hay alguien escuchando.
        appendLine("Driver language: ${when (language) { VoiceLanguage.SPANISH -> "Spanish"; VoiceLanguage.ITALIAN -> "Italian"; else -> "English" }}. Answer in that language.")
        // Nombres cortos y con distancia: es lo que el modelo necesita para
        // redactar, y nada más. Cada token de más se lee en cada orden.
        append("Places: ")
        candidates.forEachIndexed { index, place ->
            append("${index + 1}. ")
            append(if (here != null) Phrasebook.named(place, here) else Phrasebook.shortName(place))
            append(' ')
        }
        if (candidates.isEmpty()) append("(none)")
        appendLine()
        appendLine("Playing now: ${memory.focus?.let { Phrasebook.shortName(it) } ?: "nothing"}")
        append(memory.transcript())
        appendLine("Driver: \"${heard.trim()}\"")
    }

    /**
     * Lee la respuesta del modelo: la **primera línea con contenido**, partida
     * por espacios. Tolera un JSON de la forma antigua por si el modelo se
     * empeña, y es estricta con el resto: herramienta desconocida o número
     * fuera de la lista es "no entendí", nunca una acción.
     */
    fun parse(reply: String, heard: String, candidates: List<Place>, focus: Place?): Resolution {
        val line = reply.lineSequence()
            .map { it.trim().trim('`', '*', '"', '\'') }
            // Ni la valla de código ni la etiqueta "json" que la acompaña son la respuesta.
            .firstOrNull { it.isNotEmpty() && !it.equals("json", ignoreCase = true) && !it.startsWith("Driver:", ignoreCase = true) }
            ?: return Resolution(VoiceCommand.NotUnderstood(heard))

        JSON_OBJECT.find(line)?.let { json ->
            val tool = stringField(json.value, "tool") ?: return Resolution(VoiceCommand.NotUnderstood(heard))
            return Resolution(command(tool, intField(json.value, "place"), stringField(json.value, "question"), heard, candidates, focus))
        }

        // Herramienta a la izquierda de la barra, frase a la derecha. La
        // herramienta puede venir con adornos ("The tool: what_is_nearby"); se
        // busca su nombre en cualquier parte, no en la primera palabra.
        val head = line.substringBefore('|')
        val spoken = if (line.contains('|')) line.substringAfter('|').trim().trim('"').takeIf { it.isNotEmpty() } else null
        val found = TOOL_NAME.find(head) ?: return Resolution(VoiceCommand.NotUnderstood(heard))
        val tool = found.value.lowercase()
        val rest = head.substring(found.range.last + 1).trim()
        val number = Regex("\\d+").find(rest)?.value?.toIntOrNull()
        // La pregunta de una aclaración es la frase misma; si no la hay, lo que
        // siga al nombre en la parte izquierda (forma antigua "clarify: …").
        val question = spoken ?: rest.trimStart(':', '-', ' ').ifEmpty { null }
        val command = command(tool, number, question, heard, candidates, focus)
        // Una frase que dice algo solo se acepta con una orden entendida: si el
        // modelo no supo qué herramienta era, tampoco se fía uno de su frase.
        val reply = spoken?.takeIf { command !is VoiceCommand.NotUnderstood && it.length <= MAX_REPLY_CHARS }
        return Resolution(command, reply)
    }

    private fun command(
        tool: String,
        number: Int?,
        question: String?,
        heard: String,
        candidates: List<Place>,
        focus: Place?,
    ): VoiceCommand = when (tool.lowercase()) {
        STOP -> VoiceCommand.Stop
        SKIP -> VoiceCommand.Skip
        WHAT_IS_NEARBY -> VoiceCommand.WhatIsNearby
        TELL_ME_MORE -> VoiceCommand.TellMeMore
        TAKE_ME_THERE -> VoiceCommand.TakeMeThere(number?.let { candidates.getOrNull(it - 1) } ?: focus)
        TELL_ME_ABOUT -> {
            val place = number?.let { candidates.getOrNull(it - 1) } ?: focus
            if (place != null) VoiceCommand.TellMeAbout(place) else VoiceCommand.NotUnderstood(heard)
        }
        CLARIFY -> {
            val q = question?.trim().orEmpty()
            if (q.isNotEmpty()) VoiceCommand.Clarify(q) else VoiceCommand.NotUnderstood(heard)
        }
        else -> VoiceCommand.NotUnderstood(heard)
    }

    private fun stringField(json: String, name: String): String? =
        Regex("\"$name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")

    private fun intField(json: String, name: String): Int? =
        Regex("\"$name\"\\s*:\\s*\"?(\\d+)\"?").find(json)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * El primer objeto JSON de una sola capa. No hay anidamiento en lo que se pide.
     *
     * La llave de cierre va escapada a propósito: la JVM del escritorio acepta
     * `}` suelta, pero el motor ICU de Android no, y el objeto entero fallaba al
     * inicializarse **solo en el dispositivo**. Las pruebas no lo ven.
     */
    private val JSON_OBJECT = Regex("\\{[^{}]*\\}")

    /** Más largo que esto no es una frase para decir conduciendo. */
    private const val MAX_REPLY_CHARS = 220

    private val TOOL_NAME = Regex(
        listOf(TELL_ME_ABOUT, WHAT_IS_NEARBY, TELL_ME_MORE, TAKE_ME_THERE, SKIP, STOP, CLARIFY, NONE)
            .joinToString("|", prefix = "\\b(", postfix = ")\\b"),
        RegexOption.IGNORE_CASE,
    )
}
