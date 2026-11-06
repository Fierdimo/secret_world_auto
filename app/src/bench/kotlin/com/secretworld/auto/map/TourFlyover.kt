package com.secretworld.auto.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.secretworld.auto.core.designsystem.Motion
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Phrasebook
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
    /** Se tocó un sitio de alrededor: añadirlo. */
    onAddPlace: (Place) -> Unit = {},
) {
    if (mapboxToken.isNullOrBlank() || (state.stops.isEmpty() && state.intro)) {
        LaunchedEffect(Unit) { onIntroDone() }
        Box(modifier.fillMaxSize().background(Color.Black)) { overlay() }
        return
    }
    MapboxOptions.accessToken = mapboxToken
    val accent = MaterialTheme.colorScheme.primary
    // Sin paradas —armándolo a mano—, el sitio de partida y lo de alrededor.
    val overview = if (state.stops.isEmpty()) state.start to EMPTY_ZOOM else overviewOf(state.stops.map { it.position })
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
    val height = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val introPadding = with(density) {
        com.mapbox.maps.EdgeInsets(FRAME_TOP.toPx().toDouble(), FRAME_SIDE.toPx().toDouble(), FRAME_BOTTOM.toPx().toDouble(), FRAME_SIDE.toPx().toDouble())
    }
    // Para el anillo: el panel a la izquierda y la barra de iconos a la derecha;
    // arriba y abajo, poco: en un móvil apaisado la altura es lo que falta.
    val ringPadding = with(density) {
        com.mapbox.maps.EdgeInsets(RING_SIDE.toPx().toDouble(), (width * OVERLAY_SHARE + RING_SIDE).toPx().toDouble(), RING_SIDE.toPx().toDouble(), RING_RAIL.toPx().toDouble())
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

    // Contar un sitio con la vista del itinerario delante: la cámara se acerca a
    // él girando un poco, como al presentar cada parada; al acabar, de vuelta al
    // conjunto. El sitio queda a la derecha del panel, no debajo.
    LaunchedEffect(state.focus?.id, state.intro) {
        if (state.intro) return@LaunchedEffect
        val target = state.focus
        if (target == null) {
            focus = null
            // El encuadre de conjunto ya tiene en cuenta el panel; el margen que
            // dejó el acercamiento lo desplazaría otra vez hacia la izquierda.
            // El del anillo lleva su propio margen y vuelve tal cual.
            val ringed = state.radiusMeters != null && state.stops.isEmpty()
            finale?.let { viewport.fly(if (ringed) it else it.toBuilder().padding(com.mapbox.maps.EdgeInsets(0.0, 0.0, 0.0, 0.0)).build(), UNFOCUS_MS) }
            return@LaunchedEffect
        }
        focus = state.stops.indexOfFirst { it.id == target.id }.takeIf { it >= 0 }
        viewport.fly(
            CameraOptions.Builder()
                .center(target.position.point())
                .zoom(FOCUS_ZOOM)
                .pitch(STOP_PITCH)
                .bearing((viewport.cameraState?.bearing ?: FINAL_BEARING) + FOCUS_TURN)
                .padding(viewPadding)
                .build(),
            FOCUS_MS,
        )
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
            com.mapbox.maps.extension.compose.MapEffect(state.stops, state.intro, state.candidates.map { it.id }, state.radiusMeters, state.ringCenter) { mapView ->
                // Sin paradas, lo que hay para elegir: que se vea todo lo tocable; con
                // un radio de acción, el anillo entero.
                val ring = (state.ringCenter ?: state.me)?.let { me -> state.radiusMeters?.let { ringOf(me, it, RING_FRAME_POINTS) } }
                val framed = when {
                    state.stops.isNotEmpty() -> state.stops.map { it.position }
                    ring != null -> ring
                    else -> state.candidates.map { it.position } + state.start
                }
                // Con el anillo, casi cenital: inclinado, el círculo se veía elipse.
                val pitch = if (ring != null && state.stops.isEmpty()) RING_PITCH else FINAL_PITCH
                val me = state.ringCenter ?: state.me
                val radius = state.radiusMeters
                val camera = if (me != null && radius != null && state.stops.isEmpty()) {
                    // El radio de acción, justo: el zoom con el que el anillo llena el
                    // hueco a la derecha del panel. Calculado, no pedido al mapa, que
                    // al montarse aún no tiene tamaño y encuadraba de más.
                    val freeWidth = width.value * (1 - OVERLAY_SHARE) - RING_SIDE.value - RING_RAIL.value
                    val freeHeight = height.value - 2 * RING_SIDE.value
                    val fit = minOf(freeWidth, freeHeight) * RING_FILL
                    val zoom = ln(METERS_PER_DP_Z0 * cos(Math.toRadians(me.latitude)) * fit / (2 * radius)) / ln(2.0)
                    CameraOptions.Builder()
                        .center(me.point())
                        .zoom(zoom)
                        .pitch(pitch)
                        .bearing(0.0)
                        .padding(ringPadding)
                        .build()
                } else if (framed.size < 2) {
                    // Una parada o ninguna no se encuadran: se centra, a la derecha del panel.
                    CameraOptions.Builder()
                        .center((state.stops.firstOrNull()?.position ?: state.start).point())
                        .zoom(EMPTY_ZOOM)
                        .pitch(pitch)
                        .bearing(FINAL_BEARING)
                        .padding(viewPadding)
                        .build()
                } else {
                    mapView.mapboxMap.cameraForCoordinates(
                        framed.map { it.point() },
                        CameraOptions.Builder().pitch(pitch).bearing(FINAL_BEARING).build(),
                        if (state.intro) introPadding else viewPadding,
                        STOP_ZOOM,
                        null,
                    )
                }
                finale = camera
                // En la vista que se queda, cada cambio se reencuadra con suavidad.
                // Un vuelo, no un deslizamiento: al cambiar de itinerario en la lista
                // la cámara sube y baja sobre el siguiente, como en un videojuego.
                if (!state.intro) viewport.flyTo(camera, MapAnimationOptions.mapAnimationOptions { duration(REFRAME_MS) })
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
            // El radio de acción: un anillo dorado, como el de un radar.
            (state.ringCenter ?: state.me)?.let { me ->
                state.radiusMeters?.let { radius ->
                    val circle = ringOf(me, radius, RING_POINTS).map { it.point() }
                    PolylineAnnotation(points = circle) {
                        lineColor = accent.copy(alpha = 0.25f)
                        lineWidth = 12.0
                        lineBlur = 6.0
                        lineEmissiveStrength = 1.0
                    }
                    PolylineAnnotation(points = circle) {
                        lineColor = accent.copy(alpha = 0.8f)
                        lineWidth = 2.0
                        lineEmissiveStrength = 1.0
                    }
                }
            }
            // Lo de alrededor, pequeño y tocable: tocarlo lo añade al itinerario.
            if (!state.intro) {
                state.candidates
                    .filter { candidate -> state.stops.none { it.id == candidate.id } && candidate.id != state.focus?.id }
                    .forEachIndexed { index, candidate ->
                        androidx.compose.runtime.key("cand-" + candidate.id) {
                            ViewAnnotation(
                                options = viewAnnotationOptions {
                                    geometry(candidate.position.point())
                                    // Los puntos del inicio nunca se ocultan: son pequeños.
                                    allowOverlap(state.pickable)
                                },
                            ) {
                                if (state.pickable) {
                                    CandidateDot(index) { onAddPlace(candidate) }
                                } else {
                                    CandidateMarker(candidate, adds = true) { onAddPlace(candidate) }
                                }
                            }
                        }
                    }
            }
            // El coche: un punto que late.
            state.me?.let { me ->
                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(me.point())
                        allowOverlap(true)
                    },
                ) {
                    MeMarker()
                }
            }
            // Lo que se cuenta y no es una parada: su marcador, solo mientras se cuenta.
            state.focus?.takeIf { target -> state.stops.none { it.id == target.id } }?.let { target ->
                androidx.compose.runtime.key(target.id) {
                    ViewAnnotation(
                        options = viewAnnotationOptions {
                            geometry(target.position.point())
                            allowOverlap(true)
                        },
                    ) {
                        StopMarker(number = null, place = target, highlighted = true)
                    }
                }
            }
        }

        // Viñeta: los bordes oscuros llevan la mirada al centro, como en cine. Con
        // el panel a la izquierda, al centro de lo que queda de mapa, no al de la
        // pantalla: si no, la luz caía en el borde del panel.
        val lightX = if (state.intro) 0.5f else OVERLAY_SHARE + (1 - OVERLAY_SHARE) / 2
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val center = androidx.compose.ui.geometry.Offset(size.width * lightX, size.height / 2)
                    val radius = maxOf(size.width * (1 - lightX), size.width * lightX, size.height) * VIGNETTE_REACH
                    val vignette = Brush.radialGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.75f),
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

/**
 * Un sitio en el mapa del inicio: un punto, sin nombre, que no tapa a los demás.
 * El elegido lleva el suyo. Aparecen uno tras otro, como un radar que los va
 * encontrando; [order] es su turno.
 */
@Composable
private fun CandidateDot(order: Int, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val reduced = Motion.reduced
    val pop = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(order.coerceAtMost(DOT_STAGGER_MAX) * Motion.STAGGER_MS)
        pop.animateTo(1f, Motion.reward(reduced))
    }
    Box(
        Modifier
            .size(32.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = pop.value.coerceIn(0f, 1f)
            }
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(accent.copy(alpha = 0.25f)))
        Box(Modifier.size(11.dp).clip(CircleShape).border(1.5.dp, Color.Black.copy(alpha = 0.6f), CircleShape).background(accent))
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
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (adds) {
            Text("+", color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            Phrasebook.shortName(place),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
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
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        // El latido en la capa de dibujo: el marcador no se recompone en cada fotograma.
        Box(
            Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = halo
                    scaleY = halo
                }
                .clip(CircleShape)
                .background(color.copy(alpha = 0.25f)),
        )
        Box(Modifier.size(14.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape).background(color))
    }
}

/** El marcador de una parada: su foto en un círculo, con el número, entrando con un rebote. */
@Composable
private fun StopMarker(number: Int?, place: Place, highlighted: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val reduced = Motion.reduced
    val pop = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, Motion.reward(reduced)) }
    // La parada de la que se habla crece; antes saltaba de golpe.
    val size by animateDpAsState(if (highlighted) 76.dp else 56.dp, Motion.settle(reduced), label = "parada")
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
                .border(3.dp, if (highlighted) accent else Color.White, CircleShape)
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
                Modifier.size(24.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
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
private const val REFRAME_MS = 1_800L
private const val FOCUS_MS = 2_400L
private const val EMPTY_ZOOM = 15.0
private const val RING_POINTS = 96
/** Hasta dónde llega la luz de la viñeta, respecto al lado más largo. */
private const val VIGNETTE_REACH = 0.9f
private const val RING_PITCH = 20.0
/** Cuánto del hueco libre ocupa el anillo: el resto, aire alrededor. */
private const val RING_FILL = 0.92
private val RING_SIDE = 20.dp
/** Lo que ocupa la barra de iconos de la derecha. */
private val RING_RAIL = 80.dp
/** Metros por dp en el ecuador a zoom 0, con las teselas de 512 de Mapbox. */
private const val METERS_PER_DP_Z0 = 78_271.517
private const val RING_FRAME_POINTS = 8
private const val UNFOCUS_MS = 2_000L
private const val FOCUS_ZOOM = 17.0
private const val FOCUS_TURN = 35.0
/** Lo que ocupa la capa de la izquierda —línea de tiempo y asistente—: el encuadre la esquiva. */
private const val OVERLAY_SHARE = 0.40f
private val FRAME_TOP = 170.dp
private val FRAME_BOTTOM = 160.dp
private val FRAME_SIDE = 90.dp

/** Hasta cuántos puntos se escalonan: con decenas, los últimos esperarían demasiado. */
private const val DOT_STAGGER_MAX = 20
