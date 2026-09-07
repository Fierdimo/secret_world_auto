package com.secretworld.auto

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.DrivingMode
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.TourQueue
import com.secretworld.auto.domain.VoiceLanguage
import com.secretworld.auto.feature.discover.MapState
import com.secretworld.auto.map.PlatformMap
import com.secretworld.auto.feature.discover.NowPlaying
import com.secretworld.auto.feature.discover.RoutePanel
import com.secretworld.auto.feature.parked.ItineraryPanel
import com.secretworld.auto.feature.parked.PlaceDetailPanel
import com.secretworld.auto.feature.parked.SettingsPanel
import com.secretworld.auto.feature.voice.NarrationController
import kotlinx.coroutines.delay

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // De borde a borde: el mapa llega hasta el filo de la pantalla. Cualquier
        // franja del sistema que quede encima la dibuja el vehículo, no nosotros.
        enableEdgeToEdge()

        val api = SecretWorldApi.create(DeviceIdentity.of(this))
        catalogue = CatalogueRepository(api)
        mapKeys = MapCredentials(api)
        narration = NarrationController(this, catalogue, lifecycleScope, VoiceLanguage.ENGLISH)
        val modeController = DrivingModeController(vehicle, lifecycleScope)

        setContent {
            // Un coche se conduce de noche y con sol de frente: el tema es oscuro
            // por decisión de producto, no por seguir al sistema.
            SecretWorldTheme(darkTheme = true) {
                val vehicleState by vehicle.state.collectAsState()
                val mode by modeController.mode.collectAsState()
                val narrationState by narration.state.collectAsState()

                var catalogueResult by remember { mutableStateOf<CatalogueRepository.Result?>(null) }
                var panel by remember { mutableStateOf<Panel>(Panel.Home) }
                var tick by remember { mutableStateOf(0) }
                var mapboxToken by remember { mutableStateOf<String?>(null) }

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
                val upcoming = remember(places, positionKey, mode) {
                    queue.next(vehicleState, places, emptySet(), VoiceLanguage.ENGLISH, limit = 12)
                        .ifEmpty { places.sortedBy { vehicleState.position.distanceTo(it.position) } }
                }

                LaunchedEffect(Unit) { narration.ensureVoice() }
                LaunchedEffect(upcoming.joinToString { it.id }) { narration.prefetch(upcoming) }

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
        onPanel: (Panel) -> Unit,
    ) {
        when (panel) {
            Panel.Home -> RoutePanel(
                places = upcoming,
                origin = vehiclePosition,
                mode = mode,
                city = upcoming.firstOrNull()?.city,
                nowPlaying = nowPlaying,
                offline = result?.source == CatalogueRepository.Source.FALLBACK,
                onSelect = { onPanel(Panel.Detail(it)) },
                onListen = { narration.listen(it) },
                onNavigate = { onPanel(Panel.Detail(it)) },
            )

            is Panel.Detail -> PlaceDetailPanel(
                place = panel.place,
                onListen = { narration.listen(panel.place) },
                onNavigate = { /* NaviHelper pendiente: ver docs/PLEOS-SDK-SURFACE.md */ },
                onClose = { onPanel(Panel.Home) },
            )

            Panel.Itinerary -> ItineraryPanel(
                title = "A morning in ${upcoming.firstOrNull()?.city ?: "town"}",
                stops = upcoming.take(5),
                onSelect = { onPanel(Panel.Detail(it)) },
                onStart = { upcoming.firstOrNull()?.let { narration.listen(it) } },
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

    private fun nowPlayingOf(state: NarrationController.State, @Suppress("UNUSED_PARAMETER") tick: Int) =
        when (state) {
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
    }

    private sealed interface Panel {
        data object Home : Panel
        data class Detail(val place: Place) : Panel
        data object Itinerary : Panel
        data object Settings : Panel
    }

    private companion object {
        /**
         * Se arranca en Roma porque es donde el catálogo tiene densidad real, y
         * junto al Circo Máximo en vez de encima del Coliseo: si el vehículo nace
         * sobre un monumento, todas las distancias salen "3 m" y no se entiende
         * nada de lo que hace la cola.
         */
        val ROME = Coordinates(41.8859, 12.4850)
    }
}
