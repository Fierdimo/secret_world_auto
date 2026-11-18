package com.secretworld.auto.map

import android.graphics.PointF
import android.view.Gravity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.Motion
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Phrasebook
import com.secretworld.auto.domain.Place
import com.secretworld.auto.feature.discover.FlyoverState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * El itinerario y lo de alrededor sobre un mapa de **OpenStreetMap**. Sin
 * Mapbox: no lo tenemos licenciado (docs/NAVIGATION-AND-MAP-DECISIONS.md).
 *
 * Primero, **la presentación**, como la de un nivel en un videojuego: la cámara
 * vuela de parada en parada mientras la ruta se va dibujando, y cada parada
 * aparece con su foto. Tocar la salta. Después, **la vista se queda**: paradas,
 * ruta, el anillo del radio de acción con la onda del rastreo y los puntos
 * tocables, con [overlay] encima. Con **MapLibre** y los estilos libres de
 * OpenFreeMap, que no piden clave. Las líneas —ruta y anillo— son capas del mapa; los marcadores, Compose
 * encima, colocados en el punto de pantalla que les toca con cada movimiento de
 * la cámara: MapLibre no tiene anotaciones con vistas.
 */
@Composable
fun TourFlyover(
    state: FlyoverState,
    onIntroDone: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    /** Se tocó un sitio de alrededor: añadirlo. */
    onAddPlace: (Place) -> Unit = {},
) {
    val ground = MaterialTheme.colorScheme.background
    val night = ground.luminance() < 0.5f
    if (state.stops.isEmpty() && state.intro) {
        LaunchedEffect(Unit) { onIntroDone() }
        Box(modifier.fillMaxSize().background(ground)) { overlay() }
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val density = LocalDensity.current
    val overview = if (state.stops.isEmpty()) state.start to EMPTY_ZOOM else overviewOf(state.stops.map { it.position })

    // El mapa, una vez, con el ciclo de vida de la pantalla.
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    // Cada movimiento de la cámara: los marcadores se recolocan al leerlo, sin recomponer.
    val moved = remember { mutableIntStateOf(0) }
    LaunchedEffect(mapView, night) {
        val ready = suspendCancellableCoroutine { done -> mapView.getMapAsync { done.resume(it) } }
        ready.uiSettings.apply {
            isCompassEnabled = false
            isLogoEnabled = false
            // La atribución de OpenStreetMap, abajo a la derecha: a la izquierda la tapa el panel.
            isAttributionEnabled = true
            attributionGravity = Gravity.BOTTOM or Gravity.END
            with(density) { setAttributionMargins(0, 0, 8.dp.roundToPx(), 8.dp.roundToPx()) }
        }
        if (map == null) {
            ready.cameraPosition = CameraPosition.Builder()
                .target(overview.first.latLng())
                .zoom(overview.second - 1.2)
                .tilt(0.0)
                .bearing(0.0)
                .build()
        }
        ready.addOnCameraMoveListener { moved.intValue++ }
        ready.addOnCameraIdleListener { moved.intValue++ }
        val loaded = suspendCancellableCoroutine { done ->
            ready.setStyle(Style.Builder().fromUri(if (night) NIGHT_STYLE else DAY_STYLE)) { done.resume(it) }
        }
        // Sin rótulos: el mapa es el fondo, no la información.
        loaded.layers.filterIsInstance<SymbolLayer>().forEach { loaded.removeLayer(it) }
        loaded.addSource(GeoJsonSource(ROUTE_SOURCE))
        loaded.addSource(GeoJsonSource(RING_SOURCE))
        val gold = accent.toArgb()
        // Un halo ancho y la línea encima: se lee como una estela de luz.
        loaded.addLayer(LineLayer("$RING_SOURCE-halo", RING_SOURCE).withProperties(PropertyFactory.lineColor(gold), PropertyFactory.lineOpacity(0.25f), PropertyFactory.lineWidth(12f), PropertyFactory.lineBlur(6f)))
        loaded.addLayer(LineLayer("$RING_SOURCE-line", RING_SOURCE).withProperties(PropertyFactory.lineColor(gold), PropertyFactory.lineOpacity(0.8f), PropertyFactory.lineWidth(2f)))
        loaded.addLayer(LineLayer("$ROUTE_SOURCE-halo", ROUTE_SOURCE).withProperties(PropertyFactory.lineColor(gold), PropertyFactory.lineOpacity(0.35f), PropertyFactory.lineWidth(16f), PropertyFactory.lineBlur(6f)))
        loaded.addLayer(LineLayer("$ROUTE_SOURCE-line", ROUTE_SOURCE).withProperties(PropertyFactory.lineColor(gold), PropertyFactory.lineWidth(5f)))
        map = ready
        style = loaded
        moved.intValue++
    }

    val revealed = remember { Animatable(if (state.intro) 0f else state.path.size.toFloat()) }
    var shownStops by remember { mutableIntStateOf(if (state.intro) 0 else state.stops.size) }
    var focus by remember { mutableStateOf<Int?>(null) }
    var finale by remember { mutableStateOf<CameraPosition?>(null) }
    val width = CarTheme.metrics.widthDp
    val height = CarTheme.metrics.heightDp
    val overlayShare = CarTheme.metrics.overlayShare
    // Márgenes: izquierda, arriba, derecha, abajo, en píxeles.
    fun px(dp: Dp) = with(density) { dp.toPx().toDouble() }
    // La presentación: arriba el título y abajo la tarjeta de la parada, que crece
    // con la pantalla. Con márgenes fijos el sitio caía encima de la tarjeta.
    val introPadding = doubleArrayOf(px(FRAME_SIDE), px(height * INTRO_TOP_SHARE), px(FRAME_SIDE), px(height * INTRO_BOTTOM_SHARE))
    // Apilada, el panel tapa la parte de abajo y la barra de iconos va arriba a
    // la derecha; si no, el panel tapa la izquierda.
    val stacked = CarTheme.metrics.stacked
    val sheet = height * CarTheme.metrics.sheetShare
    val ringPadding = if (stacked) {
        doubleArrayOf(px(RING_SIDE), px(RING_SIDE), px(RING_RAIL), px(sheet + RING_SIDE))
    } else {
        doubleArrayOf(px(width * overlayShare + RING_SIDE), px(RING_SIDE), px(RING_RAIL), px(RING_SIDE))
    }
    val viewPadding = if (stacked) {
        // A la derecha y arriba, la barra de iconos o el "Cerrar": que no tapen paradas.
        doubleArrayOf(px(FRAME_SIDE / 2), px(FRAME_SIDE), px(RING_RAIL + FRAME_SIDE / 2), px(sheet + FRAME_SIDE / 2))
    } else {
        doubleArrayOf(px(width * overlayShare + FRAME_SIDE), px(FRAME_SIDE), px(FRAME_SIDE), px(FRAME_BOTTOM))
    }

    // La ruta, hasta donde se ha dibujado.
    LaunchedEffect(style, state.path) {
        val loaded = style ?: return@LaunchedEffect
        snapshotFlow { revealed.value.toInt() }.collect { upTo ->
            val visible = state.path.take(upTo.coerceAtLeast(0))
            loaded.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(
                FeatureCollection.fromFeatures(
                    if (visible.size >= 2) listOf(Feature.fromGeometry(LineString.fromLngLats(visible.map { it.point() }))) else emptyList(),
                ),
            )
        }
    }
    // El radio de acción: un anillo dorado, como el de un radar.
    val ringAt = state.ringCenter ?: state.me
    LaunchedEffect(style, ringAt, state.radiusMeters) {
        val loaded = style ?: return@LaunchedEffect
        val radius = state.radiusMeters
        loaded.getSourceAs<GeoJsonSource>(RING_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                if (ringAt != null && radius != null) {
                    listOf(Feature.fromGeometry(LineString.fromLngLats(ringOf(ringAt, radius, RING_POINTS).map { it.point() })))
                } else {
                    emptyList()
                },
            ),
        )
    }

    // El encuadre de conjunto: todas las paradas, el anillo entero o lo tocable.
    // También al cambiar la ventana de tamaño —Pleos pasa la app de un hueco a otro—.
    LaunchedEffect(map, state.stops, state.intro, state.candidates.map { it.id }, state.radiusMeters, state.ringCenter, width, height, stacked) {
        val ready = map ?: return@LaunchedEffect
        val ring = ringAt?.let { me -> state.radiusMeters?.let { ringOf(me, it, RING_FRAME_POINTS) } }
        val framed = when {
            state.stops.isNotEmpty() -> state.stops.map { it.position }
            ring != null -> ring
            else -> state.candidates.map { it.position } + state.start
        }
        // Con el anillo, casi cenital: inclinado, el círculo se veía elipse.
        val pitch = if (ring != null && state.stops.isEmpty()) RING_PITCH else FINAL_PITCH
        val radius = state.radiusMeters
        val camera = if (ringAt != null && radius != null && state.stops.isEmpty()) {
            // El zoom con el que el anillo llena el hueco a la derecha del panel.
            val freeWidth = if (stacked) width.value - RING_SIDE.value - RING_RAIL.value else width.value * (1 - overlayShare) - RING_SIDE.value - RING_RAIL.value
            val freeHeight = (if (stacked) height.value - sheet.value else height.value) - 2 * RING_SIDE.value
            val fit = minOf(freeWidth, freeHeight) * RING_FILL
            val zoom = ln(METERS_PER_DP_Z0 * cos(Math.toRadians(ringAt.latitude)) * fit / (2 * radius)) / ln(2.0)
            CameraPosition.Builder().target(ringAt.latLng()).zoom(zoom).tilt(pitch).bearing(0.0).padding(ringPadding).build()
        } else if (framed.size < 2) {
            CameraPosition.Builder()
                .target((state.stops.firstOrNull()?.position ?: state.start).latLng())
                .zoom(EMPTY_ZOOM).tilt(pitch).bearing(FINAL_BEARING).padding(viewPadding).build()
        } else {
            val pad = if (state.intro) introPadding else viewPadding
            // El encuadre, calculado con el mapa ya girado: el de MapLibre
            // centraba la caja sin girar y, en la ventana estrecha, las paradas
            // quedaban pegadas al borde.
            val dp = density.density.toDouble()
            val (center, zoom) = fitTurned(
                framed,
                FINAL_BEARING,
                freeWidthDp = width.value - (pad[0] + pad[2]) / dp,
                freeHeightDp = height.value - (pad[1] + pad[3]) / dp,
            )
            CameraPosition.Builder().target(center.latLng()).zoom(minOf(zoom, STOP_ZOOM))
                .tilt(pitch).bearing(FINAL_BEARING).padding(pad).build()
        }
        finale = camera
        if (!state.intro && camera != null) ready.fly(camera, REFRAME_MS)
    }

    LaunchedEffect(map, state.intro, state.stops, state.path) {
        val ready = map ?: return@LaunchedEffect
        if (!state.intro) {
            // La vista que se queda: todo a la vista, sin esperar a nada.
            focus = null
            shownStops = state.stops.size
            revealed.snapTo(state.path.size.toFloat())
            return@LaunchedEffect
        }
        delay(OPENING_MS)
        var previous = state.stops.first().position
        state.stops.forEachIndexed { index, stop ->
            val legEnd = state.legEnds.getOrElse(index) { state.path.size }.toFloat()
            launch { revealed.animateTo(legEnd, tween(LEG_MS.toInt(), easing = FastOutSlowInEasing)) }
            // La tarjeta de la parada anterior se va mientras la cámara viaja: si
            // no, decía "03" llegando a la cuarta.
            focus = null
            // De una parada a la siguiente, cerca y sin alejarse: un vuelo en
            // arco subía hasta ver media ciudad entre una y otra. Solo la primera
            // llega volando, desde el plano de conjunto.
            ready.fly(
                CameraPosition.Builder()
                    .target(stop.position.latLng())
                    .zoom(INTRO_STOP_ZOOM)
                    .tilt(STOP_PITCH)
                    .bearing(if (index == 0) FIRST_BEARING else previous.bearingTo(stop.position))
                    // El margen de la presentación, explícito: sin él la cámara se
                    // quedaba con el del panel y el sitio caía a un lado.
                    .padding(introPadding)
                    .build(),
                if (index == 0) FIRST_LEG_MS else LEG_MS,
                glide = index > 0,
            )
            shownStops = index + 1
            focus = index
            delay(STOP_HOLD_MS)
            previous = stop.position
        }
        focus = null
        ready.fly(
            finale ?: CameraPosition.Builder().target(overview.first.latLng()).zoom(overview.second).tilt(FINAL_PITCH).bearing(FINAL_BEARING).build(),
            FINALE_MS,
        )
        delay(CLOSING_MS)
        onIntroDone()
    }

    // Contar un sitio con la vista del itinerario delante: la cámara se acerca a
    // él girando un poco; al acabar, de vuelta al conjunto.
    LaunchedEffect(map, state.focus?.id, state.intro) {
        val ready = map ?: return@LaunchedEffect
        if (state.intro) return@LaunchedEffect
        val target = state.focus
        if (target == null) {
            focus = null
            finale?.let { ready.fly(it, UNFOCUS_MS) }
            return@LaunchedEffect
        }
        focus = state.stops.indexOfFirst { it.id == target.id }.takeIf { it >= 0 }
        ready.fly(
            CameraPosition.Builder()
                .target(target.position.latLng())
                .zoom(FOCUS_ZOOM)
                .tilt(STOP_PITCH)
                .bearing(ready.cameraPosition.bearing + FOCUS_TURN)
                .padding(viewPadding)
                .build(),
            FOCUS_MS,
        )
    }

    // El rastreo, como un radar: mientras se busca, una onda tras otra desde el
    // centro hasta el anillo, y los puntos de antes se apartan. Al terminar, la
    // onda en curso llega al borde y sale una última que va descubriendo los
    // puntos según los alcanza. Todo en una corrutina: dos animando la misma onda
    // se cancelaban.
    val wave = remember { Animatable(1f) }
    var hunting by remember { mutableStateOf(false) }
    var waving by remember { mutableStateOf(false) }
    val scanning by rememberUpdatedState(state.scanning && state.pickable)
    val hasDots by rememberUpdatedState(state.pickable && state.candidates.isNotEmpty() && state.radiusMeters != null)
    val reduced = Motion.reduced
    LaunchedEffect(Unit) {
        suspend fun sweep() {
            wave.snapTo(0f)
            wave.animateTo(1f, tween(WAVE_MS, easing = LinearEasing))
        }
        var shown = false
        snapshotFlow { scanning to hasDots }.collect { (searching, dots) ->
            when {
                searching -> {
                    hunting = true
                    waving = !reduced
                    if (reduced) snapshotFlow { scanning }.first { !it } else do sweep() while (scanning)
                    hunting = false
                    if (!reduced) sweep()
                    waving = false
                    shown = true
                }
                dots && !shown -> {
                    shown = true
                    if (!reduced) {
                        waving = true
                        sweep()
                        waving = false
                    }
                }
                !dots -> shown = false
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(ground)
            .then(
                if (state.intro) {
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onIntroDone)
                } else {
                    Modifier
                },
            ),
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        val ready = map
        if (ready != null) {
            // La onda, del tamaño del anillo con el zoom de ahora.
            if (waving && ringAt != null && state.radiusMeters != null) {
                val radius = state.radiusMeters!!
                Anchored(ready, moved, ringAt) {
                    @Suppress("UNUSED_VARIABLE") val tick = moved.intValue
                    val camera = ready.cameraPosition
                    val metersPerDp = METERS_PER_DP_Z0 * cos(Math.toRadians(ringAt.latitude)) / 2.0.pow(camera.zoom)
                    ScanWave(
                        diameter = (2 * radius / metersPerDp).coerceAtMost(WAVE_MAX_DP).dp,
                        squash = cos(Math.toRadians(camera.tilt)).toFloat(),
                        progress = { wave.value },
                    )
                }
            }
            // Lo de alrededor, pequeño y tocable. Buscando, los puntos de antes se apartan.
            if (!state.intro && !(state.pickable && hunting)) {
                state.candidates
                    .filter { candidate -> state.stops.none { it.id == candidate.id } && candidate.id != state.focus?.id }
                    .forEachIndexed { index, candidate ->
                        androidx.compose.runtime.key("cand-" + candidate.id) {
                            Anchored(ready, moved, candidate.position) {
                                if (state.pickable) {
                                    val reach = state.radiusMeters
                                    val delayMs = if (ringAt != null && reach != null) {
                                        (ringAt.distanceTo(candidate.position) / reach).coerceIn(0.0, 1.0) * WAVE_MS
                                    } else {
                                        index.coerceAtMost(DOT_STAGGER_MAX) * Motion.STAGGER_MS.toDouble()
                                    }
                                    CandidateDot(delayMs.toLong()) { onAddPlace(candidate) }
                                } else {
                                    CandidateMarker(candidate, adds = true) { onAddPlace(candidate) }
                                }
                            }
                        }
                    }
            }
            state.stops.take(shownStops).forEachIndexed { index, stop ->
                androidx.compose.runtime.key(stop.id) {
                    // En la vista de conjunto, pequeñas: se ven todas sin taparse.
                    Anchored(ready, moved, stop.position) { StopMarker(number = index + 1, place = stop, highlighted = focus == index, small = !state.intro) }
                }
            }
            state.me?.let { me -> Anchored(ready, moved, me) { MeMarker() } }
            state.focus?.takeIf { target -> state.stops.none { it.id == target.id } }?.let { target ->
                androidx.compose.runtime.key(target.id) {
                    Anchored(ready, moved, target.position) { StopMarker(number = null, place = target, highlighted = true) }
                }
            }
        }

        // Viñeta: la luz en el centro de lo que queda de mapa, no en el de la pantalla.
        val lightX = if (state.intro || stacked) 0.5f else overlayShare + (1 - overlayShare) / 2
        val lightY = if (stacked && !state.intro) (1 - CarTheme.metrics.sheetShare) / 2 else 0.5f
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val center = androidx.compose.ui.geometry.Offset(size.width * lightX, size.height * lightY)
                    val radius = maxOf(size.width * (1 - lightX), size.width * lightX, size.height) * VIGNETTE_REACH
                    val vignette = Brush.radialGradient(
                        0.55f to Color.Transparent,
                        1f to (if (night) Color.Black.copy(alpha = 0.75f) else ground.copy(alpha = 0.7f)),
                        center = center,
                        radius = radius,
                    )
                    onDrawBehind { drawRect(vignette) }
                },
        )

        val d = CarTheme.dimens
        if (!state.intro) {
            overlay()
            return@Box
        }
        Column(Modifier.align(Alignment.TopStart).padding(d.spaceL)) {
            Text(
                text = (state.city ?: state.stops.firstOrNull()?.city)
                    ?.let { if ('(' in it) it.substringAfter('(').substringBefore(')') else it }
                    ?.uppercase().orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            Text(
                text = state.summary,
                // En la ventana estrecha, en una línea: a dos, tapaba media pantalla.
                style = if (stacked) MaterialTheme.typography.displaySmall.let { it.copy(fontSize = it.fontSize * 0.68f) } else MaterialTheme.typography.displayMedium,
                maxLines = if (stacked) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        AnimatedContent(
            targetState = focus,
            transitionSpec = {
                (slideInHorizontally(tween(500)) { it / 2 } + fadeIn(tween(500))) togetherWith
                    (slideOutHorizontally(tween(400)) { -it / 3 } + fadeOut(tween(300)))
            },
            label = "parada",
            modifier = Modifier.align(Alignment.BottomStart).padding(d.spaceL),
        ) { index ->
            if (index != null) StopCaption(index + 1, state.stops[index], state.times.getOrNull(index).orEmpty(), compact = stacked)
        }
        Text(
            text = "▸▸",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(d.spaceL),
        )
    }
}

/**
 * [content] centrado en el punto de pantalla de [at]. La posición se calcula al
 * colocar, leyendo [moved]: al mover la cámara se recoloca sin recomponer.
 */
@Composable
private fun Anchored(map: MapLibreMap, moved: MutableIntState, at: Coordinates, content: @Composable () -> Unit) {
    Box(
        Modifier.layout { measurable, _ ->
            val placeable = measurable.measure(Constraints())
            layout(0, 0) {
                @Suppress("UNUSED_VARIABLE") val tick = moved.intValue
                val point: PointF = map.projection.toScreenLocation(at.latLng())
                placeable.place(point.x.toInt() - placeable.width / 2, point.y.toInt() - placeable.height / 2)
            }
        },
    ) { content() }
}

/**
 * Un movimiento de cámara que se puede esperar. Un vuelo sube en arco para
 * cambiar de escala; con [glide], se desliza a la misma altura, que es lo que
 * pide ir de una parada a la vecina.
 */
private suspend fun MapLibreMap.fly(camera: CameraPosition, durationMs: Long, glide: Boolean = false) = suspendCancellableCoroutine { done ->
    val callback = object : MapLibreMap.CancelableCallback {
        override fun onCancel() { if (done.isActive) done.resume(Unit) }
        override fun onFinish() { if (done.isActive) done.resume(Unit) }
    }
    if (glide) {
        easeCamera(CameraUpdateFactory.newCameraPosition(camera), durationMs.toInt(), true, callback)
    } else {
        animateCamera(CameraUpdateFactory.newCameraPosition(camera), durationMs.toInt(), callback)
    }
}

private fun Coordinates.latLng(): LatLng = LatLng(latitude, longitude)

private fun Coordinates.point(): Point = Point.fromLngLat(longitude, latitude)

/** Los estilos de OpenFreeMap: OpenStreetMap en vectorial, sin clave. */
private const val NIGHT_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val DAY_STYLE = "https://tiles.openfreemap.org/styles/positron"
private const val ROUTE_SOURCE = "sw-route"
private const val RING_SOURCE = "sw-ring"

private const val OPENING_MS = 1_200L
private const val LEG_MS = 2_600L
private const val STOP_HOLD_MS = 1_300L
private const val FINALE_MS = 2_800L
private const val CLOSING_MS = 1_500L
private const val STOP_ZOOM = 15.6
/** En la presentación, cada parada de cerca: la calle y lo que la rodea, no el barrio. */
private const val INTRO_STOP_ZOOM = 18.0
/** Lo que tapan, en la presentación, el título de arriba y la tarjeta de abajo. */
private const val INTRO_TOP_SHARE = 0.22f
private const val INTRO_BOTTOM_SHARE = 0.36f
private const val STOP_PITCH = 60.0
private const val FINAL_PITCH = 48.0
private const val FINAL_BEARING = 30.0
private const val FIRST_BEARING = 20.0
private const val FIRST_LEG_MS = 3_000L
private const val REFRAME_MS = 1_800L
private const val FOCUS_MS = 2_400L
private const val EMPTY_ZOOM = 15.0
private const val RING_POINTS = 96
private const val VIGNETTE_REACH = 0.9f
private const val RING_PITCH = 20.0
private const val RING_FILL = 0.92
private val RING_SIDE = 20.dp
private val RING_RAIL = 80.dp
/** Metros por dp en el ecuador a zoom 0, con teselas de 512. */
private const val METERS_PER_DP_Z0 = 78_271.517
private const val RING_FRAME_POINTS = 8
private const val MIN_SPAN_M = 150.0
private const val FIT_SLACK = 0.8
private const val UNFOCUS_MS = 2_000L
private const val FOCUS_ZOOM = 17.0
private const val FOCUS_TURN = 35.0
private val FRAME_TOP = 170.dp
private val FRAME_BOTTOM = 160.dp
private val FRAME_SIDE = 90.dp
private const val DOT_STAGGER_MAX = 20
private const val WAVE_MS = 1_400
private const val WAVE_MAX_DP = 4_000.0

/**
 * Un sitio en el mapa del inicio: un punto, sin nombre, que no tapa a los demás.
 * El elegido lleva el suyo. Aparecen uno tras otro, como un radar que los va
 * encontrando: [delayMs] es lo que espera el suyo.
 */
@Composable
private fun CandidateDot(delayMs: Long, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val reduced = Motion.reduced
    val pop = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        pop.animateTo(1f, Motion.reward(reduced))
    }
    Box(
        Modifier
            .size(grownDp(32f))
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = pop.value.coerceIn(0f, 1f)
            }
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(grownDp(20f)).clip(CircleShape).background(accent.copy(alpha = 0.25f)))
        Box(Modifier.size(grownDp(11f)).clip(CircleShape).border(1.5.dp, Color.Black.copy(alpha = 0.6f), CircleShape).background(accent))
    }
}

/**
 * Una onda del rastreo: un frente que va del centro al anillo, con una estela
 * que se apaga detrás. [progress] se lee al dibujar: no recompone por fotograma.
 */
@Composable
private fun ScanWave(diameter: Dp, squash: Float, progress: () -> Float) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(diameter)) {
        val t = progress()
        if (t >= 1f) return@Canvas
        val front = size.minDimension / 2 * t
        // Se ve casi hasta el borde y se apaga al llegar.
        val fade = 1f - t * t * t
        scale(scaleX = 1f, scaleY = squash) {
            if (front > 1f) {
                drawCircle(
                    Brush.radialGradient(0.4f to Color.Transparent, 1f to accent.copy(alpha = 0.18f * fade), center = center, radius = front),
                    radius = front,
                )
            }
            drawCircle(accent.copy(alpha = 0.3f * fade), radius = front, style = Stroke(width = 10.dp.toPx()))
            drawCircle(accent.copy(alpha = 0.9f * fade), radius = front, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

/** Puntos de un círculo de [meters] alrededor de [center], cerrado. */
private fun ringOf(center: Coordinates, meters: Double, points: Int): List<Coordinates> {
    val dLat = meters / 111_320.0
    val dLon = meters / (111_320.0 * cos(Math.toRadians(center.latitude)))
    return (0..points).map { i ->
        val a = 2 * Math.PI * i / points
        Coordinates(center.latitude + dLat * kotlin.math.sin(a), center.longitude + dLon * cos(a))
    }
}

/** Un sitio de alrededor: pequeño, con un "+" y el nombre, para añadirlo tocándolo. */
@Composable
private fun CandidateMarker(place: Place, adds: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = grownDp(10f), vertical = grownDp(6f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (adds) {
            Text("+", color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        } else {
            Box(Modifier.size(grownDp(8f)).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.width(grownDp(6f)))
        Text(
            Phrasebook.shortName(place),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = grownDp(150f)),
        )
    }
}

/** Donde está el coche: un punto con un halo que late. */
@Composable
private fun MeMarker() {
    val color = MaterialTheme.colorScheme.secondary
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "yo")
    val halo by pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.4f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(1_400), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "halo",
    )
    Box(Modifier.size(grownDp(40f)), contentAlignment = Alignment.Center) {
        // El latido en la capa de dibujo: el marcador no se recompone en cada fotograma.
        Box(
            Modifier
                .size(grownDp(22f))
                .graphicsLayer {
                    scaleX = halo
                    scaleY = halo
                }
                .clip(CircleShape)
                .background(color.copy(alpha = 0.25f)),
        )
        Box(Modifier.size(grownDp(14f)).clip(CircleShape).border(2.dp, Color.White, CircleShape).background(color))
    }
}

/** El marcador de una parada: su foto en un círculo, con el número, entrando con un rebote. */
@Composable
private fun StopMarker(number: Int?, place: Place, highlighted: Boolean, small: Boolean = false) {
    val accent = MaterialTheme.colorScheme.primary
    val reduced = Motion.reduced
    val pop = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, Motion.reward(reduced)) }
    // La parada de la que se habla crece; antes saltaba de golpe.
    val size by animateDpAsState(
        when {
            highlighted -> grownDp(76f)
            small -> grownDp(38f)
            else -> grownDp(56f)
        },
        Motion.settle(reduced),
        label = "parada",
    )
    Box(
        Modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        },
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .border(if (small && !highlighted) 2.dp else 3.dp, if (highlighted) accent else Color.White, CircleShape)
                .background(accent.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(place.name.take(1), color = Color.White, style = MaterialTheme.typography.titleLarge)
            if (place.imageUrl != null) {
                AsyncImage(model = place.imageUrl, contentDescription = place.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        if (number != null) {
            Box(
                Modifier.size(grownDp(if (small && !highlighted) 18f else 24f)).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StopCaption(number: Int, place: Place, time: String, compact: Boolean) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .widthIn(max = grownDp(520f))
            .clip(RoundedCornerShape(d.cardCorner))
            .background(Color.Black.copy(alpha = 0.6f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(d.cardCorner))
            .padding(d.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString().padStart(2, '0'),
            style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Spacer(Modifier.width(d.spaceS))
        Column {
            Text(
                place.name,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                time,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


/**
 * Centro y zoom con los que [points] llenan un hueco de [freeWidthDp] ×
 * [freeHeightDp] con el mapa girado [bearing] grados: el centro es el de lo que
 * ocupan en pantalla, no el de su caja de latitudes y longitudes.
 */
private fun fitTurned(points: List<Coordinates>, bearing: Double, freeWidthDp: Double, freeHeightDp: Double): Pair<Coordinates, Double> {
    val lat0 = points.map { it.latitude }.average()
    val lon0 = points.map { it.longitude }.average()
    val metersPerDegree = 111_320.0
    val shrink = cos(Math.toRadians(lat0))
    val b = Math.toRadians(bearing)
    // Metros este-norte, girados a la pantalla: x a la derecha, y hacia arriba.
    val turned = points.map {
        val x = (it.longitude - lon0) * metersPerDegree * shrink
        val y = (it.latitude - lat0) * metersPerDegree
        (x * cos(b) - y * kotlin.math.sin(b)) to (x * kotlin.math.sin(b) + y * cos(b))
    }
    val midX = (turned.minOf { it.first } + turned.maxOf { it.first }) / 2
    val midY = (turned.minOf { it.second } + turned.maxOf { it.second }) / 2
    val x = midX * cos(b) + midY * kotlin.math.sin(b)
    val y = -midX * kotlin.math.sin(b) + midY * cos(b)
    val center = Coordinates(lat0 + y / metersPerDegree, lon0 + x / (metersPerDegree * shrink))
    val spanX = (turned.maxOf { it.first } - turned.minOf { it.first }).coerceAtLeast(MIN_SPAN_M)
    val spanY = (turned.maxOf { it.second } - turned.minOf { it.second }).coerceAtLeast(MIN_SPAN_M)
    // Metros por dp a zoom z: METERS_PER_DP_Z0 · cos(lat) / 2^z. Con algo de
    // holgura, porque inclinado lo cercano se ve más grande.
    val scale = METERS_PER_DP_Z0 * shrink * FIT_SLACK
    val zoom = minOf(ln(scale * freeWidthDp.coerceAtLeast(1.0) / spanX), ln(scale * freeHeightDp.coerceAtLeast(1.0) / spanY)) / ln(2.0)
    return center to zoom
}

/** Centro y zoom que abarcan todos los puntos, sin necesitar el mapa ya montado. */
private fun overviewOf(points: List<Coordinates>): Pair<Coordinates, Double> {
    val lat = points.map { it.latitude }
    val lon = points.map { it.longitude }
    val center = Coordinates((lat.min() + lat.max()) / 2, (lon.min() + lon.max()) / 2)
    val span = max(lat.max() - lat.min(), (lon.max() - lon.min()) * cos(Math.toRadians(center.latitude)))
    val zoom = (ln(360.0 / (span.coerceAtLeast(0.002) * 2.2)) / ln(2.0)).coerceIn(10.0, 16.0)
    return center to zoom
}

/**
 * Una medida de marcador afinada en el móvil, que crece con la pantalla, pero
 * **menos que el panel**: con el mismo factor, en el coche los puntos medían
 * como botones y las paradas se tapaban unas a otras. El mapa necesita ver lo
 * que hay debajo.
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun grownDp(phone: Float): Dp = phone.dp * kotlin.math.sqrt(CarTheme.metrics.grown(1.dp).value)
