package com.secretworld.auto.llmbench

import com.secretworld.auto.domain.ConversationMemory
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.VoiceCommand
import com.secretworld.auto.domain.VoiceLanguage

/**
 * Frases de conductor con la orden que deberían producir.
 *
 * Los lugares son **otros** que los de los ejemplos del prompt (Foro, Circo
 * Máximo, Santa Cecilia): con los mismos, un modelo podría acertar copiando el
 * ejemplo en vez de entender la frase.
 */
object BenchCases {

    /** Junto al Panteón: las distancias salen en la petición, como en la app. */
    val HERE = Coordinates(41.8986, 12.4769)

    val pantheon = place("pantheon", "Pantheon", 41.8986, 12.4769)
    val trevi = place("trevi", "Trevi Fountain", 41.9009, 12.4833)
    val castle = place("castle", "Castel Sant'Angelo", 41.9031, 12.4663)
    val navona = place("navona", "Piazza Navona", 41.8992, 12.4731)
    val PLACES = listOf(pantheon, trevi, castle, navona)

    /** Qué cuenta como acierto. */
    sealed interface Expect {
        fun matches(command: VoiceCommand): Boolean
        val label: String

        data class Is(val command: VoiceCommand) : Expect {
            override fun matches(command: VoiceCommand) = command == this.command
            override val label get() = describe(command)
        }

        /** Cualquier búsqueda con algo que buscar: la palabra exacta no se puntúa. */
        data object AnySearch : Expect {
            override fun matches(command: VoiceCommand) = command is VoiceCommand.FindPlaces && command.query.isNotBlank()
            override val label = "find_places *"
        }
    }

    data class Case(
        val language: VoiceLanguage,
        val heard: String,
        val expect: Expect,
        /** Lo que suena ahora, para "esto" y "dime más". */
        val focus: Place? = null,
        /** Lo último enumerado, para "el segundo". */
        val listed: List<Place> = emptyList(),
    ) {
        fun memory() = ConversationMemory().apply {
            focus?.let { focusOn(it) }
            listed(listed)
        }
    }

    private val IT = VoiceLanguage.ITALIAN
    private val EN = VoiceLanguage.ENGLISH
    private val ES = VoiceLanguage.SPANISH

    val ALL: List<Case> = listOf(
        Case(IT, "raccontami qualcosa sulla fontana", Expect.Is(VoiceCommand.TellMeAbout(trevi))),
        Case(IT, "che cosa c'è da vedere qua intorno", Expect.Is(VoiceCommand.WhatIsNearby)),
        Case(IT, "portami al castello", Expect.Is(VoiceCommand.TakeMeThere(castle))),
        Case(IT, "dimmi di più su questo posto", Expect.Is(VoiceCommand.TellMeMore), focus = pantheon),
        Case(IT, "ho fame, dove posso mangiare una pizza", Expect.AnySearch),
        Case(IT, "va bene basta così grazie", Expect.Is(VoiceCommand.Stop), focus = pantheon),
        Case(IT, "questo saltalo", Expect.Is(VoiceCommand.Skip), focus = trevi),
        Case(IT, "il secondo per favore", Expect.Is(VoiceCommand.TellMeAbout(trevi)), listed = PLACES),
        Case(IT, "c'è un benzinaio qui vicino", Expect.AnySearch),
        Case(IT, "andiamo in piazza", Expect.Is(VoiceCommand.TakeMeThere(navona))),
        Case(IT, "parlami di quel tempio antico", Expect.Is(VoiceCommand.TellMeAbout(pantheon))),
        Case(IT, "che bel tempo oggi eh", Expect.Is(VoiceCommand.NotUnderstood("che bel tempo oggi eh"))),

        Case(EN, "tell me something about that castle", Expect.Is(VoiceCommand.TellMeAbout(castle))),
        Case(EN, "anything worth seeing around here", Expect.Is(VoiceCommand.WhatIsNearby)),
        Case(EN, "can you drive me to the fountain", Expect.Is(VoiceCommand.TakeMeThere(trevi))),
        Case(EN, "I really need a coffee", Expect.AnySearch),
        Case(EN, "ok quiet please", Expect.Is(VoiceCommand.Stop), focus = castle),
        Case(EN, "more about this one", Expect.Is(VoiceCommand.TellMeMore), focus = navona),

        Case(ES, "cuéntame algo de la fuente", Expect.Is(VoiceCommand.TellMeAbout(trevi))),
        Case(ES, "qué hay para ver por aquí", Expect.Is(VoiceCommand.WhatIsNearby)),
        Case(ES, "llévame al castillo", Expect.Is(VoiceCommand.TakeMeThere(castle))),
        Case(ES, "dónde me puedo tomar una cerveza", Expect.AnySearch),
        Case(ES, "ya está, cállate", Expect.Is(VoiceCommand.Stop), focus = trevi),
        Case(ES, "el tercero", Expect.Is(VoiceCommand.TellMeAbout(castle)), listed = PLACES),
    )

    /** "Ninguna orden" se escribe `none` y el resolvedor lo convierte en no entendido. */
    fun describe(command: VoiceCommand): String = when (command) {
        is VoiceCommand.TellMeAbout -> "tell_me_about ${command.place.id}"
        is VoiceCommand.TakeMeThere -> "take_me_there ${command.place?.id}"
        is VoiceCommand.FindPlaces -> "find_places ${command.query}"
        is VoiceCommand.Clarify -> "clarify"
        is VoiceCommand.NotUnderstood -> "none"
        else -> command::class.simpleName.orEmpty().lowercase()
    }

    private fun place(id: String, name: String, lat: Double, lon: Double) = Place(
        id = id,
        name = name,
        position = Coordinates(lat, lon),
        narrationSeconds = 60,
        languages = setOf(VoiceLanguage.ITALIAN, VoiceLanguage.ENGLISH, VoiceLanguage.SPANISH),
    )
}
