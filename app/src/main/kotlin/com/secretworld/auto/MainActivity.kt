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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.secretworld.auto.core.data.CatalogueRepository
import com.secretworld.auto.core.data.DeviceIdentity
import com.secretworld.auto.core.data.MapCredentials
import com.secretworld.auto.core.data.SecretWorldApi
import com.secretworld.auto.core.designsystem.CarSecondaryButton
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.SecretWorldTheme
import com.secretworld.auto.core.platform.DrivingModeController
import com.secretworld.auto.core.data.MapboxApi
import com.secretworld.auto.core.data.WikipediaPlaces
import com.secretworld.auto.core.data.MapboxPlaceSearch
import com.secretworld.auto.core.data.PlaceCache
import com.secretworld.auto.core.data.MapboxRouter
import com.secretworld.auto.domain.PlaceSearch
import com.secretworld.auto.core.platform.DeviceVehicleSource
import com.secretworld.auto.core.platform.SimulatedVehicleSource
import com.secretworld.auto.core.platform.VehicleSource
import com.secretworld.auto.domain.NavigationGuide
import com.secretworld.auto.domain.Route
import com.secretworld.auto.domain.Router
import com.secretworld.auto.feature.discover.NavigationUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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
import com.secretworld.auto.feature.voice.TranslatingNarrationTexts
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

    /**
     * El vehículo: simulado o el dispositivo real, según haya permiso de
     * ubicación. Se conmuta en caliente, y todo lo demás lee [vehicle] sin saber
     * cuál de los dos hay detrás.
     */
    private val simulated = SimulatedVehicleSource(ROME, headingDegrees = 20.0)
    private var device: DeviceVehicleSource? = null
    private val source = MutableStateFlow<VehicleSource>(simulated)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val vehicle: VehicleSource by lazy {
        object : VehicleSource {
            override val state: StateFlow<com.secretworld.auto.domain.VehicleState> =
                source.flatMapLatest { it.state }.stateIn(lifecycleScope, SharingStarted.Eagerly, simulated.state.value)
        }
    }

    private lateinit var router: Router
    private lateinit var search: PlaceSearch

    /** Lo último que se buscó, para enseñarlo en el mapa hasta que se pida otra cosa. */
    private val searchResults = MutableStateFlow<List<Place>>(emptyList())

    /** Un sitio que se ha ofrecido llevar; un "sí" lo confirma. */
    private var offered: Place? = null

    /** La ruta en curso y su guía. `null` cuando no se navega. */
    private val activeRoute = MutableStateFlow<Route?>(null)
    private var guide: NavigationGuide? = null
    private val announced = mutableSetOf<String>()
    private var lastReroute = 0L

    /**
     * La ubicación se pide al arrancar, no al primer uso: sin ella la app no
     * sabe dónde está el coche y todo lo demás es una demostración.
     */
    private val askForLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) useDeviceLocation() }
    private val queue = TourQueue(maxDistanceMeters = 25_000.0)
    private lateinit var catalogue: CatalogueRepository
    private lateinit var mapKeys: MapCredentials
    private lateinit var narration: NarrationController
    private lateinit var voiceInput: VoiceInput
    private lateinit var handsFree: HandsFree
    private lateinit var resolver: IntentResolver

    /** Lo que el asistente recuerda de la conversación: foco, lo enumerado, las últimas frases. */
    private val memory = ConversationMemory()

    /**
     * El idioma de la app: el del dispositivo si tiene voz, inglés si no. Manda
     * sobre la voz, el reconocedor, la gramática, las frases y el catálogo.
     */
    private val language: VoiceLanguage by lazy {
        VoiceLanguage.fromTag(java.util.Locale.getDefault().language) ?: VoiceLanguage.ENGLISH
    }
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
        mapKeys = MapCredentials(api)
        val mapbox = MapboxApi.create()
        // Todo lo que se consulta se guarda por zona: media Colombia no tiene
        // cobertura, y los sitios no se mueven.
        val cache = PlaceCache(filesDir)
        catalogue = CatalogueRepository(api, wikipedia = WikipediaPlaces(), cache = cache)
        router = MapboxRouter(mapbox, mapKeys, language)
        search = cache.caching(MapboxPlaceSearch(mapbox, mapKeys, language), language)
        if (DeviceVehicleSource(this, ROME).hasPermission) useDeviceLocation() else askForLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        // La voz se monta antes que la narración: si hay modelo de lenguaje, la
        // narración traduce con él lo que el catálogo no tenga en el idioma.
        setUpVoice()
        val texts = gemma?.let { TranslatingNarrationTexts(this, catalogue, it) } ?: catalogue
        narration = NarrationController(this, texts, lifecycleScope, language)
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
                val route by activeRoute.collectAsState()
                var navigationUi by remember { mutableStateOf<NavigationUi?>(null) }

                // La clave del mapa se pide al backend, no viaja en el APK.
                LaunchedEffect(Unit) { mapboxToken = mapKeys.mapboxToken() }

                // Se pide catálogo cuando el coche se ha movido lo suficiente como
                // para que valga la pena, no en cada fotograma.
                val positionKey = "%.2f,%.2f".format(
                    vehicleState.position.latitude,
                    vehicleState.position.longitude,
                )
                LaunchedEffect(positionKey) {
                    catalogueResult = catalogue.nearby(vehicleState.position, language = language)
                }

                val places = catalogueResult?.places.orEmpty()
                val upcoming = remember(places, positionKey, mode, skipped) {
                    queue.next(vehicleState, places, skipped, language, limit = 12)
                        .ifEmpty {
                            places.filter { it.id !in skipped }
                                .sortedBy { vehicleState.position.distanceTo(it.position) }
                        }
                }

                // Seguir la ruta con cada posición: cuánto queda, qué maniobra
                // toca, si se ha llegado o si hay que recalcular.
                LaunchedEffect(vehicleState, route) {
                    val current = route ?: run { navigationUi = null; return@LaunchedEffect }
                    val progress = guide?.progress(vehicleState) ?: return@LaunchedEffect
                    navigationUi = NavigationUi(
                        destination = Phrasebook.shortName(current.destination),
                        remaining = Phrasebook.spokenDistance(progress.remainingMeters, language).replace("right here", "here").replace("aquí mismo", "aquí"),
                        eta = Phrasebook.spokenDuration(progress.remainingSeconds, language).replace(Regex(" minut(es|os|e|o)"), " min"),
                        instruction = progress.nextStep?.instruction,
                        instructionDistance = progress.nextStep?.let {
                            when {
                                progress.metersToNextStep < 100 -> if (language == VoiceLanguage.SPANISH) "ahora" else "now"
                                language == VoiceLanguage.SPANISH -> "en ${Phrasebook.spokenDistance(progress.metersToNextStep, language)}"
                                else -> "in ${Phrasebook.spokenDistance(progress.metersToNextStep)}"
                            }
                        },
                        arrived = progress.arrived,
                    )
                    val c = Phrasebook.Context(vehicleState.position, upcoming, memory, language)
                    when {
                        progress.arrived -> {
                            narration.say(Phrasebook.arrived(c, current.destination))
                            endNavigation()
                        }
                        progress.offRoute && vehicleState.speedMetersPerSecond > 1.0 -> reroute(current, vehicleState.position, c)
                        progress.shouldAnnounce -> {
                            val step = progress.nextStep ?: return@LaunchedEffect
                            val key = "${step.at.latitude},${step.at.longitude}"
                            if (announced.add(key)) {
                                narration.say(Phrasebook.maneuver(c, progress.metersToNextStep, step.instruction))
                            }
                        }
                    }
                }

                LaunchedEffect(Unit) { narration.ensureVoice() }
                // Las frases fijas del asistente se sintetizan antes de que hagan
                // falta: la primera respuesta también tiene que ser inmediata.
                LaunchedEffect(narration.voiceInstalled) { if (narration.voiceInstalled) narration.prewarm(fixedReplies(language)) }
                // El micrófono se abre con la pantalla y se cierra con ella: una app
                // que escucha cuando no está delante no se lo merece.
                val ready by voiceReady.collectAsState()
                LaunchedEffect(ready) { if (ready && handsFree.possible) handsFree.enable() }

                // Con palabra de activación, "escuchando" significa que acaba de
                // sonar la palabra: la app se calla para no dictarse a sí misma. Con
                // el reconocedor del sistema ese estado es permanente y no dice nada.
                LaunchedEffect(voiceState) {
                    if (voiceInput.continuous && voiceState is VoiceInput.State.Listening) narration.stop()
                }
                // Y al revés: mientras habla, no escucha. Sin cancelación de eco
                // lo único que oiría es a sí misma.
                LaunchedEffect(narrationState) {
                    onDevice?.muted = narrationState !is NarrationController.State.Idle &&
                        narrationState !is NarrationController.State.Failed
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
                    val order = if (said.direct) said.text else WakeWord.commandIn(said.text, language)
                    if (order == null) {
                        voiceInput.acknowledge()
                        return@LaunchedEffect
                    }

                    if (order.isBlank()) {
                        // La despertaron sin pedirle nada: contesta y, **cuando
                        // termine de hablar**, abre el turno. Antes no: abriría el
                        // micrófono sobre su propia voz y se transcribiría a sí misma.
                        answer = yes(language)
                        voiceInput.acknowledge()
                        narration.say(yes(language)) { voiceInput.listenForAnswer() }
                        return@LaunchedEffect
                    }

                    heard = order
                    val resolvedAt = System.nanoTime()
                    val resolution = resolver.resolve(order, upcoming, memory)
                    Log.i("SWVoice", "«$order» → ${resolution.command::class.simpleName} en ${(System.nanoTime() - resolvedAt) / 1_000_000} ms" +
                        " (LLM ${gemma?.let { if (it.ready) "listo" else "cargando" } ?: "ausente"})" +
                        (resolution.reply?.let { " · dice: «$it»" } ?: ""))
                    val context = Phrasebook.Context(vehicleState.position, upcoming, memory, language)
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
                            route = route?.points ?: if (panel is Panel.Itinerary) {
                                upcoming.take(5).map { it.position }
                            } else {
                                emptyList()
                            },
                            // Lo buscado manda sobre lo cercano: si se pidió
                            // shawarma, el mapa enseña los shawarmas.
                            places = searchResults.collectAsState().value.ifEmpty { upcoming.take(12) },
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
                                navigation = navigationUi,
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
        navigation: NavigationUi?,
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
                onNavigate = { navigateTo(it) },
                navigation = navigation,
                onStopNavigation = { endNavigation() },
            )

            is Panel.Detail -> PlaceDetailPanel(
                place = panel.place,
                onListen = { play(panel.place) },
                onNavigate = {
                    navigateTo(panel.place)
                    onPanel(Panel.Home)
                },
                onClose = { onPanel(Panel.Home) },
            )

            Panel.Itinerary -> ItineraryPanel(
                title = if (language == VoiceLanguage.SPANISH) "Una mañana en ${upcoming.firstOrNull()?.city ?: "la ciudad"}" else "A morning in ${upcoming.firstOrNull()?.city ?: "town"}",
                stops = upcoming.take(5),
                onSelect = { onPanel(Panel.Detail(it)) },
                onStart = { upcoming.firstOrNull()?.let { play(it) } },
                onClose = { onPanel(Panel.Home) },
            )

            Panel.Settings -> SettingsPanel(
                voiceLanguage = when (language) { VoiceLanguage.SPANISH -> "Español"; VoiceLanguage.ITALIAN -> "Italiano"; else -> "English" },
                voiceInstalled = narration.voiceInstalled,
                voiceSizeMb = 21,
                catalogueSource = when (result?.source) {
                    CatalogueRepository.Source.LIVE -> stringResource(com.secretworld.auto.feature.parked.R.string.catalogue_live, result.places.size)
                    CatalogueRepository.Source.FALLBACK -> stringResource(com.secretworld.auto.feature.parked.R.string.catalogue_fallback)
                    CatalogueRepository.Source.POI -> stringResource(com.secretworld.auto.feature.parked.R.string.catalogue_poi, result.places.size)
                    null -> stringResource(com.secretworld.auto.feature.parked.R.string.catalogue_loading)
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
        wakeWord = WakeWord.spoken(language),
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
                memory.focusOn(command.place)
                if (catalogue.textFor(command.place, language).isNullOrBlank()) {
                    // Un sitio de búsqueda: no hay historia, hay dirección. Se
                    // ofrece llevar y se espera el "sí".
                    offered = command.place
                    val reply = Phrasebook.offerRoute(c, command.place)
                    narration.say(reply) { voiceInput.listenForAnswer() }
                    return reply
                }
                // Una breve introducción y, cuando termine, la narración. Sin
                // introducción, narrar sin más es la respuesta, como antes.
                val intro = said ?: if (narration.isPrepared(command.place)) Phrasebook.playing(c, command.place) else Phrasebook.preparing(c, command.place)
                narration.say(intro) { narration.listen(command.place) }
                intro
            }

            is VoiceCommand.FindPlaces -> {
                val reply = said ?: Phrasebook.searching(c, command.query)
                narration.say(reply)
                findPlaces(command.query)
                reply
            }

            VoiceCommand.Yes -> {
                val place = offered
                offered = null
                if (place == null) {
                    val reply = Phrasebook.notUnderstood(c)
                    narration.say(reply)
                    reply
                } else {
                    val reply = Phrasebook.routingTo(c, place)
                    narration.say(reply)
                    navigateTo(place, announce = false)
                    reply
                }
            }

            VoiceCommand.No -> {
                offered = null
                val reply = said ?: Phrasebook.stopped(c)
                narration.say(reply)
                reply
            }

            VoiceCommand.WhatIsNearby -> {
                searchResults.value = emptyList()
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

            is VoiceCommand.TakeMeThere -> {
                val target = command.place ?: memory.focus ?: memory.listed.firstOrNull()
                if (target == null) {
                    val reply = Phrasebook.noDestination(c)
                    narration.say(reply) { voiceInput.listenForAnswer() }
                    reply
                } else {
                    val reply = said ?: Phrasebook.routingTo(c, target)
                    narration.say(reply)
                    navigateTo(target, announce = false)
                    reply
                }
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
        if (catalogue.textFor(place, language).isNullOrBlank()) {
            // Un lugar real sin ficha: se sabe dónde está, no su historia. Decirlo
            // es mejor que un "no hay texto" en la pantalla.
            val c = Phrasebook.Context(vehicle.state.value.position, memory.listed, memory, language)
            narration.say(Phrasebook.noStory(c, place))
            return
        }
        narration.listen(place)
    }

    /**
     * Busca sitios alrededor, los enumera con minutos en coche y los deja en la
     * memoria y en el mapa: "el segundo" o "llévame al primero" funcionan después.
     */
    private fun findPlaces(query: String) {
        lifecycleScope.launch {
            val here = vehicle.state.value.position
            val c = Phrasebook.Context(here, memory.listed, memory, language)
            val results = search.search(query, here, limit = 4)
            Log.i("SWSearch", "«$query»: ${results.size} resultados · ${results.joinToString { it.name }}")
            searchResults.value = results
            if (results.isEmpty()) {
                narration.say(Phrasebook.nothingFound(c, query)) { voiceInput.listenForAnswer() }
                return@launch
            }
            memory.listed(results)
            val reply = Phrasebook.found(c, query, results)
            memory.remember(query, reply)
            narration.say(reply) { voiceInput.listenForAnswer() }
        }
    }

    // ------------------------------------------------------------------ navegación

    /**
     * Traza la ruta hasta [place] y la deja activa: el mapa la dibuja, la guía la
     * sigue y las maniobras se anuncian. Es lo que en el vehículo hará NaviHelper
     * cuando Pleos lo publique; hasta entonces, Mapbox (docs/MAP-OPTIONS.md).
     */
    private fun navigateTo(place: Place, announce: Boolean = true) {
        memory.focusOn(place)
        lifecycleScope.launch {
            val here = vehicle.state.value.position
            val c = Phrasebook.Context(here, memory.listed, memory, language)
            if (announce) narration.say(Phrasebook.routingTo(c, place))
            val found = router.route(here, place)
            if (found == null) {
                narration.say(Phrasebook.noRoute(c, place))
                return@launch
            }
            startNavigation(found)
            // La primera maniobra va dentro del anuncio: no hace falta que la
            // guía la repita nada más empezar.
            found.steps.firstOrNull()?.let { announced.add("${it.at.latitude},${it.at.longitude}") }
            narration.say(Phrasebook.routeStarted(c, found))
        }
    }

    private fun startNavigation(route: Route) {
        guide = NavigationGuide(route)
        announced.clear()
        activeRoute.value = route
        Log.i("SWNav", "ruta a ${route.destination.name}: %.0f m, %.0f s, ${route.steps.size} maniobras".format(route.distanceMeters, route.durationSeconds))
    }

    private fun endNavigation() {
        activeRoute.value = null
        guide = null
        announced.clear()
    }

    /** Fuera de la ruta y en marcha: se recalcula, como mucho cada quince segundos. */
    private fun reroute(current: Route, here: Coordinates, c: Phrasebook.Context) {
        val now = System.currentTimeMillis()
        if (now - lastReroute < REROUTE_EVERY_MS) return
        lastReroute = now
        lifecycleScope.launch {
            val found = router.route(here, current.destination) ?: return@launch
            narration.say(Phrasebook.rerouting(c))
            startNavigation(found)
        }
    }

    /** Pasa del simulador a la posición real del dispositivo. */
    private fun useDeviceLocation() {
        val real = device ?: DeviceVehicleSource(this, ROME).also { device = it }
        real.start()
        source.value = real
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
        val local = OnDeviceVoiceInput(this, models, language)
        val llm = GemmaLanguageModel(this, models)

        voiceInput = if (local.available) local.also { onDevice = it } else SystemVoiceInput(this)
        handsFree = HandsFree(voiceInput, lifecycleScope)
        resolver = LayeredIntentResolver(
            grammar = GrammarIntentResolver(),
            fallback = if (llm.available) {
                LlmIntentResolver(llm.also { gemma = it }, position = { vehicle.state.value.position }, language = language) {
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
            // Los mandos del simulador solo con el simulador; con GPS real, el
            // botón cambia de uno a otro.
            val simulating = source.collectAsState().value === simulated
            if (simulating) {
                CarSecondaryButton("Park", { simulated.park() }, compact = true)
                CarSecondaryButton("Drive", { simulated.drive() }, compact = true)
                CarSecondaryButton("+1 km", { simulated.advance(40.0) }, compact = true)
                if (device?.hasPermission == true) CarSecondaryButton("GPS", { useDeviceLocation() }, compact = true)
            } else {
                CarSecondaryButton("Sim", { source.value = simulated }, compact = true)
            }
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
        device?.stop()
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
        const val REROUTE_EVERY_MS = 15_000L

        /** Lo que se dice al oír "Siky" a secas, por idioma. */
        fun yes(language: VoiceLanguage) = if (language == VoiceLanguage.SPANISH) "¿Sí?" else "Yes?"

        /** Las frases sin contexto, que se pueden sintetizar por adelantado. */
        fun fixedReplies(language: VoiceLanguage) = if (language == VoiceLanguage.SPANISH) {
            listOf(yes(language), "Vale.", "Listo.", "Me callo.", "No hay nada sonando que saltar.", "Nada que saltar ahora mismo.", "Recalculando.", "Nueva ruta.")
        } else {
            listOf(yes(language), "Okay.", "Sure.", "Alright, I'll be quiet.", "There's nothing playing to skip.", "Nothing to skip right now.", "Recalculating.", "New route.")
        }
        /**
         * Se arranca en Roma porque es donde el catálogo tiene densidad real, y
         * junto al Circo Máximo en vez de encima del Coliseo: si el vehículo nace
         * sobre un monumento, todas las distancias salen "3 m" y no se entiende
         * nada de lo que hace la cola.
         */
        val ROME = Coordinates(41.8859, 12.4850)
    }
}
