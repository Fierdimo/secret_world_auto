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

    @Test
    fun `en italiano despierta con la llamada y sin ella`() {
        val it = VoiceLanguage.ITALIAN
        assertEquals("cosa c'è qui vicino", WakeWord.commandIn("Ehi Secret, cosa c'è qui vicino", it))
        assertEquals("portami al Colosseo", WakeWord.commandIn("secret portami al Colosseo", it))
        assertEquals("", WakeWord.commandIn("Sicret.", it))
        assertNull(WakeWord.commandIn("ho un segreto da dirti", it))
        assertNull(WakeWord.commandIn("cosa c'è qui vicino", it))
    }

    @Test
    fun `acepta la palabra escrita en cirilico`() {
        // Transcripciones reales del reconocedor de diez idiomas en un Huawei.
        val it = VoiceLanguage.ITALIAN
        assertEquals("", WakeWord.commandIn("Секрет.", it))
        assertEquals("cosa c'è qui vicino", WakeWord.commandIn("Секрет cosa c'è qui vicino", it))
        assertEquals("", WakeWord.commandIn("Si cre.", it))
        assertNull(WakeWord.commandIn("Привет, как дела", it))
    }

    @Test
    fun `con ruido de fondo, sigred despierta y lo parecido no`() {
        // Transcripciones reales en una cafetería, con música y voces.
        val it = VoiceLanguage.ITALIAN
        assertEquals("indiatalina.", WakeWord.commandIn("Sigred indiatalina.", it))
        assertEquals("india Catalina.", WakeWord.commandIn("Sigred india Catalina.", it))
        // "Sigred e" también encaja como llamada; lo que queda nombra igual el sitio.
        assertEquals("le Catalina.", WakeWord.commandIn("Sigred e le Catalina.", it))
        // "Tigre" sí: es como más veces se oyó "Sícret" con ruido.
        assertEquals("Cartagene", WakeWord.commandIn("Tigre Cartagene", it))
        assertEquals("Kartaję.", WakeWord.commandIn("Tygre Kartaję.", it))
        assertEquals("", WakeWord.commandIn("Tigre.", it))
        // Pero no en medio de una frase, ni lo que solo se le parece.
        assertNull(WakeWord.commandIn("ho visto una tigre", it))
        assertNull(WakeWord.commandIn("tigrato", it))
        assertNull(WakeWord.commandIn("sagrada familia", it))
        assertNull(WakeWord.commandIn("segreteria telefonica", it))
    }

    @Test
    fun `se reconoce por como suena, no por como se escribe`() {
        val es = VoiceLanguage.SPANISH
        // Transcripciones reales en un moto g75, con la orden detrás.
        listOf("Zikre", "Sikre", "Tigret", "Cigres", "Digret", "Segret").forEach { heard ->
            assertEquals("necesito un restaurante", WakeWord.commandIn("$heard necesito un restaurante", es), "con «$heard»")
        }
        // Palabras corrientes al principio de una frase no despiertan.
        listOf("sigue por la derecha", "segura que sí", "seguro", "siguiente", "sacar la basura", "cigarrillo").forEach {
            assertNull(WakeWord.commandIn(it, es), "con «$it»")
        }
    }

    @Test
    fun `mas formas del registro`() {
        val es = VoiceLanguage.SPANISH
        listOf("Icre,", "Cicre,", "Dicre", "Hicre", "Picret", "Tickred").forEach {
            assertEquals("llévame al Hotel Caribe.", WakeWord.commandIn("$it llévame al Hotel Caribe.", es), "con «$it»")
        }
    }
}
