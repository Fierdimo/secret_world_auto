package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La palabra de activación, probada con **transcripciones sucias** y en los dos
 * idiomas que tienen palabra propia.
 */
class WakeWordTest {

    private val en = VoiceLanguage.ENGLISH
    private val es = VoiceLanguage.SPANISH

    @Test
    fun `despierta con la palabra y se queda con la orden`() {
        assertEquals("what's nearby", WakeWord.commandIn("Secret, what's nearby", en))
        assertEquals("stop", WakeWord.commandIn("hey secret stop", en))
        assertEquals("qué hay cerca", WakeWord.commandIn("Sícret, qué hay cerca", es))
        assertEquals("llévame ahí", WakeWord.commandIn("eh secret llévame ahí", es))
    }

    @Test
    fun `aguanta como la transcribe el reconocedor`() {
        // Todo visto en el registro del Moto g75.
        listOf("A Secret?", "e secret", "a seacret", "Eh, secret,", "el secreto", "Ei sicre", "Secreto,").forEach {
            assertEquals("llévame a la popa", WakeWord.commandIn("$it llévame a la popa", es), "con «$it»")
        }
        listOf("hey secrets", "A secret", "Secret.").forEach {
            assertEquals("tell me more", WakeWord.commandIn("$it tell me more", en), "con «$it»")
        }
    }

    @Test
    fun `las formas de siky tambien valen, por si cuelan`() {
        assertEquals("qué hay cerca", WakeWord.commandIn("Siky, qué hay cerca", es))
        assertEquals("qué hay cerca", WakeWord.commandIn("sicky qué hay cerca", es))
    }

    @Test
    fun `despertarla sin decir nada no es lo mismo que no despertarla`() {
        assertEquals("", WakeWord.commandIn("Secret", en))
        assertEquals("", WakeWord.commandIn("Sícret.", es))
        assertNull(WakeWord.commandIn("what's nearby", en))
        assertNull(WakeWord.commandIn("qué hay cerca", es))
    }

    @Test
    fun `solo despierta al principio de la frase`() {
        assertNull(WakeWord.commandIn("I have a secret to tell you", en))
        assertNull(WakeWord.commandIn("tengo un secreto que contarte", es))
        assertNull(WakeWord.commandIn("dile a siky que pare", es))
    }

    @Test
    fun `la tolerancia cubre lo inventado y no lo que solo se parece`() {
        listOf("secrit", "sicrit", "ey sicreth").forEach {
            assertEquals("qué hay cerca", WakeWord.commandIn("$it qué hay cerca", es), "con «$it»")
        }
        assertNull(WakeWord.commandIn("es seco el terreno", es))
        assertNull(WakeWord.commandIn("sector cinco", es))
        assertNull(WakeWord.commandIn("sí, claro", es))
        assertNull(WakeWord.commandIn("secretary, come here", en))
    }

    @Test
    fun `se detecta por transcripcion en todos los idiomas`() {
        VoiceLanguage.entries.forEach { assertNull(WakeWord.forLanguage(it).keywordTokens, "en $it") }
        assertEquals("Sícret", WakeWord.spoken(es))
    }
}
