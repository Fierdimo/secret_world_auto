package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Las órdenes del nivel 1 (SPEC §2.1), comprobadas una a una.
 *
 * Se prueban con frases **como salen del reconocedor**: en minúsculas, sin
 * puntuación fiable y con palabras de relleno. Probar con la frase ideal no
 * demuestra nada, porque esa frase no llega nunca.
 */
class VoiceGrammarTest {

    private val forum = place("1", "The Roman Forum")
    private val cecilia = place("2", "The mosaics of the Basilica of St. Cecilia in Trastevere")
    private val circus = place("3", "Circus Maximus")
    private val places = listOf(forum, cecilia, circus)

    @Test
    fun `para la narracion`() {
        listOf("stop", "be quiet please", "ok stop it").forEach {
            assertEquals(VoiceCommand.Stop, VoiceGrammar.parse(it, places), "con «$it»")
        }
    }

    @Test
    fun `salta el lugar actual`() {
        listOf("skip", "not now", "skip this one").forEach {
            assertEquals(VoiceCommand.Skip, VoiceGrammar.parse(it, places), "con «$it»")
        }
    }

    @Test
    fun `enumera lo que hay cerca`() {
        listOf("what's nearby", "whats around me", "anything around here").forEach {
            assertEquals(VoiceCommand.WhatIsNearby, VoiceGrammar.parse(it, places), "con «$it»")
        }
    }

    @Test
    fun `pide mas contenido`() {
        assertEquals(VoiceCommand.TellMeMore, VoiceGrammar.parse("tell me more", places))
    }

    @Test
    fun `pide que le lleven`() {
        listOf("take me there", "navigate there").forEach {
            assertEquals(VoiceCommand.TakeMeThere(null), VoiceGrammar.parse(it, places), "con «$it»")
        }
        // "Ahí" es el lugar en foco; un nombre manda sobre el foco.
        assertEquals(VoiceCommand.TakeMeThere(cecilia), VoiceGrammar.parse("take me there", places, focus = cecilia))
        assertEquals(VoiceCommand.TakeMeThere(forum), VoiceGrammar.parse("take me to the forum", places, focus = cecilia))
    }

    @Test
    fun `nombra un lugar aunque no diga el nombre entero`() {
        val command = VoiceGrammar.parse("tell me about cecilia", places)
        assertEquals(VoiceCommand.TellMeAbout(cecilia), command)
    }

    @Test
    fun `un lugar nombrado gana al que esta sonando`() {
        val command = VoiceGrammar.parse("what is the circus maximus", places, focus = forum)
        assertEquals(VoiceCommand.TellMeAbout(circus), command)
    }

    @Test
    fun `en español, con y sin acentos`() {
        assertEquals(VoiceCommand.WhatIsNearby, VoiceGrammar.parse("qué hay cerca", places))
        assertEquals(VoiceCommand.WhatIsNearby, VoiceGrammar.parse("que hay por aqui", places))
        assertEquals(VoiceCommand.Stop, VoiceGrammar.parse("cállate ya", places))
        assertEquals(VoiceCommand.Stop, VoiceGrammar.parse("para ya", places))
        assertEquals(VoiceCommand.Skip, VoiceGrammar.parse("el siguiente", places))
        assertEquals(VoiceCommand.TellMeMore, VoiceGrammar.parse("cuéntame más", places, focus = forum))
        assertEquals(VoiceCommand.TellMeAbout(forum), VoiceGrammar.parse("háblame del roman forum", places))
        assertEquals(VoiceCommand.TellMeAbout(cecilia), VoiceGrammar.parse("qué es eso", places, focus = cecilia))
        assertEquals(VoiceCommand.TakeMeThere(circus), VoiceGrammar.parse("llévame al circus maximus", places))
        assertEquals(VoiceCommand.TakeMeThere(cecilia), VoiceGrammar.parse("vamos allí", places, focus = cecilia))
        // "para" a secas es una preposición, no una orden.
        assertEquals(VoiceCommand.TakeMeThere(forum), VoiceGrammar.parse("llévame para el forum", places))
        assertEquals(VoiceCommand.TellMeAbout(forum), VoiceGrammar.parse("el segundo", places, listed = listOf(circus, forum)))
    }

    @Test
    fun `busca sitios por categoria o por lo que siga a buscar`() {
        assertEquals(VoiceCommand.FindPlaces("restaurante"), VoiceGrammar.parse("dónde puedo comer", places))
        assertEquals(VoiceCommand.FindPlaces("restaurante"), VoiceGrammar.parse("tengo hambre", places))
        assertEquals(VoiceCommand.FindPlaces("bar"), VoiceGrammar.parse("un bar cerca", places))
        assertEquals(VoiceCommand.FindPlaces("gasolinera"), VoiceGrammar.parse("necesito echar gasolina", places))
        assertEquals(VoiceCommand.FindPlaces("shawarma"), VoiceGrammar.parse("busca shawarma", places))
        assertEquals(VoiceCommand.FindPlaces("cajero automático"), VoiceGrammar.parse("dónde hay un cajero", places))
        // El ceviche es cocina peruana: se busca el restaurante, no la palabra.
        assertEquals(VoiceCommand.FindPlaces("restaurante peruano"), VoiceGrammar.parse("búscame un ceviche por aquí", places))
        assertEquals(VoiceCommand.FindPlaces("restaurante"), VoiceGrammar.parse("where can I eat", places))
        assertEquals(VoiceCommand.FindPlaces("estación de carga eléctrica"), VoiceGrammar.parse("I need a charging station", places))
        // "qué hay cerca" sigue siendo enumerar, no buscar.
        assertEquals(VoiceCommand.WhatIsNearby, VoiceGrammar.parse("qué hay cerca", places))
    }

    @Test
    fun `si y no solo cuentan a secas`() {
        assertEquals(VoiceCommand.Yes, VoiceGrammar.parse("sí", places))
        assertEquals(VoiceCommand.Yes, VoiceGrammar.parse("vale, claro", places))
        assertEquals(VoiceCommand.No, VoiceGrammar.parse("no gracias", places))
        assertEquals(VoiceCommand.TakeMeThere(forum), VoiceGrammar.parse("sí, llévame al forum", places))
    }

    @Test
    fun `los deicticos son palabras enteras, no subcadenas`() {
        // "little" contiene "it" y "with" también: ninguna es un "eso".
        assertIs<VoiceCommand.NotUnderstood>(VoiceGrammar.parse("last little biome", places, focus = cecilia))
        assertIs<VoiceCommand.NotUnderstood>(VoiceGrammar.parse("with a city", places, focus = cecilia))
        assertEquals(VoiceCommand.TellMeAbout(cecilia), VoiceGrammar.parse("what is it", places, focus = cecilia))
    }

    @Test
    fun `nearby como lo oye whisper`() {
        listOf("what's new by", "what's newbie", "what is near by", "anything close by").forEach {
            assertEquals(VoiceCommand.WhatIsNearby, VoiceGrammar.parse(it, places), "con «$it»")
        }
    }

    @Test
    fun `un ordinal se apoya en lo ultimo enumerado`() {
        val listed = listOf(circus, forum, cecilia)
        assertEquals(VoiceCommand.TellMeAbout(forum), VoiceGrammar.parse("the second one", places, listed = listed))
        assertEquals(VoiceCommand.TellMeAbout(circus), VoiceGrammar.parse("first", places, listed = listed))
        // Sin enumeración previa un ordinal no significa nada.
        assertIs<VoiceCommand.NotUnderstood>(VoiceGrammar.parse("the second one", places))
    }

    @Test
    fun `un "what's" sin deictico ni nombre no es una orden`() {
        // Narrar el primer lugar aquí sería inventarse una orden; lo correcto es
        // dejarlo pasar al modelo de lenguaje.
        assertIs<VoiceCommand.NotUnderstood>(VoiceGrammar.parse("what's the weather like", places, focus = null))
        assertIs<VoiceCommand.NotUnderstood>(VoiceGrammar.parse("what is the time", places, focus = forum))
    }

    @Test
    fun `un deictico se apoya en el lugar en foco`() {
        val command = VoiceGrammar.parse("what is that", places, focus = cecilia)
        assertEquals(VoiceCommand.TellMeAbout(cecilia), command)
    }

    @Test
    fun `sin foco y sin nombre, un deictico cae en el primero de la cola`() {
        // Es lo que el conductor acaba de pasar: la cola va ordenada por cercania.
        val command = VoiceGrammar.parse("what is that", places, focus = null)
        assertEquals(VoiceCommand.TellMeAbout(forum), command)
    }

    @Test
    fun `lo que no entiende lo dice, no lo ignora`() {
        val command = VoiceGrammar.parse("open the windows and play some jazz", places)
        assertIs<VoiceCommand.NotUnderstood>(command)
        assertEquals("open the windows and play some jazz", command.heard)
    }

    @Test
    fun `una palabra vacia no resuelve ningun lugar`() {
        // Sin esta regla, "the" empareja con medio catalogo y cualquier frase
        // acabaria narrando el primer lugar que pase por delante.
        assertTrue(VoiceGrammar.resolve("the", places) == null)
        assertTrue(VoiceGrammar.resolve("please", places) == null)
    }

    @Test
    fun `parar gana aunque la frase nombre un lugar`() {
        // Es una orden de seguridad: quiere silencio ya, no una narracion nueva.
        assertEquals(
            VoiceCommand.Stop,
            VoiceGrammar.parse("stop talking about the roman forum", places),
        )
    }

    private fun place(id: String, name: String) = Place(
        id = id,
        name = name,
        position = Coordinates(41.89, 12.48),
        narrationSeconds = 70,
        languages = setOf(VoiceLanguage.ENGLISH),
    )

    @Test
    fun `entiende las ordenes en italiano`() {
        val it = VoiceLanguage.ITALIAN
        assertEquals(VoiceCommand.Stop, VoiceGrammar.parse("stai zitta", places, language = it))
        assertEquals(VoiceCommand.Skip, VoiceGrammar.parse("il prossimo", places, language = it))
        assertEquals(VoiceCommand.WhatIsNearby, VoiceGrammar.parse("cosa c'è qui vicino", places, language = it))
        assertEquals(VoiceCommand.TellMeMore, VoiceGrammar.parse("dimmi di più", places, focus = forum, language = it))
        assertEquals(VoiceCommand.TakeMeThere(circus), VoiceGrammar.parse("portami al circus maximus", places, language = it))
        assertEquals(VoiceCommand.TakeMeThere(cecilia), VoiceGrammar.parse("andiamo lì", places, focus = cecilia, language = it))
        assertEquals(VoiceCommand.TellMeAbout(cecilia), VoiceGrammar.parse("cos'è quello", places, focus = cecilia, language = it))
        assertEquals(VoiceCommand.TellMeAbout(forum), VoiceGrammar.parse("il secondo", places, listed = listOf(circus, forum), language = it))
        assertEquals(VoiceCommand.Yes, VoiceGrammar.parse("va bene", places, language = it))
        assertEquals(VoiceCommand.No, VoiceGrammar.parse("no grazie", places, language = it))
    }

    @Test
    fun `en italiano busca en italiano`() {
        val it = VoiceLanguage.ITALIAN
        assertEquals(VoiceCommand.FindPlaces("ristorante"), VoiceGrammar.parse("ho fame", places, language = it))
        assertEquals(VoiceCommand.FindPlaces("distributore di benzina"), VoiceGrammar.parse("devo fare benzina", places, language = it))
        assertEquals(VoiceCommand.FindPlaces("gelateria"), VoiceGrammar.parse("dove posso prendere un gelato", places, language = it))
        assertEquals(VoiceCommand.FindPlaces("carbonara"), VoiceGrammar.parse("cercami una carbonara qui vicino", places, language = it))
        // La misma categoría dicha en español, con el conductor en italiano.
        assertEquals(VoiceCommand.FindPlaces("ristorante"), VoiceGrammar.parse("tengo hambre", places, language = it))
    }

    @Test
    fun `los verbos italianos no rompen el espanol`() {
        // "cerca" es "busca" en italiano; en español sigue siendo cerca.
        assertEquals(VoiceCommand.WhatIsNearby, VoiceGrammar.parse("qué hay cerca del parque", places))
        // "la" sin acento no señala nada.
        assertIs<VoiceCommand.NotUnderstood>(VoiceGrammar.parse("la vita è bella", places, focus = cecilia, language = VoiceLanguage.ITALIAN))
    }

    @Test
    fun `pedir ir a un sitio en italiano lleva, no narra`() {
        val it = VoiceLanguage.ITALIAN
        val catalina = place("9", "Discover India Catalina")
        val here = places + catalina
        listOf(
            "portami a india catalina",
            "Portami all'India Catalina.",
            "porta mi a india catalina",
            "mi porti a india catalina",
            "voglio andare a india catalina",
            "come si arriva a india catalina",
            // Transcripciones reales: en cirílico, y "portami" a la inglesa.
            "Портами а India Catalina",
            "portammy Discover India Catalina.",
        ).forEach { said ->
            assertEquals(VoiceCommand.TakeMeThere(catalina), VoiceGrammar.parse(said, here, language = it), "con «$said»")
        }
    }

    @Test
    fun `un lugar sin intencion clara se pregunta, no se supone`() {
        val it = VoiceLanguage.ITALIAN
        val catalina = place("9", "Discover India Catalina")
        val here = places + catalina
        assertEquals(VoiceCommand.ChooseAction(catalina), VoiceGrammar.parse("India Catalina", here, language = it))
        assertEquals(VoiceCommand.ChooseAction(catalina), VoiceGrammar.parse("voglio la India Catalina", here, language = it))
        // Con intención clara no pregunta.
        assertEquals(VoiceCommand.TellMeAbout(catalina), VoiceGrammar.parse("raccontami di India Catalina", here, language = it))
        assertEquals(VoiceCommand.TakeMeThere(catalina), VoiceGrammar.parse("portami a India Catalina", here, language = it))
        // Las respuestas a "¿te llevo o te cuento?".
        assertEquals(VoiceCommand.TellMeAbout(catalina), VoiceGrammar.parse("la storia", here, focus = catalina, language = it))
        assertEquals(VoiceCommand.TakeMeThere(catalina), VoiceGrammar.parse("portami", here, focus = catalina, language = it))
        assertEquals(VoiceCommand.Yes, VoiceGrammar.parse("sì", here, focus = catalina, language = it))
    }

    @Test
    fun `si el nombre encaja con varios, pregunta cual`() {
        val old = place("10", "Castillo de San Felipe")
        val new = place("11", "Iglesia de San Felipe")
        val command = VoiceGrammar.parse("San Felipe", listOf(old, new))
        assertEquals(VoiceCommand.WhichOne(listOf(old, new)), command)
        // Y "el segundo" contesta, porque quedan enumerados.
        assertEquals(VoiceCommand.TellMeAbout(new), VoiceGrammar.parse("el segundo", listOf(old, new), listed = listOf(old, new)))
    }

    @Test
    fun `terminar la ruta de viva voz`() {
        val it = VoiceLanguage.ITALIAN
        // Transcripciones reales del Huawei, y las formas corrientes.
        listOf("Termina el percoso.", "termina percoso.", "annulla la navigazione", "cancella il percorso", "fine percorso", "stop navigation", "cancela la ruta")
            .forEach { said -> assertEquals(VoiceCommand.EndRoute, VoiceGrammar.parse(said, places, language = it), "con «$said»") }
        // "Terminal" es un destino, no una orden de parar.
        assertEquals(VoiceCommand.FindPlaces("terminal de transporte"), VoiceGrammar.parse("llévame a la terminal", places))
        // "portami" con n, como lo escribió el reconocedor.
        assertEquals(VoiceCommand.TakeMeThere(forum), VoiceGrammar.parse("Portani", places, focus = forum, language = it))
    }

    @Test
    fun `el nombre del lugar se reconoce aunque llegue mal escrito`() {
        val it = VoiceLanguage.ITALIAN
        val catalina = place("9", "Discover India Catalina")
        val here = places + catalina
        // Transcripciones reales con ruido de fondo.
        assertEquals(VoiceCommand.ChooseAction(catalina), VoiceGrammar.parse("India Cataline", here, language = it))
        assertEquals(VoiceCommand.ChooseAction(catalina), VoiceGrammar.parse("the Catala.", here, language = it))
        // Palabras cortas parecidas no cuentan.
        assertIs<VoiceCommand.NotUnderstood>(VoiceGrammar.parse("the rum", here, language = it))
    }

    @Test
    fun `el nombre completo gana al que solo lo contiene`() {
        val it = VoiceLanguage.ITALIAN
        val jewel = place("20", "Cartagena de Indias, the Jewel of the Indies")
        val city = place("21", "Cartagena")
        val here = listOf(jewel, city)
        assertEquals(VoiceCommand.ChooseAction(city), VoiceGrammar.parse("Cartagena", here, language = it))
        assertEquals(VoiceCommand.ChooseAction(jewel), VoiceGrammar.parse("Cartagena de Indias", here, language = it))
        assertEquals(VoiceCommand.TakeMeThere(city), VoiceGrammar.parse("portami a Cartagena", here, language = it))
    }

    @Test
    fun `al preguntar cual, el ordinal elige y sigue preguntando que hacer`() {
        val it = VoiceLanguage.ITALIAN
        val old = place("10", "Castillo de San Felipe")
        val new = place("11", "Iglesia de San Felipe")
        val options = listOf(old, new)
        assertEquals(VoiceCommand.ChooseAction(new), VoiceGrammar.parse("la seconda", options, listed = options, language = it, choosing = true))
        assertEquals(VoiceCommand.ChooseAction(old), VoiceGrammar.parse("il primo", options, listed = options, language = it, choosing = true))
        assertEquals(VoiceCommand.ChooseAction(new), VoiceGrammar.parse("la segunda", options, listed = options, choosing = true))
        // Contestando con el nombre, también.
        assertEquals(VoiceCommand.ChooseAction(old), VoiceGrammar.parse("castillo", options, listed = options, language = it, choosing = true))
        // Tras "¿qué hay cerca?" el ordinal sigue contando, como antes.
        assertEquals(VoiceCommand.TellMeAbout(new), VoiceGrammar.parse("la seconda", options, listed = options, language = it))
    }

    @Test
    fun `lo buscado se pide por categoria cuando la tiene`() {
        assertEquals("gas_station", VoiceGrammar.searchCategoryOf("distributore di benzina"))
        assertEquals("restaurant", VoiceGrammar.searchCategoryOf("ristorante"))
        assertEquals("restaurant", VoiceGrammar.searchCategoryOf("restaurante"))
        // Lo que propone el modelo con sus palabras.
        assertEquals("cafe", VoiceGrammar.searchCategoryOf("coffee"))
        assertEquals("gas_station", VoiceGrammar.searchCategoryOf("gasolina"))
        // Sin categoría, se busca el texto.
        assertNull(VoiceGrammar.searchCategoryOf("carbonara"))
    }

    @Test
    fun `busquedas dichas como se dicen, con el tipo de cocina`() {
        val es = VoiceLanguage.SPANISH
        val it = VoiceLanguage.ITALIAN
        fun category(said: String, language: VoiceLanguage): String? {
            val command = VoiceGrammar.parse(said, emptyList(), language = language)
            return (command as? VoiceCommand.FindPlaces)?.let { VoiceGrammar.searchCategoryOf(it.query) }
        }
        mapOf(
            "dame los restaurantes de comida mejicana más cercana" to "mexican_restaurant",
            "en dónde encuentro un centro comercial" to "shopping_mall",
            "dónde puedo comer comida italiana" to "italian_restaurant",
            "quiero comer sushi" to "sushi_restaurant",
            "hay alguna marisquería cerca" to "seafood_restaurant",
            "busca un restaurante peruano" to "peruvian_restaurant",
            "necesito una lavandería" to "laundry",
            "dónde hay un cine" to "cinema",
            "llévame al centro comercial más cercano" to "shopping_mall",
            "where can I find a mexican restaurant" to "mexican_restaurant",
            "find me the nearest mall" to "shopping_mall",
            "tengo hambre" to "restaurant",
        ).forEach { (said, expected) -> assertEquals(expected, category(said, es), "con «$said»") }
        mapOf(
            "dove trovo un ristorante messicano" to "mexican_restaurant",
            "voglio mangiare pesce" to "seafood_restaurant",
            "c'è un centro commerciale qui vicino" to "shopping_mall",
            "dove posso comprare vestiti" to "clothing_store",
            "un ristorante cinese vicino" to "chinese_restaurant",
            "ho fame" to "restaurant",
        ).forEach { (said, expected) -> assertEquals(expected, category(said, it), "con «$said»") }
        // Lo que se busca se dice en el idioma del conductor.
        assertEquals(VoiceCommand.FindPlaces("ristorante messicano"), VoiceGrammar.parse("dove trovo un ristorante messicano", emptyList(), language = it))
    }

    @Test
    fun `cambiar de idioma de viva voz`() {
        val es = VoiceLanguage.SPANISH
        val it = VoiceLanguage.ITALIAN
        val en = VoiceLanguage.ENGLISH
        mapOf(
            "parla spagnolo" to es,
            "habla en español" to es,
            "cambia la lingua in italiano" to it,
            "in italiano" to it,
            "en español" to es,
            "speak English" to en,
            "cambia el idioma a inglés" to en,
            "háblame en italiano" to it,
        ).forEach { (said, expected) ->
            assertEquals(VoiceCommand.SwitchLanguage(expected), VoiceGrammar.parse(said, places, language = it), "con «$said»")
        }
        // El nombre del idioma suelto sigue siendo cocina.
        assertEquals("italian_restaurant", (VoiceGrammar.parse("dónde puedo comer comida italiana", places) as VoiceCommand.FindPlaces).let { VoiceGrammar.searchCategoryOf(it.query) })
        assertEquals("italian_restaurant", (VoiceGrammar.parse("un ristorante italiano vicino", places, language = it) as VoiceCommand.FindPlaces).let { VoiceGrammar.searchCategoryOf(it.query) })
    }
}
