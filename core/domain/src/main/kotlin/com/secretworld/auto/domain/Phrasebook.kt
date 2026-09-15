package com.secretworld.auto.domain

/**
 * Lo que el asistente **dice** cuando la gramática entiende la orden.
 *
 * Es la capa rápida: el modelo de lenguaje sabe redactar, pero cuesta un segundo
 * por frase; para las órdenes que la gramática resuelve al instante la respuesta
 * también tiene que ser instantánea. Lo que la hace natural no es el modelo,
 * es el **contexto**: nombrar lo que se acaba de enumerar, el lugar del que se
 * habla, lo que viene después. Y no repetirse: varias formas de decir lo mismo,
 * elegidas por turno.
 *
 * Todo en inglés por ahora, como la voz de la v1. El italiano llega con la voz
 * italiana (SPEC RF-1.7), y será otra tabla, no otra lógica.
 */
object Phrasebook {

    /** Lo que hace falta saber para contestar con sentido. */
    data class Context(
        val here: Coordinates,
        /** Lo cercano, en orden de la cola. */
        val around: List<Place>,
        val memory: ConversationMemory,
    ) {
        val turn: Int get() = memory.turns.size
    }

    /** Un lugar con su distancia dicha, para enumerar. */
    fun named(place: Place, from: Coordinates): String =
        "${shortName(place)}, ${spokenDistance(from.distanceTo(place.position))}"

    fun nothingNearby(c: Context): String = pick(
        c,
        "I don't have anything worth a stop around here yet.",
        "Nothing on my list around here. I'll tell you when something comes up.",
    )

    /** Enumera y pregunta: lo que hace que "el segundo" signifique algo después. */
    fun nearby(c: Context, listed: List<Place>): String {
        val names = listed.map { named(it, c.here) }
        val opening = pick(c, "Around you: ", "Nearby there's ", "Close to you: ")
        val list = when (names.size) {
            1 -> names[0]
            2 -> "${names[0]} and ${names[1]}"
            else -> names.dropLast(1).joinToString("; ") + "; and " + names.last()
        }
        val ask = pick(c, "Which one?", "Want to hear about any of them?", "Say the name, or first, second, third.")
        return "$opening$list. $ask"
    }

    fun playing(c: Context, place: Place): String = pick(
        c,
        "Here's ${shortName(place)}.",
        "${shortName(place)}, coming up.",
        "Sure — ${shortName(place)}.",
    )

    fun noMore(c: Context): String {
        val focus = c.memory.focus ?: return "Nothing is playing right now. Ask me what's nearby."
        return pick(
            c,
            "That's all I have on ${shortName(focus)} for now.",
            "I've told you what I know about ${shortName(focus)}. Want to hear what else is around?",
        )
    }

    fun noNavigation(c: Context): String {
        val focus = c.memory.focus
        val target = focus?.let { " to ${shortName(it)}" }.orEmpty()
        return pick(
            c,
            "I can't set a destination$target yet — that part isn't wired to the car's navigation.",
            "Navigation$target isn't available yet. I can keep telling you about it, though.",
        )
    }

    fun skipped(c: Context, skipped: Place, next: Place?): String {
        val onward = next?.let { " Next up is ${shortName(it)}." }.orEmpty()
        return pick(c, "Skipping ${shortName(skipped)}.$onward", "Okay, leaving ${shortName(skipped)}.$onward")
    }

    fun nothingToSkip(c: Context): String = pick(c, "There's nothing playing to skip.", "Nothing to skip right now.")

    fun stopped(c: Context): String = pick(c, "Okay.", "Sure.", "Alright, I'll be quiet.")

    fun notUnderstood(c: Context): String {
        val hint = c.around.firstOrNull()?.let { " Or ask about ${shortName(it)}." }.orEmpty()
        return pick(
            c,
            "Sorry, I didn't catch that. You can ask what's nearby, or name a place.$hint",
            "I didn't get that. Try \"what's nearby\", or say a place.$hint",
            "Say that again? I can tell you what's around, or about a place.$hint",
        )
    }

    /** Distancias como se dicen, no como se miden: nadie oye "1 437 metros". */
    fun spokenDistance(meters: Double): String = when {
        meters < 100 -> "right here"
        meters < 950 -> "${(meters / 50).toInt() * 50} metres"
        else -> "%.1f kilometres".format(meters / 1000).replace(".0 ", " ")
    }

    /**
     * El nombre como se dice: "The mosaics of the Basilica of St. Cecilia in
     * Trastevere" es una ficha, no algo que un guía diga en voz alta.
     */
    fun shortName(place: Place): String {
        val words = place.name.split(' ')
        return if (words.size <= 6) place.name else words.take(6).joinToString(" ")
    }

    /** Elige una variante por turno, para no decir siempre lo mismo. */
    private fun pick(c: Context, vararg variants: String): String = variants[c.turn % variants.size]
}
