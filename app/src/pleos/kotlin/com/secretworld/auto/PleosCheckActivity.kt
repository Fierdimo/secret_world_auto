package com.secretworld.auto

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.secretworld.auto.feature.voice.PleosCaas
import com.secretworld.auto.feature.voice.PleosCredentials
import com.secretworld.auto.feature.voice.PleosLanguageModel
import com.secretworld.auto.feature.voice.PleosRawTts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.stt.listener.OnServerConnectionListener as SttServer
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import ai.pleos.playground.tts.listener.OnServerConnectionListener as TtsServer

/**
 * Verifica por separado lo que da la plataforma: TTS, STT, LLM y navegación.
 * En la app el fallo de uno tapa a los demás —sin voz se apaga la escucha—;
 * aquí cada uno se prueba solo, con su error tal cual. Todo queda también en
 * el registro, con la etiqueta SWCheck.
 *
 * adb shell am start -n com.secretworld.auto.pleos/com.secretworld.auto.PleosCheckActivity --es run all
 */
class PleosCheckActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView
    private var running: Job? = null
    private val credentials by lazy { PleosCredentials.of(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 22f
            setTextColor(Color.BLACK)
            setPadding(24, 24, 24, 24)
        }
        scroll = ScrollView(this).apply { addView(output) }
        val buttons = LinearLayout(this).apply {
            listOf("ALL" to "all", "TTS" to "tts", "STT" to "stt", "LLM" to "llm", "NAV" to "nav", "OWN" to "own").forEach { (label, key) ->
                addView(
                    Button(this@PleosCheckActivity).apply {
                        text = label
                        textSize = 22f
                        setOnClickListener { run(key) }
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                fitsSystemWindows = true
                setBackgroundColor(Color.WHITE)
                addView(buttons)
                addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            },
        )
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        environment()
        if (savedInstanceState == null) intent.getStringExtra("run")?.let(::run)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("run")?.let(::run)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun say(line: String) {
        Log.i(TAG, line)
        runOnUiThread {
            output.append(line + "\n")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun version(pkg: String): String =
        runCatching { packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull() ?: "missing"

    private fun environment() {
        say("app ${BuildConfig.VERSION_NAME} · ${android.os.Build.DISPLAY}")
        say("caas ${version(PleosCaas.PACKAGE)} · navi service ${version(NAVI_SERVICE)} · credentials ${if (credentials != null) "yes" else "no"}")
        NAVIGATORS.forEach { pkg ->
            say("navigator $pkg ${version(pkg)} · launchable ${packageManager.getLaunchIntentForPackage(pkg) != null}")
        }
        val recognizers = packageManager.queryIntentServices(Intent(android.speech.RecognitionService.SERVICE_INTERFACE), 0)
            .joinToString { it.serviceInfo.packageName }
        say("system recognizers: ${recognizers.ifEmpty { "none" }}")
        listOf("TTS_SERVICE", "STT_SERVICE", "LLM_SERVICE", "NAVI_ROUTE", "NAVI_ROUTE_SEARCH").forEach { name ->
            val granted = checkSelfPermission("pleos.car.permission.$name") == PackageManager.PERMISSION_GRANTED
            say("permission $name: ${if (granted) "granted" else "NOT granted"}")
        }
    }

    private fun run(what: String) {
        if (running?.isActive == true) return
        running = scope.launch {
            val results = linkedMapOf<String, String>()
            suspend fun step(name: String, block: suspend () -> String) {
                say("--- $name")
                val verdict = try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    "FAIL ${e.javaClass.simpleName}: ${e.message}"
                }
                results[name] = verdict
                // Que la plataforma termine de soltar este paso antes del siguiente.
                delay(4_000)
                say("$name => $verdict")
            }
            if (what == "tts" || what == "all") {
                // El coreano, primero: es el control. Si ni él suena, falla la voz, no el idioma.
                listOf("ko-KR" to "안녕하세요. 테스트입니다.", "en-US" to "Hello, this is a test.", "es-US" to "Hola, esto es una prueba.")
                    .forEach { (tag, text) ->
                        step("TTS sdk $tag") { tts(tag, text) }
                        step("TTS raw $tag") {
                            PleosRawTts(this@PleosCheckActivity).speak(text, tag, TTS_START_MS, TTS_END_MS) { say("tts raw $it") }
                        }
                    }
            }
            if (what == "stt" || what == "all") {
                step("STT sdk en-US") { sttSdk("en-US") }
                step("STT sdk es-US") { sttSdk("es-US") }
                step("STT system en-US") { sttSystem("en-US") }
            }
            if (what == "llm" || what == "all") step("LLM") { llm() }
            if (what == "nav" || what == "all") step("NAV") { nav() }
            if (what == "own") {
                step("OWN it media") { own(android.media.AudioAttributes.USAGE_MEDIA) }
                step("OWN it assistant") { own(android.media.AudioAttributes.USAGE_ASSISTANT) }
            }
            say("=== RESULT")
            results.forEach { (name, verdict) -> say("$name: $verdict") }
            say("=== END")
        }
    }

    private suspend fun tts(tag: String, text: String): String {
        val mode = if (credentials != null) TtsMode.HYBRID else TtsMode.ON_DEVICE
        val client = TextToSpeech(this, mode)
        val init = CompletableDeferred<String?>()
        val began = CompletableDeferred<String?>()
        val done = CompletableDeferred<String?>()
        try {
            client.initialize(object : TtsInit {
                override fun onSuccess() { init.complete(null) }
                override fun onFailure(error: TtsError) { init.complete("init $error") }
            })
            val failure = withTimeoutOrNull(INIT_MS) { init.await() to true } ?: return "FAIL init timeout ($mode)"
            failure.first?.let { return "FAIL $it ($mode)" }
            credentials?.let { keys ->
                val server = CompletableDeferred<String>()
                client.registerApp(keys.clientId, keys.clientSecret, object : TtsServer {
                    override fun onConnected() { server.complete("connected") }
                    override fun onFailed(error: TtsError) { server.complete("failed $error") }
                })
                say("server: ${withTimeoutOrNull(INIT_MS) { server.await() } ?: "timeout"}")
            }
            client.addEventListener(object : EventListener {
                override fun onStart() { say("tts start"); began.complete(null) }
                override fun onReady() { say("tts ready") }
                override fun onDone() { say("tts done"); done.complete(null) }
                // Un fin de la frase anterior puede llegar tarde: solo cuenta si esta ya empezó.
                override fun onStop() { say("tts stop"); if (began.isCompleted) done.complete("stopped") }
                override fun onError(error: TtsError) {
                    say("tts error $error")
                    began.complete("error $error")
                    done.complete("error $error")
                }
                override fun onUpdatedRms(rms: Double) = Unit
            })
            client.setLocale(Locale.forLanguageTag(tag))
            client.speak(text, true)
            val started = withTimeoutOrNull(TTS_START_MS) { began.await() to true } ?: return "FAIL no start in ${TTS_START_MS / 1000}s ($mode)"
            started.first?.let { return "FAIL $it ($mode)" }
            val ended = withTimeoutOrNull(TTS_END_MS) { done.await() to true } ?: return "PARTIAL started, no end ($mode)"
            return ended.first?.let { "FAIL $it ($mode)" } ?: "OK, engine reports it spoke: confirm by ear ($mode)"
        } finally {
            runCatching { client.stop() }
            runCatching { client.release() }
        }
    }

    private suspend fun sttSdk(tag: String): String {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return "FAIL no microphone permission"
        val mode = if (credentials != null) SttMode.HYBRID else SttMode.ON_DEVICE
        val client = SpeechToText(this, mode)
        val init = CompletableDeferred<String?>()
        val result = CompletableDeferred<String>()
        var last = ""
        var peak = Double.NEGATIVE_INFINITY
        var opened = false
        try {
            client.initialize(object : SttInit {
                override fun onSuccess() { init.complete(null) }
                override fun onFailure(error: SttError) { init.complete("init $error") }
            })
            val failure = withTimeoutOrNull(INIT_MS) { init.await() to true } ?: return "FAIL init timeout ($mode)"
            failure.first?.let { return "FAIL $it ($mode)" }
            credentials?.let { keys ->
                val server = CompletableDeferred<String>()
                client.registerApp(keys.clientId, keys.clientSecret, object : SttServer {
                    override fun onConnected() { server.complete("connected") }
                    override fun onFailed(error: SttError) { server.complete("failed $error") }
                })
                say("server: ${withTimeoutOrNull(INIT_MS) { server.await() } ?: "timeout"}")
            }
            client.addListener(object : ResultListener {
                override fun onUpdated(text: String, isFinal: Boolean) {
                    say("stt \"$text\"${if (isFinal) " (final)" else ""}")
                    if (text.isNotBlank()) last = text
                    if (isFinal && text.isNotBlank()) result.complete("OK heard \"$text\"")
                }
                override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) = Unit
                override fun onStartedRecognition() { opened = true; say("stt started") }
                override fun onEndedRecognition() {
                    say("stt ended")
                    result.complete(if (last.isBlank()) "NOTHING HEARD" else "OK heard \"$last\"")
                }
                override fun onError(error: SttError) { say("stt error $error"); result.complete("FAIL error $error") }
                override fun onReady() { say("stt ready") }
                override fun onUpdatedRms(rms: Double) { if (rms > peak) peak = rms }
            })
            client.setLocale(Locale.forLanguageTag(tag))
            client.request()
            say(">>> SPEAK NOW ($tag)")
            val verdict = withTimeoutOrNull(STT_MS) { result.await() }
                ?: if (last.isNotBlank()) "PARTIAL heard \"$last\", no final" else "FAIL no result in ${STT_MS / 1000}s (started: $opened)"
            return "$verdict ($mode, peak rms ${if (peak.isInfinite()) "none" else "%.1f".format(Locale.US, peak)})"
        } finally {
            runCatching { client.stop() }
            runCatching { client.release() }
        }
    }

    private suspend fun sttSystem(tag: String): String {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return "FAIL no system recognizer"
        val result = CompletableDeferred<String>()
        val engine = SpeechRecognizer.createSpeechRecognizer(this)
        try {
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    result.complete(if (heard.isBlank()) "NOTHING HEARD" else "OK heard \"$heard\"")
                }
                override fun onError(error: Int) { result.complete("FAIL error $error") }
                override fun onReadyForSpeech(params: Bundle?) { say("system ready") }
                override fun onBeginningOfSpeech() { say("system speech") }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            engine.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag),
            )
            say(">>> SPEAK NOW ($tag, system)")
            return withTimeoutOrNull(STT_MS) { result.await() } ?: "FAIL no result in ${STT_MS / 1000}s"
        } finally {
            runCatching { engine.destroy() }
        }
    }

    private suspend fun llm(): String {
        // Cada trozo llega como un mensaje: se enseñan los primeros.
        var messages = 0
        val model = PleosLanguageModel(this, probeTimeoutMs = LLM_MS, timeoutMs = LLM_MS) { cmd, data ->
            if (messages++ < 12) say("llm $cmd ${data.replace('\n', ' ').take(80)}")
        }
        try {
            model.load()
            val ready = withTimeoutOrNull(LLM_MS + 5_000) {
                while (!model.ready && !model.failed) delay(200)
                model.ready
            } ?: return "FAIL no answer in ${LLM_MS / 1000}s"
            if (!ready) return "FAIL no complete answer in ${LLM_MS / 1000}s ($messages messages received)"
            val started = System.nanoTime()
            val answer = model.complete("", "What is the capital of France? Answer in one word.", singleLine = true)
            return "OK \"$answer\" in ${(System.nanoTime() - started) / 1_000_000} ms"
        } finally {
            model.release()
        }
    }

    /** Nuestra voz italiana: la sintetiza, deja el audio a mano para sacarlo con adb y lo reproduce por [usage]. */
    private suspend fun own(usage: Int): String {
        val file = java.io.File(getExternalFilesDir(null), "own-it.wav")
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val italian = com.secretworld.auto.domain.VoiceLanguage.ITALIAN
            val installer = com.secretworld.auto.feature.voice.VoiceInstaller(this@PleosCheckActivity)
            installer.install(italian).getOrThrow()
            if (!file.isFile) {
                val voice = com.secretworld.auto.feature.voice.PiperSpeechSynthesizer(installer)
                val done = voice.synthesizeToFile(OWN_TEXT, italian, file.absolutePath)
                say("own synthesis: $done")
                voice.release()
            }
        }
        val ended = CompletableDeferred<Unit>()
        val player = android.media.MediaPlayer()
        try {
            player.setAudioAttributes(
                android.media.AudioAttributes.Builder().setUsage(usage).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            player.setDataSource(file.absolutePath)
            player.prepare()
            player.setOnCompletionListener { ended.complete(Unit) }
            say("own playing ${player.duration} ms")
            player.start()
            withTimeoutOrNull(30_000) { ended.await() } ?: return "FAIL did not end"
            return "OK played, confirm by ear"
        } finally {
            player.release()
        }
    }

    private suspend fun nav(): String {
        val helper = NaviHelper(this)
        val init = CompletableDeferred<String?>()
        val outcome = CompletableDeferred<String>()
        try {
            helper.addListener(object : NaviHelperEventListener {
                override fun onRouteStarted(info: RouteStartInfo) { say("nav route started $info") }
                override fun onRoutingCompleted() { say("nav routing completed"); outcome.complete("OK routing completed") }
                override fun onRouteCancelled() { say("nav route cancelled") }
                override fun onDestinationChanged() { say("nav destination changed") }
                override fun onRouteStateInfo(info: ai.pleos.playground.navi.data.RouteStateInfo) { say("nav route state $info") }
                override fun onDestinationInfo(info: ai.pleos.playground.navi.data.DestinationInfo) { say("nav destination info $info") }
                override fun onDrivingInfo(info: ai.pleos.playground.navi.data.DrivingInfo) { say("nav driving info $info") }
                override fun onDestinationArrived(info: ai.pleos.playground.navi.data.DestinationArrivedInfo) { say("nav destination arrived $info") }
                override fun onNavigationStatus(status: NavigationStatus) { say("nav status $status") }
                override fun onError(code: NaviErrorCode) { say("nav error $code"); outcome.complete("FAIL error $code") }
            })
            helper.initialize({ init.complete(null) }, { error -> init.complete(error.toString()) })
            val failure = withTimeoutOrNull(INIT_MS) { init.await() to true } ?: return "FAIL service did not connect"
            failure.first?.let { return "FAIL $it" }
            say("nav service connected")
            val navigator = NAVIGATORS.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
            if (navigator == null) {
                say("no launchable navigator: the request goes to the service only")
            } else {
                startActivity(navigator.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                delay(NAV_WARM_MS)
            }
            helper.requestRoute(
                RouteInfo(
                    latitude = intent.getStringExtra("lat")?.toDoubleOrNull() ?: 37.48544722,
                    longitude = intent.getStringExtra("lon")?.toDoubleOrNull() ?: 127.03636666,
                    poiName = intent.getStringExtra("name") ?: "42dot", poiId = "",
                    address = intent.getStringExtra("name") ?: "42dot, Seoul", poiSubId = "", routeOption = RouteOption.RECOMMENDED,
                ),
            )
            say("nav requestRoute sent")
            val verdict = withTimeoutOrNull(NAV_MS) { outcome.await() }
            if (verdict?.startsWith("OK") == true) {
                helper.getRouteStateInfo()
                helper.getDestinationInfo()
                delay(4_000)
            }
            return verdict
                ?: "PARTIAL request accepted, navigator did not answer in ${NAV_MS / 1000}s"
        } finally {
            // `keep`: la ruta se queda puesta, para verla en el navegador.
            if (!intent.getBooleanExtra("keep", false)) runCatching { helper.cancelRoute() }
            runCatching { helper.release() }
        }
    }

    private companion object {
        const val TAG = "SWCheck"
        const val OWN_TEXT = "Il Colosseo, originariamente conosciuto come Anfiteatro Flavio, è il più grande anfiteatro romano del mondo. " +
            "Si trova nel centro della città di Roma."
        const val NAVI_SERVICE = "ai.pleos.playground.service.navi"
        val NAVIGATORS = listOf("ai.umos.maps.android.navigation.app", "com.autoever.navi")
        const val INIT_MS = 10_000L
        const val TTS_START_MS = 40_000L
        const val TTS_END_MS = 20_000L
        const val STT_MS = 20_000L
        const val LLM_MS = 120_000L
        const val NAV_WARM_MS = 12_000L
        const val NAV_MS = 20_000L
    }
}
