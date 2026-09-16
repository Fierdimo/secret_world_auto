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
 * Inglés y español; el italiano llega con la voz italiana (SPEC RF-1.7). Cada
 * idioma es otra tabla, no otra lógica: cada función elige por [Context.language].
 */
object Phrasebook {

    /** Lo que hace falta saber para contestar con sentido. */
    data class Context(
        val here: Coordinates,
        /** Lo cercano, en orden de la cola. */
        val around: List<Place>,
        val memory: ConversationMemory,
        val language: VoiceLanguage = VoiceLanguage.ENGLISH,
    ) {
        val turn: Int get() = memory.turns.size
        val spanish: Boolean get() = language == VoiceLanguage.SPANISH
    }

    /** Un lugar con su distancia dicha, para enumerar. */
    fun named(place: Place, from: Coordinates, language: VoiceLanguage = VoiceLanguage.ENGLISH): String =
        "${shortName(place)}, ${spokenDistance(from.distanceTo(place.position), language)}"

    fun nothingNearby(c: Context): String = if (c.spanish) pick(
        c,
        "Por aquí no tengo nada que merezca una parada, de momento.",
        "No tengo nada en la lista por aquí. Te aviso cuando aparezca algo.",
    ) else pick(
        c,
        "I don't have anything worth a stop around here yet.",
        "Nothing on my list around here. I'll tell you when something comes up.",
    )

    /** Enumera y pregunta: lo que hace que "el segundo" signifique algo después. */
    fun nearby(c: Context, listed: List<Place>): String {
        val names = listed.map { named(it, c.here, c.language) }
        val and = if (c.spanish) "y" else "and"
        val list = when (names.size) {
            1 -> names[0]
            2 -> "${names[0]} $and ${names[1]}"
            else -> names.dropLast(1).joinToString("; ") + "; $and " + names.last()
        }
        if (c.spanish) {
            val opening = pick(c, "A tu alrededor: ", "Cerca tienes ", "Por aquí hay ")
            val ask = pick(c, "¿Cuál?", "¿Te cuento alguno?", "Di el nombre, o primero, segundo, tercero.")
            return "$opening$list. $ask"
        }
        val opening = pick(c, "Around you: ", "Nearby there's ", "Close to you: ")
        val ask = pick(c, "Which one?", "Want to hear about any of them?", "Say the name, or first, second, third.")
        return "$opening$list. $ask"
    }

    /** Cuando la historia no está preparada: se avisa, que la espera con aviso no es espera. */
    fun preparing(c: Context, place: Place): String = if (c.spanish) pick(
        c,
        "Un momento, busco lo que sé de ${shortName(place)}.",
        "Dame un segundo y te cuento ${shortName(place)}.",
        "Ahora te cuento ${shortName(place)}; lo estoy preparando.",
    ) else pick(
        c,
        "One moment, let me find what I know about ${shortName(place)}.",
        "Give me a second and I'll tell you about ${shortName(place)}.",
        "Coming right up: ${shortName(place)}. Just preparing it.",
    )

    fun playing(c: Context, place: Place): String = if (c.spanish) pick(
        c,
        "Aquí va: ${shortName(place)}.",
        "${shortName(place)}, ahí voy.",
        "Claro. ${shortName(place)}.",
    ) else pick(
        c,
        "Here's ${shortName(place)}.",
        "${shortName(place)}, coming up.",
        "Sure — ${shortName(place)}.",
    )

    fun noMore(c: Context): String {
        val focus = c.memory.focus
            ?: return if (c.spanish) "Ahora no suena nada. Pregúntame qué hay cerca." else "Nothing is playing right now. Ask me what's nearby."
        return if (c.spanish) pick(
            c,
            "Eso es todo lo que tengo de ${shortName(focus)} por ahora.",
            "Ya te conté lo que sé de ${shortName(focus)}. ¿Quieres saber qué más hay cerca?",
        ) else pick(
            c,
            "That's all I have on ${shortName(focus)} for now.",
            "I've told you what I know about ${shortName(focus)}. Want to hear what else is around?",
        )
    }

    // ---- navegación

    fun routingTo(c: Context, place: Place): String = if (c.spanish) pick(
        c,
        "Claro, te llevo a ${shortName(place)}. Un momento.",
        "Con gusto. Buscando la ruta a ${shortName(place)}.",
        "Vale, vamos a ${shortName(place)}. Dame un segundo.",
    ) else pick(
        c,
        "Sure, taking you to ${shortName(place)}. One moment.",
        "Happy to. Finding the route to ${shortName(place)}.",
        "Okay, off to ${shortName(place)}. Give me a second.",
    )

    /**
     * La ruta, presentada como lo haría alguien que va contigo: a dónde, hacia
     * dónde, cuánto, y qué hacer ahora. Todo en una sola frase larga, para que
     * el conductor no tenga que esperar tres respuestas.
     */
    fun routeStarted(c: Context, route: Route): String {
        val heading = cardinal(c.here.bearingTo(route.destination.position), c.language)
        val first = route.steps.firstOrNull()?.instruction?.trimEnd('.')?.replaceFirstChar { it.lowercase() }
        return if (c.spanish) {
            buildString {
                append(pick(c, "Iniciando ruta hacia ", "Vamos allá: ruta hacia ", "Perfecto, ruta hacia "))
                append("${shortName(route.destination)}, dirección $heading. ")
                append("Son ${spokenDistance(route.distanceMeters, c.language)}, unos ${spokenDuration(route.durationSeconds, c.language)}.")
                if (first != null) append(" Ahora, $first.")
            }
        } else {
            buildString {
                append(pick(c, "Starting your route to ", "Here we go: route to ", "Great, heading to "))
                append("${shortName(route.destination)}, $heading of here. ")
                append("It's ${spokenDistance(route.distanceMeters)}, about ${spokenDuration(route.durationSeconds)}.")
                if (first != null) append(" Now, $first.")
            }
        }
    }

    /** "norte", "noreste"… a partir de un rumbo en grados. */
    fun cardinal(bearing: Double, language: VoiceLanguage): String {
        val names = if (language == VoiceLanguage.SPANISH) {
            listOf("norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste")
        } else {
            listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
        }
        val index = (((bearing % 360 + 360) % 360 + 22.5) / 45).toInt() % 8
        return names[index]
    }

    fun noRoute(c: Context, place: Place): String = if (c.spanish) pick(
        c,
        "No encuentro una ruta por carretera hasta ${shortName(place)} desde aquí.",
        "Ahora mismo no tengo ruta hasta ${shortName(place)}.",
    ) else pick(
        c,
        "I couldn't find a road route to ${shortName(place)} from here.",
        "No route to ${shortName(place)} right now — I may be missing a connection.",
    )

    fun noDestination(c: Context): String =
        if (c.spanish) "¿A dónde? Dime un lugar, o pregúntame qué hay cerca." else "Where to? Say a place, or ask what's nearby."

    fun arrived(c: Context, place: Place): String = if (c.spanish) pick(
        c,
        "Has llegado a ${shortName(place)}.",
        "Aquí es: ${shortName(place)}.",
    ) else pick(
        c,
        "You've arrived at ${shortName(place)}.",
        "This is it — ${shortName(place)}.",
    )

    fun rerouting(c: Context): String =
        if (c.spanish) pick(c, "Recalculando.", "Nueva ruta.") else pick(c, "Recalculating.", "New route.")

    /**
     * "In 200 metres, turn left onto Via Sacra." A menos de cien metros no hay
     * distancia que decir: "en aquí mismo" no lo dice nadie. Es "ahora".
     */
    fun maneuver(c: Context, meters: Double, instruction: String): String {
        val lowered = instruction.trimEnd('.').replaceFirstChar { it.lowercase() }
        return when {
            meters < 100 -> if (c.spanish) "Ahora, $lowered." else "Now, $lowered."
            c.spanish -> "En ${spokenDistance(meters, c.language)}, $lowered."
            else -> "In ${spokenDistance(meters)}, $lowered."
        }
    }

    fun noStory(c: Context, place: Place): String = if (c.spanish) pick(
        c,
        "${shortName(place)} está en el mapa, pero todavía no tengo su historia. Puedo llevarte.",
        "Sé dónde está ${shortName(place)}, no su historia. ¿Quieres que te lleve?",
    ) else pick(
        c,
        "${shortName(place)} is on the map, but I don't have a story for it yet. I can take you there.",
        "I know where ${shortName(place)} is, not its story. Want directions?",
    )

    /** "6 minutes", "about an hour", "1 hour 20". */
    fun spokenDuration(seconds: Double, language: VoiceLanguage = VoiceLanguage.ENGLISH): String {
        val minutes = (seconds / 60).toInt().coerceAtLeast(1)
        val hours = minutes / 60
        return if (language == VoiceLanguage.SPANISH) when {
            minutes < 60 -> "$minutes ${if (minutes == 1) "minuto" else "minutos"}"
            minutes % 60 < 5 -> "$hours ${if (hours == 1) "hora" else "horas"}"
            else -> "$hours ${if (hours == 1) "hora" else "horas"} y ${minutes % 60}"
        } else when {
            minutes < 60 -> "$minutes ${if (minutes == 1) "minute" else "minutes"}"
            minutes % 60 < 5 -> "$hours ${if (hours == 1) "hour" else "hours"}"
            else -> "$hours hours ${minutes % 60}"
        }
    }

    fun noNavigation(c: Context): String {
        val focus = c.memory.focus
        if (c.spanish) {
            val target = focus?.let { " a ${shortName(it)}" }.orEmpty()
            return "Todavía no puedo fijar destino$target: esa parte no está conectada al navegador del coche."
        }
        val target = focus?.let { " to ${shortName(it)}" }.orEmpty()
        return pick(
            c,
            "I can't set a destination$target yet — that part isn't wired to the car's navigation.",
            "Navigation$target isn't available yet. I can keep telling you about it, though.",
        )
    }

    fun skipped(c: Context, skipped: Place, next: Place?): String {
        if (c.spanish) {
            val onward = next?.let { " El siguiente es ${shortName(it)}." }.orEmpty()
            return pick(c, "Salto ${shortName(skipped)}.$onward", "Vale, dejamos ${shortName(skipped)}.$onward")
        }
        val onward = next?.let { " Next up is ${shortName(it)}." }.orEmpty()
        return pick(c, "Skipping ${shortName(skipped)}.$onward", "Okay, leaving ${shortName(skipped)}.$onward")
    }

    fun nothingToSkip(c: Context): String =
        if (c.spanish) pick(c, "No hay nada sonando que saltar.", "Nada que saltar ahora mismo.")
        else pick(c, "There's nothing playing to skip.", "Nothing to skip right now.")

    fun stopped(c: Context): String =
        if (c.spanish) pick(c, "Vale.", "Listo.", "Me callo.") else pick(c, "Okay.", "Sure.", "Alright, I'll be quiet.")

    fun notUnderstood(c: Context): String {
        if (c.spanish) {
            val hint = c.around.firstOrNull()?.let { " O pregúntame por ${shortName(it)}." }.orEmpty()
            return pick(
                c,
                "Perdona, no te entendí. Puedes preguntar qué hay cerca, o decir un lugar.$hint",
                "No lo pillé. Prueba con \"qué hay cerca\", o di un lugar.$hint",
                "¿Me lo repites? Puedo contarte qué hay alrededor, o hablarte de un lugar.$hint",
            )
        }
        val hint = c.around.firstOrNull()?.let { " Or ask about ${shortName(it)}." }.orEmpty()
        return pick(
            c,
            "Sorry, I didn't catch that. You can ask what's nearby, or name a place.$hint",
            "I didn't get that. Try \"what's nearby\", or say a place.$hint",
            "Say that again? I can tell you what's around, or about a place.$hint",
        )
    }

    /** Distancias como se dicen, no como se miden: nadie oye "1 437 metros". */
    fun spokenDistance(meters: Double, language: VoiceLanguage = VoiceLanguage.ENGLISH): String {
        val spanish = language == VoiceLanguage.SPANISH
        return when {
            meters < 100 -> if (spanish) "aquí mismo" else "right here"
            meters < 950 -> "${(meters / 50).toInt() * 50} ${if (spanish) "metros" else "metres"}"
            else -> "%.1f ${if (spanish) "kilómetros" else "kilometres"}".format(meters / 1000).replace(".0 ", " ").replace(",0 ", " ")
        }
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
