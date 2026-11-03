package com.secretworld.auto.llmbench

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Debug
import android.util.Log
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import com.secretworld.auto.domain.IntentPrompt
import com.secretworld.auto.domain.LanguageModel
import com.secretworld.auto.domain.LlmIntentResolver
import com.secretworld.auto.feature.voice.GemmaLanguageModel
import com.secretworld.auto.feature.voice.ModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Pasa [BenchCases] por un modelo y cuenta aciertos, tiempos y memoria.
 *
 * Un modelo por ejecución, para que la memoria medida sea la suya:
 *
 *     adb shell am start -S -n com.secretworld.auto.llmbench/.BenchActivity --es model qwen
 *     adb shell am start -S -n com.secretworld.auto.llmbench/.BenchActivity --es model gemma
 *
 * El resultado va a logcat (tag SWLlmBench) y a `files/results-<modelo>.tsv`.
 * Ver spikes/llm-bench/README.md.
 */
class BenchActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val out = StringBuilder()
    private lateinit var view: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK); addView(view) })

        val which = intent.getStringExtra("model") ?: "qwen"
        scope.launch { run(which) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun run(which: String) {
        line("=== $which · ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} ===")
        val pssBefore = pssMb()

        val started = System.nanoTime()
        val (model, release) = when (which) {
            "gemma" -> {
                val gemma = GemmaLanguageModel(applicationContext, ModelStore(applicationContext))
                gemma.load().getOrElse { return fail("no cargó Gemma: $it (¿falta models/llm-gemma3-1b/model.task?)") }
                line("cargado en ${ms(started)} ms")
                val warm = System.nanoTime()
                gemma.warm(IntentPrompt.PREFIX)
                line("prefijo preparado en ${ms(warm)} ms")
                gemma to gemma::release
            }
            else -> {
                val file = File(getExternalFilesDir("models"), "qwen3-0.6b/model.litertlm")
                val qwen = LiteRtLanguageModel(file, cacheDir)
                qwen.load().getOrElse { return fail("no cargó Qwen: $it") }
                line("cargado en ${ms(started)} ms")
                qwen to qwen::release
            }
        }
        line("memoria: ${pssBefore} MB antes, ${pssMb()} MB con el modelo")
        line("")

        val rows = mutableListOf<String>()
        val results = BenchCases.ALL.map { case ->
            val resolver = LlmIntentResolver(
                model as LanguageModel,
                position = { BenchCases.HERE },
                language = case.language,
                onFailure = { Log.e(TAG, "el modelo falló", it) },
            )
            val t = System.nanoTime()
            val resolution = resolver.resolve(case.heard, BenchCases.PLACES, case.memory())
            val elapsed = ms(t)
            val ok = case.expect.matches(resolution.command)
            val got = BenchCases.describe(resolution.command)
            line("${if (ok) "OK  " else "MAL "} ${case.language.code} ${elapsed.toString().padStart(6)} ms  «${case.heard}»")
            line("        esperado ${case.expect.label} · obtenido $got")
            line("        dice: ${resolution.reply ?: "-"}")
            rows += listOf(which, case.language.code, ok, elapsed, case.heard, case.expect.label, got, resolution.reply.orEmpty())
                .joinToString("\t") { it.toString().replace('\t', ' ') }
            Triple(case, ok, elapsed)
        }

        line("")
        line("=== resumen $which ===")
        results.groupBy { it.first.language }.forEach { (language, list) ->
            line("${language.code}: ${list.count { it.second }}/${list.size} aciertos")
        }
        val times = results.map { it.third }.sorted()
        line("total: ${results.count { it.second }}/${results.size} aciertos")
        line("tiempo por orden: mediana ${times[times.size / 2]} ms, máx ${times.last()} ms, primera ${results.first().third} ms")
        line("memoria al final: ${pssMb()} MB")

        File(getExternalFilesDir(null), "results-$which.tsv").writeText(
            "modelo\tidioma\tacierto\tms\toido\tesperado\tobtenido\tdice\n" + rows.joinToString("\n") + "\n",
        )
        release()
        line("BENCH DONE")
    }

    private fun fail(reason: String) {
        line("FALLO: $reason")
        line("BENCH DONE")
    }

    private fun pssMb(): Int = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024

    private fun ms(since: Long) = (System.nanoTime() - since) / 1_000_000

    @Synchronized
    private fun line(s: String) {
        Log.i(TAG, s)
        out.append(s).append('\n')
        val text = out.toString()
        runOnUiThread { view.text = text }
    }

    private companion object {
        const val TAG = "SWLlmBench"
    }
}
