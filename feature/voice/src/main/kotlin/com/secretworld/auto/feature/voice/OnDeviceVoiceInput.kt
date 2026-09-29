package com.secretworld.auto.feature.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.ToneGenerator
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiser
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserGtcrnModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserModelConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.secretworld.auto.domain.VoiceLanguage
import com.secretworld.auto.domain.WakeWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * El micrófono **en el dispositivo**: palabra de activación, detector de voz y
 * reconocedor, los tres con sherpa-onnx y sin red.
 *
 * Es lo que sustituye al bucle de reabrir el reconocedor del sistema. La
 * diferencia no es de matiz: aquí el micrófono está abierto siempre, pero lo
 * único que corre en continuo es un detector de 3 M de parámetros que solo sabe
 * decir si ha sonado "Siky". El reconocedor de verdad —Whisper— solo se
 * ejecuta sobre la frase que viene después, delimitada por el detector de voz.
 *
 * Cómo se encadena, en un solo hilo de audio. Hay **dos formas de despertar**,
 * según el idioma ([WakeWord]): el detector de palabras clave solo sabe inglés.
 *
 * ```
 * inglés:   micrófono ──▶ [KWS] ──"hey secret"──▶ [VAD] ──frase──▶ [Whisper] ──▶ Heard
 * español:  micrófono ──▶ [VAD] ──frase──▶ [Whisper] ──¿empieza por "hola secreto"?──▶ Heard
 *                                                       └── no ──▶ se descarta en silencio
 * ```
 *
 * La segunda transcribe **todo** lo que se dice en el coche, y eso cuesta CPU:
 * ~0,5 s de Whisper por cada frase que oiga. Es el precio de un idioma que el
 * detector no conoce, y está anotado como coste a medir en el ROADMAP.
 *
 * Todo lo de sherpa-onnx se toca **solo desde ese hilo**: sus objetos no son
 * seguros entre hilos y compartirlos es la forma más rápida de un cierre nativo.
 *
 * Los modelos no van en el APK: los resuelve [ModelStore] (SPEC RF-1.8.7).
 */
class OnDeviceVoiceInput(
    private val context: Context,
    private val models: ModelStore,
    private val language: VoiceLanguage,
) : VoiceInput {

    private val _state = MutableStateFlow<VoiceInput.State>(VoiceInput.State.Idle)
    override val state: StateFlow<VoiceInput.State> = _state.asStateFlow()

    override val available: Boolean
        get() = models.missing(ModelCatalog.listening(language)).isEmpty()

    /** Si el APK trae dentro lo que falta para escuchar: se instalará al arrancar. */
    val bundled: Boolean
        get() = models.missing(ModelCatalog.listening(language)).all(models::isBundled)

    override val hasPermission: Boolean
        get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override val continuous: Boolean = true

    /** Cómo se despierta en este idioma. */
    private val wake = WakeWord.forLanguage(language)

    /** Sin tokens para el detector, se despierta por transcripción. */
    private val wakeByTranscript: Boolean = wake.keywordTokens == null

    /** Los tres motores. Se crean juntos en [load] y solo los usa el hilo de audio. */
    private class Engines(
        val spotter: KeywordSpotter,
        val vad: Vad,
        val recognizer: OfflineRecognizer,
        /** Opcional: sin el paquete del reductor, las frases van tal cual. */
        val denoiser: OfflineSpeechDenoiser?,
    ) {
        fun release() {
            spotter.release()
            vad.release()
            recognizer.release()
            denoiser?.release()
        }
    }

    @Volatile
    private var engines: Engines? = null

    val loaded: Boolean get() = engines != null

    /** Si se quiere vigilancia permanente de la palabra de activación. */
    @Volatile
    private var spotting = false

    /** Hay una orden directa pendiente: la siguiente frase no necesita "Siky". */
    @Volatile
    private var directRequested = false

    /** La orden directa pendiente es la respuesta a una pregunta nuestra. */
    @Volatile
    private var answerExpected = false

    /** Se pidió cortar la frase en curso. La atiende el hilo de audio. */
    @Volatile
    private var abortRequested = false

    /**
     * Mientras la app habla, no escucha. Sin cancelación de eco, lo que oye en
     * ese momento es a sí misma: transcribirlo gasta CPU y, con la vigilancia
     * por transcripción, podría hasta despertarse con su propia voz. Se pierde
     * la interrupción por voz, que en este dispositivo ya no funcionaba.
     */
    @Volatile
    var muted: Boolean = false

    @Volatile
    private var running = false
    private var thread: Thread? = null

    /**
     * Carga los modelos. Cuesta segundos y decenas de MB; se llama una vez, en
     * segundo plano, al arrancar. Idempotente.
     */
    suspend fun load(): Result<Unit> = withContext(Dispatchers.IO) {
        if (engines != null) return@withContext Result.success(Unit)
        runCatching {
            val kws = requireNotNull(models.dirFor(ModelCatalog.KWS_EN)) { "falta ${ModelCatalog.KWS_EN.id}" }
            val vad = requireNotNull(models.dirFor(ModelCatalog.VAD)) { "falta ${ModelCatalog.VAD.id}" }
            val asrPack = ModelCatalog.asrFor(language)
            val asr = requireNotNull(models.dirFor(asrPack)) { "falta ${asrPack.id}" }

            val started = System.nanoTime()
            // La palabra del idioma, escrita para el detector. El fichero del
            // paquete es solo un ejemplo: manda [WakeWord].
            val keywords = File(context.cacheDir, "keywords-${language.code}.txt").apply {
                // Sin tokens en el idioma, el detector se crea igual —es barato—
                // pero no se usa: se vigila por transcripción.
                writeText((wake.keywordTokens ?: "▁HE Y ▁SE C RE T") + " @wake\n")
            }
            val built = Engines(
                spotter = KeywordSpotter(config = spotterConfig(kws, keywords)),
                vad = Vad(config = vadConfig(vad)),
                recognizer = OfflineRecognizer(config = recognizerConfig(asr)),
                denoiser = models.dirFor(ModelCatalog.DENOISER)?.let { dir ->
                    runCatching { OfflineSpeechDenoiser(config = denoiserConfig(dir)) }
                        .onFailure { Log.w(TAG, "reductor de ruido no disponible", it) }
                        .getOrNull()
                },
            )
            Log.i(TAG, "reductor de ruido: ${if (built.denoiser != null) "activo" else "no instalado"}")
            Log.i(TAG, "modelos de escucha cargados en ${(System.nanoTime() - started) / 1_000_000} ms")
            synchronized(this) {
                if (engines == null) engines = built else built.release()
            }
        }
    }

    override fun listen(direct: Boolean) {
        if (!available) {
            _state.value = VoiceInput.State.Failed("voice models are not installed")
            return
        }
        if (!hasPermission) {
            _state.value = VoiceInput.State.Failed("microphone permission not granted")
            return
        }
        if (engines == null) {
            _state.value = VoiceInput.State.Failed("still loading the voice models")
            return
        }
        if (direct) {
            directRequested = true
            _state.value = VoiceInput.State.Listening
        } else {
            spotting = true
        }
        ensureThread()
    }

    override fun listenForAnswer() {
        answerExpected = true
        listen(direct = true)
    }

    override fun cancel() {
        directRequested = false
        abortRequested = true
        // Sin vigilancia no queda nada que hacer en el hilo; con ella, sigue.
        if (!spotting) stopThread()
        if (_state.value is VoiceInput.State.Listening) _state.value = VoiceInput.State.Idle
    }

    override fun stopListening() {
        spotting = false
        directRequested = false
        abortRequested = true
        stopThread()
        if (_state.value is VoiceInput.State.Listening) _state.value = VoiceInput.State.Idle
    }

    override fun acknowledge() {
        if (_state.value !is VoiceInput.State.Listening) _state.value = VoiceInput.State.Idle
    }

    /** Suelta los modelos. La RAM del infotainment no es infinita. */
    fun release() {
        stopListening()
        synchronized(this) {
            engines?.release()
            engines = null
        }
    }

    private fun ensureThread() {
        if (running) return
        running = true
        thread = Thread(::audioLoop, "sw-voice-audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun stopThread() {
        running = false
        thread?.join(500)
        thread = null
    }

    // ------------------------------------------------------------------ hilo de audio

    private enum class Mode {
        /** Vigilando con el detector de palabras clave. */
        SPOT,
        /** Vigilando por transcripción: cada frase se escribe y se mira si empieza por la palabra. */
        LISTEN_ALL,
        /** Capturando una orden. */
        CAPTURE,
    }

    @SuppressLint("MissingPermission")
    private fun audioLoop() {
        val engines = engines ?: run { running = false; return }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // VOICE_RECOGNITION: la fuente pensada para reconocedores, sin procesado
        // que cambie la voz. Es la única con la que el detector reconoce "Siky"
        // en el móvil de pruebas.
        //
        // Lo que NO resuelve, y está medido: mientras la app narra por el
        // altavoz, el micrófono se oye a sí mismo a diez veces el nivel de la voz
        // (0,08 frente a 0,008) y "Siky" no la interrumpe. Se probó:
        // - AcousticEchoCanceler sobre esta fuente: dice estar activo, no cancela.
        // - VOICE_COMMUNICATION (cancelación de eco del sistema): la voz entra
        //   pero deformada, y el detector no reconoce la palabra ni una vez.
        // - NoiseSuppressor: silencia también la voz.
        // Interrumpir la narración por voz con altavoz y micrófono juntos exige
        // cancelación de eco de verdad, con la señal reproducida como referencia:
        // o la del vehículo, o una por software (WebRTC APM). Ver ROADMAP.
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, SAMPLE_RATE * 2),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            _state.value = VoiceInput.State.Failed("no audio input on this device")
            running = false
            return
        }
        val echo = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } else null
        Log.i(TAG, "cancelación de eco: ${echo?.enabled ?: "no disponible"}")

        // Un toque breve al oír "Siky". Es lo que separa "me ha oído" de "no sé
        // si me ha oído": sin él, el conductor repite la orden justo mientras
        // el reconocedor está trabajando en la primera.
        val chime = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, CHIME_VOLUME) }.getOrNull()

        var spotStream = engines.spotter.createStream()
        // Chunks de vigilancia desde la última renovación del flujo del detector.
        var spotChunks = 0
        val pcm = ShortArray(CHUNK_SAMPLES)
        val chunk = FloatArray(CHUNK_SAMPLES)
        var mode = if (wakeByTranscript) Mode.LISTEN_ALL else Mode.SPOT
        var capture: Capture? = if (wakeByTranscript) Capture(direct = false, preRoll = emptyList(), transcriptWake = true) else null
        // Los últimos chunks, siempre: cuando "Siky" se confirma, la orden ya ha
        // empezado a sonar, y sin esto se pierde su primera sílaba.
        val recent = ArrayDeque<FloatArray>()
        // Nivel de entrada, para poder ver en el registro si el micrófono oye algo.
        var levelAcc = 0.0
        var levelChunks = 0

        try {
            record.startRecording()
            abortRequested = false
            // Por dónde entra el audio: en un móvil con auriculares conectados no
            // es obvio, y cambia lo que significa cada medida del registro.
            record.routedDevice?.let { Log.i(TAG, "micrófono en uso: ${it.productName} (tipo ${it.type})") }
            while (running) {
                // Sin vigilancia ni orden pendiente no hay nada que oír.
                if (mode != Mode.CAPTURE && !spotting && !directRequested) break

                val n = record.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                if (n <= 0) continue
                var energy = 0.0
                for (i in 0 until n) {
                    chunk[i] = pcm[i] / 32768f
                    energy += chunk[i] * chunk[i]
                }
                levelAcc += Math.sqrt(energy / n)
                if (++levelChunks >= LEVEL_EVERY_CHUNKS) {
                    Log.d(TAG, "nivel medio del micrófono: %.4f (%s)".format(levelAcc / levelChunks, mode))
                    levelAcc = 0.0
                    levelChunks = 0
                }

                if (muted && mode != Mode.CAPTURE) {
                    // Se sigue leyendo el micrófono —para que no se acumule— y
                    // se tira lo leído.
                    recent.clear()
                    if (mode == Mode.LISTEN_ALL) capture = Capture(direct = false, preRoll = emptyList(), transcriptWake = true)
                    continue
                }

                if (abortRequested) {
                    abortRequested = false
                    if (mode == Mode.CAPTURE) {
                        Log.i(TAG, "frase cancelada")
                        capture = null
                        engines.spotter.reset(spotStream)
                        mode = Mode.SPOT
                    }
                }

                if (mode == Mode.SPOT) {
                    recent.addLast(chunk.copyOf(n))
                    while (recent.size > PRE_ROLL_CHUNKS) recent.removeFirst()
                }

                if (directRequested && mode != Mode.CAPTURE) {
                    directRequested = false
                    capture = Capture(direct = true, preRoll = emptyList(), answer = answerExpected)
                    answerExpected = false
                    recent.clear()
                    engines.vad.reset()
                    mode = Mode.CAPTURE
                    // El chunk que ha disparado el cambio ya es parte de la frase.
                }

                when (mode) {
                    Mode.SPOT -> {
                        // El detector es un modelo en streaming y arrastra estado
                        // desde que se abrió el flujo. Medido: reconoce "Siky" al
                        // arrancar y deja de hacerlo tras unos minutos de audio
                        // continuo, con el micrófono oyendo perfectamente. Un
                        // flujo nuevo cada poco lo mantiene como recién abierto.
                        if (++spotChunks >= SPOT_RENEW_CHUNKS) {
                            spotStream.release()
                            spotStream = engines.spotter.createStream()
                            spotChunks = 0
                        }
                        spotStream.acceptWaveform(chunk.copyOf(n), SAMPLE_RATE)
                        while (engines.spotter.isReady(spotStream)) engines.spotter.decode(spotStream)
                        val keyword = engines.spotter.getResult(spotStream).keyword
                        if (keyword.isNotEmpty()) {
                            Log.i(TAG, "palabra de activación: «$keyword»")
                            chime?.startTone(ToneGenerator.TONE_PROP_ACK, CHIME_MS)
                            engines.spotter.reset(spotStream)
                            capture = Capture(direct = false, preRoll = recent.toList())
                            recent.clear()
                            engines.vad.reset()
                            _state.value = VoiceInput.State.Listening
                            mode = Mode.CAPTURE
                        }
                    }

                    Mode.LISTEN_ALL -> {
                        // Cada frase que suena se transcribe. Si empieza por la
                        // palabra, es una orden; si no, no iba con nosotros.
                        val current = capture ?: Capture(direct = false, preRoll = emptyList(), transcriptWake = true).also { capture = it }
                        if (current.feed(chunk, n, engines.vad)) {
                            finish(current, engines, chime)
                            capture = Capture(direct = false, preRoll = emptyList(), transcriptWake = true)
                            engines.vad.reset()
                        }
                    }

                    Mode.CAPTURE -> {
                        val current = capture ?: continue
                        val done = current.feed(chunk, n, engines.vad)
                        if (done) {
                            finish(current, engines, chime)
                            engines.vad.reset()
                            if (wakeByTranscript) {
                                capture = Capture(direct = false, preRoll = emptyList(), transcriptWake = true)
                                mode = Mode.LISTEN_ALL
                            } else {
                                capture = null
                                // Flujo nuevo, no reset: durante la captura el
                                // detector no ha oído nada y su estado ya no vale.
                                spotStream.release()
                                spotStream = engines.spotter.createStream()
                                spotChunks = 0
                                mode = Mode.SPOT
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "el hilo de audio ha muerto", e)
            _state.value = VoiceInput.State.Failed(e.message ?: "audio failure")
        } finally {
            runCatching { record.stop() }
            record.release()
            echo?.release()
            chime?.release()
            spotStream.release()
            running = false
        }
    }

    /**
     * Una frase en curso.
     *
     * Guarda **todo** el audio desde que empezó —incluido el pre-roll de antes de
     * la detección— y usa el detector de voz solo para saber cuándo el conductor
     * ha terminado. Se probó a pasarle a Whisper únicamente los trozos que el
     * detector daba por habla, y el resultado era "watch" por "what's": los
     * cortes en seco en los bordes de cada palabra le quitan justo las
     * consonantes con las que distingue una de otra. Whisper lleva bien el
     * silencio; lo que no lleva bien es que le recorten.
     */
    private inner class Capture(
        val direct: Boolean,
        preRoll: List<FloatArray>,
        val answer: Boolean = false,
        /**
         * Vigilancia por transcripción: no hay palabra clave delante, así que
         * esta captura empieza en silencio y espera lo que haga falta a que
         * alguien hable; el silencio previo se recorta al transcribir.
         */
        val transcriptWake: Boolean = false,
    ) {
        private val startedAt = System.nanoTime()
        private val window = FloatArray(VAD_WINDOW)
        private var filled = 0
        private var speechSeen = false
        /** Índice de muestra hasta el que se ha oído hablar. */
        private var lastSpeechAt = 0
        /** Índice de muestra en que empezó a hablar. */
        private var firstSpeechAt = 0
        private var total = 0
        val samples = ArrayList<Float>(SAMPLE_RATE * 10)

        init {
            preRoll.forEach { chunk -> chunk.forEach { samples.add(it) } }
            total = samples.size
        }

        /** @return si la frase se ha acabado, por silencio o por tiempo. */
        fun feed(chunk: FloatArray, n: Int, vad: Vad): Boolean {
            for (i in 0 until n) samples.add(chunk[i])
            var i = 0
            while (i < n) {
                val take = minOf(VAD_WINDOW - filled, n - i)
                System.arraycopy(chunk, i, window, filled, take)
                filled += take
                i += take
                total += take
                if (filled == VAD_WINDOW) {
                    vad.acceptWaveform(window)
                    filled = 0
                    if (vad.isSpeechDetected()) {
                        if (!speechSeen) firstSpeechAt = total
                        speechSeen = true
                        lastSpeechAt = total
                    }
                    // Los segmentos no se usan: solo se vacían para no acumular.
                    while (!vad.empty()) vad.pop()
                }
            }

            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            val silenceMs = (total - lastSpeechAt) * 1000L / SAMPLE_RATE
            // Habló y luego calló lo bastante.
            if (speechSeen && silenceMs > END_SILENCE_MS) return true
            if (transcriptWake) {
                // Vigilando: el silencio no acaba nada, y para no acumular audio
                // sin fin se descarta lo que ya no puede ser el principio de una frase.
                if (!speechSeen && samples.size > SAMPLE_RATE * 2) {
                    samples.subList(0, samples.size - SAMPLE_RATE / 2).clear()
                    total = samples.size
                }
                return speechSeen && (total - firstSpeechAt) * 1000L / SAMPLE_RATE > MAX_CAPTURE_MS
            }
            // Nunca habló: se le esperó lo razonable. Tras una pregunta, más:
            // decidir entre tres sitios lleva un momento.
            if (!speechSeen && elapsedMs > (if (answer) NO_ANSWER_MS else NO_SPEECH_MS)) return true
            // Habla sin parar: en marcha no hay órdenes de ocho segundos.
            return elapsedMs > MAX_CAPTURE_MS
        }

        /**
         * El audio que se transcribe: silencio limpio delante, la frase, y poco
         * después de la última palabra. El silencio inicial no es relleno: a
         * Whisper le va mejor arrancar de silencio que de un corte en seco.
         */
        fun audio(): FloatArray? {
            if (!speechSeen) return null
            val lead = SAMPLE_RATE * LEAD_SILENCE_MS / 1000
            val start = (firstSpeechAt - SAMPLE_RATE * PRE_SPEECH_MS / 1000).coerceAtLeast(0)
            val end = minOf(samples.size, lastSpeechAt + SAMPLE_RATE * TAIL_MS / 1000)
            return FloatArray(lead + (end - start)) { if (it < lead) 0f else samples[start + it - lead] }
        }
    }

    private fun finish(capture: Capture, engines: Engines, chime: ToneGenerator?) {
        val audio = capture.audio()
        if (audio == null) {
            if (capture.transcriptWake) return
            Log.i(TAG, "sin habla tras ${if (capture.answer) "la pregunta" else "la palabra de activación"}")
            _state.value = when {
                // Una pregunta sin respuesta no es un fallo: se vuelve a vigilar.
                capture.answer -> VoiceInput.State.Idle
                capture.direct -> VoiceInput.State.Failed("I didn't catch that")
                // "Siky" a secas, sin orden detrás: la pantalla decide qué contestar.
                else -> VoiceInput.State.Heard("", direct = true)
            }
            return
        }

        val started = System.nanoTime()
        // Con música o voces de fondo, lo primero es limpiar la frase: la
        // palabra de activación es corta y rara, y es lo que antes se ensucia
        // («Sigred» por "Sícret", medido en una cafetería).
        val clean = engines.denoiser?.let { denoiser ->
            runCatching { denoiser.run(audio, SAMPLE_RATE).samples }
                .onFailure { Log.w(TAG, "fallo del reductor de ruido; se usa el audio original", it) }
                .getOrNull()
        } ?: audio
        val text = transcribe(engines, clean)
        // Con reductor se transcribe también el audio original, y no para
        // comparar: medido en una cafetería, el reductor limpia la orden pero
        // se come la "S" de "Sícret" —«Igri», «Icre»—, porque una sibilante se
        // parece al ruido. Cada versión acierta en lo suyo; se usan las dos.
        val raw = if (clean !== audio) transcribe(engines, audio) else text
        val decodeMs = (System.nanoTime() - started) / 1_000_000
        val audioMs = audio.size * 1000L / SAMPLE_RATE
        Log.i(TAG, "oído en ${decodeMs} ms (${audioMs} ms de audio): «$text»")
        if (raw != text) Log.i(TAG, "sin reductor: «$raw»")

        val afterWake = WakeWord.commandIn(text, language)
        val rawAfterWake = WakeWord.commandIn(raw, language)
        if (capture.transcriptWake) {
            // Vigilando por transcripción: sin la palabra delante, no iba con
            // nosotros y se descarta en silencio. Con ella, el toque de "te oí"
            // llega ahora, que es cuando se sabe. Basta con que la tenga una
            // de las dos lecturas.
            val order = afterWake ?: rawAfterWake
            if (order == null) {
                // Se anota lo que se descarta: es como crece la lista de formas
                // en que el reconocedor escribe la palabra.
                Log.d(TAG, "sin palabra de activación, descartado: «$text»")
                return
            }
            chime?.startTone(ToneGenerator.TONE_PROP_ACK, CHIME_MS)
            val other = listOfNotNull(afterWake, rawAfterWake).filter { it != order && it.isNotBlank() }
            _state.value = VoiceInput.State.Heard(order, direct = true, alternatives = other)
            return
        }

        // Tras el detector, Whisper puede haber escrito también la palabra; si
        // cuela al principio de la orden, se le quita para que la gramática no la vea.
        val order = afterWake ?: text
        val other = listOf(rawAfterWake ?: raw).filter { it != order && it.isNotBlank() }
        _state.value = when {
            order.isNotBlank() -> VoiceInput.State.Heard(order, direct = true, alternatives = other)
            capture.direct -> VoiceInput.State.Failed("I didn't catch that")
            // La palabra a secas: la pantalla decide qué contestar.
            else -> VoiceInput.State.Heard("", direct = true)
        }
    }

    // ------------------------------------------------------------------ configuración

    private fun spotterConfig(dir: File, keywords: File) = KeywordSpotterConfig(
        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
        modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = File(dir, "encoder.onnx").absolutePath,
                decoder = File(dir, "decoder.onnx").absolutePath,
                joiner = File(dir, "joiner.onnx").absolutePath,
            ),
            tokens = File(dir, "tokens.txt").absolutePath,
            modelType = "zipformer2",
            numThreads = 1,
            provider = "cpu",
        ),
        keywordsFile = keywords.absolutePath,
        // Medido con voz sintética: a 0,25 —el valor por defecto— "Siky" a secas
        // no dispara; a 0,1 dispara y las frases parecidas ("sick", "silky",
        // "seek the key") siguen sin hacerlo. Es un nombre inventado: el modelo
        // no lo ha oído nunca y hay que pedirle menos certeza.
        keywordsThreshold = KEYWORD_THRESHOLD,
        keywordsScore = KEYWORD_BOOST,
        maxActivePaths = 4,
        numTrailingBlanks = 1,
    )

    private fun vadConfig(dir: File) = VadModelConfig(
        sileroVadModelConfig = SileroVadModelConfig(
            model = File(dir, "silero_vad.onnx").absolutePath,
            threshold = 0.5f,
            // Lo que se calla antes de dar la frase por terminada. Corto: en
            // marcha las órdenes son de tres palabras y una pausa larga se
            // siente como que no oye.
            minSilenceDuration = 0.7f,
            minSpeechDuration = 0.2f,
            windowSize = VAD_WINDOW,
            maxSpeechDuration = MAX_CAPTURE_MS / 1000f,
        ),
        sampleRate = SAMPLE_RATE,
        numThreads = 1,
        provider = "cpu",
    )

    private fun transcribe(engines: Engines, audio: FloatArray): String =
        engines.recognizer.createStream().let { stream ->
            try {
                stream.acceptWaveform(audio, SAMPLE_RATE)
                engines.recognizer.decode(stream)
                engines.recognizer.getResult(stream).text
            } finally {
                stream.release()
            }
        }.trim()

    private fun denoiserConfig(dir: File) = OfflineSpeechDenoiserConfig(
        model = OfflineSpeechDenoiserModelConfig(
            gtcrn = OfflineSpeechDenoiserGtcrnModelConfig(model = File(dir, "gtcrn_simple.onnx").absolutePath),
            numThreads = 1,
            provider = "cpu",
        ),
    )

    private fun recognizerConfig(dir: File) = OfflineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
        // FastConformer de NVIDIA, multilingüe (en, es, fr, de; o la versión de
        // diez idiomas, con italiano): decide el idioma solo. Medido frente a Whisper base: seis veces más rápido y entiende
        // el español, que Whisper destrozaba.
        modelConfig = OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = File(dir, "encoder.onnx").absolutePath,
                decoder = File(dir, "decoder.onnx").absolutePath,
                joiner = File(dir, "joiner.onnx").absolutePath,
            ),
            tokens = File(dir, "tokens.txt").absolutePath,
            modelType = "nemo_transducer",
            numThreads = 2,
            provider = "cpu",
        ),
        decodingMethod = "greedy_search",
    )

    private companion object {
        const val TAG = "SWVoiceInput"
        const val SAMPLE_RATE = 16_000
        /** 100 ms por lectura: bastante para no despertar el hilo sin parar, poco para reaccionar. */
        const val CHUNK_SAMPLES = SAMPLE_RATE / 10
        /** Silero trabaja en ventanas de 512 muestras a 16 kHz. */
        const val VAD_WINDOW = 512
        /** Cada cuántas lecturas de 100 ms se registra el nivel: cada 5 s. */
        const val LEVEL_EVERY_CHUNKS = 50
        /** Cada cuántas lecturas de vigilancia se renueva el flujo del detector: 20 s. */
        const val SPOT_RENEW_CHUNKS = 200
        const val NO_SPEECH_MS = 2_000L
        /**
         * Tras una pregunta se espera más: hay que oírla entera, pensar y
         * contestar. Con 4 s, un "sì" dado con calma llegaba tarde y se
         * descartaba por no llevar la palabra de activación.
         */
        const val NO_ANSWER_MS = 8_000L
        /** Silencio tras la última palabra que da la frase por terminada. */
        const val END_SILENCE_MS = 500L
        /** Cuánto se conserva después de la última palabra. */
        const val TAIL_MS = 300
        /**
         * Chunks de 100 ms que se anteponen a la orden. Tres: con cinco entraba
         * el final de "Siky" y Whisper lo leía como parte de la orden.
         */
        const val PRE_ROLL_CHUNKS = 3
        const val LEAD_SILENCE_MS = 400
        /** Cuánto se conserva antes de la primera palabra al vigilar por transcripción. */
        const val PRE_SPEECH_MS = 300
        const val KEYWORD_THRESHOLD = 0.1f
        const val CHIME_VOLUME = 60
        const val CHIME_MS = 120
        const val KEYWORD_BOOST = 2.0f
        const val MAX_CAPTURE_MS = 8_000L
    }
}
