package com.secretworld.auto

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import com.secretworld.auto.domain.VoiceGrammar
import com.secretworld.auto.domain.VoiceLanguage
import com.secretworld.auto.feature.discover.MapState
import com.secretworld.auto.map.PlatformMap
import com.secretworld.auto.feature.discover.NowPlaying
import com.secretworld.auto.feature.discover.PermissionNotice
import com.secretworld.auto.feature.discover.RoutePanel
import com.secretworld.auto.feature.discover.VoiceStatus
import com.secretworld.auto.feature.discover.VoiceUi
import com.secretworld.auto.feature.parked.ItineraryPanel
import com.secretworld.auto.feature.parked.LanguageOption
import com.secretworld.auto.feature.parked.PlaceDetailPanel
import com.secretworld.auto.feature.parked.SettingsPanel
import com.secretworld.auto.feature.voice.GemmaLanguageModel
import com.secretworld.auto.feature.voice.HandsFree
import com.secretworld.auto.feature.voice.ModelCatalog
import com.secretworld.auto.feature.voice.ModelStore
import com.secretworld.auto.feature.voice.SharedLanguageModel
import com.secretworld.auto.feature.voice.VoiceInstaller
import com.secretworld.auto.feature.voice.NarrationController
import com.secretworld.auto.feature.voice.OnDeviceVoiceInput
import com.secretworld.auto.feature.voice.SystemVoiceInput
import com.secretworld.auto.feature.voice.TranslatingNarrationTexts
import com.secretworld.auto.feature.voice.VoiceInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
     * Ubicación y micrófono se piden **juntos, al arrancar**. La ubicación,
     * porque sin ella la app no sabe dónde está el coche. El micrófono se pedía
     * al pulsar el botón de hablar, pero ese botón ya no existe: se habla con la
     * palabra de activación, y sin permiso no habría forma de concederlo.
     * Juntos porque Android descarta la segunda petición si llega mientras la
     * primera está en pantalla.
     */
    private val askForPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshPermissions() }

    /**
     * Qué permisos hay. Sin ellos la app **no falla en silencio**: el panel
     * avisa de qué falta y ofrece activarlo (ver [requestPermission]).
     */
    private val micGranted = MutableStateFlow(false)
    private val locationGranted = MutableStateFlow(false)

    /**
     * Relee los permisos y enciende lo que dependía de ellos. Se llama al
     * arrancar, tras cada diálogo y al volver a primer plano —por ejemplo desde
     * los ajustes del sistema—, así que tiene que poder repetirse sin efecto.
     */
    private fun refreshPermissions() {
        // Precisa o aproximada: con cualquiera de las dos se sabe dónde se está.
        val location = LOCATION.any(::isGranted)
        // Solo al concederse: si no, volver a primer plano desharía el modo
        // simulado que se hubiera elegido en la barra.
        if (location && !locationGranted.value) useDeviceLocation()
        locationGranted.value = location
        micGranted.value = isGranted(Manifest.permission.RECORD_AUDIO)
        // La escucha también se apaga en onStop; al volver hay que encenderla.
        // Antes de que carguen los modelos lo hace el propio arranque.
        if (voiceReady.value && ::handsFree.isInitialized) handsFree.enable()
    }

    private fun isGranted(permission: String) =
        checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Pide un permiso que falta. Si Android ya no deja preguntar —se rechazó con
     * "no volver a preguntar"— no queda otra que los ajustes de la app: pedirlo
     * igual no mostraría nada y el botón parecería roto.
     */
    private fun requestPermission(vararg permissions: String) {
        if (permissions.any(::shouldShowRequestPermissionRationale)) {
            askForPermissions.launch(arrayOf(*permissions))
        } else {
            startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", packageName, null),
                ),
            )
        }
    }
    private val queue = TourQueue(maxDistanceMeters = 25_000.0)
    private lateinit var catalogue: CatalogueRepository
    private lateinit var mapKeys: MapCredentials
    private lateinit var narration: NarrationController
    private lateinit var voiceInput: VoiceInput
    private lateinit var handsFree: HandsFree
    private lateinit var resolver: IntentResolver
    private val voiceInstaller by lazy { VoiceInstaller(this) }
    private val grammar by lazy { GrammarIntentResolver(language) }

    /** Lo que el asistente recuerda de la conversación: foco, lo enumerado, las últimas frases. */
    private val memory = ConversationMemory()

    /**
     * El idioma de la app: el del dispositivo si tiene voz, inglés si no. Manda
     * sobre la voz, el reconocedor, la gramática, las frases y el catálogo. Un
     * sabor puede fijarlo (`R.string.forced_language`): la demo va en italiano
     * sea cual sea el idioma del teléfono.
     */
    private val language: VoiceLanguage by lazy {
        VoiceLanguage.fromTag(java.util.Locale.getDefault().language) ?: VoiceLanguage.ENGLISH
    }

    /**
     * El idioma elegido en los ajustes, si se eligió. Manda sobre el del sabor
     * y sobre el del teléfono: quien conduce habla como quiere, no como vino
     * configurado el aparato.
     */
    private fun chosenLanguage(context: Context): String? =
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getString(LANGUAGE_KEY, null)

    /** Cambia el idioma de toda la app y la vuelve a abrir en él. */
    private fun switchLanguage(to: VoiceLanguage, announce: Boolean = false) {
        if (to == language) {
            if (announce) narration.say(Phrasebook.nowSpeaking(to))
            return
        }
        getSharedPreferences(SETTINGS, MODE_PRIVATE).edit()
            .putString(LANGUAGE_KEY, to.code)
            .putBoolean(ANNOUNCE_KEY, announce)
            .apply()
        narration.stop()
        recreate()
    }

    /**
     * Un sabor con idioma fijo lo impone aquí, antes de que exista nada: así
     * los textos de pantalla, los formatos y [language] salen del mismo sitio.
     */
    override fun attachBaseContext(newBase: Context) {
        val forced = chosenLanguage(newBase) ?: newBase.getString(R.string.forced_language)
        if (forced.isEmpty()) return super.attachBaseContext(newBase)
        val locale = java.util.Locale.forLanguageTag(forced)
        java.util.Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(locale) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    /**
     * Avance de la instalación de los modelos que trae el APK, de 0 a 100, o
     * `null` si no hay nada que instalar. Solo ocurre en el primer arranque.
     */
    private val modelsPercent = MutableStateFlow<Int?>(null)
    private var onDevice: OnDeviceVoiceInput? = null
    private var gemma: GemmaLanguageModel? = null

    /**
     * Si el micrófono está listo para escuchar. Los modelos en el dispositivo
     * tardan segundos en cargar, y abrir la escucha antes solo produce un fallo.
     */
    private val voiceReady = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // De borde a borde: el mapa llega hasta el filo de la pantalla. Cualquier
        // franja del sistema que quede encima la dibuja el vehículo, no nosotros.
        // El tono lo decide el sabor, no el sistema: los iconos de las barras
        // tienen que seguirlo o quedan claros sobre fondo claro.
        val dark = resources.getBoolean(R.bool.dark_theme)
        val bars = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)

        val api = SecretWorldApi.create(DeviceIdentity.of(this))
        mapKeys = MapCredentials(api)
        val mapbox = MapboxApi.create()
        // Todo lo que se consulta se guarda por zona: media Colombia no tiene
        // cobertura, y los sitios no se mueven.
        val cache = PlaceCache(filesDir)
        catalogue = CatalogueRepository(api, wikipedia = WikipediaPlaces(), cache = cache)
        router = MapboxRouter(mapbox, mapKeys, language)
        search = cache.caching(MapboxPlaceSearch(mapbox, mapKeys, language), language)
        refreshPermissions()
        // Las dos ubicaciones juntas: Android 12+ solo ofrece elegir entre
        // precisa y aproximada si se piden ambas.
        val missing = buildList {
            if (LOCATION.none(::isGranted)) addAll(LOCATION)
            if (!isGranted(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
        }
        if (missing.isNotEmpty()) askForPermissions.launch(missing.toTypedArray())
        // La voz se monta antes que la narración: si hay modelo de lenguaje, la
        // narración traduce con él lo que el catálogo no tenga en el idioma.
        setUpVoice()
        val texts = gemma?.let { TranslatingNarrationTexts(this, catalogue, it) } ?: catalogue
        narration = NarrationController(this, texts, lifecycleScope, language)
        val modeController = DrivingModeController(vehicle, lifecycleScope)

        setContent {
            // Oscuro y a tamaño de coche en el vehículo; claro y a tamaño de móvil
            // en la demo: ver `R.bool.dark_theme` y `R.bool.handheld`.
            SecretWorldTheme(darkTheme = dark, handheld = resources.getBoolean(R.bool.handheld)) {
                val vehicleState by vehicle.state.collectAsState()
                val mode by modeController.mode.collectAsState()
                val narrationState by narration.state.collectAsState()
                val modelsPercentNow by modelsPercent.collectAsState()
                val micOn by micGranted.collectAsState()
                val locationOn by locationGranted.collectAsState()
                val notices = listOfNotNull(
                    PermissionNotice(
                        message = stringResource(R.string.permission_mic_off),
                        action = stringResource(R.string.permission_enable),
                        onClick = { requestPermission(Manifest.permission.RECORD_AUDIO) },
                    ).takeIf { !micOn },
                    PermissionNotice(
                        message = stringResource(R.string.permission_location_off, ROME_NAME),
                        action = stringResource(R.string.permission_enable),
                        onClick = { requestPermission(*LOCATION.toTypedArray()) },
                    ).takeIf { !locationOn },
                )
                val voiceState by voiceInput.state.collectAsState()
                val hearingNow by voiceInput.hearing.collectAsState()
                // Desde que se acepta una orden hasta que la app empieza a
                // contestar. Sin esto, entre la frase y la respuesta —en un
                // móvil lento, segundos— no se veía nada.
                var thinking by remember { mutableStateOf(false) }

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
                        remaining = Phrasebook.spokenDistance(progress.remainingMeters, language).replace("right here", "here").replace("aquí mismo", "aquí").replace("proprio qui", "qui"),
                        eta = Phrasebook.spokenDuration(progress.remainingSeconds, language).replace(Regex(" minut(es|os|e|o|i)"), " min"),
                        instruction = progress.nextStep?.instruction,
                        instructionDistance = progress.nextStep?.let {
                            when {
                                progress.metersToNextStep < 100 -> language.say(en = "now", es = "ahora", it = "ora")
                                else -> {
                                    val distance = Phrasebook.spokenDistance(progress.metersToNextStep, language)
                                    language.say(en = "in $distance", es = "en $distance", it = "tra $distance")
                                }
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
                LaunchedEffect(narration.voiceInstalled) {
                    if (!narration.voiceInstalled) return@LaunchedEffect
                    narration.prewarm(fixedReplies(language))
                    val prefs = getSharedPreferences(SETTINGS, MODE_PRIVATE)
                    if (prefs.getBoolean(ANNOUNCE_KEY, false)) {
                        prefs.edit().putBoolean(ANNOUNCE_KEY, false).apply()
                        narration.say(Phrasebook.nowSpeaking(language))
                    }
                }
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
                // Y al revés: mientras habla, no escucha —salvo que se pueda
                // interrumpir, ver OnDeviceVoiceInput.bargeIn—. Sin cancelación
                // de eco lo único que oiría es a sí misma.
                LaunchedEffect(narrationState) {
                    onDevice?.speaking = narrationState !is NarrationController.State.Idle &&
                        narrationState !is NarrationController.State.Failed
                }
                // Se deja de "pensar" en cuanto la app empieza a contestar; si por
                // lo que sea no contesta, a los pocos segundos, para no quedarse
                // girando.
                LaunchedEffect(narrationState) { if (narrationState !is NarrationController.State.Idle) thinking = false }
                LaunchedEffect(thinking) {
                    if (!thinking) return@LaunchedEffect
                    delay(THINKING_MAX_MS)
                    thinking = false
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
                    // Una respuesta a "¿Sí?" que vuelve a empezar por la palabra
                    // de activación es otra llamada: se le quita. Sin esto, quien
                    // repetía "Sícret" veía contestada solo una vez de cada dos,
                    // porque la segunda se tomaba como la orden.
                    val order = if (said.direct) {
                        WakeWord.commandIn(said.text, language) ?: said.text
                    } else {
                        WakeWord.commandIn(said.text, language)
                    }
                    if (order == null) {
                        voiceInput.acknowledge()
                        return@LaunchedEffect
                    }
                    thinking = true
                    // Interrumpir: si la app estaba hablando, se calla ya. Lo
                    // que se le pide ahora manda sobre lo que estaba diciendo.
                    if (narrationState !is NarrationController.State.Idle) narration.stop()

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
                    // Primero la gramática con cada lectura de la frase —la
                    // limpia y la original—, que es instantánea; el modelo, que
                    // tarda segundos, solo si ninguna se entiende.
                    val readings = listOf(order) + said.alternatives
                    val resolution = readings.firstNotNullOfOrNull { reading ->
                        grammar.resolve(reading, upcoming, memory).takeIf { it.command !is VoiceCommand.NotUnderstood }
                    } ?: resolver.resolve(order, upcoming, memory)
                    Log.i("SWVoice", "«$order» → ${resolution.command::class.simpleName} en ${(System.nanoTime() - resolvedAt) / 1_000_000} ms" +
                        " (LLM ${gemma?.let { when { it.ready -> "listo"; it.failed -> "no disponible"; else -> "cargando" } } ?: "ausente"})" +
                        (resolution.reply?.let { " · dice: «$it»" } ?: ""))
                    val context = Phrasebook.Context(vehicleState.position, upcoming, memory, language)
                    val reply = if (said.direct && resolution.command is VoiceCommand.NotUnderstood) {
                        // Contestaba a una pregunta nuestra y no se entendió:
                        // una frase corta y de vuelta a esperar la palabra. La
                        // larga de "no te entendí" dejaba el micrófono mudo diez
                        // segundos, y lo oído puede ni ir dirigido a la app.
                        notCaught(language).also { narration.say(it) }
                    } else {
                        obey(resolution, context) { place -> skipped = skipped + place.id }
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
                // Navegando, la banda se reduce a una tarjeta: más estrecha y solo
                // del alto de su contenido. El resto de la pantalla es mapa.
                val navigating = navigationUi != null && panel is Panel.Home
                val panelWidth = (if (navigating) metrics.navigationPanelWidth() else metrics.panelWidth(expanded)) + safeStart
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
                                .align(if (navigating) Alignment.TopStart else Alignment.CenterStart)
                                .width(panelWidth)
                                .then(if (navigating) Modifier else Modifier.fillMaxHeight())
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
                                nowPlaying = nowPlayingOf(narrationState, tick) ?: installingOf(modelsPercentNow),
                                voice = voiceUiOf(voiceState, heard, answer, listening, hearingNow, thinking),
                                navigation = navigationUi,
                                notices = notices,
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
        notices: List<PermissionNotice>,
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
                onSelect = { onPanel(Panel.Detail(it)) },
                onListen = { play(it) },
                onNavigate = { navigateTo(it) },
                navigation = navigation,
                onStopNavigation = { endNavigation() },
                notices = notices,
                onStopNarration = { narration.stop() },
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
                title = upcoming.firstOrNull()?.city.let { city ->
                    language.say(
                        en = "A morning in ${city ?: "town"}",
                        es = "Una mañana en ${city ?: "la ciudad"}",
                        it = city?.let { "Una mattina a $it" } ?: "Una mattina in città",
                    )
                },
                stops = upcoming.take(5),
                onSelect = { onPanel(Panel.Detail(it)) },
                onStart = { upcoming.firstOrNull()?.let { play(it) } },
                onClose = { onPanel(Panel.Home) },
            )

            Panel.Settings -> SettingsPanel(
                // Cada idioma con su propio nombre: quien no entiende el actual
                // tiene que poder encontrar el suyo.
                languages = voiceInstaller.availableLanguages().map { available ->
                    LanguageOption(
                        code = available.code,
                        label = available.say(en = "English", es = "Español", it = "Italiano"),
                        selected = available == language,
                    )
                },
                onLanguage = { code -> VoiceLanguage.fromTag(code)?.let(::switchLanguage) },
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
        hearingNow: Boolean,
        thinkingNow: Boolean,
    ) = VoiceUi(
        status = when {
            thinkingNow -> VoiceStatus.THINKING
            hearingNow -> VoiceStatus.HEARING
            state is VoiceInput.State.Listening -> VoiceStatus.YOUR_TURN
            handsFreeOn -> VoiceStatus.WAITING
            else -> VoiceStatus.OFF
        },
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
                NowPlaying(getString(R.string.installing_voice, state.percent), 0, 0, preparing = true, stoppable = false)
            is NarrationController.State.Failed ->
                NowPlaying(state.reason, 0, 0, preparing = true, stoppable = false)
            NarrationController.State.Idle -> null
        }

    /** El primer arranque, mientras se instalan los modelos que trae el APK. */
    private fun installingOf(percent: Int?) =
        percent?.let { NowPlaying(getString(R.string.installing_models, it), 0, 0, preparing = true, stoppable = false) }

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
                    // Un "sí" sin nada ofrecido es contestar al "¿Sí?": quiere
                    // hablar. Se le da paso con algo corto y se escucha otra vez.
                    // Antes caía en "no te entendí", una frase de veinte palabras
                    // que en un móvil lento dejaba el micrófono mudo varios
                    // segundos: parecía que la app se había bloqueado.
                    val reply = goAhead(language)
                    narration.say(reply) { voiceInput.listenForAnswer() }
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

            is VoiceCommand.SwitchLanguage -> {
                if (command.language !in voiceInstaller.availableLanguages()) {
                    // Sin voz en ese idioma no se puede hablar en él: se dice, no se finge.
                    val reply = language.say(
                        en = "I don't have that language on this device.",
                        es = "No tengo ese idioma en este dispositivo.",
                        it = "Non ho quella lingua su questo dispositivo.",
                    )
                    narration.say(reply)
                    reply
                } else {
                    // La confirmación se dice ya en el idioma nuevo, al reabrir.
                    switchLanguage(command.language, announce = true)
                    Phrasebook.nowSpeaking(command.language)
                }
            }

            VoiceCommand.EndRoute -> {
                narration.stop()
                if (activeRoute.value == null) {
                    val reply = Phrasebook.stopped(c)
                    narration.say(reply)
                    reply
                } else {
                    endNavigation()
                    val reply = Phrasebook.routeEnded(c)
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

            is VoiceCommand.ChooseAction -> {
                // Se nombró un lugar sin decir qué hacer: se pregunta. Un "sí" o
                // "llévame" llevan (queda ofrecido y en foco); "la historia" lo
                // cuenta. Sin historia no hay nada que elegir: se ofrece llevar.
                memory.focusOn(command.place)
                offered = command.place
                val reply = if (catalogue.textFor(command.place, language).isNullOrBlank()) {
                    Phrasebook.offerRoute(c, command.place)
                } else {
                    Phrasebook.chooseAction(c, command.place)
                }
                narration.say(reply) { voiceInput.listenForAnswer() }
                reply
            }

            is VoiceCommand.WhichOne -> {
                memory.listed(command.places, choosing = true)
                val reply = Phrasebook.whichOne(c, command.places)
                narration.say(reply) { voiceInput.listenForAnswer() }
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
            val results = search.search(query, here, limit = 4, category = VoiceGrammar.searchCategoryOf(query))
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
        val llm = SharedLanguageModel.get(this)

        // Lo que trae el APK cuenta como instalado: se instala justo abajo, antes
        // de cargarlo. Es lo que hace que la demo funcione sin adb ni CDN.
        // Interrumpir hablando: lo decide el sabor, y para medir se puede
        // forzar al abrir la app —adb shell am start ... --ez bargein true
        // --es mic communication— sin recompilar.
        local.bargeIn = intent.getBooleanExtra("bargein", resources.getBoolean(R.bool.barge_in))
        if (intent.getStringExtra("mic") == "communication") {
            local.source = android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }
        voiceInput = if (local.available || local.bundled) local.also { onDevice = it } else SystemVoiceInput(this)
        handsFree = HandsFree(voiceInput, lifecycleScope)
        resolver = LayeredIntentResolver(
            grammar = grammar,
            fallback = if (llm.available || llm.bundled) {
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
            installBundledModels(models)
            onDevice?.load()?.onFailure { Log.e("SWVoice", "modelos de escucha", it) }
            voiceReady.value = true
            // El LLM se carga después del micrófono: es lo más pesado y lo menos
            // urgente, y hasta que esté la gramática sigue atendiendo.
            gemma?.load()?.onFailure { Log.e("SWVoice", "LLM", it) }
            gemma?.warm(IntentPrompt.PREFIX)
        }
    }

    /**
     * Instala desde el APK los modelos que traiga y falten. Solo tarda la
     * primera vez —descomprimir el LLM son decenas de segundos—; después no
     * hay nada que hacer y no se enseña nada.
     */
    private suspend fun installBundledModels(models: ModelStore) {
        val packs = models.listening(language) + ModelCatalog.DENOISER + ModelCatalog.LLM
        if (packs.none { !models.isAvailable(it) && models.isBundled(it) }) return
        withContext(Dispatchers.IO) {
            val failed = models.installBundled(packs) { modelsPercent.value = it }
            failed.forEach { Log.e("SWVoice", "no se pudo instalar ${it.id} desde el APK") }
        }
        modelsPercent.value = null
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
                text = "${stringResource(if (mode == DrivingMode.PARKED) R.string.mode_parked else R.string.mode_driving)} · %.0f km/h".format(speedKmh),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = d.spaceS),
            )
            if (enabled) {
                CarSecondaryButton(stringResource(R.string.bar_itinerary), { onPanel(Panel.Itinerary) }, compact = true)
                CarSecondaryButton(stringResource(R.string.bar_settings), { onPanel(Panel.Settings) }, compact = true)
            }
            // Los mandos del simulador solo con el simulador; con GPS real, el
            // botón cambia de uno a otro.
            val simulating = source.collectAsState().value === simulated
            if (simulating) {
                CarSecondaryButton(stringResource(R.string.bar_park), { simulated.park() }, compact = true)
                CarSecondaryButton(stringResource(R.string.bar_drive), { simulated.drive() }, compact = true)
                CarSecondaryButton("+1 km", { simulated.advance(40.0) }, compact = true)
                if (device?.hasPermission == true) CarSecondaryButton("GPS", { useDeviceLocation() }, compact = true)
            } else {
                CarSecondaryButton("Sim", { source.value = simulated }, compact = true)
            }
            CarSecondaryButton(stringResource(R.string.bar_quiet), { narration.stop() }, compact = true)
        }
    }

    override fun onStart() {
        super.onStart()
        refreshPermissions()
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
        narration.release()
        // Solo al salir de verdad: al reabrirse —cambio de idioma— el modelo se
        // queda cargado para la actividad nueva.
        if (isFinishing) gemma?.release()
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
        /** Lo más que se enseña "elaboro" sin que la app llegue a contestar. */
        const val THINKING_MAX_MS = 10_000L

        /** Lo que se dice al oír "Siky" a secas, por idioma. */
        fun yes(language: VoiceLanguage) = language.say(en = "Yes?", es = "¿Sí?", it = "Sì?")

        /** No se entendió la respuesta a una pregunta: corto, sin sermón. */
        fun notCaught(language: VoiceLanguage) = language.say(en = "Sorry, I didn't catch that.", es = "Perdona, no te entendí.", it = "Scusa, non ho capito.")

        /** Lo que se dice a un "sí" que no responde a ninguna oferta: adelante. */
        fun goAhead(language: VoiceLanguage) = language.say(en = "Go ahead.", es = "Dime.", it = "Dimmi.")

        /**
         * Las frases sin contexto, que se pueden sintetizar por adelantado.
         * Tienen que ser **idénticas** a las de [Phrasebook] para que se usen.
         */
        fun fixedReplies(language: VoiceLanguage) = listOf(yes(language), goAhead(language), notCaught(language)) + language.say(
            en = listOf("Okay.", "Sure.", "Alright, I'll be quiet.", "There's nothing playing to skip.", "Nothing to skip right now.", "Recalculating.", "New route.", "Route ended."),
            es = listOf("Vale.", "Listo.", "Me callo.", "No hay nada sonando que saltar.", "Nada que saltar ahora mismo.", "Recalculando.", "Nueva ruta.", "Ruta terminada."),
            it = listOf("Va bene.", "Fatto.", "D'accordo, faccio silenzio.", "Non c'è niente in riproduzione da saltare.", "Niente da saltare adesso.", "Ricalcolo.", "Nuovo percorso.", "Percorso terminato."),
        )
        /**
         * Se arranca en Roma porque es donde el catálogo tiene densidad real, y
         * junto al Circo Máximo en vez de encima del Coliseo: si el vehículo nace
         * sobre un monumento, todas las distancias salen "3 m" y no se entiende
         * nada de lo que hace la cola.
         */
        val ROME = Coordinates(41.8859, 12.4850)
        /** Cómo se llama el punto de partida en el aviso de ubicación. */
        const val ROME_NAME = "Roma"

        const val SETTINGS = "settings"
        const val LANGUAGE_KEY = "language"
        /** Al reabrir tras un cambio de idioma pedido de viva voz, confirmarlo. */
        const val ANNOUNCE_KEY = "announce_language"

        /** La ubicación, en sus dos precisiones: basta con una. */
        val LOCATION = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
