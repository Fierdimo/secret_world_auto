package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SpokenTextTest {

    private fun italian(text: String) = SpokenText.forSpeech(text, VoiceLanguage.ITALIAN)

    @Test
    fun `los miles separados por espacios se unen`() {
        assertEquals("tra 50000 e 87000 spettatori", italian("tra 50 000 e 87 000 spettatori"))
        assertEquals("1250000 abitanti", italian("1 250 000 abitanti"))
    }

    @Test
    fun `un numero que empieza por cero se dice con palabras en italiano`() {
        assertEquals("zero zero 1 8 4 Roma", italian("00184 Roma"))
        assertEquals("Agente zero zero 7", italian("Agente 007"))
        assertEquals("alle 10:zero zero", italian("alle 10:00"))
        assertEquals("zero,5 metri", italian("0,5 metri"))
    }

    @Test
    fun `los demas numeros no se tocan`() {
        listOf("Nel 2000", "Il 10 gennaio 1900", "50.000 persone", "nel 1500 e 1872", "2 chiese").forEach { assertEquals(it, italian(it)) }
    }

    @Test
    fun `fuera del italiano los ceros se quedan`() {
        assertEquals("Agente 007", SpokenText.forSpeech("Agente 007", VoiceLanguage.SPANISH))
        assertEquals("50000 espectadores", SpokenText.forSpeech("50 000 espectadores", VoiceLanguage.SPANISH))
    }
}
