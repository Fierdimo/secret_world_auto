package com.secretworld.auto.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.ThemeValue
import com.mapbox.maps.extension.compose.style.standard.rememberStandardStyleState
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Place
import com.secretworld.auto.feature.discover.FlyoverState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max

/**
 * El itinerario sobre el mapa. Primero, **la presentación**, como la de un
 * nivel en un videojuego: un mapa nocturno y simplificado —sin etiquetas,
 * edificios en 3D—, la cámara que vuela de parada en parada mientras la ruta
 * se va dibujando, y cada parada que aparece con su foto. Tocar la salta.
 *
 * Después, **la vista se queda**: las paradas y la ruta enteras, con [overlay]
 * encima —la línea de tiempo y el asistente—. Si el itinerario cambia ("empieza
 * por el castillo"), cambia aquí mismo: los marcadores se renumeran, la ruta se
 * rehace y la cámara se reencuadra.
 *
 * Sin clave de Mapbox no hay mapa: queda [overlay] sobre fondo oscuro.
 */
@OptIn(MapboxExperimental::class)
@Composable
fun TourFlyover(
    state: FlyoverState,
    mapboxToken: String?,
    onIntroDone: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    if (mapboxToken.isNullOrBlank() || state.stops.isEmpty()) {
        LaunchedEffect(Unit) { onIntroDone() }
        Box(modifier.fillMaxSize().background(Color.Black)) { overlay() }
        return
    }
    MapboxOptions.accessToken = mapboxToken
    val accent = MaterialTheme.colorScheme.primary
    val overview = overviewOf(state.stops.map { it.position })
    val viewport = rememberMapViewportState {
        setCameraOptions {
            center(overview.first.point())
            zoom(overview.second - 1.2)
            pitch(0.0)
            bearing(0.0)
        }
    }
    val revealed = remember { Animatable(if (state.intro) 0f else state.path.size.toFloat()) }
    var shownStops by remember { mutableIntStateOf(if (state.intro) 0 else state.stops.size) }
    var focus by remember { mutableStateOf<Int?>(null) }
    // El encuadre de conjunto lo calcula el propio mapa: el que abarca todas las
    // paradas con la inclinación final. Durante la presentación deja sitio al
    // título y a la tarjeta; después, a la línea de tiempo, a la izquierda.
    var finale by remember { mutableStateOf<CameraOptions?>(null) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val width = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val introPadding = with(density) {
        com.mapbox.maps.EdgeInsets(FRAME_TOP.toPx().toDouble(), FRAME_SIDE.toPx().toDouble(), FRAME_BOTTOM.toPx().toDouble(), FRAME_SIDE.toPx().toDouble())
    }
    val viewPadding = with(density) {
        com.mapbox.maps.EdgeInsets(FRAME_SIDE.toPx().toDouble(), (width * OVERLAY_SHARE + FRAME_SIDE).toPx().toDouble(), FRAME_BOTTOM.toPx().toDouble(), FRAME_SIDE.toPx().toDouble())
    }

    LaunchedEffect(state.intro, state.stops, state.path) {
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
            viewport.fly(
                CameraOptions.Builder()
                    .center(stop.position.point())
                    .zoom(STOP_ZOOM)
                    .pitch(STOP_PITCH)
                    .bearing(if (index == 0) FIRST_BEARING else previous.bearingTo(stop.position))
                    .build(),
                if (index == 0) FIRST_LEG_MS else LEG_MS,
            )
            shownStops = index + 1
            focus = index
            delay(STOP_HOLD_MS)
            previous = stop.position
        }
        focus = null
        viewport.fly(
            finale ?: CameraOptions.Builder()
                .center(overview.first.point())
                .zoom(overview.second)
                .pitch(FINAL_PITCH)
                .bearing(FINAL_BEARING)
                .build(),
            FINALE_MS,
        )
        delay(CLOSING_MS)
        onIntroDone()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (state.intro) {
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onIntroDone)
                } else {
                    Modifier
                },
            ),
    ) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = viewport,
            style = {
                MapboxStandardStyle(
                    standardStyleState = rememberStandardStyleState {
                        configurationsState.apply {
                            lightPreset = LightPresetValue.NIGHT
                            theme = ThemeValue.MONOCHROME
                            showPlaceLabels = BooleanValue(false)
                            showRoadLabels = BooleanValue(false)
                            showPointOfInterestLabels = BooleanValue(false)
                            showTransitLabels = BooleanValue(false)
                        }
                    },
                )
            },
            scaleBar = {},
            compass = {},
        ) {
            com.mapbox.maps.extension.compose.MapEffect(state.stops, state.intro) { mapView ->
                val camera = mapView.mapboxMap.cameraForCoordinates(
                    state.stops.map { it.position.point() },
                    CameraOptions.Builder().pitch(FINAL_PITCH).bearing(FINAL_BEARING).build(),
                    if (state.intro) introPadding else viewPadding,
                    STOP_ZOOM,
                    null,
                )
                finale = camera
                // En la vista que se queda, cada cambio se reencuadra con suavidad.
                if (!state.intro) viewport.easeTo(camera, MapAnimationOptions.mapAnimationOptions { duration(REFRAME_MS) })
            }
            val visible = state.path.take(revealed.value.toInt().coerceAtLeast(0))
            if (visible.size >= 2) {
                val line = visible.map { it.point() }
                // Un halo ancho y la línea encima: se lee como una estela de luz.
                PolylineAnnotation(points = line) {
                    lineColor = accent.copy(alpha = 0.35f)
                    lineWidth = 16.0
                    lineBlur = 6.0
                    lineEmissiveStrength = 1.0
                }
                PolylineAnnotation(points = line) {
                    lineColor = accent
                    lineWidth = 5.0
                    lineEmissiveStrength = 1.0
                }
            }
            state.stops.take(shownStops).forEachIndexed { index, stop ->
                // Por id: al reordenar, cada marcador se renumera sin volver a nacer.
                androidx.compose.runtime.key(stop.id) {
                    ViewAnnotation(
                        options = viewAnnotationOptions {
                            geometry(stop.position.point())
                            allowOverlap(true)
                        },
                    ) {
                        StopMarker(number = index + 1, place = stop, highlighted = focus == index)
                    }
                }
            }
        }

        // Viñeta: los bordes oscuros llevan la mirada al centro, como en cine.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))),
        )

        val d = CarTheme.dimens
        if (!state.intro) {
            overlay()
            return@Box
        }
        Column(Modifier.align(Alignment.TopStart).padding(d.spaceL)) {
            Text(
                // La ciudad, sin el barrio que Wikipedia pone delante: "Blas de
                // Lezo (Cartagena de Indias)" → "Cartagena de Indias".
                text = state.stops.firstOrNull()?.city
                    ?.let { if ('(' in it) it.substringAfter('(').substringBefore(')') else it }
                    ?.uppercase().orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            Text(
                text = state.summary,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        // La parada en la que está la cámara, entrando desde la derecha.
        AnimatedContent(
            targetState = focus,
            transitionSpec = {
                (slideInHorizontally(tween(500)) { it / 2 } + fadeIn(tween(500))) togetherWith
                    (slideOutHorizontally(tween(400)) { -it / 3 } + fadeOut(tween(300)))
            },
            label = "parada",
            modifier = Modifier.align(Alignment.BottomStart).padding(d.spaceL),
        ) { index ->
            if (index != null) StopCaption(index + 1, state.stops[index], state.times.getOrNull(index).orEmpty())
        }

        Text(
            text = "▸▸",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(d.spaceL),
        )
    }
}

/** El marcador de una parada: su foto en un círculo, con el número, entrando con un rebote. */
@Composable
private fun StopMarker(number: Int, place: Place, highlighted: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    val size = if (highlighted) 76.dp else 56.dp
    Box(Modifier.scale(pop.value), contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .border(3.dp, if (highlighted) accent else Color.White, CircleShape)
                .background(accent.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(place.name.take(1), color = Color.White, style = MaterialTheme.typography.titleLarge)
            if (place.imageUrl != null) {
                AsyncImage(model = place.imageUrl, contentDescription = place.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StopCaption(number: Int, place: Place, time: String) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(d.cardCorner))
            .background(Color.Black.copy(alpha = 0.6f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(d.cardCorner))
            .padding(d.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString().padStart(2, '0'),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Spacer(Modifier.width(d.spaceS))
        Column {
            Text(place.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(time, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

/** Un vuelo de cámara que se puede esperar. */
private suspend fun MapViewportState.fly(camera: CameraOptions, durationMs: Long) = suspendCancellableCoroutine { done ->
    flyTo(camera, MapAnimationOptions.mapAnimationOptions { duration(durationMs) }) { done.resume(Unit) }
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

private fun Coordinates.point(): Point = Point.fromLngLat(longitude, latitude)

private const val OPENING_MS = 1_200L
private const val LEG_MS = 2_600L
private const val STOP_HOLD_MS = 1_300L
private const val FINALE_MS = 2_800L
private const val CLOSING_MS = 1_500L
private const val STOP_ZOOM = 15.6
private const val STOP_PITCH = 62.0
private const val FINAL_PITCH = 48.0
private const val FINAL_BEARING = 30.0
private const val FIRST_BEARING = 20.0
private const val FIRST_LEG_MS = 3_000L
private const val REFRAME_MS = 1_200L
/** Lo que ocupa la capa de la izquierda —línea de tiempo y asistente—: el encuadre la esquiva. */
private const val OVERLAY_SHARE = 0.40f
private val FRAME_TOP = 170.dp
private val FRAME_BOTTOM = 160.dp
private val FRAME_SIDE = 90.dp
