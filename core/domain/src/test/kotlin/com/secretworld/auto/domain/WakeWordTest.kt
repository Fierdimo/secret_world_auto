package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La palabra de activación, probada con **transcripciones sucias**.
 *
 * Probarla con "siky" escrito perfecto no demuestra nada: un nombre inventado no
 * está en el diccionario del reconocedor y jamás vuelve así dos veces seguidas.
 */
class WakeWordTest {

    @Test
    fun `despierta con la palabra y se queda con la orden`() {
        assertEquals("what's nearby", WakeWord.commandIn("Siky, what's nearby"))
        assertEquals("stop", WakeWord.commandIn("siky stop"))
    }

    @Test
    fun `aguanta como la transcribe el reconocedor`() {
        // «silky» no es una suposición: es lo que devolvió el reconocedor del
        // moto g75 al decirle "siky" en voz alta.
        listOf("sicky", "sikhi", "psyche", "sky", "ciky", "silky").forEach {
            assertEquals(
                "tell me more",
                WakeWord.commandIn("$it tell me more"),
                "con «$it»",
            )
        }
    }

    @Test
    fun `despertarla sin decir nada no es lo mismo que no despertarla`() {
        // Cadena vacia: hay que contestar "dime". Null: no iba con nosotros.
        assertEquals("", WakeWord.commandIn("Siky"))
        assertNull(WakeWord.commandIn("what's nearby"))
    }

    @Test
    fun `solo despierta al principio de la frase`() {
        // "sky" aparece en cualquier conversacion; en medio no puede disparar.
        assertNull(WakeWord.commandIn("look at the sky"))
        assertNull(WakeWord.commandIn("what a beautiful sky today"))
    }

    @Test
    fun `no confunde palabras que solo se parecen de lejos`() {
        listOf("sticky", "skyline", "sickly", "sixty").forEach {
            assertTrue(!WakeWord.matches(it), "«$it» no deberia despertarla")
        }
    }
}
