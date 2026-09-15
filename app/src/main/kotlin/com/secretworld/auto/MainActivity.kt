package com.secretworld.auto

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.lifecycleScope
import com.secretworld.auto.core.data.CatalogueRepository
import com.secretworld.auto.core.data.DeviceIdentity
import com.secretworld.auto.core.data.MapCredentials
import com.secretworld.auto.core.data.SecretWorldApi
import com.secretworld.auto.core.designsystem.CarSecondaryButton
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.SecretWorldTheme
import com.secretworld.auto.core.platform.DrivingModeController
import com.secretworld.auto.core.platform.SimulatedVehicleSource
import com.secretworld.auto.domain.ConversationMemory
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.DrivingMode
import com.secretworld.auto.domain.GrammarIntentResolver
import com.secretworld.auto.domain.IntentPrompt
import com.secretworld.auto.domain.IntentResolver
import com.secretworld.auto.domain.LayeredIntentResolver
import com.secretworld.auto.domain.LlmIntentResolver
import com.secretworld.auto.domain.Resolution
import com.secretworld.auto.domain.Phrasebook
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.TourQueue
import com.secretworld.auto.domain.VoiceCommand
import com.secretworld.auto.domain.WakeWord
import com.secretworld.auto.domain.VoiceLanguage
import com.secretworld.auto.feature.discover.MapState
import com.secretworld.auto.map.PlatformMap
import com.secretworld.auto.feature.discover.NowPlaying
import com.secretworld.auto.feature.discover.RoutePanel
import com.secretworld.auto.feature.discover.VoiceUi
import com.secretworld.auto.feature.parked.ItineraryPanel
import com.secretworld.auto.feature.parked.PlaceDetailPanel
import com.secretworld.auto.feature.parked.SettingsPanel
import com.secretworld.auto.feature.voice.GemmaLanguageModel
import com.secretworld.auto.feature.voice.HandsFree
import com.secretworld.auto.feature.voice.ModelStore
import com.secretworld.auto.feature.voice.NarrationController
import com.secretworld.auto.feature.voice.OnDeviceVoiceInput
import com.secretworld.auto.feature.voice.SystemVoiceInput
import com.secretworld.auto.feature.voice.VoiceInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * La app entera vive sobre un solo lienzo: **el mapa siempre está**.
 *
 * Es la diferencia de fondo con la app móvil, donde el mapa es una pantalla a la
 * que se entra y de la que se sale. Aquí nunca se sale: los paneles se acoplan a
 * su izquierda y el mapa sigue detrás, situando al usuario en todo momento.
 *
 * Ninguna pantalla decide si puede mostrarse: eso lo dice [DrivingModeController]
 * y todo lo demás obedece.
 */
class MainActivity : ComponentActivity() {

    private val vehicle = SimulatedVehicleSource(ROME, headingDegrees = 20.0)
    private val queue = TourQueue(maxDistanceMeters = 25_000.0)
    private lateinit var catalogue: CatalogueRepository
    private lateinit var mapKeys: MapCredentials
    private lateinit var narration: NarrationController
    private lateinit var voiceInput: VoiceInput
    private lateinit var handsFree: HandsFree
    private lateinit var resolver: IntentResolver

    /** Lo que el asistente recuerda de la conversación: foco, lo enumerado, las últimas frases. */
    private val memory = ConversationMemory()
    private var onDevice: OnDeviceVoiceInput? = null
    private var gemma: GemmaLanguageModel? = null

    /**
     * Si el micrófono está listo para escuchar. Los modelos en el dispositivo
     * tardan segundos en cargar, y abrir la escucha antes solo produce un fallo.
     */
    private val voiceReady = MutableStateFlow(false)

    /**
     * El permiso de micrófono se pide **al primer intento de hablar**, no al
     * arrancar: un diálogo de permisos en la cara nada más abrir una app de coche
     * es lo que hace que se conceda sin leer o se deniegue por reflejo.
     */
    private val askForMicrophone = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) return@registerForActivityResult
        // Concedido el micrófono, lo primero es dejarlo escuchando: el permiso se
        // pidió porque alguien quería hablar.
        handsFree.enable()
        voiceInput.listen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // De borde a borde: el mapa llega hasta el filo de la pantalla. Cualquier
        // franja del sistema que quede encima la dibuja el vehículo, no nosotros.
        enableEdgeToEdge()

        val api = SecretWorldApi.create(DeviceIdentity.of(this))
        catalogue = CatalogueRepository(api)
        mapKeys = MapCredentials(api)
        narration = NarrationController(this, catalogue, lifecycleScope, VoiceLanguage.ENGLISH)
        setUpVoice()
        val modeController = DrivingModeController(vehicle, lifecycleScope)

        setContent {
            // Un coche se conduce de noche y con sol de frente: el tema es oscuro
            // por decisión de producto, no por seguir al sistema.
            SecretWorldTheme(darkTheme = true) {
                val vehicleState by vehicle.state.collectAsState()
                val mode by modeController.mode.collectAsState()
                val narrationState by narration.state.collectAsState()
                val voiceState by voiceInput.state.collectAsState()

                var catalogueResult by remember { mutableStateOf<CatalogueRepository.Result?>(null) }
                var panel by remember { mutableStateOf<Panel>(Panel.Home) }
                var tick by remember { mutableStateOf(0) }
                var mapboxToken by remember { mutableStateOf<String?>(null) }
                // Lo descartado de viva voz. La cola ya sabe respetarlo.
                var skipped by remember { mutableStateOf(emptySet<String>()) }
                var answer by remember { mutableStateOf<String?>(null) }
                var heard by remember { mutableStateOf<String?>(null) }
                val listening by handsFree.enabled.collectAsState()

                // La clave del mapa se pide al backend, no viaja en el APK.
                LaunchedEffect(Unit) { mapboxToken = mapKeys.mapboxToken() }

                // Se pide catálogo cuando el coche se ha movido lo suficiente como
                // para que valga la pena, no en cada fotograma.
                val positionKey = "%.2f,%.2f".format(
                    vehicleState.position.latitude,
                    vehicleState.position.longitude,
                )
                LaunchedEffect(positionKey) {
                    catalogueResult = catalogue.nearby(vehicleState.position)
                }

                val places = catalogueResult?.places.orEmpty()
                val upcoming = remember(places, positionKey, mode, skipped) {
                    queue.next(vehicleState, places, skipped, VoiceLanguage.ENGLISH, limit = 12)
                        .ifEmpty {
                            places.filter { it.id !in skipped }
                                .sortedBy { vehicleState.position.distanceTo(it.position) }
                        }
                }

                LaunchedEffect(Unit) { narration.ensureVoice() }
                // Las frases fijas del asistente se sintetizan antes de que hagan
                // falta: la primera respuesta también tiene que ser inmediata.
                LaunchedEffect(narration.voiceInstalled) { if (narration.voiceInstalled) narration.prewarm(FIXED_REPLIES) }
                // El micrófono se abre con la pantalla y se cierra con ella: una app
                // que escucha cuando no está delante no se lo merece.
                val ready by voiceReady.collectAsState()
                LaunchedEffect(ready) { if (ready && handsFree.possible) handsFree.enable() }

                // Con palabra de activación, "escuchando" significa que acaba de
                // sonar "Siky": la app se calla para no dictarse a sí misma. Con
                // el reconocedor del sistema ese estado es permanente y no dice nada.
                LaunchedEffect(voiceState) {
                    if (voiceInput.continuous && voiceState is VoiceInput.State.Listening) narration.stop()
                }
                LaunchedEffect(upcoming.joinToString { it.id }) { narration.prefetch(upcoming) }

                // Lo que se oye se atiende una sola vez: el estado se reconoce en
                // cuanto se actúa, para que una recomposición no repita la orden.
                LaunchedEffect(voiceState) {
                    val said = voiceState as? VoiceInput.State.Heard ?: return@LaunchedEffect

                    // Con el micrófono siempre abierto, lo que se oye no es
                    // necesariamente para nosotros. La palabra de activación es lo
                    // que separa una orden de una conversación entre ocupantes, y
                    // lo que no la lleva se descarta **en silencio**: contestar a
                    // todo sería peor que no oír.
                    val order = if (said.direct) said.text else WakeWord.commandIn(said.text)
                    if (order == null) {
                        voiceInput.acknowledge()
                        return@LaunchedEffect
                    }

                    if (order.isBlank()) {
                        // La despertaron sin pedirle nada: contesta y, **cuando
                        // termine de hablar**, abre el turno. Antes no: abriría el
                        // micrófono sobre su propia voz y se transcribiría a sí misma.
                        answer = YES
                        voiceInput.acknowledge()
                        narration.say(YES) { voiceInput.listenForAnswer() }
                        return@LaunchedEffect
                    }

                    heard = order
                    val resolvedAt = System.nanoTime()
                    val resolution = resolver.resolve(order, upcoming, memory)
                    Log.i("SWVoice", "«$order» → ${resolution.command::class.simpleName} en ${(System.nanoTime() - resolvedAt) / 1_000_000} ms" +
                        " (LLM ${gemma?.let { if (it.ready) "listo" else "cargando" } ?: "ausente"})" +
                        (resolution.reply?.let { " · dice: «$it»" } ?: ""))
                    val context = Phrasebook.Context(vehicleState.position, upcoming, memory)
                    val reply = obey(resolution, context) { place ->
                        skipped = skipped + place.id
                    }
                    memory.remember(order, reply)
                    answer = reply
                    voiceInput.acknowledge()
                }

                // Ni la respuesta ni el fallo se quedan fijos en pantalla: pasado
                // un rato, la banda vuelve a ser lo que era. Un aviso que no se va
                // acaba siendo parte del decorado y deja de leerse.
                LaunchedEffect(answer) {
                    if (answer == null) return@LaunchedEffect
                    delay(ANSWER_LINGER_MS)
                    answer = null
                    heard = null
                }
                LaunchedEffect(voiceState) {
                    if (voiceState !is VoiceInput.State.Failed) return@LaunchedEffect
                    delay(ANSWER_LINGER_MS)
                    voiceInput.acknowledge()
                }

                // Refresco del progreso de reproducción. Sin esto la barra miente.
                LaunchedEffect(narrationState) {
                    while (narration.isPlaying) {
                        tick++
                        delay(500)
                    }
                }

                // En marcha no se puede tener abierto un panel de lectura larga.
                LaunchedEffect(mode) {
                    if (mode == DrivingMode.DRIVING) panel = Panel.Home
                }

                // El mapa es el fondo, a pantalla completa. La banda de
                // información flota encima: en un coche la información se apoya
                // sobre el mapa, no lo parte en dos.
                //
                // Los tamaños los calcula CarMetrics a partir de la ventana, así
                // que una pantalla nueva —Connect-S, waterfall, media pantalla—
                // no necesita código nuevo aquí.
                val metrics = CarTheme.metrics
                val expanded = panel !is Panel.Home
                // El inset de seguridad —el recorte de cámara, que en apaisado cae
                // sobre el borde izquierdo— se le **suma** al ancho de la banda en
                // vez de restárselo a su contenido. Si no, la columna se estrecha
                // 44 dp y las etiquetas de los botones se recortan en la pantalla
                // más pequeña, justo donde menos sobra.
                val safeStart = WindowInsets.safeDrawing
                    .asPaddingValues()
                    .calculateStartPadding(LocalLayoutDirection.current)
                val panelWidth = metrics.panelWidth(expanded) + safeStart
                // La fracción que el mapa usa para desplazar su foco tiene que ser
                // la de la banda **ya ensanchada**, no la teórica.
                val panelFraction = (panelWidth / metrics.widthDp).coerceIn(0f, 0.9f)

                Box(Modifier.fillMaxSize()) {
                    PlatformMap(
                        state = MapState(
                            vehicle = vehicleState,
                            // Solo se dibuja línea cuando hay un itinerario de
                            // verdad. Unir puntos de interés sueltos pintaría una
                            // carretera que no existe, y eso en un mapa es peor
                            // que no dibujar nada.
                            route = if (panel is Panel.Itinerary) {
                                upcoming.take(5).map { it.position }
                            } else {
                                emptyList()
                            },
                            places = upcoming.take(12),
                            highlighted = (panel as? Panel.Detail)?.place?.id
                                ?: upcoming.firstOrNull()?.id,
                            mapboxToken = mapboxToken,
                            panelCoverFraction = panelFraction,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )

                    // La banda y la barra flotan **sobre** el mapa, cada una en
                    // su esquina, en vez de repartirse la pantalla en filas. Así
                    // la banda dispone de todo el alto: una columna que se corta a
                    // media pantalla desperdicia justo lo que el conductor mira.
                    Box(Modifier.fillMaxSize()) {
                        // La banda va **pegada al borde**, no flotando con un
                        // margen: los insets de seguridad —el recorte de cámara en
                        // apaisado— se aplican a su contenido, no a su fondo. Al
                        // revés se ve un margen puesto a mano que en el vehículo no
                        // tiene ninguna razón de ser.
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .width(panelWidth)
                                .fillMaxHeight()
                                .clip(
                                    RoundedCornerShape(
                                        topEnd = CarTheme.dimens.cardCorner,
                                        bottomEnd = CarTheme.dimens.cardCorner,
                                    ),
                                )
                                .animateContentSize(),
                        ) {
                            PanelContent(
                                panel = panel,
                                upcoming = upcoming,
                                vehiclePosition = vehicleState.position,
                                mode = mode,
                                result = catalogueResult,
                                nowPlaying = nowPlayingOf(narrationState, tick),
                                voice = voiceUiOf(voiceState, heard, answer, listening),
                                onSpeak = ::speak,
                                onPanel = { panel = it },
                            )
                        }

                        DeveloperBar(
                            mode = mode,
                            speedKmh = vehicleState.speedMetersPerSecond * 3.6,
                            onPanel = { panel = it },
                            enabled = mode == DrivingMode.PARKED,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                // Nunca por debajo de la banda: si la cruza, le
                                // vuelve a robar el alto que se le acaba de dar.
                                .padding(start = panelWidth)
                                .safeDrawingPadding()
                                .padding(CarTheme.dimens.spaceS),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelContent(
        panel: Panel,
        upcoming: List<Place>,
        vehiclePosition: Coordinates,
        mode: DrivingMode,
        result: CatalogueRepository.Result?,
        nowPlaying: NowPlaying?,
        voice: VoiceUi,
        onSpeak: () -> Unit,
        onPanel: (Panel) -> Unit,
    ) {
        when (panel) {
            Panel.Home -> RoutePanel(
                places = upcoming,
                origin = vehiclePosition,
                mode = mode,
                city = upcoming.firstOrNull()?.city,
                nowPlaying = nowPlaying,
                voice = voice,
                offline = result?.source == CatalogueRepository.Source.FALLBACK,
                onSpeak = onSpeak,
                onSelect = { onPanel(Panel.Detail(it)) },
                onListen = { play(it) },
                onNavigate = { onPanel(Panel.Detail(it)) },
            )

            is Panel.Detail -> PlaceDetailPanel(
                place = panel.place,
                onListen = { play(panel.place) },
                onNavigate = { /* NaviHelper pendiente: ver docs/PLEOS-SDK-SURFACE.md */ },
                onClose = { onPanel(Panel.Home) },
            )

            Panel.Itinerary -> ItineraryPanel(
                title = "A morning in ${upcoming.firstOrNull()?.city ?: "town"}",
                stops = upcoming.take(5),
                onSelect = { onPanel(Panel.Detail(it)) },
                onStart = { upcoming.firstOrNull()?.let { play(it) } },
                onClose = { onPanel(Panel.Home) },
            )

            Panel.Settings -> SettingsPanel(
                voiceLanguage = "English",
                voiceInstalled = narration.voiceInstalled,
                voiceSizeMb = 21,
                catalogueSource = when (result?.source) {
                    CatalogueRepository.Source.LIVE -> "live · ${result.places.size} places"
                    CatalogueRepository.Source.FALLBACK -> "offline fallback"
                    null -> "loading"
                },
                onRemoveVoice = { },
                onClose = { onPanel(Panel.Home) },
            )
        }
    }

    /**
     * Lo que se enseña del asistente.
     *
     * Con escucha permanente, `Listening` es el estado normal y **no se anuncia**:
     * un cartel fijo de "escuchando" deja de leerse en dos minutos. Lo que sí se
     * enseña siempre es que el micrófono está vivo, en el botón de la cabecera.
     */
    private fun voiceUiOf(
        state: VoiceInput.State,
        heard: String?,
        answer: String?,
        handsFreeOn: Boolean,
    ) = VoiceUi(
        listening = state is VoiceInput.State.Listening && !handsFreeOn,
        handsFree = handsFreeOn,
        heard = heard,
        answer = answer ?: (state as? VoiceInput.State.Failed)?.reason,
    )

    private fun nowPlayingOf(state: NarrationController.State, @Suppress("UNUSED_PARAMETER") tick: Int) =
        when (state) {
            is NarrationController.State.Speaking -> NowPlaying(
                placeName = state.text,
                positionMs = 0,
                durationMs = 0,
                preparing = state.preparing,
            )
            is NarrationController.State.Playing -> NowPlaying(
                placeName = state.placeName,
                positionMs = narration.positionMs(),
                durationMs = state.durationMs,
            )
            is NarrationController.State.Synthesizing ->
                NowPlaying(state.placeName, 0, 0, preparing = true)
            is NarrationController.State.InstallingVoice ->
                NowPlaying("Installing voice… ${state.percent}%", 0, 0, preparing = true)
            is NarrationController.State.Failed ->
                NowPlaying(state.reason, 0, 0, preparing = true)
            NarrationController.State.Idle -> null
        }

    /**
     * Abre el micrófono, pidiendo permiso la primera vez.
     *
     * Pulsar mientras ya escucha **cancela**: es la salida cuando el reconocedor
     * se queda esperando una frase que no va a llegar.
     */
    private fun speak() {
        if (voiceInput.state.value is VoiceInput.State.Listening) {
            voiceInput.cancel()
            return
        }
        if (!voiceInput.hasPermission) {
            askForMicrophone.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        // Si hablo, la app se calla. Además de ser lo que uno espera de un
        // asistente, la narración sale por el mismo altavoz que oye el micrófono:
        // dejarla sonar es dictarle al reconocedor nuestra propia voz.
        narration.stop()
        voiceInput.listen()
    }

    /**
     * Ejecuta la orden y devuelve **lo que se le contesta al conductor**.
     *
     * Tres reglas de fondo:
     *
     * - Lo que no se sabe hacer **se dice en voz alta**, no se ignora. "Llévame
     *   ahí" no funciona porque la librería de navegación de Pleos no es pública
     *   (ver docs/OPEN-QUESTIONS.md), y callarse eso deja al conductor esperando
     *   una maniobra que nunca llega.
     * - La frase la pone quien mejor la sepa poner: si el modelo redactó una con
     *   el contexto que se le dio, esa; si fue la gramática, el [Phrasebook] con
     *   el mismo contexto. Nunca una frase fija sin más.
     * - Cuando se pregunta algo, el turno se abre **al terminar de hablar**.
     */
    private fun obey(
        resolution: Resolution,
        c: Phrasebook.Context,
        onSkip: (Place) -> Unit,
    ): String {
        val said = resolution.reply
        return when (val command = resolution.command) {
            is VoiceCommand.TellMeAbout -> {
                // Una breve introducción y, cuando termine, la narración. Sin
                // introducción, narrar sin más es la respuesta, como antes.
                val intro = said ?: Phrasebook.playing(c, command.place)
                memory.focusOn(command.place)
                narration.say(intro) { narration.listen(command.place) }
                intro
            }

            VoiceCommand.WhatIsNearby -> {
                // Enumerar, no narrar: "¿qué hay cerca?" pide una lista. Con
                // distancia, y con pregunta al final para que "el segundo"
                // signifique algo después.
                val nearby = c.around.take(3)
                memory.listed(nearby)
                val reply = said ?: if (nearby.isEmpty()) Phrasebook.nothingNearby(c) else Phrasebook.nearby(c, nearby)
                narration.say(reply) { if (nearby.isNotEmpty()) voiceInput.listenForAnswer() }
                reply
            }

            VoiceCommand.TellMeMore -> {
                val reply = said ?: Phrasebook.noMore(c)
                narration.say(reply)
                reply
            }

            VoiceCommand.TakeMeThere -> {
                val reply = said ?: Phrasebook.noNavigation(c)
                narration.say(reply)
                reply
            }

            VoiceCommand.Skip -> {
                val current = memory.focus ?: c.around.firstOrNull()
                if (current == null) {
                    val reply = said ?: Phrasebook.nothingToSkip(c)
                    narration.say(reply)
                    reply
                } else {
                    narration.stop()
                    onSkip(current)
                    val next = c.around.firstOrNull { it.id != current.id }
                    val reply = said ?: Phrasebook.skipped(c, current, next)
                    narration.say(reply)
                    reply
                }
            }

            VoiceCommand.Stop -> {
                narration.stop()
                val reply = said ?: Phrasebook.stopped(c)
                narration.say(reply)
                reply
            }

            is VoiceCommand.Clarify -> {
                // Entendió a medias: pregunta y, cuando termine, abre el turno.
                narration.say(command.question) { voiceInput.listenForAnswer() }
                command.question
            }

            is VoiceCommand.NotUnderstood -> {
                val reply = Phrasebook.notUnderstood(c)
                narration.say(reply)
                reply
            }
        }
    }

    /** Narra un lugar y lo deja en foco: a partir de aquí, "eso" es esto. */
    private fun play(place: Place) {
        memory.focusOn(place)
        narration.listen(place)
    }

    /**
     * Elige cómo se oye y cómo se entiende, según lo que haya instalado.
     *
     * Con los modelos de escucha en el dispositivo, el micrófono es
     * [OnDeviceVoiceInput]: palabra de activación de verdad, sin red. Sin ellos,
     * el reconocedor del sistema reabierto en cada frase, que es lo que había.
     * Con el LLM instalado, lo que la gramática no entiende pasa por él; sin él,
     * la gramática sola. Nada de esto se decide en la tienda: se decide con lo
     * que [ModelStore] encuentra (SPEC RF-1.8.7–1.8.9).
     */
    private fun setUpVoice() {
        val models = ModelStore(this)
        val local = OnDeviceVoiceInput(this, models, VoiceLanguage.ENGLISH)
        val llm = GemmaLanguageModel(this, models)

        voiceInput = if (local.available) local.also { onDevice = it } else SystemVoiceInput(this)
        handsFree = HandsFree(voiceInput, lifecycleScope)
        resolver = LayeredIntentResolver(
            grammar = GrammarIntentResolver(),
            fallback = if (llm.available) {
                LlmIntentResolver(llm.also { gemma = it }, position = { vehicle.state.value.position }) {
                    Log.e("SWVoice", "el LLM no contestó", it)
                }
            } else {
                null
            },
        )
        Log.i(
            "SWVoice",
            "micrófono: ${if (onDevice != null) "en el dispositivo" else "sistema"} · " +
                "LLM: ${if (gemma != null) "gemma 3 1b" else "no instalado"}",
        )

        lifecycleScope.launch {
            onDevice?.load()?.onFailure { Log.e("SWVoice", "modelos de escucha", it) }
            voiceReady.value = true
            // El LLM se carga después del micrófono: es lo más pesado y lo menos
            // urgente, y hasta que esté la gramática sigue atendiendo.
            gemma?.load()?.onFailure { Log.e("SWVoice", "LLM", it) }
            gemma?.warm(IntentPrompt.PREFIX)
        }
    }

    /** Controles del simulador. No forman parte del producto. */
    @Composable
    private fun DeveloperBar(
        mode: DrivingMode,
        speedKmh: Double,
        onPanel: (Panel) -> Unit,
        enabled: Boolean,
        modifier: Modifier = Modifier,
    ) {
        val d = CarTheme.dimens
        Row(
            modifier
                .height(d.utilityBarHeight)
                .clip(RoundedCornerShape(d.cardCorner))
                // Sobre el mapa, sin fondo, esta fila es ilegible.
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.82f))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = d.spaceS),
            horizontalArrangement = Arrangement.spacedBy(d.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${mode.name.lowercase()} · %.0f km/h".format(speedKmh),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = d.spaceS),
            )
            if (enabled) {
                CarSecondaryButton("Itinerary", { onPanel(Panel.Itinerary) }, compact = true)
                CarSecondaryButton("Settings", { onPanel(Panel.Settings) }, compact = true)
            }
            CarSecondaryButton("Park", { vehicle.park() }, compact = true)
            CarSecondaryButton("Drive", { vehicle.drive() }, compact = true)
            CarSecondaryButton("+1 km", { vehicle.advance(40.0) }, compact = true)
            CarSecondaryButton("Quiet", { narration.stop() }, compact = true)
        }
    }

    override fun onStop() {
        super.onStop()
        narration.stop()
        handsFree.disable()
    }

    override fun onDestroy() {
        super.onDestroy()
        onDevice?.release()
        gemma?.release()
    }

    private sealed interface Panel {
        data object Home : Panel
        data class Detail(val place: Place) : Panel
        data object Itinerary : Panel
        data object Settings : Panel
    }

    private companion object {
        /** Cuánto se queda en pantalla una respuesta del asistente. */
        const val ANSWER_LINGER_MS = 8_000L

        /** Lo que se dice al oír "Siky" a secas. */
        const val YES = "Yes?"

        /** Las frases sin contexto, que se pueden sintetizar por adelantado. */
        val FIXED_REPLIES = listOf(YES, "Okay.", "Sure.", "Alright, I'll be quiet.", "There's nothing playing to skip.", "Nothing to skip right now.")
        /**
         * Se arranca en Roma porque es donde el catálogo tiene densidad real, y
         * junto al Circo Máximo en vez de encima del Coliseo: si el vehículo nace
         * sobre un monumento, todas las distancias salen "3 m" y no se entiende
         * nada de lo que hace la cola.
         */
        val ROME = Coordinates(41.8859, 12.4850)
    }
}
