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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.secretworld.auto.core.designsystem.Motion
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.secretworld.auto.core.designsystem.panelInsetsPadding
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
import com.secretworld.auto.core.data.PlaceImages
import com.secretworld.auto.core.data.SecretWorldApi
import com.secretworld.auto.core.designsystem.CarSecondaryButton
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.SecretWorldTheme
import com.secretworld.auto.core.platform.DrivingModeController
import com.secretworld.auto.core.data.MapboxApi
import com.secretworld.auto.core.data.NominatimAreas
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
import com.secretworld.auto.domain.Bounds
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.DrivingMode
import com.secretworld.auto.domain.GrammarIntentResolver
import com.secretworld.auto.domain.IntentPrompt
import com.secretworld.auto.domain.IntentResolver
import com.secretworld.auto.domain.ItineraryConversation
import com.secretworld.auto.domain.ItineraryDesigner
import com.secretworld.auto.domain.ItineraryEdit
import com.secretworld.auto.domain.ItineraryProposal
import com.secretworld.auto.domain.ItineraryRequest
import com.secretworld.auto.domain.Tour
import com.secretworld.auto.domain.PlaceKinds
import com.secretworld.auto.domain.LayeredIntentResolver
import com.secretworld.auto.domain.ItineraryLibrary
import com.secretworld.auto.domain.ItineraryReply
import com.secretworld.auto.domain.ItineraryReplyPrompt
import com.secretworld.auto.domain.ItineraryRequests
import com.secretworld.auto.domain.SavedItinerary
import com.secretworld.auto.domain.LlmIntentResolver
import com.secretworld.auto.domain.Resolution
import com.secretworld.auto.domain.Phrasebook
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.TourQueue
import com.secretworld.auto.domain.VoiceCommand
import com.secretworld.auto.domain.WakeWord
import com.secretworld.auto.domain.VoiceGrammar
import com.secretworld.auto.domain.NameMatcher
import com.secretworld.auto.domain.VoiceLanguage
import com.secretworld.auto.feature.discover.NowPlaying
import com.secretworld.auto.feature.discover.PermissionNotice
import com.secretworld.auto.feature.discover.RoutePanel
import com.secretworld.auto.feature.discover.AssistantScreen
import com.secretworld.auto.feature.discover.HomeOverlay
import com.secretworld.auto.feature.discover.ItineraryLibraryOverlay
import com.secretworld.auto.feature.discover.ItineraryOverlay
import com.secretworld.auto.feature.discover.SavedItineraryUi
import com.secretworld.auto.feature.discover.FlyoverState
import com.secretworld.auto.feature.discover.MenuEntry
import com.secretworld.auto.feature.discover.MenuIcon
import com.secretworld.auto.feature.discover.MenuRail
import com.secretworld.auto.feature.discover.collapseMenuOnOutsideTap
import com.secretworld.auto.feature.discover.rememberMenuRailState
import com.secretworld.auto.feature.parked.SettingAction
import com.secretworld.auto.map.TourFlyover
import com.secretworld.auto.feature.discover.TourStopUi
import com.secretworld.auto.feature.discover.TourUi
import com.secretworld.auto.feature.discover.SearchShortcut
import com.secretworld.auto.feature.discover.SearchUi
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

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
    /** Lo que se buscó, dicho como se enseña: "Ristorante messicano". */
    private val searchQuery = MutableStateFlow<String?>(null)

    /** Un sitio que se ha ofrecido llevar; un "sí" lo confirma. */
    private var offered: Place? = null

    /** La conversación para armar un itinerario: lo que se preguntó o propuso. */
    private val itinerary = ItineraryConversation()
    private val profiles by lazy { TravellerProfiles(this) }
    private val savedItineraries by lazy { SavedItineraries(this) }
    /** El itinerario en curso. `null` si no se está haciendo ninguno. */
    private val tour = MutableStateFlow<Tour?>(null)
    /** Tras qué parada del itinerario en curso toca comer; se olvida al ofrecerlo. */
    private var tourMealAfter: Int? = null
    /** La parada a la que ya se llegó: la llegada se anuncia una sola vez. */
    private var arrivedAt: String? = null
    /** Al llegar se ofrece la historia de la parada; un "sí" la narra (SPEC §2.4). */
    private var storyOffered: Place? = null
    /** Se ofreció buscar dónde comer; un "sí" busca. */
    private var lunchOffered = false
    /** Se cumplió el tiempo en la parada y se propuso seguir; un "sí" sigue. */
    private var moveOnOffered = false
    private var moveOnJob: kotlinx.coroutines.Job? = null
    private var planJob: kotlinx.coroutines.Job? = null
    /** El itinerario que se enseña: propuesto, o el que está en curso. */
    private val shownPlan = MutableStateFlow<ItineraryProposal?>(null)
    /** Lo que se dice fuera del turno de una orden —la propuesta, al terminar de planificar—, para los subtítulos. */
    private val lateReply = MutableStateFlow<String?>(null)
    /** La presentación cinemática del itinerario recién propuesto. */
    private val flyover = MutableStateFlow<FlyoverState?>(null)

    private val settingsPrefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    /**
     * El radio de acción del inicio, en km: lo de alrededor y las búsquedas,
     * dentro de él. Se elige en los ajustes.
     */
    private val homeRadiusKm by lazy { MutableStateFlow(settingsPrefs.getInt(RADIUS_KEY, DEFAULT_RADIUS_KM)) }

    /** La última búsqueda de una pestaña, para repetirla si cambia el radio. */
    private var lastSearch: Pair<String, String?>? = null

    // ------------------------------------------------------------------ imágenes

    private val imagePrefs by lazy { getSharedPreferences("place_images", MODE_PRIVATE) }
    private val placeImages by lazy {
        PlaceImages(object : PlaceImages.Memory {
            override fun get(id: String): String? = imagePrefs.getString(id, null)
            override fun put(id: String, url: String) = imagePrefs.edit().putString(id, url).apply()
        })
    }

    /** Las imágenes encontradas para sitios que no traían la suya, por id. */
    private val images = MutableStateFlow<Map<String, String>>(emptyMap())
    private val picturing = mutableSetOf<String>()
    private val pictureGate = kotlinx.coroutines.sync.Semaphore(PICTURE_PARALLEL)

    /**
     * Busca, en segundo plano y de pocas en pocas, la imagen de cada sitio: la
     * suya si responde, otra si no la tiene o no carga.
     */
    private fun picture(places: List<Place>) {
        places.filter { it.id !in images.value && picturing.add(it.id) }.forEach { place ->
            lifecycleScope.launch {
                val url = pictureGate.withPermit { placeImages.find(place, language, mapKeys.mapboxToken()) }
                if (url != null) images.value = images.value + (place.id to url)
            }
        }
    }

    /** Con la imagen comprobada o encontrada; mientras tanto, la que traiga. */
    private fun Place.pictured(known: Map<String, String> = images.value): Place =
        known[id]?.takeIf { it != imageUrl }?.let { copy(imageUrl = it) } ?: this

    private fun List<Place>.pictured(known: Map<String, String> = images.value): List<Place> = map { it.pictured(known) }

    private fun FlyoverState.pictured(known: Map<String, String>): FlyoverState =
        copy(stops = stops.pictured(known), candidates = candidates.pictured(known), focus = focus?.pictured(known))

    /** El sitio elegido en la lista o el mapa del inicio: la cámara vuela a él. */
    private val homeSelected = MutableStateFlow<Place?>(null)

    /** Lo que se está contando con el inicio delante: la cámara se acerca mientras dura. */
    private val homeFocus = MutableStateFlow<Place?>(null)

    /** Lo que devuelve la cámara al itinerario cuando se acaba de contar un sitio. */
    private var focusJob: kotlinx.coroutines.Job? = null

    /** La ventana de los itinerarios guardados, abierta con estos; `null`, cerrada. */
    private val library = MutableStateFlow<List<SavedItinerary>?>(null)

    /** Armando uno a mano y aún sin paradas: el mapa de alrededor, para ir eligiendo. */
    private val drafting = MutableStateFlow(false)

    /** El que se ve en el mapa de "Mis itinerarios". */
    private val librarySelected = MutableStateFlow(0)

    /** Cuántos hay guardados. */
    private val savedCount = MutableStateFlow(0)

    /**
     * El guardado que se está viendo o cambiando: al confirmarlo se sustituye en
     * vez de guardarse otro. `null` con un itinerario recién armado.
     */
    private var editingId: String? = null

    /** Si comparte pantalla: entonces la app es compacta y el navegador está al lado. */
    private val multiWindow = MutableStateFlow(false)
    /** Si la app está en la ventana flotante: entonces solo atiende la voz. */
    private val pip = MutableStateFlow(false)
    /** Se le pasó una ruta al navegador del usuario, que nosotros no podemos cerrar. */
    private var externalRouteOpen = false

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
    private val areas = NominatimAreas()
    private val whereAmI by lazy { WhereAmI(this) }
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

    /**
     * Probar la conversación sin micrófono, solo en builds depurables:
     *
     *     adb shell am start -n <paquete>/com.secretworld.auto.MainActivity -f 0x20000000 --es say "ármame un itinerario"
     *
     * En la de entrega no: otra app podría dictarle órdenes.
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        val text = intent.getStringExtra("say")
        if (debuggable && text != null && ::voiceInput.isInitialized) voiceInput.inject(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Las imágenes, identificándose: Wikimedia devuelve 403 a quien no lo hace,
        // y las fotos de Wikipedia se quedaban en la inicial.
        coil3.SingletonImageLoader.setSafe { context ->
            coil3.ImageLoader.Builder(context)
                .components {
                    add(
                        coil3.network.okhttp.OkHttpNetworkFetcherFactory(
                            callFactory = {
                                okhttp3.OkHttpClient.Builder()
                                    .addInterceptor { chain ->
                                        chain.proceed(chain.request().newBuilder().header("User-Agent", IMAGE_USER_AGENT).build())
                                    }
                                    .build()
                            },
                        ),
                    )
                }
                .build()
        }
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
                // Dónde está el usuario de verdad, no la "ciudad" del lugar más cercano.
                var where by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(positionKey) {
                    where = whereAmI.describe(vehicleState.position, java.util.Locale.forLanguageTag(language.code))
                }

                // Una guía de la ciudad entera no es un sitio al que ir ni que enumerar.
                val places = catalogueResult?.places.orEmpty().filterNot(PlaceKinds::isCityGuide)
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
                            // Si es una parada del itinerario, la llegada la anuncia
                            // el itinerario, que además ofrece la historia.
                            if (tour.value?.current?.id != current.destination.id) {
                                narration.say(Phrasebook.arrived(c, current.destination))
                            }
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

                // Llegar a una parada del itinerario, se navegue con quien se navegue:
                // con Google Maps al lado, la posición es lo único que se sabe.
                val currentTour by tour.collectAsState()
                val late by lateReply.collectAsState()
                LaunchedEffect(late) {
                    if (late != null) {
                        answer = late
                        lateReply.value = null
                    }
                }
                LaunchedEffect(vehicleState, currentTour) { checkTourArrival(upcoming) }

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
                    // Con un itinerario a la vista, sus paradas también se pueden
                    // nombrar, aunque no estén cerca: "cuéntame del Teatro Heredia".
                    val nameable = (upcoming + shownPlan.value?.stops.orEmpty()).distinctBy { it.id }
                    val understood = readings.map { grammar.resolve(it, nameable, memory) }
                        .filter { it.command !is VoiceCommand.NotUnderstood }

                    // Con una pregunta o una propuesta de itinerario pendiente, lo que
                    // se dice se lee primero como respuesta: "tres horas, museos" no
                    // es buscar museos, y no hace falta el modelo para entenderlo.
                    // Con la ventana de los guardados abierta —o "empieza mi itinerario"
                    // sin nada pendiente—, "el segundo" o "empieza el del castillo"
                    // eligen uno de ellos.
                    val shelf = library.value
                    val grammarFirst = understood.firstOrNull()?.command
                    if (shelf != null || (grammarFirst == VoiceCommand.StartItinerary && !itinerary.busy)) {
                        val saved = shelf ?: savedList()
                        val choice = ItineraryLibrary.choose(order, saved)
                            ?: ItineraryLibrary.Choice(0, ItineraryLibrary.Action.START)
                                .takeIf { shelf != null && saved.size == 1 && grammarFirst == VoiceCommand.Yes }
                        val c = Phrasebook.Context(vehicleState.position, upcoming, memory, language)
                        val reply = when {
                            choice != null -> libraryChoice(saved, choice, c)
                            shelf != null && (grammarFirst == VoiceCommand.No || ItineraryRequests.refuses(order)) -> {
                                closeLibrary()
                                ""
                            }
                            else -> null
                        }
                        if (reply != null) {
                            Log.i("SWItinerary", "«$order» → guardados: $choice · dice: «$reply»")
                            if (reply.isNotEmpty()) memory.remember(order, reply)
                            answer = reply.ifEmpty { null }
                            voiceInput.acknowledge()
                            return@LaunchedEffect
                        }
                    }

                    // Un "sí" o un "no" justo después de «¿te llevo o te la cuento?»
                    // contestan a eso, no al itinerario: con uno guardado, el "sí"
                    // lo arrancaba.
                    val answersOffer = offered != null && (grammarFirst == VoiceCommand.Yes || grammarFirst == VoiceCommand.No)
                    if (itinerary.busy && !answersOffer) {
                        val grammarSays = understood.firstOrNull()?.command ?: VoiceCommand.NotUnderstood(order)
                        val pending = itinerary.state
                        val step = itinerary.hear(order, grammarSays, profiles.load())
                        Log.d("SWItinerary", "«$order» con ${pending::class.simpleName}: gramática ${grammarSays::class.simpleName}, paso ${step?.let { it::class.simpleName }}")
                        if (step == null && understood.isEmpty() && askModel(order, pending)) {
                            voiceInput.acknowledge()
                            return@LaunchedEffect
                        }
                        // Pasó a otra cosa: la propuesta deja de enseñarse.
                        // El mapa se va con él: si no, quedaba el mapa sin el panel.
                        if (step == null && !itinerary.busy && tour.value == null) {
                            shownPlan.value = null
                            flyover.value = null
                        }
                        if (step != null) {
                            val c = Phrasebook.Context(vehicleState.position, upcoming, memory, language)
                            val reply = itineraryStep(step, c)
                            Log.i("SWVoice", "«$order» → itinerario ${step::class.simpleName} · dice: «$reply»")
                            memory.remember(order, reply)
                            answer = reply
                            voiceInput.acknowledge()
                            return@LaunchedEffect
                        }
                    }
                    // Qué lectura manda: primero la que elige un sitio de la lista
                    // recién leída —contestar "Sung Waa" no es buscar otra vez—;
                    // luego la que da una categoría concreta —«ficcería» no es
                    // nada, «pizería» es pizzería—; luego la primera.
                    fun picksListed(command: VoiceCommand) = when (command) {
                        is VoiceCommand.TakeMeThere -> command.place in memory.listed
                        is VoiceCommand.ChooseAction -> command.place in memory.listed
                        is VoiceCommand.TellMeAbout -> command.place in memory.listed
                        else -> false
                    }
                    // Pedir ir a un sitio concreto es más preciso que buscar un tipo:
                    // «llevábame» no se entendía y la otra lectura, «llévame», sí.
                    val resolution = understood.firstOrNull { picksListed(it.command) }
                        ?: understood.firstOrNull { it.command is VoiceCommand.NavigateToNamed || it.command is VoiceCommand.TakeMeThere }
                        ?: understood.firstOrNull { (it.command as? VoiceCommand.FindPlaces)?.let { find -> VoiceGrammar.searchCategoryOf(find.query) != null } ?: false }
                        ?: understood.firstOrNull()
                        ?: resolver.resolve(order, nameable, memory)
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
                    Log.i("SWVoice", "dice: «$reply»")
                    memory.remember(order, reply)
                    // Una orden que se atiende en silencio —abrir "Mis itinerarios"— no
                    // deja subtítulo ni se queda "pensando": ya está hecha.
                    answer = reply.ifEmpty { null }
                    if (reply.isEmpty()) {
                        thinking = false
                        heard = null
                    }
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

                // La voz es la protagonista y el mapa ya no guía: lo hace el
                // navegador del usuario, al lado. El mapa, si sale, enseña el
                // itinerario (SPEC §4.4). Compacta cuando comparte pantalla.
                val metrics = CarTheme.metrics
                val split by multiWindow.collectAsState()
                val compact = split || metrics.widthDp < COMPACT_WIDTH
                val plan by shownPlan.collectAsState()
                val shownPlanUi = plan?.takeIf {
                    currentTour != null || itinerary.state is ItineraryConversation.State.Proposed || itinerary.state is ItineraryConversation.State.Saved
                }
                val tourUi = shownPlanUi?.let { tourUiOf(it, currentTour) }
                val searchShown = searchQuery.collectAsState().value
                val results = searchResults.collectAsState().value

                val inPip by pip.collectAsState()
                val imgs by images.collectAsState()
                LaunchedEffect(Unit) { refreshSaved() }
                // Las imágenes de lo que se enseña, en cuanto se enseña.
                LaunchedEffect(upcoming.map { it.id }) { picture(upcoming) }
                LaunchedEffect(results.map { it.id }) { picture(results) }
                val searchUi = searchShown?.let { query ->
                    SearchUi(
                        title = if (query.endsWith(SIMILAR_MARK)) {
                            query.removeSuffix(SIMILAR_MARK)
                        } else if (" · " in query) {
                            query.replaceFirstChar { it.uppercase() }
                        } else {
                            stringResource(com.secretworld.auto.feature.discover.R.string.search_nearby, query.replaceFirstChar { it.uppercase() })
                        },
                        places = results.pictured(imgs),
                    )
                }
                // Tocar una sugerencia es decirla: pasa por el mismo camino que la voz.
                // "Mis itinerarios" siempre: también es donde se empieza uno nuevo.
                val suggestionsUi = (listOf(myItineraries(language)) + suggestionsFor(language)).map { text ->
                                SearchShortcut(label = text) { voiceInput.inject(text) }
                            }
                val shortcutsUi = SHORTCUTS.map { (category, label) ->
                                val text = label(language)
                                SearchShortcut(label = text.replaceFirstChar { it.uppercase() }, category = category) { findPlaces(text, category, announce = true) }
                            }
                val homeAt by homeSelected.collectAsState()
                val toldAt by homeFocus.collectAsState()
                val menu = rememberMenuRailState()
                val radiusKm by homeRadiusKm.collectAsState()
                // Lo de alrededor, dentro del radio y del más cercano al más lejano:
                // no la ciudad entera.
                val around = remember(places, positionKey, radiusKm) {
                    places.filter { vehicleState.position.distanceTo(it.position) <= radiusKm * 1000.0 }
                        .sortedBy { vehicleState.position.distanceTo(it.position) }
                }
                Box(Modifier.fillMaxSize().collapseMenuOnOutsideTap(menu)) {
                    val shelf by library.collectAsState()
                    val shelfAt by librarySelected.collectAsState()
                    val fly by flyover.collectAsState()
                    val draftNow by drafting.collectAsState()
                    val draftUi = TourUi(
                        stops = emptyList(),
                        proposed = true,
                        current = -1,
                        summary = "",
                        title = stringResource(R.string.itinerary_draft),
                    )
                    // Si al abrir la app no hubo red, la clave del mapa no llegó: se
                    // vuelve a pedir al enseñar un itinerario, o el mapa sale negro.
                    val flying = fly != null
                    LaunchedEffect(flying) { if (flying && mapboxToken == null) mapboxToken = mapKeys.mapboxToken() }

                    // Un solo mapa para el inicio, "Mis itinerarios" y el itinerario: es
                    // el mundo, y no se va; lo que cambia es el panel de encima. Con "Mis
                    // itinerarios" abierta, también en compacto: es su fondo.
                    val itineraryMap = fly?.takeIf { (!compact || shelf != null) && !inPip }
                    val homeMap = itineraryMap == null && panel is Panel.Home && !inPip && !compact
                    if (itineraryMap != null || homeMap) {
                        LaunchedEffect(around.map { it.id }) { picture(around) }
                        LaunchedEffect(itineraryMap?.stops?.map { it.id }, itineraryMap?.candidates?.map { it.id }) {
                            itineraryMap?.let { picture(it.stops + it.candidates) }
                        }
                        val places = searchUi?.places ?: around.pictured(imgs)
                        val selectedPlace = homeAt?.takeIf { chosen -> places.any { it.id == chosen.id } }
                        val c = Phrasebook.Context(vehicleState.position, upcoming, memory, language)
                        val screen = when {
                            itineraryMap == null -> HudScreen.HOME
                            shelf != null -> HudScreen.LIBRARY
                            else -> HudScreen.ITINERARY
                        }
                        // Lo último de cada pantalla: la que se va se sigue viendo mientras sale.
                        var lastShelf by remember { mutableStateOf<List<SavedItinerary>>(emptyList()) }
                        shelf?.let { lastShelf = it }
                        var lastTour by remember { mutableStateOf<TourUi?>(null) }
                        (tourUi ?: draftUi.takeIf { draftNow })?.let { lastTour = it }
                        TourFlyover(
                            state = itineraryMap?.pictured(imgs) ?: FlyoverState(
                                start = vehicleState.position,
                                stops = emptyList(),
                                times = emptyList(),
                                path = emptyList(),
                                legEnds = emptyList(),
                                summary = "",
                                intro = false,
                                focus = toldAt ?: selectedPlace,
                                candidates = places,
                                pickable = true,
                                me = vehicleState.position,
                                radiusMeters = radiusKm * 1000.0,
                            ),
                            mapboxToken = mapboxToken,
                            onIntroDone = { flyover.value = flyover.value?.copy(intro = false) },
                            onAddPlace = { place ->
                                if (itineraryMap == null) homeSelected.value = place else answer = addToItinerary(place, c)
                            },
                            overlay = {
                                // Cambiar de pantalla es cambiar de escena, como en el menú de un
                                // juego: el panel de ahora sale por la izquierda y el nuevo entra
                                // desde fuera de la pantalla, un instante después, y ocupa su sitio.
                                val reduced = Motion.reduced
                                AnimatedContent(
                                    targetState = screen,
                                    transitionSpec = {
                                        val offstage = { width: Int -> -(width * HUD_OFFSTAGE).toInt() }
                                        val enter = if (reduced) {
                                            fadeIn(snap())
                                        } else {
                                            slideInHorizontally(tween(HUD_ENTER_MS, delayMillis = HUD_ENTER_DELAY_MS, easing = EaseOutCubic), offstage) +
                                                fadeIn(tween(HUD_ENTER_MS / 2, delayMillis = HUD_ENTER_DELAY_MS))
                                        }
                                        val exit = if (reduced) {
                                            fadeOut(snap())
                                        } else {
                                            slideOutHorizontally(tween(HUD_EXIT_MS, easing = EaseInCubic), offstage) + fadeOut(tween(HUD_EXIT_MS))
                                        }
                                        // El que entra, por encima del que se va.
                                        (enter togetherWith exit).apply { targetContentZIndex = 1f }
                                    },
                                    label = "hud",
                                ) { shown ->
                                    Box(Modifier.fillMaxSize()) {
                                        when (shown) {
                                            HudScreen.HOME -> HomeOverlay(
                                                city = where ?: upcoming.firstOrNull()?.city,
                                                offline = catalogueResult?.source == CatalogueRepository.Source.FALLBACK,
                                                voice = voiceUiOf(voiceState, heard, answer, listening, hearingNow, thinking),
                                                nowPlaying = (nowPlayingOf(narrationState, tick) ?: installingOf(modelsPercentNow))
                                                    ?.takeUnless { narrationState is NarrationController.State.Speaking },
                                                notices = notices,
                                                tour = tourUi,
                                                search = searchUi,
                                                nearby = around.pictured(imgs),
                                                origin = vehicleState.position,
                                                navigation = navigationUi,
                                                shortcuts = shortcutsUi,
                                                activeSearch = searchShown,
                                                radiusKm = radiusKm,
                                                widerRadiusKm = RADII_KM.firstOrNull { it > radiusKm },
                                                onWiden = ::setRadius,
                                                selected = selectedPlace,
                                                onSelect = { homeSelected.value = it },
                                                onDetails = { panel = Panel.Detail(it) },
                                                onListen = { play(it) },
                                                onNavigate = { navigateTo(it) },
                                                onAdd = { place -> answer = addToItinerary(place, c) },
                                                onStopNarration = { narration.stop() },
                                                onStopNavigation = { endNavigation() },
                                                onClearSearch = ::clearSearch,
                                            )
                                            HudScreen.LIBRARY -> {
                                                val saved = lastShelf
                                                fun act(index: Int, action: ItineraryLibrary.Action) {
                                                    if (index in saved.indices) answer = libraryChoice(saved, ItineraryLibrary.Choice(index, action), c).ifEmpty { null }
                                                }
                                                ItineraryLibraryOverlay(
                                                    items = saved.map(::savedUiOf),
                                                    selected = shelfAt,
                                                    voice = voiceUiOf(voiceState, heard, answer, listening, hearingNow, thinking),
                                                    onSelect = { previewSaved(it) },
                                                    onOpen = { act(it, ItineraryLibrary.Action.OPEN) },
                                                    onStart = { act(it, ItineraryLibrary.Action.START) },
                                                    onDelete = { act(it, ItineraryLibrary.Action.DELETE) },
                                                    onSuggest = {
                                                        closeLibrary()
                                                        answer = itineraryStep(itinerary.request(ItineraryRequest(), profiles.load()), c)
                                                    },
                                                    onBuild = { answer = buildItinerary(c) },
                                                    onClose = ::closeLibrary,
                                                )
                                            }
                                            HudScreen.ITINERARY -> lastTour?.let { ui ->
                                                ItineraryOverlay(
                                                    tour = ui,
                                                    voice = voiceUiOf(voiceState, heard, answer, listening, hearingNow, thinking),
                                                    speaking = narrationState is NarrationController.State.Speaking && !(narrationState as NarrationController.State.Speaking).preparing ||
                                                        narrationState is NarrationController.State.Playing,
                                                    onClose = ::closeItineraryView,
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    } else if (panel is Panel.Home || inPip) {
                        AssistantScreen(
                            mini = inPip,
                            compact = compact,
                            city = where ?: upcoming.firstOrNull()?.city,
                            offline = catalogueResult?.source == CatalogueRepository.Source.FALLBACK,
                            mode = mode,
                            voice = voiceUiOf(voiceState, heard, answer, listening, hearingNow, thinking),
                            speaking = narrationState is NarrationController.State.Speaking && !(narrationState as NarrationController.State.Speaking).preparing ||
                                narrationState is NarrationController.State.Playing,
                            nowPlaying = (nowPlayingOf(narrationState, tick) ?: installingOf(modelsPercentNow))
                                // Una respuesta del asistente ya está en los subtítulos.
                                ?.takeUnless { narrationState is NarrationController.State.Speaking },
                            notices = notices,
                            tour = tourUi,
                            search = searchUi,
                            nearby = upcoming.pictured(imgs),
                            origin = vehicleState.position,
                            navigation = navigationUi,
                            // Tocar una sugerencia es decirla: pasa por el mismo camino que la voz.
                            // "Mis itinerarios" siempre: también es donde se empieza uno nuevo.
                            suggestions = suggestionsUi,
                            shortcuts = shortcutsUi,
                            onSelect = { panel = Panel.Detail(it) },
                            onListen = { play(it) },
                            onNavigate = { navigateTo(it) },
                            onStopNarration = { narration.stop() },
                            onStopNavigation = { endNavigation() },
                            onClearSearch = ::clearSearch,
                            onAdd = { place -> answer = addToItinerary(place, Phrasebook.Context(vehicleState.position, upcoming, memory, language)) },
                        )
                    } else {
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

                    // Un solo botón discreto para los ajustes —simulador y silencio
                    // incluidos—, en vez de una barra fija que estorbaba.
                    // Arriba a la derecha, la barra de iconos: un toque enseña qué es,
                    // otro lo abre. Con la vista de un itinerario delante, solo los ajustes.
                    if (panel is Panel.Home && !inPip && itineraryMap == null) {
                        val ctx = Phrasebook.Context(vehicleState.position, upcoming, memory, language)
                        MenuRail(
                            entries = listOfNotNull(
                                MenuEntry(MenuIcon.ITINERARIES, myItineraries(language)) {
                                    answer = openLibrary(ctx).ifEmpty { null }
                                }.takeIf { itineraryMap == null },
                                MenuEntry(MenuIcon.SETTINGS, settingsLabel(language)) { panel = Panel.Settings },
                            ),
                            state = menu,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .panelInsetsPadding()
                                .padding(CarTheme.dimens.spaceS),
                        )
                    }

                }
            }
        }
    }

    /** Qué panel hay sobre el mapa. */
    private enum class HudScreen { HOME, LIBRARY, ITINERARY }

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
                search = searchQuery.collectAsState().value?.let { query ->
                    SearchUi(
                        // Con zona, o si son parecidos, el título ya lo dice; si no, "cerca".
                        title = if (query.endsWith(SIMILAR_MARK)) {
                            query.removeSuffix(SIMILAR_MARK)
                        } else if (" · " in query) {
                            query.replaceFirstChar { it.uppercase() }
                        } else {
                            stringResource(com.secretworld.auto.feature.discover.R.string.search_nearby, query.replaceFirstChar { it.uppercase() })
                        },
                        places = searchResults.collectAsState().value,
                    )
                },
                onClearSearch = ::clearSearch,
                shortcuts = SHORTCUTS.map { (category, label) ->
                    val text = label(language)
                    SearchShortcut(label = text.replaceFirstChar { it.uppercase() }) { findPlaces(text, category, announce = true) }
                },
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
                radii = RADII_KM,
                radiusKm = homeRadiusKm.collectAsState().value,
                onRadius = ::setRadius,
                onRemoveVoice = { },
                onClose = { onPanel(Panel.Home) },
                testingStatus = "${stringResource(if (mode == DrivingMode.PARKED) R.string.mode_parked else R.string.mode_driving)} · %.0f km/h"
                    .format(vehicle.state.collectAsState().value.speedMetersPerSecond * 3.6),
                testing = testingActions(),
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
        answer = answer ?: (state as? VoiceInput.State.Failed)?.takeUnless { it.quiet }?.reason,
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
                focusMapOn(command.place)
                intro
            }

            is VoiceCommand.NavigateToNamed -> {
                // Un sitio concreto: se busca por su nombre y, si el primero es
                // el pedido, se va. Si hay dudas, se enumeran para elegir.
                val reply = said ?: Phrasebook.lookingFor(c, command.name)
                narration.say(reply)
                navigateToNamed(command.name, command.area)
                reply
            }

            is VoiceCommand.FindPlaces -> {
                val reply = said ?: command.area?.let { area ->
                    Phrasebook.searchingIn(c, command.query, if (area == VoiceGrammar.CITY_CENTRE) Phrasebook.cityCentre(c) else area.replaceFirstChar { it.uppercase() })
                } ?: Phrasebook.searching(c, command.query)
                narration.say(reply)
                findPlaces(command.query, VoiceGrammar.searchCategoryOf(command.query), area = command.area)
                reply
            }

            VoiceCommand.Yes -> {
                // Lo último ofrecido manda: la historia de la parada a la que se
                // acaba de llegar, buscar dónde comer, o llevar a un sitio.
                storyOffered?.let { stop ->
                    storyOffered = null
                    val intro = Phrasebook.playing(c, stop)
                    narration.say(intro) { narration.listen(stop) }
                    memory.focusOn(stop)
                    return intro
                }
                if (moveOnOffered) {
                    moveOnOffered = false
                    return advanceTour(c)
                }
                if (lunchOffered) {
                    lunchOffered = false
                    val query = language.say(en = "restaurant", es = "restaurante", it = "ristorante")
                    val reply = Phrasebook.searching(c, query)
                    narration.say(reply)
                    findPlaces(query, VoiceGrammar.searchCategoryOf(query))
                    return reply
                }
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
                    // Con navegador, en silencio: el suyo ya anuncia la ruta.
                    val reply = Phrasebook.routingTo(c, place)
                    if (!hasExternalNavigation()) narration.say(reply)
                    navigateTo(place, announce = false)
                    reply
                }
            }

            VoiceCommand.No -> {
                offered = null
                // No a seguir: sin prisa, se queda.
                if (moveOnOffered) {
                    moveOnOffered = false
                    val reply = Phrasebook.stayLonger(c)
                    narration.say(reply)
                    return reply
                }
                // En un itinerario, decir que no a la historia o a comer es seguir.
                if (tour.value != null && (storyOffered != null || lunchOffered)) {
                    storyOffered = null
                    lunchOffered = false
                    return advanceTour(c)
                }
                val reply = said ?: Phrasebook.stopped(c)
                narration.say(reply)
                reply
            }

            VoiceCommand.WhatIsNearby -> {
                clearSearch()
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
                    if (!hasExternalNavigation()) narration.say(reply)
                    navigateTo(target, announce = false)
                    reply
                }
            }

            VoiceCommand.Skip -> {
                // En un itinerario, "siguiente parada" o "salta esta" es pasar a la siguiente.
                if (tour.value != null) {
                    narration.stop()
                    return advanceTour(c)
                }
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
                if (tour.value != null) {
                    val floating = isInPictureInPictureMode
                    endTour()
                    endNavigation()
                    val reply = withNavigatorHint(Phrasebook.itineraryEnded(c), c, floating)
                    narration.say(reply)
                    return reply
                }
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

            is VoiceCommand.PlanItinerary -> itineraryStep(itinerary.request(command.request, profiles.load()), c)

            VoiceCommand.StartItinerary -> {
                // Con uno solo, ese; con varios, se pregunta cuál con la ventana delante.
                val saved = savedList()
                when (saved.size) {
                    0 -> Phrasebook.noSavedItinerary(c).also { narration.say(it) }
                    1 -> libraryChoice(saved, ItineraryLibrary.Choice(0, ItineraryLibrary.Action.START), c)
                    else -> openLibrary(c)
                }
            }

            VoiceCommand.ShowItineraries -> openLibrary(c)

            // Sin itinerario a la vista: empieza uno con ese sitio. Con uno, lo
            // atiende antes la conversación como un cambio.
            is VoiceCommand.AddToItinerary -> addToItinerary(command.place, c)

            VoiceCommand.BuildItinerary -> buildItinerary(c)

            VoiceCommand.ForgetProfile -> {
                profiles.clear()
                val reply = Phrasebook.profileForgotten(c)
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

    // ------------------------------------------------------------------ itinerarios

    /** Hace lo que pide la conversación del itinerario y devuelve lo que se dice. */
    private fun itineraryStep(step: ItineraryConversation.Step, c: Phrasebook.Context): String {
        // Lo que se ofreció antes ya no está en el aire: el "sí" siguiente es para esto.
        offered = null
        return itineraryStepNow(step, c)
    }

    private fun itineraryStepNow(step: ItineraryConversation.Step, c: Phrasebook.Context): String = when (step) {
        is ItineraryConversation.Step.Ask -> {
            val reply = Phrasebook.askItinerary(c, step.missing)
            narration.say(reply) { voiceInput.listenForAnswer() }
            reply
        }
        is ItineraryConversation.Step.Plan -> {
            val reply = Phrasebook.planningItinerary(c)
            narration.say(reply)
            planItinerary(step.request)
            reply
        }
        is ItineraryConversation.Step.Repropose -> {
            // Un cambio a uno de la lista: pasa a su vista para seguir con él.
            library.value = null
            showItinerary(step.proposal)
            // El cambio se ve en el mismo mapa con los marcadores renumerados, sin
            // recorrido: ese se traza cuando se acepta. Se dice solo lo que cambió.
            refreshItineraryMap(step.proposal, route = false)
            val reply = Phrasebook.itineraryChanged(c, step.proposal, step.change)
            narration.say(reply) { voiceInput.listenForAnswer() }
            reply
        }
        is ItineraryConversation.Step.Save -> {
            // Confirmado no es arrancado: se guarda y se pregunta si empezar ya.
            saveCurrent(step.proposal)
            shownPlan.value = step.proposal
            // Aceptado: ahora sí, el recorrido por calles.
            refreshItineraryMap(step.proposal, route = true)
            val reply = Phrasebook.itinerarySaved(c)
            narration.say(reply) { voiceInput.listenForAnswer() }
            reply
        }
        is ItineraryConversation.Step.Start -> {
            library.value = null
            // "Inicia el itinerario" a la propuesta: se guarda también.
            saveCurrent(step.proposal)
            flyover.value = null
            startTour(step.proposal)
            val first = step.proposal.stops.first()
            // En silencio: el navegador ya habla al arrancar la ruta, y dos voces a
            // la vez no se entienden. La frase queda solo en pantalla.
            val reply = Phrasebook.itineraryStarted(c, first)
            navigateTo(first, announce = false)
            reply
        }
        is ItineraryConversation.Step.Shelved -> {
            library.value = null
            saveCurrent(step.proposal)
            flyover.value = null
            shownPlan.value = null
            val reply = Phrasebook.itineraryShelved(c)
            narration.say(reply)
            reply
        }
        ItineraryConversation.Step.Discarded -> {
            library.value = null
            flyover.value = null
            shownPlan.value = null
            val reply = Phrasebook.itineraryDiscarded(c)
            narration.say(reply)
            reply
        }
    }

    /**
     * Con la vista del itinerario delante, la cámara se acerca a lo que se
     * cuenta; cuando la narración acaba —o se corta—, vuelve al itinerario. La
     * introducción y la narración van seguidas con un instante en silencio
     * entre ellas: solo cuenta como final un silencio que dura.
     */
    private fun focusMapOn(place: Place) {
        val onHome = flyover.value == null
        if (!onHome && flyover.value?.intro != false) return
        if (onHome) homeFocus.value = place else flyover.value = flyover.value?.copy(focus = place)
        focusJob?.cancel()
        focusJob = lifecycleScope.launch {
            withTimeoutOrNull(SPEECH_START_MS) { narration.state.first { it !is NarrationController.State.Idle } }
            while (true) {
                narration.state.first { it is NarrationController.State.Idle || it is NarrationController.State.Failed }
                delay(FOCUS_SETTLE_MS)
                val now = narration.state.value
                if (now is NarrationController.State.Idle || now is NarrationController.State.Failed) break
            }
            if (onHome) homeFocus.value = null else flyover.value = flyover.value?.copy(focus = null)
        }
    }

    /**
     * Cerrar la vista del itinerario es dejarlo y volver a "Mis itinerarios",
     * en silencio, para elegir otro; sin ninguno guardado, al inicio. Lo
     * propuesto y sin guardar se descarta. Un recorrido en marcha no se toca.
     */
    private fun closeItineraryView() {
        flyover.value = null
        drafting.value = false
        focusJob?.cancel()
        if (tour.value != null) return
        // De vuelta a la lista, con el que se veía elegido si estaba guardado.
        showLibrary(selectId = editingId)
    }

    // ------------------------------------------------------------------ a mano

    /** Lo que se puede añadir tocándolo en el mapa: lo que hay alrededor de [center]. */
    private suspend fun candidatesAround(center: Coordinates): List<Place> =
        runCatching { catalogue.nearby(center, language = language).places.take(MAX_CANDIDATES) }.getOrDefault(emptyList())

    /** "Quiero armar mi propio itinerario": el mapa de alrededor, vacío, para ir eligiendo. */
    private fun buildItinerary(c: Phrasebook.Context): String {
        planJob?.cancel()
        itinerary.reset()
        editingId = null
        library.value = null
        shownPlan.value = null
        drafting.value = true
        val here = vehicle.state.value.position
        lifecycleScope.launch {
            flyover.value = FlyoverState(
                start = here,
                stops = emptyList(),
                times = emptyList(),
                path = emptyList(),
                legEnds = emptyList(),
                summary = "",
                intro = false,
                candidates = candidatesAround(here),
            )
        }
        val reply = Phrasebook.draftItinerary(c)
        narration.say(reply) { voiceInput.listenForAnswer() }
        return reply
    }

    /**
     * Un sitio elegido —tocado en el mapa o en la lista, o "añade…"—: al
     * itinerario a la vista, al final; sin ninguno, empieza uno con él.
     */
    private fun addToItinerary(place: Place, c: Phrasebook.Context): String {
        itinerary.edited(ItineraryEdit.Add(place, atEnd = true))?.let { step ->
            if (flyover.value == null) {
                (step as? ItineraryConversation.Step.Repropose)?.let { change ->
                    lifecycleScope.launch { flyover.value = flyoverOf(change.proposal, intro = false, route = false) }
                }
            }
            return itineraryStep(step, c)
        }
        planJob?.cancel()
        editingId = null
        drafting.value = false
        library.value = null
        val proposal = ItineraryDesigner.startedWith(place, vehicle.state.value.position, nowMinute())
        itinerary.proposed(proposal)
        showItinerary(proposal)
        lifecycleScope.launch { flyover.value = flyoverOf(proposal, intro = false, route = false) }
        val reply = Phrasebook.itineraryStartedWith(c, place)
        narration.say(reply) { voiceInput.listenForAnswer() }
        return reply
    }

    // ------------------------------------------------------------------ guardados

    private fun nowMinute(): Int = java.time.LocalTime.now().let { it.hour * 60 + it.minute }

    private fun savedList(): List<SavedItinerary> = savedItineraries.all(vehicle.state.value.position, nowMinute())

    private fun refreshSaved() {
        val all = savedList()
        savedCount.value = all.size
        if (library.value != null) library.value = all
    }

    /** Guarda lo confirmado: encima del que se estaba cambiando, o como uno nuevo. */
    private fun saveCurrent(proposal: ItineraryProposal) {
        val id = editingId ?: savedItineraries.newId().also { editingId = it }
        // Con las imágenes encontradas: así el guardado las trae al abrirlo.
        savedItineraries.save(id, proposal.copy(stops = proposal.stops.pictured()))
        refreshSaved()
    }

    /**
     * La ventana de los guardados, en silencio: se ven, y no hace falta leerlos
     * ni quedarse esperando. Se elige tocando o con la palabra de activación.
     */
    private fun openLibrary(c: Phrasebook.Context): String {
        showLibrary(selectId = null)
        return ""
    }

    /**
     * La lista sobre el mapa, con [selectId] —o el primero— elegido y a la vista.
     * También sin ninguno guardado: desde aquí se empieza uno nuevo.
     */
    private fun showLibrary(selectId: String?) {
        val all = savedList()
        all.forEach { picture(it.proposal.stops) }
        savedCount.value = all.size
        planJob?.cancel()
        drafting.value = false
        library.value = all
        val at = all.indexOfFirst { it.id == selectId }.coerceAtLeast(0)
        if (all.isEmpty()) {
            // Sin ninguno: el mapa de alrededor, de fondo.
            itinerary.reset()
            shownPlan.value = null
            editingId = null
            val here = vehicle.state.value.position
            flyover.value = FlyoverState(here, emptyList(), emptyList(), emptyList(), emptyList(), "", intro = false)
        } else {
            previewSaved(at)
        }
    }

    /**
     * Elegir uno de la lista: el mapa vuela a él y queda como el itinerario a la
     * vista, así que "empecemos" o "empieza por el castillo" van con él.
     */
    private fun previewSaved(index: Int) {
        val item = library.value?.getOrNull(index) ?: return
        librarySelected.value = index
        editingId = item.id
        offered = null
        itinerary.resumed(item.proposal)
        shownPlan.value = item.proposal
        memory.listed(item.proposal.stops)
        // Primero en línea recta, al momento; después, con las calles.
        lifecycleScope.launch {
            flyover.value = flyoverOf(item.proposal, intro = false, route = false, around = false)
            val routed = flyoverOf(item.proposal, intro = false, around = false)
            if (library.value != null && librarySelected.value == index) flyover.value = routed
        }
    }

    /** Salir de "Mis itinerarios": al inicio, sin nada a medias. */
    private fun boundsAround(center: Coordinates, meters: Double): Bounds {
        val dLat = meters / 111_320.0
        val dLon = meters / (111_320.0 * kotlin.math.cos(Math.toRadians(center.latitude)))
        return Bounds(center.latitude - dLat, center.longitude - dLon, center.latitude + dLat, center.longitude + dLon)
    }

    /** Otro radio de acción: se guarda, y la búsqueda a la vista se rehace con él. */
    private fun setRadius(km: Int) {
        settingsPrefs.edit().putInt(RADIUS_KEY, km).apply()
        homeRadiusKm.value = km
        val again = lastSearch
        if (searchQuery.value != null && again != null) findPlaces(again.first, again.second)
    }

    private fun closeLibrary() {
        library.value = null
        flyover.value = null
        if (tour.value != null) return
        itinerary.reset()
        shownPlan.value = null
        editingId = null
    }

    /** Lo elegido de los guardados: empezarlo, verlo en el mapa o borrarlo. */
    private fun libraryChoice(saved: List<SavedItinerary>, choice: ItineraryLibrary.Choice, c: Phrasebook.Context): String {
        val item = saved[choice.index]
        return when (choice.action) {
            ItineraryLibrary.Action.START -> {
                library.value = null
                editingId = item.id
                itineraryStep(ItineraryConversation.Step.Start(item.proposal), c)
            }
            ItineraryLibrary.Action.OPEN -> {
                // Su vista, para cambiarlo: la del itinerario, sin presentación,
                // con los sitios de alrededor para añadir.
                if (librarySelected.value != choice.index || library.value == null) previewSaved(choice.index)
                library.value = null
                showItinerary(item.proposal)
                lifecycleScope.launch { flyover.value = flyoverOf(item.proposal, intro = false) }
                ""
            }
            ItineraryLibrary.Action.DELETE -> {
                savedItineraries.delete(item.id)
                if (editingId == item.id) editingId = null
                showLibrary(selectId = saved.getOrNull(choice.index + 1)?.id ?: saved.getOrNull(choice.index - 1)?.id)
                Phrasebook.itineraryDeleted(c).also { narration.say(it) }
            }
        }
    }

    /** Lo que enseña la ventana de cada guardado. */
    private fun savedUiOf(item: SavedItinerary): SavedItineraryUi {
        val stops = item.proposal.stops.pictured()
        val date = java.time.Instant.ofEpochMilli(item.savedAt).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag(language.code)))
        return SavedItineraryUi(
            id = item.id,
            title = stops.first().name,
            destination = stops.last().name.takeIf { stops.size > 1 },
            count = resources.getQuantityString(R.plurals.itinerary_stops, stops.size, stops.size),
            duration = Phrasebook.spokenDuration(item.proposal.minutes * 60.0, language),
            date = date,
            stops = stops,
        )
    }

    /**
     * Lo que la gramática no entendió con una propuesta delante, se le pregunta
     * al modelo: "venga, en marcha" o "lo hacemos otro rato". Fuera del efecto
     * de la voz, que se cancela en cuanto el micrófono vuelve a escuchar, y el
     * modelo tarda segundos. Si no lo ve claro, la propuesta sigue pendiente y
     * se pide que lo repita. `false` si no hay a quién preguntar.
     */
    private fun askModel(heard: String, pending: ItineraryConversation.State): Boolean {
        // Solo con una propuesta: lo único que puede decidir es guardarla.
        if (pending !is ItineraryConversation.State.Proposed) return false
        // Una palabra suelta no se le pregunta: "para" llegó a leerse como
        // "guárdalo" y, ya guardado, arrancó la ruta.
        if (heard.trim().split(Regex("\\s+")).size < MODEL_MIN_WORDS) return false
        val model = gemma?.takeIf { it.ready } ?: return false
        itinerary.restore(pending)
        lifecycleScope.launch {
            val heardAs = try {
                ItineraryReplyPrompt.parse(model.complete(ItineraryReplyPrompt.PREFIX, ItineraryReplyPrompt.request(heard)))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SWItinerary", "el LLM no contestó", e)
                null
            }
            Log.i("SWItinerary", "«$heard» → modelo: $heardAs")
            // El modelo solo puede guardar una propuesta: la vista sigue abierta y
            // no se pierde nada. Empezar —abre el navegador— o dejarlo para luego
            // —cierra la vista— no se deciden con él: a "y entonces le dije que
            // tal" contestó "later" y el itinerario se fue de la pantalla.
            val reply = heardAs.takeIf { it == ItineraryReply.SAVE && pending is ItineraryConversation.State.Proposed }
            // Mientras pensaba pudo pasar a otra cosa.
            if (itinerary.state != pending) return@launch
            val c = Phrasebook.Context(vehicle.state.value.position, memory.listed, memory, language)
            val said = reply?.let { itinerary.answer(it) }?.let { itineraryStep(it, c) }
                // Sin abrir otra vez el micrófono: si era ruido, volvería a oírlo.
                ?: notCaught(language).also { narration.say(it) }
            memory.remember(heard, said)
        }
        return true
    }

    /**
     * Busca candidatos —alrededor del coche o en la zona pedida— y propone. La
     * propuesta se dice con pregunta al final, y se queda esperando el sí.
     */
    private fun planItinerary(request: ItineraryRequest) {
        planJob?.cancel()
        // Uno nuevo: al confirmarlo se guarda aparte, no encima del que se viera antes.
        editingId = null
        planJob = lifecycleScope.launch {
            val here = vehicle.state.value.position
            val c = Phrasebook.Context(here, memory.listed, memory, language)
            val area = request.area?.let { name ->
                areas.locate(name, here, language) ?: run {
                    itinerary.reset()
                    narration.say(Phrasebook.itineraryAreaNotFound(c, name.replaceFirstChar { it.uppercase() })) { voiceInput.listenForAnswer() }
                    return@launch
                }
            }
            val from = area?.position ?: here
            val candidates = catalogue.nearby(from, language = language).places
            val now = java.time.LocalTime.now()
            val proposal = ItineraryDesigner.plan(from, candidates, request, profiles.load(), now.hour * 60 + now.minute)
            Log.i("SWItinerary", "${request} → ${proposal.stops.size} paradas, ${proposal.minutes} min, comida tras ${proposal.mealAfter}")
            if (proposal.stops.isEmpty()) {
                itinerary.reset()
                narration.say(Phrasebook.noItinerary(c)) { voiceInput.listenForAnswer() }
                return@launch
            }
            itinerary.proposed(proposal)
            // La presentación primero —con la ruta por calles, tramo a tramo— y la
            // propuesta hablada a la vez: se oye mientras la cámara vuela.
            flyover.value = flyoverOf(proposal)
            showItinerary(proposal)
            val reply = Phrasebook.proposeItinerary(c, proposal)
            Log.i("SWItinerary", "propone: «$reply»")
            lateReply.value = reply
            narration.say(reply) { voiceInput.listenForAnswer() }
        }
    }

    /**
     * Lo que necesita la presentación: la ruta por calles de parada en parada. Si
     * un tramo no se puede calcular —sin red—, ese tramo va en línea recta.
     */
    private suspend fun flyoverOf(proposal: ItineraryProposal, intro: Boolean = true, route: Boolean = true, around: Boolean = true): FlyoverState {
        // Desde la primera parada, no desde donde esté el coche: el itinerario es
        // el recorrido entre las paradas; llegar a la primera es cosa del navegador.
        val legs = if (!route) emptyList() else coroutineScope {
            proposal.stops.mapIndexed { index, stop ->
                if (index == 0) {
                    async { listOf(stop.position) }
                } else {
                    val origin = proposal.stops[index - 1].position
                    async { router.route(origin, stop)?.points?.takeIf { it.size >= 2 } ?: listOf(origin, stop.position) }
                }
            }.awaitAll()
        }
        val path = ArrayList<Coordinates>()
        val ends = ArrayList<Int>()
        legs.forEach { leg ->
            path += if (path.isEmpty()) leg else leg.drop(1)
            ends += path.size
        }
        val ui = tourUiOf(proposal, null)
        return FlyoverState(
            start = proposal.stops.first().position,
            stops = proposal.stops,
            times = ui.stops.map { stop -> listOf("${stop.time} – ${stop.until}", stop.stay).filter { it.isNotBlank() && it != " – " }.joinToString(" · ") },
            path = path,
            legEnds = ends,
            summary = ui.summary,
            intro = intro,
            candidates = if (intro || !around) emptyList() else candidatesAround(proposal.stops.first().position),
        )
    }

    /**
     * Tras un cambio, la misma vista sin repetir la presentación: con [route],
     * el recorrido rehecho; sin él, solo las paradas, a falta de aceptarlo.
     */
    private fun refreshItineraryMap(proposal: ItineraryProposal, route: Boolean) {
        if (flyover.value == null) return
        lifecycleScope.launch { flyover.value = flyoverOf(proposal, intro = false, route = route) }
    }

    /** La propuesta en la lista del panel y en el mapa, para verla con el coche detenido. */
    private fun showItinerary(proposal: ItineraryProposal) {
        clearSearch()
        shownPlan.value = proposal
        memory.listed(proposal.stops)
    }

    private fun clock(minute: Int) = "%d:%02d".format((minute / 60) % 24, minute % 60)

    private fun settingsLabel(language: VoiceLanguage): String =
        language.say(en = "Settings", es = "Ajustes", it = "Impostazioni")

    private fun myItineraries(language: VoiceLanguage): String =
        language.say(en = "My itineraries", es = "Mis itinerarios", it = "I miei itinerari")

    /** Lo que se le puede pedir, para tocarlo en la pantalla. */
    private fun suggestionsFor(language: VoiceLanguage): List<String> = language.say(
        en = listOf("Plan me an itinerary", "What's nearby?", "Where can I eat?"),
        es = listOf("Ármame un itinerario", "¿Qué hay cerca?", "¿Dónde puedo comer?"),
        it = listOf("Organizzami un itinerario", "Cosa c'è qui vicino?", "Dove posso mangiare?"),
    )

    /** El itinerario como lo enseña la pantalla: cada parada con su hora. */
    private fun tourUiOf(plan: ItineraryProposal, current: Tour?) = TourUi(
        stops = plan.stops.mapIndexed { index, place ->
            val arrival = plan.arrivals.getOrNull(index)
            val stay = plan.stays.getOrNull(index)
            TourStopUi(
                place = place.pictured(),
                time = arrival?.let(::clock).orEmpty(),
                lunchAfter = plan.mealAfter == index && index < plan.stops.lastIndex,
                until = if (arrival != null && stay != null) clock(arrival + stay) else "",
                stay = stay?.let { Phrasebook.spokenDuration(it * 60.0, language) }.orEmpty(),
            )
        },
        proposed = current == null,
        saved = current == null && itinerary.state is ItineraryConversation.State.Saved,
        current = current?.index ?: -1,
        summary = getString(R.string.itinerary_summary, plan.stops.size, Phrasebook.spokenDuration(plan.minutes * 60.0, language)),
        title = resources.getQuantityString(R.plurals.itinerary_stops, plan.stops.size, plan.stops.size),
        duration = Phrasebook.spokenDuration(plan.minutes * 60.0, language),
    )

    private fun startTour(proposal: ItineraryProposal) {
        // Lo dicho al pedir se aprende: la próxima vez no hace falta repetirlo.
        profiles.update { it.learn(proposal.request) }
        tour.value = Tour(proposal.stops)
        shownPlan.value = proposal
        tourMealAfter = proposal.mealAfter
        arrivedAt = null
        storyOffered = null
        lunchOffered = false
    }

    private fun endTour() {
        moveOnJob?.cancel()
        moveOnOffered = false
        tour.value = null
        shownPlan.value = null
        leavePip()
        tourMealAfter = null
        arrivedAt = null
        storyOffered = null
        lunchOffered = false
        clearSearch()
    }

    /**
     * A la siguiente parada. Si tocaba comer tras esta, antes se ofrece buscar
     * dónde, y se sigue con el sí o el no.
     */
    private fun advanceTour(c: Phrasebook.Context): String {
        val current = tour.value ?: return Phrasebook.noMoreStops(c).also { narration.say(it) }
        storyOffered = null
        moveOnOffered = false
        moveOnJob?.cancel()
        if (tourMealAfter == current.index) {
            tourMealAfter = null
            lunchOffered = true
            val reply = Phrasebook.lunchTime(c)
            narration.say(reply) { voiceInput.listenForAnswer() }
            return reply
        }
        val next = current.advanced()
        if (next == null) {
            val floating = isInPictureInPictureMode
            endTour()
            endNavigation()
            val reply = withNavigatorHint(Phrasebook.itineraryFinished(c), c, floating)
            narration.say(reply)
            return reply
        }
        tour.value = next
        arrivedAt = null
        // Como al empezar: la ruta nueva la anuncia el navegador.
        val reply = Phrasebook.nextStop(c, next.current, next.isLast)
        navigateTo(next.current, announce = false)
        return reply
    }

    /**
     * Llegar a la parada en curso: se apunta como visitada y se **ofrece** su
     * historia (SPEC §2.4). Sin historia que contar, se sigue.
     */
    private fun checkTourArrival(around: List<Place>) {
        val current = tour.value ?: return
        val stop = current.current
        if (arrivedAt == stop.id) return
        val here = vehicle.state.value.position
        if (here.distanceTo(stop.position) > ARRIVAL_METERS) return
        arrivedAt = stop.id
        profiles.update { it.visited(listOf(stop)) }
        val c = Phrasebook.Context(here, around, memory, language)
        if (catalogue.textFor(stop, language).isNullOrBlank()) {
            advanceTour(c)
            return
        }
        storyOffered = stop
        memory.focusOn(stop)
        val stay = shownPlan.value?.let { plan -> plan.stays.getOrNull(plan.stops.indexOfFirst { it.id == stop.id }) }
        narration.say(Phrasebook.arrivedAtStop(c, stop, stay)) { voiceInput.listenForAnswer() }
        scheduleMoveOn(current, stay)
    }

    /**
     * Cuando se cumple el tiempo previsto en la parada, se **propone** seguir y
     * se espera el sí. Si el conductor ya pasó a la siguiente, no se dice nada.
     */
    private fun scheduleMoveOn(at: Tour, stayMinutes: Int?) {
        moveOnJob?.cancel()
        val minutes = stayMinutes ?: return
        moveOnJob = lifecycleScope.launch {
            delay(minutes * 60_000L)
            val now = tour.value ?: return@launch
            if (now.index != at.index) return@launch
            moveOnOffered = true
            storyOffered = null
            val c = Phrasebook.Context(vehicle.state.value.position, memory.listed, memory, language)
            val reply = Phrasebook.timeToMoveOn(c, now.current, now.next)
            lateReply.value = reply
            narration.say(reply) { voiceInput.listenForAnswer() }
        }
    }

    /**
     * Entrega el destino al navegador que tenga el usuario: Google Maps o Waze
     * (SPEC §2.4, RF-8.11). En pantalla dividida se abre en la otra mitad; a
     * pantalla completa queda delante, y Secret World sigue al volver.
     */
    private fun hasExternalNavigation(): Boolean =
        listOf(GOOGLE_MAPS, WAZE).any { packageManager.getLaunchIntentForPackage(it) != null }

    private fun openExternalNavigation(place: Place): Boolean {
        val (lat, lon) = place.position.latitude to place.position.longitude
        val candidates = listOf(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("google.navigation:q=$lat,$lon&mode=d"))
                .setPackage(GOOGLE_MAPS),
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://waze.com/ul?ll=$lat,$lon&navigate=yes"))
                .setPackage(WAZE),
        )
        val intent = candidates.firstOrNull { it.resolveActivity(packageManager) != null } ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        // En la otra mitad de la pantalla, no encima de nosotros.
        if (isInMultiWindowMode) intent.addFlags(android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        return runCatching { startActivity(intent) }.isSuccess.also { if (it) externalRouteOpen = true }
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
    /**
     * Busca sitios de un tipo y los deja **en la lista del panel**, no solo en
     * el mapa: si se pidieron restaurantes, lo que se ve son restaurantes. La
     * voz dice los tres primeros; la lista enseña hasta ocho.
     */
    private fun findPlaces(query: String, category: String?, announce: Boolean = false, area: String? = null) {
        // Una búsqueda nueva manda sobre la anterior: si aquella tardaba —la
        // lista de barrios, la red—, su respuesta llegaría después y se
        // mezclaría con lo que se está pidiendo ahora.
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            val here = vehicle.state.value.position
            val c = Phrasebook.Context(here, memory.listed, memory, language)
            if (announce) narration.say(Phrasebook.searching(c, query))
            // En otra zona —"en el barrio Las Gaviotas", "en el centro"—: primero
            // dónde está, y se busca alrededor de ella en vez del coche.
            val where = area?.let { areas.locate(it, here, language) }
            if (area != null && where == null) {
                val name = if (area == VoiceGrammar.CITY_CENTRE) Phrasebook.cityCentre(c) else area.replaceFirstChar { it.uppercase() }
                narration.say(Phrasebook.areaNotFound(c, name))
                return@launch
            }
            // Sin zona dicha, dentro del radio de acción: lo mismo que "Alrededor".
            val radiusM = homeRadiusKm.value * 1000.0
            lastSearch = (query to category).takeIf { where == null }
            val results = search.search(
                query,
                where?.position ?: here,
                limit = if (where != null) SEARCH_AREA_SHOWN else SEARCH_RADIUS_SHOWN,
                category = category,
                within = where?.bounds ?: boundsAround(here, radiusM),
            )
                .filter { where != null || here.distanceTo(it.position) <= radiusM }
                .let { mixed ->
                    // Donde el buscador mezcla lo que no es —tiendas de dentro de
                    // un centro comercial marcadas como centro comercial—, solo lo
                    // que lo dice en el nombre, más lo que OpenStreetMap sabe que es.
                    val strict = VoiceGrammar.strictness(category) ?: return@let mixed
                    val center = where?.position ?: here
                    val tagged = coroutineScope {
                        strict.osmQueries.map { q -> async { areas.findTagged(strict.osmTag, q, center, where?.bounds, language) } }.awaitAll().flatten()
                    }
                    (mixed.filter { VoiceGrammar.nameSays(it.name, strict) } + tagged)
                        .distinctBy { it.name.lowercase().trim() }
                }
                .let { inside ->
                    // Pocos dentro del barrio —lo escaso, como la comida oriental—:
                    // se completa con lo más cercano a él, aunque caiga justo fuera.
                    if (where == null || inside.size >= FEW_IN_AREA) {
                        inside
                    } else {
                        (inside + search.search(query, where.position, limit = SEARCH_SHOWN, category = category)).distinctBy { it.id }
                    }
                }
                // Lo que el complemento de arriba añadió pasa por el mismo filtro.
                .filter { place ->
                    val strict = VoiceGrammar.strictness(category) ?: return@filter true
                    place.id.startsWith("osm:") || VoiceGrammar.nameSays(place.name, strict)
                }
                // En la lista, por cercanía al coche: es la distancia que se enseña.
                .sortedBy { here.distanceTo(it.position) }
            Log.i("SWSearch", "«$query» [$category]${where?.let { " en ${it.name}" }.orEmpty()}: ${results.size} resultados · ${results.joinToString { it.name }}")
            searchResults.value = results
            searchQuery.value = listOfNotNull(query, where?.name).joinToString(" · ")
            if (results.isEmpty()) {
                narration.say(Phrasebook.nothingFound(c, query)) { voiceInput.listenForAnswer() }
                return@launch
            }
            memory.listed(results)
            val reply = Phrasebook.found(c, query, results.take(SEARCH_SPOKEN), area = where?.name)
            memory.remember(query, reply)
            narration.say(reply) { voiceInput.listenForAnswer() }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    /**
     * Lleva a un sitio dicho por su nombre o su dirección. Si el buscador
     * devuelve primero uno que se llama como lo pedido, ruta directa; si no,
     * las opciones en la lista, para elegir de viva voz o tocando.
     */
    private fun navigateToNamed(name: String, area: String?) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            val here = vehicle.state.value.position
            val c = Phrasebook.Context(here, memory.listed, memory, language)
            val where = area?.let { areas.locate(it, here, language) }
            // Dos buscadores a la vez: Mapbox tiene los negocios; OpenStreetMap,
            // los monumentos. Se elige el que mejor se llama como lo pedido.
            val center = where?.position ?: here
            val (fromMap, fromOsm) = coroutineScope {
                val map = async { search.findByName(name, center, where?.bounds) }
                val osm = async { areas.find(name, center, where?.bounds, language) }
                map.await() to osm.await()
            }
            val found = (fromMap + fromOsm).distinctBy { "${it.name.lowercase()}@${"%.3f".format(java.util.Locale.ROOT, it.position.latitude)}" }
            val best = NameMatcher.best(name, found)
            Log.i("SWSearch", "destino «$name»${where?.let { " en ${it.name}" }.orEmpty()}: elegido ${best?.name} de ${found.joinToString { it.name }}")
            when {
                found.isEmpty() -> narration.say(Phrasebook.namedNotFound(c, name.replaceFirstChar { it.uppercase() }))
                // Solo si se llama como lo pedido: con un único resultado que no
                // coincide también se pregunta, no se da por hecho.
                best != null -> {
                    val target = best
                    clearSearch()
                    navigateTo(target)
                }
                else -> {
                    searchResults.value = found
                    // El título dice que son parecidos, no lo pedido.
                    searchQuery.value = getString(R.string.search_similar, name.replaceFirstChar { it.uppercase() }) + SIMILAR_MARK
                    memory.listed(found)
                    // Ninguno se llama como lo pedido: se dice que no está, y se
                    // ofrecen los parecidos. Enumerarlos sin más parecía que sí.
                    val reply = Phrasebook.closestTo(c, name, found.take(SEARCH_SPOKEN))
                    memory.remember(name, reply)
                    narration.say(reply) { voiceInput.listenForAnswer() }
                }
            }
        }
    }

    /** Vuelve a la lista de siempre. */
    private fun clearSearch() {
        searchResults.value = emptyList()
        searchQuery.value = null
    }

    // ------------------------------------------------------------------ navegación

    /**
     * Traza la ruta hasta [place] y la deja activa: el mapa la dibuja, la guía la
     * sigue y las maniobras se anuncian. Es lo que en el vehículo hará NaviHelper
     * cuando Pleos lo publique; hasta entonces, Mapbox (docs/MAP-OPTIONS.md).
     */
    /**
     * Lleva a un sitio. **La navegación no es nuestra**: el destino se le pasa al
     * navegador del usuario. La propia —Mapbox en el banco de pruebas— queda
     * solo para cuando no hay ninguno instalado.
     */
    private fun navigateTo(place: Place, announce: Boolean = true) {
        memory.focusOn(place)
        lifecycleScope.launch {
            val here = vehicle.state.value.position
            val c = Phrasebook.Context(here, memory.listed, memory, language)
            // Con navegador, nada que decir: su voz anuncia la ruta, y la nuestra
            // la pisaba al pasar a la ventana flotante.
            if (announce && !hasExternalNavigation()) narration.say(Phrasebook.routingTo(c, place))
            if (hasExternalNavigation()) {
                // A pantalla completa, primero a la ventana flotante: así se sigue
                // oyendo y hablando con el navegador delante.
                if (!isInMultiWindowMode && enterPip() && openExternalNavigation(place)) {
                    endNavigation()
                    return@launch
                }
                // Sin ventana flotante, primero termina de hablar: el navegador nos
                // deja detrás, y al irnos la voz se corta a media frase.
                // Lo que se acaba de pedir decir puede no haber empezado aún.
                withTimeoutOrNull(SPEECH_START_MS) { narration.state.first { it !is NarrationController.State.Idle } }
                withTimeoutOrNull(SPEECH_WAIT_MS) { narration.state.first { it is NarrationController.State.Idle } }
                if (openExternalNavigation(place)) {
                    endNavigation()
                    return@launch
                }
            }
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
        voiceInput = if (local.available || local.bundled) local.also { onDevice = it } else SystemVoiceInput(this, language)
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

    /** El simulador y el silencio, dentro de los ajustes. No forman parte del producto. */
    @Composable
    private fun testingActions(): List<SettingAction> {
        val simulating = source.collectAsState().value === simulated
        return buildList {
            if (simulating) {
                add(SettingAction(stringResource(R.string.bar_park)) { simulated.park() })
                add(SettingAction(stringResource(R.string.bar_drive)) { simulated.drive() })
                add(SettingAction("+1 km") { simulated.advance(40.0) })
                if (device?.hasPermission == true) add(SettingAction("GPS") { useDeviceLocation() })
            } else {
                add(SettingAction("Sim") { source.value = simulated })
            }
            add(SettingAction(stringResource(R.string.bar_quiet)) { narration.stop() })
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
                text = "${stringResource(if (mode == DrivingMode.PARKED) R.string.mode_parked else R.string.mode_driving)} · %.0f km/h".format(speedKmh),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = d.spaceS),
            )
            if (enabled) {
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
        multiWindow.value = isInMultiWindowMode
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        multiWindow.value = isInMultiWindowMode
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pip.value = isInPictureInPictureMode
    }

    /**
     * Con un itinerario en marcha, salir de la app —el botón de inicio— no la
     * esconde: pasa a la ventana flotante y sigue atendiendo la voz.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (tour.value != null) enterPip()
    }

    /**
     * La ventana flotante (SPEC §4.4, en el móvil): al iniciar una ruta, Secret
     * World se hace pequeño junto al navegador y **solo atiende la voz**. Sigue
     * visible, así que no pasa por [onStop]: el micrófono y la narración siguen.
     * En el coche no hay: allí la doble sección la pone el sistema.
     */
    private fun enterPip(): Boolean {
        if (isInPictureInPictureMode) return true
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        val params = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(PIP_WIDTH, PIP_HEIGHT))
            .build()
        return runCatching { enterPictureInPictureMode(params) }
            .onFailure { Log.w("SWPip", "no se pudo pasar a la ventana flotante", it) }
            .getOrDefault(false)
    }

    /**
     * Si la ruta la lleva el navegador del usuario, se le dice que la cierre él:
     * una app no puede cerrar la de otra. En Pleos sí, con NaviHelper.
     */
    private fun withNavigatorHint(reply: String, c: Phrasebook.Context, floating: Boolean): String {
        if (!externalRouteOpen) return reply
        externalRouteOpen = false
        return reply + " " + Phrasebook.closeNavigatorHint(c, floating)
    }

    /**
     * De la ventana flotante a pantalla completa: se acabó la ruta. Desde
     * Android 14 el sistema lo bloquea —una ventana flotante no cuenta como
     * visible para abrir actividades— y hay que tocarla; en versiones
     * anteriores vuelve sola.
     */
    private fun leavePip() {
        if (!isInPictureInPictureMode) return
        startActivity(
            android.content.Intent(this, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
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
        /** A esta distancia de una parada del itinerario se está en ella. */
        const val ARRIVAL_METERS = 150.0
        /** La ventana flotante, algo más alta que ancha: núcleo, subtítulo y parada. */
        const val PIP_WIDTH = 4
        const val PIP_HEIGHT = 5
        /** Por debajo de este ancho, la pantalla del asistente es compacta. */
        val COMPACT_WIDTH = 600.dp
        const val GOOGLE_MAPS = "com.google.android.apps.maps"
        const val WAZE = "com.waze"
        /** Lo más que se espera a que termine una frase antes de abrir el navegador. */
        const val SPEECH_WAIT_MS = 12_000L
        const val SPEECH_START_MS = 1_500L
        /** Silencio que cuenta como final de una narración, no como la pausa tras la introducción. */
        const val FOCUS_SETTLE_MS = 1_500L
        const val MODEL_MIN_WORDS = 2
        /** Sitios de alrededor tocables en el mapa: más, y se tapan unos a otros. */
        const val MAX_CANDIDATES = 14
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

        /**
         * Búsquedas de un toque: lo que más se necesita en carretera. La
         * etiqueta es también lo que se dice ("Cerco benzina qui vicino").
         */
        val SHORTCUTS: List<Pair<String, (VoiceLanguage) -> String>> = listOf(
            "restaurant" to { l -> l.say(en = "restaurants", es = "restaurantes", it = "ristoranti") },
            "gas_station" to { l -> l.say(en = "fuel", es = "gasolina", it = "benzina") },
            "parking_lot" to { l -> l.say(en = "parking", es = "parqueaderos", it = "parcheggi") },
            "hotel" to { l -> l.say(en = "hotels", es = "hoteles", it = "hotel") },
            "pharmacy" to { l -> l.say(en = "pharmacies", es = "farmacias", it = "farmacie") },
            "atm" to { l -> l.say(en = "ATMs", es = "cajeros", it = "bancomat") },
        )

        /** Cuántos resultados de búsqueda se enseñan, y cuántos se dicen. */
        const val SEARCH_SHOWN = 8
        /** Búsquedas de imágenes a la vez: más, y Wikipedia limita. */
        const val PICTURE_PARALLEL = 4
        /** Fuera de escena: el panel ocupa el 40 % izquierdo, y un poco más para que no asome. */
        const val HUD_OFFSTAGE = 0.45f
        const val HUD_EXIT_MS = 200
        const val HUD_ENTER_MS = 420
        /** El nuevo entra cuando el de antes ya va saliendo: se reemplazan, no se pisan. */
        const val HUD_ENTER_DELAY_MS = 120
        const val IMAGE_USER_AGENT = "SecretWorldAuto/0.1 (https://sworld.co.uk; bench build)"
        /** Dentro del radio de acción: todos los que haya, hasta un tope razonable. */
        const val SEARCH_RADIUS_SHOWN = 40
        const val RADIUS_KEY = "radius_km"
        const val DEFAULT_RADIUS_KM = 3
        val RADII_KM = listOf(1, 3, 5, 10)
        /** En un barrio se enseña más: se busca por todo él, no solo lo cercano. */
        const val SEARCH_AREA_SHOWN = 30
        /** Menos que esto dentro de un barrio y se completa con lo de al lado. */
        const val FEW_IN_AREA = 5
        /** Marca, invisible, de una lista de parecidos: su título va tal cual. */
        const val SIMILAR_MARK = "\u200B"
        const val SEARCH_SPOKEN = 3

        const val SETTINGS = "settings"
        const val LANGUAGE_KEY = "language"
        /** Al reabrir tras un cambio de idioma pedido de viva voz, confirmarlo. */
        const val ANNOUNCE_KEY = "announce_language"

        /** La ubicación, en sus dos precisiones: basta con una. */
        val LOCATION = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
