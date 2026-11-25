package com.secretworld.auto.domain

/**
 * El texto tal y como hay que dárselo a nuestra voz para que se entienda.
 *
 * La voz italiana (riccardo, x_low) se rompe con un dígito 0 al principio de un
 * número —«00184», «007», el «000» de «50 000»—: no suena mal ese número, sino
 * ininteligible **la frase entera**. Medido sintetizando y volviendo a
 * reconocer. La palabra «zero» la dice bien.
 */
object SpokenText {

    /** Miles separados por un espacio, también el fijo y el fino, como los escribe la Wikipedia: «50 000». */
    private val SPACED_THOUSANDS = Regex("(?<![\\d.,])\\d{1,3}(?:[ \\u00a0\\u2009\\u202f]\\d{3})+(?!\\d)")

    /** Un número que empieza por cero. No lo es el «000» de «50.000», que se lee bien. */
    private val LEADING_ZERO = Regex("(?<!\\d)(?<!\\d\\.)0\\d*")

    fun forSpeech(text: String, language: VoiceLanguage): String {
        val joined = SPACED_THOUSANDS.replace(text) { it.value.filter(Char::isDigit) }
        if (language != VoiceLanguage.ITALIAN) return joined
        return LEADING_ZERO.replace(joined) { number ->
            number.value.map { if (it == '0') "zero" else it.toString() }.joinToString(" ")
        }
    }
}
