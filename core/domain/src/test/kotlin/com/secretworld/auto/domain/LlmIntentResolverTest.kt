package com.secretworld.auto.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * El modelo se prueba **sin modelo**: lo que importa aquí es que el prefijo no
 * cambie entre llamadas, que la petición lleve lo que hace falta y que el parseo
 * no acepte nada que no sea una de las órdenes.
 */
class LlmIntentResolverTest {

    private val forum = place("1", "The Roman Forum")
    private val cecilia = place("2", "Basilica of St. Cecilia")
    private val circus = place("3", "Circus Maximus")
    private val places = listOf(forum, cecilia, circus)

    private fun memory(focus: Place? = null, listed: List<Place> = emptyList()) = ConversationMemory().apply {
        focus?.let { focusOn(it) }
        listed(listed)
    }

    /** Un modelo que contesta siempre lo mismo y apunta lo que se le pidió. */
    private class Canned(private val reply: String, override val ready: Boolean = true) : LanguageModel {
        val prefixes = mutableListOf<String>()
        val requests = mutableListOf<String>()
        override suspend fun complete(prefix: String, request: String): String {
            prefixes += prefix
            requests += request
            return reply
        }
    }

    @Test
    fun `una herramienta con lugar numerado se resuelve al lugar`() = runTest {
        val model = Canned("""{"tool": "tell_me_about", "place": 2}""")
        val command = LlmIntentResolver(model).resolve("that church thing", places, memory()).command
        assertEquals(VoiceCommand.TellMeAbout(cecilia), command)
    }

    @Test
    fun `el prefijo es identico en todas las llamadas`() = runTest {
        val model = Canned("""{"tool": "stop"}""")
        val resolver = LlmIntentResolver(model)
        resolver.resolve("enough", places, memory())
        resolver.resolve("basta così", places, memory(focus = forum))
        assertEquals(2, model.prefixes.size)
        assertEquals(model.prefixes[0], model.prefixes[1])
        assertTrue(model.prefixes[0].contains("tell_me_about"))
    }

    @Test
    fun `la peticion lleva los lugares numerados y el foco`() = runTest {
        val model = Canned("""{"tool": "stop"}""")
        LlmIntentResolver(model).resolve("hmm", places, memory(focus = cecilia))
        val request = model.requests.single()
        assertTrue(request.contains("1. The Roman Forum"))
        assertTrue(request.contains("3. Circus Maximus"))
        assertTrue(request.contains("Playing now: Basilica of St. Cecilia"))
        assertTrue(request.contains("Driver: \"hmm\""))
    }

    @Test
    fun `lo ultimo enumerado va primero en la lista y la conversacion viaja`() = runTest {
        val model = Canned("""{"tool": "stop"}""")
        val remembered = memory(listed = listOf(circus, forum)).apply { remember("what's nearby", "Around you: Circus Maximus, the Forum.") }
        LlmIntentResolver(model).resolve("the second one", places, remembered)
        val request = model.requests.single()
        assertTrue(request.indexOf("1. Circus Maximus") < request.indexOf("2. The Roman Forum"))
        assertTrue(request.contains("3. Basilica of St. Cecilia"))
        assertTrue(request.contains("Assistant: \"Around you: Circus Maximus, the Forum.\""))
    }

    @Test
    fun `una linea con la herramienta basta`() {
        assertEquals(VoiceCommand.WhatIsNearby, IntentPrompt.parse("what_is_nearby", "x", places, null).command)
        assertEquals(VoiceCommand.WhatIsNearby, IntentPrompt.parse("  what_is_nearby.\n", "x", places, null).command)
        assertEquals(VoiceCommand.TellMeAbout(cecilia), IntentPrompt.parse("tell_me_about 2", "x", places, null).command)
        assertEquals(VoiceCommand.TellMeAbout(cecilia), IntentPrompt.parse("**tell_me_about 2**", "x", places, null).command)
        assertEquals(VoiceCommand.Stop, IntentPrompt.parse("STOP", "x", places, null).command)
        assertEquals(VoiceCommand.WhatIsNearby, IntentPrompt.parse("The tool: What_is_nearby", "x", places, null).command)
        assertEquals(VoiceCommand.TellMeAbout(circus), IntentPrompt.parse("Tool: tell_me_about, place 3.", "x", places, null).command)
        assertEquals(VoiceCommand.Clarify("The Forum or the Circus?"), IntentPrompt.parse("clarify: The Forum or the Circus?", "x", places, null).command)
    }

    @Test
    fun `la frase tras la barra es lo que se dice, y solo con orden entendida`() {
        val ok = IntentPrompt.parse("what_is_nearby | Around you there's the Forum. Which one?", "x", places, null)
        assertEquals(VoiceCommand.WhatIsNearby, ok.command)
        assertEquals("Around you there's the Forum. Which one?", ok.reply)

        val clarify = IntentPrompt.parse("clarify | Do you mean the Forum?", "x", places, null)
        assertEquals(VoiceCommand.Clarify("Do you mean the Forum?"), clarify.command)

        val bad = IntentPrompt.parse("open_sunroof | Opening the roof!", "x", places, null)
        assertIs<VoiceCommand.NotUnderstood>(bad.command)
        assertEquals(null, bad.reply)

        val silent = IntentPrompt.parse("stop", "x", places, null)
        assertEquals(null, silent.reply)
    }

    @Test
    fun `la peticion lleva distancias cuando se sabe donde esta el coche`() = runTest {
        val model = Canned("stop | Sure.")
        LlmIntentResolver(model, position = { Coordinates(41.89, 12.48) }).resolve("hmm", places, memory())
        assertTrue(model.requests.single().contains("1. The Roman Forum, right here"))
    }

    @Test
    fun `si el modelo sigue escribiendo dialogo, solo cuenta su primera linea`() {
        val reply = "tell_me_about 1\nDriver: \"what's nearby\"\nwhat_is_nearby\n"
        assertEquals(VoiceCommand.TellMeAbout(forum), IntentPrompt.parse(reply, "x", places, null).command)
    }

    @Test
    fun `envoltorios de texto y vallas de codigo se toleran`() {
        val replies = listOf(
            "```json\n{\"tool\": \"what_is_nearby\"}\n```",
            "Sure! {\"tool\":\"what_is_nearby\"} Hope that helps.",
            "{ \"tool\" : \"WHAT_IS_NEARBY\" }",
        )
        replies.forEach {
            assertEquals(VoiceCommand.WhatIsNearby, IntentPrompt.parse(it, "x", places, null).command, "con «$it»")
        }
    }

    @Test
    fun `una herramienta desconocida no es una orden`() {
        val command = IntentPrompt.parse("""{"tool": "open_sunroof"}""", "abre el techo", places, null).command
        assertIs<VoiceCommand.NotUnderstood>(command)
    }

    @Test
    fun `un numero fuera de la lista no inventa un lugar`() {
        val command = IntentPrompt.parse("""{"tool": "tell_me_about", "place": 9}""", "x", places, null).command
        assertIs<VoiceCommand.NotUnderstood>(command)
    }

    @Test
    fun `sin numero pero con foco, cuenta el foco`() {
        val command = IntentPrompt.parse("""{"tool": "tell_me_about"}""", "what's that", places, circus).command
        assertEquals(VoiceCommand.TellMeAbout(circus), command)
    }

    @Test
    fun `aclarar exige una pregunta`() {
        assertEquals(
            VoiceCommand.Clarify("The Forum or the Circus?"),
            IntentPrompt.parse("""{"tool": "clarify", "question": "The Forum or the Circus?"}""", "x", places, null).command,
        )
        assertIs<VoiceCommand.NotUnderstood>(IntentPrompt.parse("""{"tool": "clarify"}""", "x", places, null).command)
    }

    @Test
    fun `sin JSON no hay orden`() {
        assertIs<VoiceCommand.NotUnderstood>(IntentPrompt.parse("I think you want the forum.", "x", places, null).command)
        assertIs<VoiceCommand.NotUnderstood>(IntentPrompt.parse("", "x", places, null).command)
    }

    @Test
    fun `un modelo que no esta listo no se llama`() = runTest {
        val model = Canned("""{"tool": "stop"}""", ready = false)
        val command = LlmIntentResolver(model).resolve("stop", places, memory()).command
        assertIs<VoiceCommand.NotUnderstood>(command)
        assertTrue(model.requests.isEmpty())
    }

    @Test
    fun `la gramatica va primero y el modelo solo ve lo que ella no entiende`() = runTest {
        val model = Canned("""{"tool": "what_is_nearby"}""")
        val layered = LayeredIntentResolver(GrammarIntentResolver(), LlmIntentResolver(model))

        assertEquals(VoiceCommand.Stop, layered.resolve("stop", places, memory()).command)
        assertTrue(model.requests.isEmpty(), "la gramática ya lo entendió")

        assertEquals(VoiceCommand.WhatIsNearby, layered.resolve("anything worth a look", places, memory()).command)
        assertEquals(1, model.requests.size)
    }

    @Test
    fun `sin modelo, la gramatica sola sigue diciendo que no entiende`() = runTest {
        val layered = LayeredIntentResolver(GrammarIntentResolver(), fallback = null)
        assertIs<VoiceCommand.NotUnderstood>(layered.resolve("anything worth a look", places, memory()).command)
        assertFalse(layered.resolve("stop", places, memory()).command is VoiceCommand.NotUnderstood)
    }

    private fun place(id: String, name: String) = Place(
        id = id,
        name = name,
        position = Coordinates(41.89, 12.48),
        narrationSeconds = 60,
        languages = setOf(VoiceLanguage.ENGLISH),
    )
}
