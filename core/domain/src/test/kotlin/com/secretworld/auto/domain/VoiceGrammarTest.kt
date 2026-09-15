package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
        listOf("take me there", "navigate there", "take me to the forum").forEach {
            assertEquals(VoiceCommand.TakeMeThere, VoiceGrammar.parse(it, places), "con «$it»")
        }
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
}
