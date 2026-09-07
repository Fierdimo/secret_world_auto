package com.secretworld.auto.feature.voice

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.secretworld.auto.domain.VoiceLanguage
import java.io.File
import kotlin.concurrent.thread

/**
 * Spike 2: mide si un motor TTS embebido sirve para este producto.
 *
 * No es producto. Sintetiza un texto real de narracion en los dos idiomas de la
 * v1, escribe los WAV para poder escucharlos, y reporta carga, duracion, factor
 * de tiempo real y memoria. El resultado sale tambien por logcat con tag SWVoice.
 */
class VoiceSpikeActivity : Activity() {

    private val report = StringBuilder()
    private lateinit var view: TextView
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.BLACK) }
        view = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(40, 40, 40, 40)
        }
        scroll.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        setContentView(scroll)

        thread(name = "voice-spike") { runSpike() }
    }

    private fun runSpike() {
        line("=== SPIKE 2 · TTS EMBEBIDO ===")
        val outDir = getExternalFilesDir(null) ?: filesDir
        line("salida: ${outDir.absolutePath}")

        val installer = VoiceInstaller(this)
        val synthesizer = PiperSpeechSynthesizer(installer)
        line("voces ya instaladas: " + (installer.installedLanguages().joinToString { it.code }.ifEmpty { "ninguna" }))

        for (language in VoiceLanguage.entries) {
            line("")
            line("--- ${language.code.uppercase()} ---")
            val text = NarrationSamples.forLanguage(language)
            line("palabras del texto: ${text.split(Regex("\\s+")).size}")

            val pack = VoiceCatalog.packFor(language)
            line("paquete: ${pack?.bytes?.div(1048576)} MB · licencia: ${pack?.licence}")
            if (!installer.isInstalled(language)) {
                val installStart = System.nanoTime()
                val result = installer.install(language) { p ->
                    when (p) {
                        is VoiceInstaller.Progress.Downloading ->
                            line("  descargando %d%%".format((p.fraction * 100).toInt()))
                        VoiceInstaller.Progress.Verifying -> line("  verificando checksum")
                        VoiceInstaller.Progress.Extracting -> line("  extrayendo")
                        is VoiceInstaller.Progress.Done -> line("  instalada")
                        is VoiceInstaller.Progress.Failed -> line("  FALLO: ${p.reason}")
                    }
                }
                if (result.isFailure) {
                    line("no se pudo instalar la voz, se salta el idioma")
                    continue
                }
                line("instalacion completa: %.1f s".format((System.nanoTime() - installStart) / 1e9))
            }

            val out = File(outDir, "narracion-${language.code}.wav")
            try {
                Runtime.getRuntime().gc()
                val before = usedMemoryMb()
                val loadStart = System.nanoTime()
                // La primera sintesis incluye la carga del modelo.
                val first = synthesizer.synthesizeToFile(text, language, out.absolutePath)
                val totalFirst = (System.nanoTime() - loadStart) / 1_000_000_000.0
                val after = usedMemoryMb()

                line("audio generado     : %.1f s a %d Hz".format(first.audioSeconds, first.sampleRate))
                line("sintesis           : %.2f s".format(first.elapsedSeconds))
                line("carga + sintesis   : %.2f s".format(totalFirst))
                line("factor tiempo real : %.3f  %s".format(
                    first.realTimeFactor,
                    if (first.realTimeFactor < 0.5) "OK, cumple RNF-7b" else "POR ENCIMA DEL PRESUPUESTO",
                ))
                line("memoria            : %d -> %d MB".format(before, after))
                line("fichero            : ${out.name} (${out.length() / 1024} KB)")

                // Segunda pasada con el motor ya cargado: es el coste real en marcha.
                val second = synthesizer.synthesizeToFile(
                    text, language, File(outDir, "narracion-${language.code}-2.wav").absolutePath,
                )
                line("2a pasada (motor caliente): %.2f s, RTF %.3f".format(
                    second.elapsedSeconds, second.realTimeFactor,
                ))
            } catch (t: Throwable) {
                line("ERROR: $t")
                Log.e(TAG, "fallo sintetizando ${language.code}", t)
            }
        }
        line("")
        line("=== FIN ===")
    }

    private fun usedMemoryMb(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / 1_048_576
    }

    private fun line(s: String) {
        Log.i(TAG, s)
        report.append(s).append('\n')
        ui.post { view.text = report.toString() }
    }

    private companion object {
        const val TAG = "SWVoice"
    }
}
