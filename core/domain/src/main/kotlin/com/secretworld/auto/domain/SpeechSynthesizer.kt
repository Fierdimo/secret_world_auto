package com.secretworld.auto.domain

/**
 * Puerto de sintesis de voz. Vive en el dominio y es Kotlin puro a proposito:
 * el dominio decide *que* se dice y *cuando*, no *como* se produce el sonido.
 *
 * Hay dos implementaciones previstas: el motor embebido de la v1 y, si Pleos
 * llega a publicar su `TTS_SERVICE`, una que hable con la plataforma. Cambiar de
 * una a otra no debe tocar nada por encima de este interfaz.
 */
interface SpeechSynthesizer {

    /** Idiomas que este motor puede hablar ahora mismo. */
    val availableLanguages: Set<VoiceLanguage>

    /**
     * Sintetiza [text] y deja el resultado en [outputPath] como WAV.
     * Devuelve el resultado con lo medido, o lanza si el motor falla.
     */
    fun synthesizeToFile(text: String, language: VoiceLanguage, outputPath: String): Synthesis
}

/**
 * Lo producido y lo que costo producirlo. [realTimeFactor] por debajo de 1
 * significa que se sintetiza mas rapido de lo que dura el audio, que es la
 * condicion para poder ir por delante de la narracion (RNF-7b).
 */
data class Synthesis(
    val outputPath: String,
    val audioSeconds: Double,
    val elapsedSeconds: Double,
    val sampleRate: Int,
) {
    val realTimeFactor: Double
        get() = if (audioSeconds > 0) elapsedSeconds / audioSeconds else Double.NaN
}
