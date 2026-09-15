package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals("what's nearby", WakeWord.commandIn("Hey Secret, what's nearby", en))
        assertEquals("stop", WakeWord.commandIn("hey secret stop", en))
        assertEquals("qué hay cerca", WakeWord.commandIn("Ey Sícret, qué hay cerca", es))
        assertEquals("llévame ahí", WakeWord.commandIn("hey secret llévame ahí", es))
    }

    @Test
    fun `aguanta como la transcribe el reconocedor`() {
        listOf("hey secrets", "Hey, Secret,", "a secret", "hey secrete").forEach {
            assertEquals("tell me more", WakeWord.commandIn("$it tell me more", en), "con «$it»")
        }
        listOf("Ey, sicret.", "Hey Secret,", "ei secret", "ay sícret", "Ey siquet").forEach {
            assertEquals("cuéntame más", WakeWord.commandIn("$it cuéntame más", es), "con «$it»")
        }
    }

    @Test
    fun `despertarla sin decir nada no es lo mismo que no despertarla`() {
        // Cadena vacía: hay que contestar "dime". Null: no iba con nosotros.
        assertEquals("", WakeWord.commandIn("Hey Secret", en))
        assertEquals("", WakeWord.commandIn("Ey sícret.", es))
        assertNull(WakeWord.commandIn("what's nearby", en))
        assertNull(WakeWord.commandIn("qué hay cerca", es))
    }

    @Test
    fun `solo despierta al principio de la frase`() {
        assertNull(WakeWord.commandIn("I have a secret to tell you", en))
        assertNull(WakeWord.commandIn("tengo un secreto que contarte", es))
        assertNull(WakeWord.commandIn("keep it secret", en))
    }

    @Test
    fun `cada idioma tiene su palabra, y la marca se tolera con dos letras de diferencia`() {
        assertEquals("Hey Secret", WakeWord.spoken(en))
        assertEquals("Ey Sícret", WakeWord.spoken(es))
        // Lo que vio el registro al decir "Ey Sícret": el reconocedor escribe
        // "e secret", "a seacret", "el secreto". Todo eso despierta.
        listOf("e secret", "a seacret", "el secreto", "ey secreth").forEach {
            assertEquals("qué hay cerca", WakeWord.commandIn("$it qué hay cerca", es), "con «$it»")
        }
        // Y lo que se parece de lejos, no.
        assertNull(WakeWord.commandIn("es secretario de estado", es))
        assertNull(WakeWord.commandIn("hey sector five", en))
    }

    @Test
    fun `solo el ingles tiene detector de palabra clave`() {
        assertEquals("▁HE Y ▁SE C RE T", WakeWord.forLanguage(en).keywordTokens)
        assertNull(WakeWord.forLanguage(es).keywordTokens)
    }

    @Test
    fun `no confunde palabras que solo se parecen de lejos`() {
        listOf("hey secretary", "hey sector", "a secret garden").forEach {
            assertFalse(WakeWord.matches(it, WakeWord.forLanguage(en)), "«$it» no debería despertarla")
        }
    }
}
