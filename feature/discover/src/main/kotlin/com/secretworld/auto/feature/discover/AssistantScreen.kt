package com.secretworld.auto.feature.discover

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secretworld.auto.core.designsystem.CarSecondaryButton
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.panelInsetsPadding
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.DrivingMode
import com.secretworld.auto.domain.Place
import kotlin.math.PI
import kotlin.math.sin

/**
 * El itinerario tal como se enseña: propuesto o en curso, con la hora de cada
 * parada y dónde se come.
 *
 * @param proposed si aún se espera el sí: la línea de tiempo lo dice.
 * @param current la parada a la que se va, o `-1` si aún no se empezó.
 */
data class TourUi(
    val stops: List<TourStopUi>,
    val proposed: Boolean,
    val current: Int,
    /** "Unas 4 horas y 40", "6 paradas". */
    val summary: String,
)

data class TourStopUi(
    val place: Place,
    /** "14:20". */
    val time: String,
    /** Tras esta parada se para a comer. */
    val lunchAfter: Boolean,
)

/**
 * La pantalla del asistente (SPEC §4.4): **la voz es la protagonista**, no el
 * mapa. Un núcleo que dice qué está haciendo —espera, oye, piensa, habla—, la
 * conversación como subtítulos y, al lado, el contexto de lo que se está
 * haciendo: el itinerario, lo que se buscó o lo que hay cerca.
 *
 * **Compacta** cuando comparte pantalla con el navegador, que es el uso normal
 * en marcha: solo la columna del asistente y la parada en curso. El mapa lo
 * pone el navegador de al lado.
 *
 * **Mínima** en la ventana flotante del móvil, durante una ruta: solo el
 * núcleo, lo último dicho y la parada en curso. Ahí solo se habla.
 *
 * Sin mapa: el que importa lo pone el navegador.
 */
@Composable
fun AssistantScreen(
    compact: Boolean,
    city: String?,
    offline: Boolean,
    mode: DrivingMode,
    voice: VoiceUi,
    /** Si la app está hablando: el núcleo late con la voz. */
    speaking: Boolean,
    nowPlaying: NowPlaying?,
    notices: List<PermissionNotice>,
    tour: TourUi?,
    search: SearchUi?,
    nearby: List<Place>,
    origin: Coordinates,
    navigation: NavigationUi?,
    /** Pedir que se ponga junto al navegador, si está a pantalla completa y hay uno. */
    showSplitHint: Boolean,
    shortcuts: List<SearchShortcut>,
    onSelect: (Place) -> Unit,
    onListen: (Place) -> Unit,
    onNavigate: (Place) -> Unit,
    onStopNarration: () -> Unit,
    onStopNavigation: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
    /** En la ventana flotante: lo mínimo, y solo voz. */
    mini: Boolean = false,
) {
    val d = CarTheme.dimens
    val background = MaterialTheme.colorScheme.background
    if (mini) {
        MiniAssistant(voice = voice, speaking = speaking, tour = tour, modifier = modifier.background(background))
        return
    }
    Row(
        modifier
            .fillMaxSize()
            .background(background)
            .panelInsetsPadding()
            .padding(d.spaceM),
        horizontalArrangement = Arrangement.spacedBy(d.spaceM),
    ) {
        AssistantColumn(
            compact = compact,
            city = city,
            offline = offline,
            voice = voice,
            speaking = speaking,
            nowPlaying = nowPlaying,
            notices = notices,
            tour = tour,
            navigation = navigation,
            showSplitHint = showSplitHint,
            onStopNarration = onStopNarration,
            onStopNavigation = onStopNavigation,
            modifier = if (compact) Modifier.fillMaxSize() else Modifier.weight(ASSISTANT_WEIGHT).fillMaxHeight(),
        )
        if (!compact) {
            ContextColumn(
                mode = mode,
                tour = tour,
                search = search,
                nearby = nearby,
                origin = origin,
                shortcuts = shortcuts,
                onSelect = onSelect,
                onListen = onListen,
                onNavigate = onNavigate,
                onClearSearch = onClearSearch,
                modifier = Modifier.weight(1f - ASSISTANT_WEIGHT).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun AssistantColumn(
    compact: Boolean,
    city: String?,
    offline: Boolean,
    voice: VoiceUi,
    speaking: Boolean,
    nowPlaying: NowPlaying?,
    notices: List<PermissionNotice>,
    tour: TourUi?,
    navigation: NavigationUi?,
    showSplitHint: Boolean,
    onStopNarration: () -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier,
) {
    val d = CarTheme.dimens
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = city ?: stringResource(R.string.finding_where),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (offline) {
                Text(
                    text = stringResource(R.string.offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = d.spaceXs),
                )
            }
        }

        notices.forEach {
            Spacer(Modifier.height(d.spaceS))
            PermissionCard(it)
        }

        // El núcleo se queda con el hueco que sobra: grande cuando no pasa nada,
        // más pequeño cuando hay itinerario y subtítulos que leer.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight) * if (compact && tour != null) 0.7f else 0.85f
            AssistantCore(status = voice.status, speaking = speaking, modifier = Modifier.size(side.coerceAtMost(MAX_CORE)))
        }
        // El estado, bajo el núcleo: se leen juntos.
        VoiceStatusChip(voice.status)
        Spacer(Modifier.height(d.spaceS))

        Subtitles(voice = voice, compact = compact)

        if (showSplitHint) {
            Spacer(Modifier.height(d.spaceS))
            Hint(stringResource(R.string.split_hint))
        }

        // En compacto, lo único del contexto que cabe: la parada en curso.
        if (compact && tour != null) {
            Spacer(Modifier.height(d.spaceS))
            CurrentStops(tour)
        }
        if (navigation != null) {
            Spacer(Modifier.height(d.spaceS))
            NavigationCard(navigation, onStopNavigation)
        }
        if (nowPlaying != null) {
            Spacer(Modifier.height(d.spaceS))
            NowPlayingCard(nowPlaying, onStopNarration)
        }
    }
}

/**
 * La ventana flotante: el núcleo, lo último que se dijo y la parada en curso.
 * No hay nada que tocar; se habla.
 */
@Composable
private fun MiniAssistant(voice: VoiceUi, speaking: Boolean, tour: TourUi?, modifier: Modifier) {
    val d = CarTheme.dimens
    val stop = tour?.stops?.getOrNull(tour.current.coerceAtLeast(0))
    Column(
        modifier.fillMaxSize().padding(d.spaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            AssistantCore(status = voice.status, speaking = speaking, modifier = Modifier.size(minOf(maxWidth, maxHeight)))
        }
        val line = voice.answer ?: voice.heard
        if (line != null) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (stop != null) {
            Text(
                text = stop.place.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * El núcleo: la presencia del asistente. Respira esperando, se agita oyendo,
 * gira pensando y late hablando. No dice nada que no diga el estado de la
 * cabecera, pero se entiende de reojo, que es como se mira en un coche.
 */
@Composable
fun AssistantCore(status: VoiceStatus, speaking: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    val color = when {
        speaking -> primary
        status == VoiceStatus.HEARING -> secondary
        status == VoiceStatus.THINKING || status == VoiceStatus.YOUR_TURN -> primary
        status == VoiceStatus.OFF -> idle.copy(alpha = 0.4f)
        // En reposo, la marca atenuada: vivo sin llamar la atención.
        else -> primary.copy(alpha = 0.55f)
    }
    val energy by animateFloatAsState(
        targetValue = when {
            speaking -> 1f
            status == VoiceStatus.HEARING -> 0.8f
            status == VoiceStatus.THINKING -> 0.6f
            status == VoiceStatus.YOUR_TURN -> 0.5f
            status == VoiceStatus.OFF -> 0f
            else -> 0.2f
        },
        animationSpec = tween(400),
        label = "energía",
    )
    val time = rememberInfiniteTransition(label = "núcleo")
    val phase by time.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "fase",
    )
    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val base = size.minDimension / 2
        val wave = (2 * PI * phase).toFloat()
        // El halo: un degradado que se expande con la energía.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.28f * (0.4f + energy)), Color.Transparent),
                center = center,
                radius = base,
            ),
            radius = base,
            center = center,
        )
        // Tres anillos que respiran desfasados; con energía, se separan.
        for (ring in 0 until RINGS) {
            val offset = ring * 2f * PI.toFloat() / RINGS
            val breathe = 1f + 0.04f * sin(wave + offset) + 0.10f * energy * sin(wave * 3 + offset)
            drawCircle(
                color = color.copy(alpha = 0.55f - ring * 0.15f),
                radius = base * (0.42f + ring * 0.14f) * breathe,
                center = center,
                style = Stroke(width = base * 0.018f * (1 + energy)),
            )
        }
        // Pensando, un arco que gira alrededor.
        if (status == VoiceStatus.THINKING && !speaking) {
            drawArc(
                color = color,
                startAngle = 360f * phase * 2,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(center.x - base * 0.8f, center.y - base * 0.8f),
                size = androidx.compose.ui.geometry.Size(base * 1.6f, base * 1.6f),
                style = Stroke(width = base * 0.03f),
            )
        }
        // El centro: sólido, late al hablar.
        val pulse = 1f + 0.12f * energy * sin(wave * 5)
        drawCircle(color = color, radius = base * 0.26f * pulse, center = center)
    }
}

/**
 * La conversación, como subtítulos: lo que se oyó y lo que se contesta. Ver lo
 * que se oyó es lo que permite corregirse cuando el reconocedor entendió otra cosa.
 */
@Composable
private fun Subtitles(voice: VoiceUi, compact: Boolean) {
    val d = CarTheme.dimens
    Column(Modifier.fillMaxWidth().animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (voice.heard != null) {
            Text(
                text = "“${voice.heard}”",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(d.spaceXs))
        }
        Text(
            text = voice.answer ?: stringResource(R.string.assistant_hint, voice.wakeWord),
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = if (voice.answer != null) FontWeight.Medium else FontWeight.Normal,
            color = if (voice.answer != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = if (compact) 3 else 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Hint(text: String) {
    val d = CarTheme.dimens
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardCorner))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
            .padding(d.spaceS),
    )
}

/** En compacto: la parada en curso y la siguiente, en una tarjeta. */
@Composable
private fun CurrentStops(tour: TourUi) {
    val d = CarTheme.dimens
    val now = tour.stops.getOrNull(tour.current.coerceAtLeast(0)) ?: return
    val next = tour.stops.getOrNull(tour.current.coerceAtLeast(0) + 1)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(d.spaceS),
    ) {
        Text(
            text = stringResource(if (tour.proposed) R.string.tour_proposed else R.string.tour_active) + " · " + tour.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StopLine(label = stringResource(R.string.tour_now), stop = now, strong = true)
        if (next != null) StopLine(label = stringResource(R.string.tour_next), stop = next, strong = false)
    }
}

@Composable
private fun StopLine(label: String, stop: TourStopUi, strong: Boolean) {
    val d = CarTheme.dimens
    Row(Modifier.fillMaxWidth().padding(top = d.spaceXs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LABEL_WIDTH),
        )
        Text(
            text = stop.place.name,
            style = if (strong) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(stop.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Lo de al lado del asistente, con pantalla completa: el itinerario con su mapa
 * si lo hay; si no, lo buscado; si no, lo cercano.
 */
@Composable
private fun ContextColumn(
    mode: DrivingMode,
    tour: TourUi?,
    search: SearchUi?,
    nearby: List<Place>,
    origin: Coordinates,
    shortcuts: List<SearchShortcut>,
    onSelect: (Place) -> Unit,
    onListen: (Place) -> Unit,
    onNavigate: (Place) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier,
) {
    val d = CarTheme.dimens
    val driving = mode == DrivingMode.DRIVING
    Column(modifier) {
        when {
            tour != null -> Timeline(tour, Modifier.fillMaxWidth().weight(1f))
            else -> {
                if (!driving && search == null && shortcuts.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(d.spaceXs)) {
                        shortcuts.forEach { CarSecondaryButton(label = it.label, onClick = it.onClick, compact = true) }
                    }
                    Spacer(Modifier.height(d.spaceS))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = search?.title ?: stringResource(if (driving) R.string.section_driving else R.string.section_parked),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (search != null) CarSecondaryButton(label = stringResource(R.string.search_clear), onClick = onClearSearch, compact = true)
                }
                Spacer(Modifier.height(d.spaceS))
                val shown = (search?.places ?: nearby).take(if (driving) DRIVING_ITEMS else PARKED_ITEMS)
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.nothing_around),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(d.spaceS),
                        userScrollEnabled = !driving,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        items(shown, key = { it.id }) { place ->
                            PlaceEntry(
                                place = place,
                                distance = formatDistance(origin.distanceTo(place.position)),
                                showDetails = !driving,
                                showNavigate = true,
                                dense = true,
                                hasStory = place.kind == null && place.summary != null,
                                onSelect = { onSelect(place) },
                                onListen = { onListen(place) },
                                onNavigate = { onNavigate(place) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * El itinerario como línea de tiempo: cada parada con su hora, la hecha
 * apagada, la de ahora destacada y la comida en su sitio.
 */
@Composable
private fun Timeline(tour: TourUi, modifier: Modifier) {
    val d = CarTheme.dimens
    Column(
        modifier
            .clip(RoundedCornerShape(d.cardCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(d.spaceS),
    ) {
        Text(
            text = stringResource(if (tour.proposed) R.string.tour_proposed else R.string.tour_active),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (tour.proposed) tour.summary + " · " + stringResource(R.string.tour_say_yes) else tour.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(d.spaceS))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(d.spaceXs)) {
            items(tour.stops.indices.toList(), key = { tour.stops[it].place.id }) { index ->
                val stop = tour.stops[index]
                val state = when {
                    tour.proposed -> StopState.UPCOMING
                    index < tour.current -> StopState.DONE
                    index == tour.current -> StopState.CURRENT
                    else -> StopState.UPCOMING
                }
                TimelineRow(number = index + 1, stop = stop, state = state)
                if (stop.lunchAfter) TimelineLunch()
            }
        }
    }
}

private enum class StopState { DONE, CURRENT, UPCOMING }

@Composable
private fun TimelineRow(number: Int, stop: TourStopUi, state: StopState) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(BADGE)
                .clip(CircleShape)
                .background(
                    when (state) {
                        StopState.CURRENT -> accent
                        StopState.DONE -> muted.copy(alpha = 0.3f)
                        StopState.UPCOMING -> accent.copy(alpha = 0.18f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = if (state == StopState.CURRENT) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(d.spaceS))
        Text(
            text = stop.place.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (state == StopState.CURRENT) FontWeight.Medium else FontWeight.Normal,
            color = if (state == StopState.DONE) muted else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(stop.time, style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun TimelineLunch() {
    val d = CarTheme.dimens
    Text(
        text = "· " + stringResource(R.string.tour_lunch),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = BADGE + d.spaceS),
    )
}

/** Cuánto del ancho se lleva la columna del asistente con pantalla completa. */
private const val ASSISTANT_WEIGHT = 0.4f
private const val RINGS = 3
private const val CYCLE_MS = 4_000
private const val DRIVING_ITEMS = 3
private const val PARKED_ITEMS = 12
private val MAX_CORE: Dp = 320.dp
private val LABEL_WIDTH: Dp = 88.dp
private val BADGE: Dp = 28.dp
