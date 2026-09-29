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
 * Inglés, español e italiano. Cada idioma es otra tabla, no otra lógica: cada
 * función elige con [VoiceLanguage.say], que exige los tres.
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

        fun <T> say(en: T, es: T, it: T): T = language.say(en, es, it)
    }

    /** Un lugar con su distancia dicha, para enumerar. */
    fun named(place: Place, from: Coordinates, language: VoiceLanguage = VoiceLanguage.ENGLISH): String =
        "${shortName(place)}, ${spokenDistance(from.distanceTo(place.position), language)}"

    fun nothingNearby(c: Context): String = pick(
        c,
        c.say(
            en = listOf(
                "I don't have anything worth a stop around here yet.",
                "Nothing on my list around here. I'll tell you when something comes up.",
            ),
            es = listOf(
                "Por aquí no tengo nada que merezca una parada, de momento.",
                "No tengo nada en la lista por aquí. Te aviso cuando aparezca algo.",
            ),
            it = listOf(
                "Per ora qui intorno non ho niente che valga una sosta.",
                "Qui vicino non ho niente in lista. Ti avviso quando compare qualcosa.",
            ),
        ),
    )

    /** Enumera y pregunta: lo que hace que "el segundo" signifique algo después. */
    fun nearby(c: Context, listed: List<Place>): String {
        val list = joined(c, listed.map { named(it, c.here, c.language) })
        val opening = pick(
            c,
            c.say(
                en = listOf("Around you: ", "Nearby there's ", "Close to you: "),
                es = listOf("A tu alrededor: ", "Cerca tienes ", "Por aquí hay "),
                it = listOf("Intorno a te: ", "Qui vicino c'è ", "Vicino a te: "),
            ),
        )
        val ask = pick(
            c,
            c.say(
                en = listOf("Which one?", "Want to hear about any of them?", "Say the name, or first, second, third."),
                es = listOf("¿Cuál?", "¿Te cuento alguno?", "Di el nombre, o primero, segundo, tercero."),
                it = listOf("Quale?", "Vuoi che te ne racconti uno?", "Dimmi il nome, oppure primo, secondo, terzo."),
            ),
        )
        return "$opening$list. $ask"
    }

    /** Cuando la historia no está preparada: se avisa, que la espera con aviso no es espera. */
    fun preparing(c: Context, place: Place): String {
        val name = shortName(place)
        return pick(
            c,
            c.say(
                en = listOf(
                    "One moment, let me find what I know about $name.",
                    "Give me a second and I'll tell you about $name.",
                    "Coming right up: $name. Just preparing it.",
                ),
                es = listOf(
                    "Un momento, busco lo que sé de $name.",
                    "Dame un segundo y te cuento $name.",
                    "Ahora te cuento $name; lo estoy preparando.",
                ),
                it = listOf(
                    "Un momento, cerco quello che so su $name.",
                    "Dammi un secondo e ti racconto $name.",
                    "Arriva subito: $name. Lo sto preparando.",
                ),
            ),
        )
    }

    fun playing(c: Context, place: Place): String {
        val name = shortName(place)
        return pick(
            c,
            c.say(
                en = listOf("Here's $name.", "$name, coming up.", "Sure — $name."),
                es = listOf("Aquí va: $name.", "$name, ahí voy.", "Claro. $name."),
                it = listOf("Ecco $name.", "$name, arriva.", "Certo. $name."),
            ),
        )
    }

    fun noMore(c: Context): String {
        val focus = c.memory.focus ?: return c.say(
            en = "Nothing is playing right now. Ask me what's nearby.",
            es = "Ahora no suena nada. Pregúntame qué hay cerca.",
            it = "Adesso non c'è niente in riproduzione. Chiedimi cosa c'è qui vicino.",
        )
        val name = shortName(focus)
        return pick(
            c,
            c.say(
                en = listOf(
                    "That's all I have on $name for now.",
                    "I've told you what I know about $name. Want to hear what else is around?",
                ),
                es = listOf(
                    "Eso es todo lo que tengo de $name por ahora.",
                    "Ya te conté lo que sé de $name. ¿Quieres saber qué más hay cerca?",
                ),
                it = listOf(
                    "Per ora è tutto quello che ho su $name.",
                    "Ti ho detto quello che so su $name. Vuoi sapere cos'altro c'è qui intorno?",
                ),
            ),
        )
    }

    // ---- búsqueda de sitios

    fun searching(c: Context, query: String): String = pick(
        c,
        c.say(
            en = listOf("Looking for $query nearby.", "Let me see what's around for $query.", "One moment, checking $query near you."),
            es = listOf("Busco $query por aquí.", "A ver qué hay de $query cerca.", "Un momento, miro $query cerca de ti."),
            it = listOf("Cerco $query qui vicino.", "Vediamo cosa c'è di $query qui intorno.", "Un momento, controllo $query vicino a te."),
        ),
    )

    fun nothingFound(c: Context, query: String): String = c.say(
        en = "I can't find $query around here. Want me to look for something else?",
        es = "No encuentro $query por aquí cerca. ¿Busco otra cosa?",
        it = "Non trovo $query qui vicino. Cerco qualcos'altro?",
    )

    /**
     * Los resultados, como los daría alguien que va en el coche: nombre, qué
     * es, y cuánto se tarda **conduciendo**, que es lo que importa al volante.
     * Termina ofreciendo llevar, y deja la lista en memoria para "el segundo".
     */
    fun found(c: Context, query: String, results: List<Place>): String {
        val items = results.map { place ->
            val minutes = drivingMinutes(c.here.distanceTo(place.position))
            val what = place.kind?.takeIf { !place.name.contains(it, ignoreCase = true) }?.let { ", $it" }.orEmpty()
            val away = c.say(
                en = if (minutes <= 1) "a minute away" else "$minutes minutes away",
                es = if (minutes <= 1) "a un minuto" else "a $minutes minutos",
                it = if (minutes <= 1) "a un minuto" else "a $minutes minuti",
            )
            "${shortName(place)}$what, $away"
        }
        val opening = pick(
            c,
            c.say(
                en = listOf("Nearby there's ", "I found ", "Around here: "),
                es = listOf("Cerca tienes ", "He encontrado ", "Por aquí hay "),
                it = listOf("Qui vicino c'è ", "Ho trovato ", "Qui intorno: "),
            ),
        )
        val ask = pick(
            c,
            c.say(
                en = listOf("Want me to take you to one?", "Which one?", "Say the name, or first, second, third."),
                es = listOf("¿Te llevo a alguno?", "¿A cuál vamos?", "Di el nombre, o primero, segundo, tercero."),
                it = listOf("Ti porto in uno di questi?", "Dove andiamo?", "Dimmi il nome, oppure primo, secondo, terzo."),
            ),
        )
        return "$opening${joined(c, items)}. $ask"
    }

    /**
     * Se nombró un lugar sin decir qué hacer: llevar o contar, que elija.
     * Corta a propósito: cada palabra es tiempo de síntesis, y en un móvil
     * lento la versión con distancia tardaba segundos en empezar a sonar.
     */
    fun chooseAction(c: Context, place: Place): String {
        val name = shortName(place)
        return pick(
            c,
            c.say(
                en = listOf("$name: shall I take you, or tell you about it?", "$name. Go there, or hear its story?"),
                es = listOf("$name: ¿te llevo o te la cuento?", "$name. ¿Vamos, o te cuento su historia?"),
                it = listOf("$name: ti ci porto o te la racconto?", "$name. Ci andiamo, o te la racconto?"),
            ),
        )
    }

    /**
     * El nombre encaja con varios: cuál. Se numeran, porque con nombres largos
     * nadie recuerda cuál era cuál, y así "la segunda" contesta.
     */
    fun whichOne(c: Context, places: List<Place>): String {
        val ordinals = c.say(
            en = listOf("the first", "the second", "the third"),
            es = listOf("el primero", "el segundo", "el tercero"),
            it = listOf("il primo", "il secondo", "il terzo"),
        )
        val options = places.take(ordinals.size).mapIndexed { i, place -> "${ordinals[i]}, ${shortName(place)}" }.joinToString("; ")
        val count = places.size.coerceAtMost(ordinals.size)
        return c.say(
            en = "I have $count: $options. Which one?",
            es = "Tengo $count: $options. ¿Cuál?",
            it = "Ne ho ${if (count == 2) "due" else "tre"}: $options. Quale?",
        )
    }

    /** Un sitio sin historia pero con dirección: se ofrece llevar. */
    fun offerRoute(c: Context, place: Place): String {
        val minutes = drivingMinutes(c.here.distanceTo(place.position))
        val name = shortName(place)
        val where = place.address?.let { c.say(en = ", at $it", es = ", en $it", it = ", in $it") }.orEmpty()
        return c.say(
            en = "$name is about $minutes minutes away by car$where. Shall I take you?",
            es = "$name está a unos $minutes minutos en coche$where. ¿Te llevo?",
            it = "$name è a circa $minutes minuti in macchina$where. Ti ci porto?",
        )
    }

    /**
     * Minutos conduciendo por ciudad a partir de la distancia en línea recta:
     * la ruta real es más larga y se va más despacio de lo que parece. Es una
     * estimación para elegir; el tiempo de verdad lo da la ruta.
     */
    fun drivingMinutes(meters: Double): Int = (meters * CITY_DETOUR / CITY_METERS_PER_MINUTE).toInt().coerceAtLeast(1)

    private const val CITY_DETOUR = 1.4
    /** Unos 22 km/h de media en ciudad, semáforos incluidos. */
    private const val CITY_METERS_PER_MINUTE = 370.0

    // ---- navegación

    fun routingTo(c: Context, place: Place): String {
        val name = shortName(place)
        return pick(
            c,
            c.say(
                en = listOf(
                    "Sure, taking you to $name. One moment.",
                    "Happy to. Finding the route to $name.",
                    "Okay, off to $name. Give me a second.",
                ),
                es = listOf(
                    "Claro, te llevo a $name. Un momento.",
                    "Con gusto. Buscando la ruta a $name.",
                    "Vale, vamos a $name. Dame un segundo.",
                ),
                it = listOf(
                    "Certo, ti porto a $name. Un momento.",
                    "Volentieri. Cerco il percorso per $name.",
                    "Va bene, andiamo a $name. Dammi un secondo.",
                ),
            ),
        )
    }

    /**
     * La ruta, presentada como lo haría alguien que va contigo: a dónde, hacia
     * dónde, cuánto, y qué hacer ahora. Todo en una sola frase larga, para que
     * el conductor no tenga que esperar tres respuestas.
     */
    fun routeStarted(c: Context, route: Route): String {
        val heading = cardinal(c.here.bearingTo(route.destination.position), c.language)
        val first = route.steps.firstOrNull()?.instruction?.trimEnd('.')?.replaceFirstChar { it.lowercase() }
        val name = shortName(route.destination)
        val distance = spokenDistance(route.distanceMeters, c.language)
        val duration = spokenDuration(route.durationSeconds, c.language)
        return buildString {
            append(
                pick(
                    c,
                    c.say(
                        en = listOf("Starting your route to ", "Here we go: route to ", "Great, heading to "),
                        es = listOf("Iniciando ruta hacia ", "Vamos allá: ruta hacia ", "Perfecto, ruta hacia "),
                        it = listOf("Avvio il percorso verso ", "Si parte: percorso verso ", "Perfetto, percorso verso "),
                    ),
                ),
            )
            append(
                c.say(
                    en = "$name, $heading of here. It's $distance, about $duration.",
                    es = "$name, dirección $heading. Son $distance, unos $duration.",
                    it = "$name, direzione $heading. Sono $distance, circa $duration.",
                ),
            )
            if (first != null) append(c.say(en = " Now, $first.", es = " Ahora, $first.", it = " Ora, $first."))
        }
    }

    /** "norte", "noreste"… a partir de un rumbo en grados. */
    fun cardinal(bearing: Double, language: VoiceLanguage): String {
        val names = language.say(
            en = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"),
            es = listOf("norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste"),
            it = listOf("nord", "nord-est", "est", "sud-est", "sud", "sud-ovest", "ovest", "nord-ovest"),
        )
        val index = (((bearing % 360 + 360) % 360 + 22.5) / 45).toInt() % 8
        return names[index]
    }

    fun noRoute(c: Context, place: Place): String {
        val name = shortName(place)
        return pick(
            c,
            c.say(
                en = listOf(
                    "I couldn't find a road route to $name from here.",
                    "No route to $name right now — I may be missing a connection.",
                ),
                es = listOf(
                    "No encuentro una ruta por carretera hasta $name desde aquí.",
                    "Ahora mismo no tengo ruta hasta $name.",
                ),
                it = listOf(
                    "Non trovo un percorso stradale per $name da qui.",
                    "In questo momento non ho un percorso per $name.",
                ),
            ),
        )
    }

    fun noDestination(c: Context): String = c.say(
        en = "Where to? Say a place, or ask what's nearby.",
        es = "¿A dónde? Dime un lugar, o pregúntame qué hay cerca.",
        it = "Dove andiamo? Dimmi un luogo, oppure chiedimi cosa c'è qui vicino.",
    )

    fun arrived(c: Context, place: Place): String {
        val name = shortName(place)
        return pick(
            c,
            c.say(
                en = listOf("You've arrived at $name.", "This is it — $name."),
                es = listOf("Has llegado a $name.", "Aquí es: $name."),
                it = listOf("Sei arrivato a $name.", "Eccoci: $name."),
            ),
        )
    }

    /** Se dice ya en el idioma nuevo, al volver a abrirse la app en él. */
    fun nowSpeaking(language: VoiceLanguage): String =
        language.say(en = "I'm speaking English now.", es = "Ahora hablo español.", it = "Ora parlo italiano.")

    fun routeEnded(c: Context): String = c.say(en = "Route ended.", es = "Ruta terminada.", it = "Percorso terminato.")

    fun rerouting(c: Context): String = pick(
        c,
        c.say(
            en = listOf("Recalculating.", "New route."),
            es = listOf("Recalculando.", "Nueva ruta."),
            it = listOf("Ricalcolo.", "Nuovo percorso."),
        ),
    )

    /**
     * "In 200 metres, turn left onto Via Sacra." A menos de cien metros no hay
     * distancia que decir: "en aquí mismo" no lo dice nadie. Es "ahora".
     */
    fun maneuver(c: Context, meters: Double, instruction: String): String {
        val lowered = instruction.trimEnd('.').replaceFirstChar { it.lowercase() }
        if (meters < 100) return c.say(en = "Now, $lowered.", es = "Ahora, $lowered.", it = "Ora, $lowered.")
        val distance = spokenDistance(meters, c.language)
        return c.say(en = "In $distance, $lowered.", es = "En $distance, $lowered.", it = "Tra $distance, $lowered.")
    }

    fun noStory(c: Context, place: Place): String {
        val name = shortName(place)
        return pick(
            c,
            c.say(
                en = listOf(
                    "$name is on the map, but I don't have a story for it yet. I can take you there.",
                    "I know where $name is, not its story. Want directions?",
                ),
                es = listOf(
                    "$name está en el mapa, pero todavía no tengo su historia. Puedo llevarte.",
                    "Sé dónde está $name, no su historia. ¿Quieres que te lleve?",
                ),
                it = listOf(
                    "$name è sulla mappa, ma non ho ancora la sua storia. Posso portartici.",
                    "So dov'è $name, non la sua storia. Vuoi che ti ci porti?",
                ),
            ),
        )
    }

    /** "6 minutes", "about an hour", "1 hour 20". */
    fun spokenDuration(seconds: Double, language: VoiceLanguage = VoiceLanguage.ENGLISH): String {
        val minutes = (seconds / 60).toInt().coerceAtLeast(1)
        val hours = minutes / 60
        val rest = minutes % 60
        return when (language) {
            VoiceLanguage.SPANISH -> when {
                minutes < 60 -> "$minutes ${if (minutes == 1) "minuto" else "minutos"}"
                rest < 5 -> "$hours ${if (hours == 1) "hora" else "horas"}"
                else -> "$hours ${if (hours == 1) "hora" else "horas"} y $rest"
            }
            VoiceLanguage.ITALIAN -> when {
                minutes < 60 -> "$minutes ${if (minutes == 1) "minuto" else "minuti"}"
                rest < 5 -> if (hours == 1) "un'ora" else "$hours ore"
                else -> "${if (hours == 1) "un'ora" else "$hours ore"} e $rest"
            }
            VoiceLanguage.ENGLISH -> when {
                minutes < 60 -> "$minutes ${if (minutes == 1) "minute" else "minutes"}"
                rest < 5 -> "$hours ${if (hours == 1) "hour" else "hours"}"
                else -> "$hours hours $rest"
            }
        }
    }

    fun noNavigation(c: Context): String {
        val focus = c.memory.focus?.let(::shortName)
        return when (c.language) {
            VoiceLanguage.SPANISH ->
                "Todavía no puedo fijar destino${focus?.let { " a $it" }.orEmpty()}: esa parte no está conectada al navegador del coche."
            VoiceLanguage.ITALIAN ->
                "Non posso ancora impostare la destinazione${focus?.let { " per $it" }.orEmpty()}: questa parte non è collegata al navigatore dell'auto."
            VoiceLanguage.ENGLISH -> {
                val target = focus?.let { " to $it" }.orEmpty()
                pick(
                    c,
                    listOf(
                        "I can't set a destination$target yet — that part isn't wired to the car's navigation.",
                        "Navigation$target isn't available yet. I can keep telling you about it, though.",
                    ),
                )
            }
        }
    }

    fun skipped(c: Context, skipped: Place, next: Place?): String {
        val name = shortName(skipped)
        val onward = next?.let {
            c.say(en = " Next up is ${shortName(it)}.", es = " El siguiente es ${shortName(it)}.", it = " Il prossimo è ${shortName(it)}.")
        }.orEmpty()
        return pick(
            c,
            c.say(
                en = listOf("Skipping $name.$onward", "Okay, leaving $name.$onward"),
                es = listOf("Salto $name.$onward", "Vale, dejamos $name.$onward"),
                it = listOf("Salto $name.$onward", "Va bene, lasciamo $name.$onward"),
            ),
        )
    }

    fun nothingToSkip(c: Context): String = pick(
        c,
        c.say(
            en = listOf("There's nothing playing to skip.", "Nothing to skip right now."),
            es = listOf("No hay nada sonando que saltar.", "Nada que saltar ahora mismo."),
            it = listOf("Non c'è niente in riproduzione da saltare.", "Niente da saltare adesso."),
        ),
    )

    fun stopped(c: Context): String = pick(
        c,
        c.say(
            en = listOf("Okay.", "Sure.", "Alright, I'll be quiet."),
            es = listOf("Vale.", "Listo.", "Me callo."),
            it = listOf("Va bene.", "Fatto.", "D'accordo, faccio silenzio."),
        ),
    )

    fun notUnderstood(c: Context): String {
        val first = c.around.firstOrNull()?.let(::shortName)
        val hint = first?.let {
            c.say(en = " Or ask about $it.", es = " O pregúntame por $it.", it = " Oppure chiedimi di $it.")
        }.orEmpty()
        return pick(
            c,
            c.say(
                en = listOf(
                    "Sorry, I didn't catch that. You can ask what's nearby, or name a place.$hint",
                    "I didn't get that. Try \"what's nearby\", or say a place.$hint",
                    "Say that again? I can tell you what's around, or about a place.$hint",
                ),
                es = listOf(
                    "Perdona, no te entendí. Puedes preguntar qué hay cerca, o decir un lugar.$hint",
                    "No lo pillé. Prueba con \"qué hay cerca\", o di un lugar.$hint",
                    "¿Me lo repites? Puedo contarte qué hay alrededor, o hablarte de un lugar.$hint",
                ),
                it = listOf(
                    "Scusa, non ho capito. Puoi chiedermi cosa c'è qui vicino, o dirmi un luogo.$hint",
                    "Non ho capito bene. Prova con \"cosa c'è qui vicino\", o dimmi un luogo.$hint",
                    "Puoi ripetere? Posso dirti cosa c'è intorno, o parlarti di un luogo.$hint",
                ),
            ),
        )
    }

    /** Distancias como se dicen, no como se miden: nadie oye "1 437 metros". */
    fun spokenDistance(meters: Double, language: VoiceLanguage = VoiceLanguage.ENGLISH): String = when {
        meters < 100 -> language.say(en = "right here", es = "aquí mismo", it = "proprio qui")
        meters < 950 -> "${(meters / 50).toInt() * 50} ${language.say(en = "metres", es = "metros", it = "metri")}"
        else -> "%.1f ${language.say(en = "kilometres", es = "kilómetros", it = "chilometri")}"
            .format(meters / 1000).replace(".0 ", " ").replace(",0 ", " ")
    }

    /**
     * El nombre como se dice: "The mosaics of the Basilica of St. Cecilia in
     * Trastevere" es una ficha, no algo que un guía diga en voz alta.
     */
    fun shortName(place: Place): String {
        val words = place.name.split(' ')
        return if (words.size <= 6) place.name else words.take(6).joinToString(" ")
    }

    /** "a, b; y c", con la conjunción del idioma. */
    private fun joined(c: Context, items: List<String>): String {
        val and = c.say(en = "and", es = "y", it = "e")
        return when (items.size) {
            1 -> items[0]
            2 -> "${items[0]} $and ${items[1]}"
            else -> items.dropLast(1).joinToString("; ") + "; $and " + items.last()
        }
    }

    /** Elige una variante por turno, para no decir siempre lo mismo. */
    private fun pick(c: Context, variants: List<String>): String = variants[c.turn % variants.size]
}
