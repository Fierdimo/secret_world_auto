package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.fail

/**
 * Todas las órdenes de la app, dichas con naturalidad en los tres idiomas.
 *
 * Las demás pruebas comprueban cada orden por su lado; esta comprueba que
 * **ninguna se quede sin cubrir en ningún idioma**. Se hizo al preparar un
 * vídeo en inglés: media docena de órdenes básicas solo funcionaban en español.
 *
 * El catálogo es el de Cartagena tal como llega, con los nombres mezclados:
 * unos en español ("Castillo San Felipe") y otros en el idioma de la Wikipedia
 * ("Palace of the Inquisition"). Quien habla en inglés dice "the castle" y
 * quien habla en italiano "il castello", y los dos se refieren al castillo.
 * Cuando falla, enseña todas las frases que fallan de una vez.
 */
class VoiceCoverageTest {

    private val castle = place("castle", "Castillo San Felipe de Barajas")
    private val convent = place("convent", "Convento de la Popa")
    private val catalina = place("catalina", "India Catalina")
    private val tower = place("tower", "Torre del Reloj")
    private val palace = place("palace", "Palace of the Inquisition")
    private val square = place("square", "Plaza de Bolívar")
    private val catalogue = listOf(castle, convent, catalina, tower, palace, square)

    private val en = VoiceLanguage.ENGLISH
    private val es = VoiceLanguage.SPANISH
    private val it = VoiceLanguage.ITALIAN

    private val failures = mutableListOf<String>()

    /** Lo que dice la gramática, con lo que la app le pasa en cada momento. */
    private fun heard(
        language: VoiceLanguage,
        phrase: String,
        focus: Place? = null,
        listed: List<Place> = emptyList(),
        expected: (VoiceCommand) -> Boolean,
    ) {
        val command = VoiceGrammar.parse(phrase, catalogue, focus, listed, language)
        if (!expected(command)) failures += "[${language.code}] «$phrase» → $command"
    }

    private fun check(label: String, ok: Boolean) {
        if (!ok) failures += label
    }

    @Test
    fun `cada orden se entiende en los tres idiomas`() {
        // Callar y saltar.
        listOf(en to "stop", en to "be quiet", es to "para", es to "cállate", it to "basta", it to "stai zitto")
            .forEach { (l, p) -> heard(l, p) { it == VoiceCommand.Stop } }
        listOf(en to "skip", en to "next one", es to "siguiente", es to "sáltalo", it to "salta", it to "prossimo")
            .forEach { (l, p) -> heard(l, p) { it == VoiceCommand.Skip } }

        // Qué hay cerca y cuéntame más.
        listOf(en to "what's nearby", en to "what's around here", es to "qué hay cerca", es to "qué hay por aquí", it to "cosa c'è qui vicino", it to "cosa c'è intorno")
            .forEach { (l, p) -> heard(l, p) { it == VoiceCommand.WhatIsNearby } }
        listOf(en to "tell me more", es to "cuéntame más", it to "dimmi di più")
            .forEach { (l, p) -> heard(l, p, focus = castle) { it == VoiceCommand.TellMeMore } }

        // Contar un sitio, nombrado en otro idioma o por su nombre propio.
        listOf(
            en to "tell me about the castle", en to "tell me about San Felipe", en to "what is the convent",
            en to "tell me about the tower", en to "tell me about the square",
            es to "háblame del castillo", es to "cuéntame de San Felipe", es to "háblame del palacio",
            it to "parlami del castello", it to "raccontami di San Felipe", it to "parlami del palazzo", it to "raccontami della piazza",
        ).forEach { (l, p) -> heard(l, p) { it is VoiceCommand.TellMeAbout } }
        heard(en, "tell me about the castle") { it is VoiceCommand.TellMeAbout && it.place == castle }
        heard(it, "parlami del palazzo") { it is VoiceCommand.TellMeAbout && it.place == palace }
        heard(es, "háblame de la plaza") { it is VoiceCommand.TellMeAbout && it.place == square }

        // Ir a un sitio: el que está a la vista, uno del catálogo o uno que no está.
        listOf(en to "take me there", en to "let's go there", es to "llévame ahí", es to "vamos para allá", it to "portami lì", it to "andiamo lì")
            .forEach { (l, p) -> heard(l, p, focus = castle) { it is VoiceCommand.TakeMeThere && it.place == castle } }
        listOf(en to "take me to the castle", es to "llévame al castillo", it to "portami al castello")
            .forEach { (l, p) -> heard(l, p) { it is VoiceCommand.TakeMeThere && it.place == castle } }
        listOf(en to "take me to Hotel Caribe", es to "llévame al Hotel Caribe", it to "portami all'Hotel Caribe")
            .forEach { (l, p) -> heard(l, p) { it is VoiceCommand.NavigateToNamed } }

        // Buscar sitios de un tipo, aquí o en otra zona.
        listOf(
            en to "find restaurants", en to "I'm hungry", en to "where can I get a coffee", en to "is there a gas station near here",
            es to "busca restaurantes", es to "tengo hambre", es to "dónde hay una gasolinera", es to "necesito un café",
            it to "cerca ristoranti", it to "ho fame", it to "dov'è un distributore", it to "c'è un bar qui vicino",
        ).forEach { (l, p) -> heard(l, p) { it is VoiceCommand.FindPlaces && it.area == null } }
        listOf(en to "restaurants in Bocagrande", es to "restaurantes en Bocagrande", it to "ristoranti a Bocagrande")
            .forEach { (l, p) -> heard(l, p) { it is VoiceCommand.FindPlaces && it.area == "bocagrande" } }

        // Sí y no a una oferta.
        listOf(en to "yes", en to "sure", es to "sí", es to "vale", it to "sì", it to "certo")
            .forEach { (l, p) -> heard(l, p) { it == VoiceCommand.Yes } }
        listOf(en to "no thanks", es to "no gracias", it to "no grazie")
            .forEach { (l, p) -> heard(l, p) { it == VoiceCommand.No } }

        // Terminar la ruta o el viaje.
        listOf(
            en to "end the trip", en to "stop navigation", en to "end navigation", en to "cancel the route", en to "finish the trip", en to "we're done",
            es to "termina el viaje", es to "cancela la ruta", es to "terminamos el recorrido", es to "acaba el viaje",
            it to "termina il viaggio", it to "fine percorso", it to "annulla il percorso", it to "abbiamo finito",
        ).forEach { (l, p) -> heard(l, p) { it == VoiceCommand.EndRoute } }

        // Cambiar de idioma.
        heard(en, "speak Spanish") { it == VoiceCommand.SwitchLanguage(es) }
        heard(es, "habla en inglés") { it == VoiceCommand.SwitchLanguage(en) }
        heard(it, "parla spagnolo") { it == VoiceCommand.SwitchLanguage(es) }

        // Itinerarios: pedir uno, empezarlo, verlos, armarlo a mano, añadir.
        listOf(
            en to "plan me an itinerary for three hours", en to "plan a tour this afternoon", en to "what can I do this afternoon",
            es to "hazme un itinerario de tres horas", es to "qué puedo hacer esta tarde",
            it to "organizzami un giro di tre ore", it to "cosa posso fare oggi pomeriggio",
        ).forEach { (l, p) -> heard(l, p) { it is VoiceCommand.PlanItinerary } }
        heard(en, "plan me an itinerary for three hours") { it is VoiceCommand.PlanItinerary && it.request.minutes == 180 }
        listOf(en to "start my itinerary", en to "start the itinerary", es to "empieza mi itinerario", it to "inizia il mio itinerario")
            .forEach { (l, p) -> heard(l, p) { it == VoiceCommand.StartItinerary } }
        listOf(en to "show my itineraries", en to "my itineraries", es to "mis itinerarios", it to "i miei itinerari")
            .forEach { (l, p) -> heard(l, p) { it == VoiceCommand.ShowItineraries } }
        listOf(en to "I want to build my own itinerary", es to "quiero armar mi propio itinerario", it to "voglio fare il mio itinerario personale")
            .forEach { (l, p) -> heard(l, p) { it == VoiceCommand.BuildItinerary } }
        listOf(
            en to "add the castle to my itinerary", en to "add San Felipe",
            es to "añade el castillo", es to "agrega la torre del reloj al itinerario",
            it to "aggiungi il castello", it to "aggiungi la torre",
        ).forEach { (l, p) -> heard(l, p) { it is VoiceCommand.AddToItinerary } }
        listOf(en to "forget my preferences", es to "olvida mis preferencias", it to "dimentica le mie preferenze")
            .forEach { (l, p) -> heard(l, p) { it == VoiceCommand.ForgetProfile } }

        // Elegir de una lista recién leída.
        val read = listOf(catalina, tower, palace)
        listOf(en to "the second one", es to "el segundo", it to "il secondo")
            .forEach { (l, p) -> heard(l, p, listed = read) { it is VoiceCommand.TellMeAbout && it.place == tower } }

        if (failures.isNotEmpty()) fail("No se entienden ${failures.size} frases:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `cada cambio de itinerario se entiende en los tres idiomas`() {
        val stops = listOf(convent, castle, tower, square)
        fun edit(language: VoiceLanguage, phrase: String, ok: (ItineraryEdit?) -> Boolean) {
            val named = (VoiceGrammar.parse(phrase, catalogue, language = language) as? VoiceCommand.AddToItinerary)?.place
            val edit = ItineraryRequests.edit(phrase, stops, named)
            check("[${language.code}] «$phrase» → $edit", ok(edit))
        }
        listOf(en to "start with the castle", es to "empieza por el castillo", it to "inizia dal castello")
            .forEach { (l, p) -> edit(l, p) { it == ItineraryEdit.Move(castle, first = true) } }
        listOf(en to "leave the convent for the end", es to "deja el convento para el final", it to "lascia il convento per ultimo")
            .forEach { (l, p) -> edit(l, p) { it == ItineraryEdit.Move(convent, first = false) } }
        listOf(en to "more time at the castle", es to "más tiempo en el castillo", it to "più tempo al castello")
            .forEach { (l, p) -> edit(l, p) { it == ItineraryEdit.Stay(castle, longer = true) } }
        listOf(en to "less time at the square", es to "menos tiempo en la plaza", it to "meno tempo in piazza")
            .forEach { (l, p) -> edit(l, p) { it == ItineraryEdit.Stay(square, longer = false) } }
        listOf(en to "the tower before the castle", es to "la torre antes del castillo", it to "la torre prima del castello")
            .forEach { (l, p) -> edit(l, p) { it == ItineraryEdit.Relative(tower, castle, before = true) } }
        listOf(en to "add the palace", es to "añade el palacio", it to "aggiungi il palazzo")
            .forEach { (l, p) -> edit(l, p) { it == ItineraryEdit.Add(palace) } }

        // Quitar: lo decide la conversación con estas dos piezas.
        listOf(en to "remove the convent", es to "quita el convento", it to "togli il convento").forEach { (l, p) ->
            check("[${l.code}] «$p» → quitar ${ItineraryRequests.removes(p)}, sitio ${VoiceGrammar.resolve(p, stops)}",
                ItineraryRequests.removes(p) && VoiceGrammar.resolve(p, stops) == convent)
        }

        if (failures.isNotEmpty()) fail("No se entienden ${failures.size} cambios:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `cada respuesta a una propuesta se entiende en los tres idiomas`() {
        mapOf(
            ItineraryReply.SAVE to listOf("save it", "I like it", "keep it", "guárdalo", "me gusta", "salvalo", "mi piace"),
            ItineraryReply.START to listOf("start now", "let's go", "start the itinerary", "empieza", "vamos", "partiamo", "andiamo"),
            ItineraryReply.LATER to listOf("later", "maybe tomorrow", "luego", "otro día", "più tardi", "domani"),
        ).forEach { (reply, phrases) ->
            phrases.forEach { p -> check("«$p» → ${ItineraryRequests.reply(p)}, se esperaba $reply", ItineraryRequests.reply(p) == reply) }
        }
        if (failures.isNotEmpty()) fail("No se entienden ${failures.size} respuestas:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `una equivalencia no confunde un verbo con un sitio`() {
        // "Porta" es "puerta" y es "lleva": con la Puerta del Reloj en el catálogo,
        // "porta mi al castello" tiene que seguir yendo al castillo.
        val gate = place("gate", "Puerta del Reloj")
        listOf("porta mi al castello", "portami al castello").forEach { p ->
            val command = VoiceGrammar.parse(p, catalogue + gate, language = it)
            check("[it] «$p» → $command", command is VoiceCommand.TakeMeThere && command.place == castle)
        }
        val command = VoiceGrammar.parse("take me to the gate", catalogue + gate, language = en)
        check("[en] «take me to the gate» → $command", command is VoiceCommand.TakeMeThere && command.place == gate)
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    private fun place(id: String, name: String) =
        Place(id = id, name = name, position = Coordinates(10.42, -75.54), narrationSeconds = 60, languages = setOf(VoiceLanguage.SPANISH))
}
