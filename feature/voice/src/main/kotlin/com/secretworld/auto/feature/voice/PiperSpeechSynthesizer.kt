package com.secretworld.auto.feature.voice

import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.secretworld.auto.domain.SpeechSynthesizer
import com.secretworld.auto.domain.Synthesis
import com.secretworld.auto.domain.VoiceLanguage
import java.io.File

/**
 * Sintesis embebida con sherpa-onnx y voces Piper VITS.
 *
 * Las voces **no viajan en el APK**: las instala [VoiceInstaller] en `filesDir`
 * cuando el usuario elige idioma. Este sintetizador solo sabe leer de disco, asi
 * que un idioma no instalado sencillamente no esta disponible.
 */
class PiperSpeechSynthesizer(private val installer: VoiceInstaller) : SpeechSynthesizer {

    private val engines = mutableMapOf<VoiceLanguage, OfflineTts>()
    private val enginesLock = Any()

    override val availableLanguages: Set<VoiceLanguage>
        get() = installer.installedLanguages()

    override fun synthesizeToFile(
        text: String,
        language: VoiceLanguage,
        outputPath: String,
    ): Synthesis {
        val tts = engineFor(language)
        val started = System.nanoTime()
        // Una sintesis a la vez por motor. Dos a la vez —una respuesta por
        // frases mientras se prepara la siguiente— no van el doble de lentas:
        // van cuatro veces mas lentas, y el motor no promete ser seguro entre hilos.
        val audio = synchronized(tts) { tts.generate(text, 0, 1.0f) }
        val elapsed = (System.nanoTime() - started) / 1_000_000_000.0

        require(audio.save(outputPath)) { "no se pudo escribir $outputPath" }

        val seconds = audio.samples.size.toDouble() / audio.sampleRate
        return Synthesis(outputPath, seconds, elapsed, audio.sampleRate)
    }

    /**
     * Deja la voz cargada sin sintetizar nada. Cargarla cuesta segundos —en un
     * móvil lento, la mitad de los 15 s que tardó la primera pregunta—, y
     * mejor pagarlos al arrancar que delante del conductor.
     */
    fun warm(language: VoiceLanguage) {
        engineFor(language)
    }

    /** Libera los motores. El disco es barato, la RAM del infotainment no. */
    fun release() {
        engines.clear()
    }

    /**
     * Los motores son caros de crear —decenas de MB y segundos— y por eso se
     * reutilizan. El candado no es decorativo: sin el, dos hilos que pidan el
     * mismo idioma a la vez construyen dos motores y el dispositivo se ahoga.
     */
    private fun engineFor(language: VoiceLanguage): OfflineTts = synchronized(enginesLock) {
        engines.getOrPut(language) {
        val dir = installer.installDirFor(language)
        check(installer.isInstalled(language)) {
            "la voz ${language.code} no esta instalada: instalar antes de narrar"
        }
        val model = requireNotNull(dir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") }) {
            "no hay modelo .onnx en $dir"
        }

        Log.i(TAG, "cargando voz ${language.code}: ${model.name}")
        OfflineTts(
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = model.absolutePath,
                        tokens = File(dir, "tokens.txt").absolutePath,
                        dataDir = File(dir, "espeak-ng-data").absolutePath,
                    ),
                    numThreads = NUM_THREADS,
                    debug = false,
                    provider = "cpu",
                ),
            ),
        )
        }
    }

    private companion object {
        const val TAG = "SWVoice"

        /**
         * El infotainment tiene 4 nucleos y los comparte con el resto del sistema.
         * Acaparar todos para sintetizar por delante seria egoista y contraproducente.
         */
        const val NUM_THREADS = 2
    }
}
