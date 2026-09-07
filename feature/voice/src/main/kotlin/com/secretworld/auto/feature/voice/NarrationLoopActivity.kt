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
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.NarrationPrefetcher
import com.secretworld.auto.domain.NarrationTextSource
import com.secretworld.auto.domain.NarrationTrigger
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.TourQueue
import com.secretworld.auto.domain.VehicleState
import com.secretworld.auto.domain.VoiceLanguage
import java.io.File
import kotlin.concurrent.thread

/**
 * Demuestra el bucle completo del MVP con un vehiculo simulado:
 *
 *   posicion -> TourQueue -> NarrationPrefetcher -> sintesis a disco
 *                         -> NarrationTrigger -> ¿estaba listo el audio?
 *
 * La pregunta que responde es la unica que importa de esta estrategia:
 * **¿llega el audio antes de que haga falta?** Si en algun disparo el fichero no
 * esta, la anticipacion no sirve y RNF-1 no se cumple.
 */
class NarrationLoopActivity : Activity() {

    private val report = StringBuilder()
    private lateinit var view: TextView
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.BLACK) }
        view = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
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
        thread(name = "narration-loop") { run() }
    }

    private fun run() {
        val language = VoiceLanguage.ENGLISH
        line("=== BUCLE DE NARRACION · vehiculo simulado ===")

        val installer = VoiceInstaller(this)
        if (!installer.isInstalled(language)) {
            line("instalando voz ${language.code}...")
            if (installer.install(language).isFailure) {
                line("no hay voz instalada y no se pudo instalar. Fin.")
                return
            }
        }

        val store = DiskNarrationStore(filesDir).apply { clear() }
        val synthesizer = PiperSpeechSynthesizer(installer)
        val queue = TourQueue()
        val trigger = NarrationTrigger()
        val prefetcher = NarrationPrefetcher(
            store = store,
            synthesizer = synthesizer,
            textSource = SampleTexts,
            budgetBytes = 30L * 1024 * 1024,
            lookahead = 2,
        )

        val route = buildRoute()
        line("ruta simulada: ${route.size} lugares, 90 km/h, rumbo norte")
        line("presupuesto de cache: 30 MB · lookahead: 2")
        line("")

        val speed = 25.0 // m/s ~ 90 km/h
        var travelled = 0.0
        val heard = mutableSetOf<String>()
        var late = 0

        // Cada paso son 20 s de conduccion, o 500 m.
        repeat(TICKS) { tick ->
            val vehicle = VehicleState(
                position = northOf(START, travelled),
                headingDegrees = 0.0,
                speedMetersPerSecond = speed,
                parked = false,
            )
            val upcoming = queue.next(vehicle, route, heard, language, limit = 4)
            if (upcoming.isEmpty()) {
                travelled += speed * TICK_SECONDS
                return@repeat
            }

            val started = System.nanoTime()
            val prefetch = prefetcher.prefetch(upcoming, language)
            val prefetchSeconds = (System.nanoTime() - started) / 1e9

            line("t=${tick * TICK_SECONDS}s  km ${"%.1f".format(travelled / 1000)}  cola: ${upcoming.joinToString { it.id }}")
            if (prefetch.synthesized.isNotEmpty()) {
                line("   sintetizado por delante: ${prefetch.synthesized.joinToString()} (%.1f s)".format(prefetchSeconds))
            }
            if (prefetch.evicted.isNotEmpty()) line("   tirado de cache: ${prefetch.evicted.joinToString()}")
            if (prefetch.failed.isNotEmpty()) line("   FALLOS: ${prefetch.failed}")

            val next = upcoming.first()
            if (trigger.shouldStart(vehicle, next)) {
                val ready = store.has(next.id, language)
                val size = File(store.pathFor(next.id, language)).length() / 1024
                if (ready) {
                    line("   >>> NARRAR ${next.id}: audio LISTO en disco ($size KB)")
                } else {
                    late++
                    line("   >>> NARRAR ${next.id}: AUDIO NO LISTO — habria que esperar")
                }
                heard += next.id
            }

            travelled += speed * TICK_SECONDS
        }

        line("")
        line("cache final: ${store.entries().size} narraciones, ${store.totalBytes() / 1024} KB")
        line("lugares narrados: ${heard.size}")
        line(if (late == 0) "TODOS los disparos encontraron el audio ya listo" else "$late disparos sin audio listo")
        line("=== FIN ===")
    }

    private fun buildRoute(): List<Place> = listOf(
        1_500.0 to "porta-san-sebastiano",
        4_000.0 to "tomba-cecilia-metella",
        7_000.0 to "villa-dei-quintili",
        10_500.0 to "casal-rotondo",
        14_000.0 to "torre-selce",
    ).map { (meters, id) ->
        Place(
            id = id,
            name = id,
            position = northOf(START, meters),
            narrationSeconds = 25,
            languages = setOf(VoiceLanguage.ENGLISH, VoiceLanguage.ITALIAN),
        )
    }

    private fun northOf(from: Coordinates, meters: Double) =
        Coordinates(from.latitude + meters / 111_320.0, from.longitude)

    private fun line(s: String) {
        Log.i(TAG, s)
        report.append(s).append('\n')
        ui.post { view.text = report.toString() }
    }

    /** Textos cortos: aqui se mide la tuberia, no la prosa. */
    private object SampleTexts : NarrationTextSource {
        override fun textFor(place: Place, language: VoiceLanguage): String {
            val name = place.name.replace('-', ' ')
            return when (language) {
                VoiceLanguage.ENGLISH ->
                    "Coming up on your right is $name, one of the landmarks of the ancient Appian Way. " +
                        "It has stood beside this road for nearly two thousand years, watching traffic " +
                        "pass in both directions."
                VoiceLanguage.ITALIAN ->
                    "Sulla destra sta arrivando $name, uno dei monumenti dell'antica via Appia. " +
                        "E accanto a questa strada da quasi duemila anni."
            }
        }
    }

    private companion object {
        const val TAG = "SWLoop"
        const val TICKS = 14
        const val TICK_SECONDS = 20
        val START = Coordinates(41.8800, 12.5000)
    }
}
