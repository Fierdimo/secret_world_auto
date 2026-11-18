package com.secretworld.auto.feature.discover

import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.secretworld.auto.core.designsystem.Motion
import com.secretworld.auto.core.designsystem.entering
import com.secretworld.auto.core.designsystem.panelInsetsPadding
import com.secretworld.auto.core.designsystem.rememberListEntrance
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.DrivingMode
import com.secretworld.auto.domain.Place
import kotlin.math.PI
import kotlin.math.cos
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
    /** "6 paradas · 4 horas y 40". */
    val summary: String,
    /** "6 paradas". */
    val title: String = summary,
    /** "4 horas y 40". */
    val duration: String = "",
    /** Confirmado y guardado, a falta de empezar. */
    val saved: Boolean = false,
)

data class TourStopUi(
    val place: Place,
    /** "14:20": cuándo se llega. */
    val time: String,
    /** Tras esta parada se para a comer. */
    val lunchAfter: Boolean,
    /** "15:35": cuándo se sigue. */
    val until: String = "",
    /** "1 h 15": cuánto se queda uno. Un itinerario no es pasar por delante. */
    val stay: String = "",
)

/**
 * La pantalla del asistente (SPEC §4.4): **la voz es la protagonista**. Un
 * núcleo que dice qué está haciendo —espera, oye, piensa, habla—, la
 * conversación como subtítulos y sugerencias que se pueden tocar; al lado, el
 * contexto con fotos: el itinerario, lo que se buscó o lo que hay cerca.
 *
 * **Compacta** cuando comparte pantalla o la ventana es estrecha: la columna
 * del asistente con lo justo debajo —la parada en curso, o los dos sitios más
 * cercanos—. **Mínima** en la ventana flotante del móvil, durante una ruta:
 * solo se habla.
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
    /** Búsquedas de un toque: "Restaurantes", "Gasolina". */
    shortcuts: List<SearchShortcut>,
    /** Lo que se le puede pedir, para tocarlo en vez de decirlo. */
    suggestions: List<SearchShortcut>,
    onSelect: (Place) -> Unit,
    onListen: (Place) -> Unit,
    onNavigate: (Place) -> Unit,
    onStopNarration: () -> Unit,
    onStopNavigation: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
    /** El "+" de cada sitio: al itinerario, o a uno nuevo que empieza por él. */
    onAdd: ((Place) -> Unit)? = null,
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
        horizontalArrangement = Arrangement.spacedBy(d.spaceL),
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
            nearby = nearby,
            origin = origin,
            navigation = navigation,
            suggestions = suggestions,
            onListen = onListen,
            onSelect = onSelect,
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
                onAdd = onAdd,
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
    nearby: List<Place>,
    origin: Coordinates,
    navigation: NavigationUi?,
    suggestions: List<SearchShortcut>,
    onListen: (Place) -> Unit,
    onSelect: (Place) -> Unit,
    onStopNarration: () -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier,
) {
    val d = CarTheme.dimens
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        WhereHeader(city = city, offline = offline)

        notices.forEach {
            Spacer(Modifier.height(d.spaceS))
            PermissionCard(it)
        }

        // El núcleo se queda con el hueco que sobra.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight) * 0.9f
            AssistantCore(status = voice.status, speaking = speaking, modifier = Modifier.size(side.coerceAtMost(MAX_CORE)))
        }
        VoiceStatusChip(voice.status)
        Spacer(Modifier.height(d.spaceS))
        Subtitles(voice = voice, compact = compact)

        // Lo que se le puede pedir, cuando no está ya contestando algo.
        if (voice.answer == null && voice.heard == null && suggestions.isNotEmpty()) {
            Spacer(Modifier.height(d.spaceS))
            SuggestionRow(suggestions)
        }

        // En compacto no hay columna de al lado: lo justo, debajo.
        if (compact) {
            when {
                tour != null -> {
                    Spacer(Modifier.height(d.spaceS))
                    CurrentStops(tour)
                }
                nowPlaying == null && nearby.isNotEmpty() -> {
                    Spacer(Modifier.height(d.spaceS))
                    NearbyRows(nearby.take(COMPACT_NEARBY), origin, onSelect, onListen)
                }
            }
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
 * Dónde se está: la ciudad grande y el barrio pequeño encima. Wikipedia los da
 * juntos —"Blas de Lezo (Cartagena de Indias)"— y en una línea se cortaba
 * justo lo que importa.
 */
@Composable
internal fun WhereHeader(city: String?, offline: Boolean) {
    val d = CarTheme.dimens
    val name = city ?: stringResource(R.string.finding_where)
    val (area, town) = splitArea(name)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (area ?: stringResource(R.string.brand)).uppercase(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (offline) {
                Text(
                    text = "  ·  " + stringResource(R.string.offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Spacer(Modifier.height(d.spaceXs / 2))
        Text(
            text = town,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Blas de Lezo (Cartagena de Indias)" → barrio y ciudad. */
private fun splitArea(name: String): Pair<String?, String> {
    val open = name.indexOf('(')
    val close = name.lastIndexOf(')')
    if (open <= 0 || close <= open) return null to name
    return name.substring(0, open).trim() to name.substring(open + 1, close).trim()
}

/** Sugerencias de lo que se le puede pedir: tocar una es como decirla. */
@Composable
internal fun SuggestionRow(suggestions: List<SearchShortcut>) {
    val d = CarTheme.dimens
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(d.spaceXs),
    ) {
        suggestions.forEach { suggestion ->
            Text(
                text = "“${suggestion.label}”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .clickable(onClick = suggestion.onClick)
                    .padding(horizontal = d.spaceS, vertical = d.spaceXs),
            )
        }
    }
}

/** En compacto, sin itinerario: los sitios más cercanos con su miniatura. */
@Composable
private fun NearbyRows(places: List<Place>, origin: Coordinates, onSelect: (Place) -> Unit, onListen: (Place) -> Unit) {
    val d = CarTheme.dimens
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(d.spaceXs)) {
        places.forEach { place ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(d.cardCorner))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(place) }
                    .padding(d.spaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaceThumb(place, THUMB)
                Spacer(Modifier.width(d.spaceS))
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        formatDistance(origin.distanceTo(place.position)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (place.kind == null && place.summary != null) RoundAction(Glyph.LISTEN, { onListen(place) }, filled = true)
            }
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
 * gira pensando y late hablando. Se entiende de reojo, que es como se mira en
 * un coche.
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
        else -> primary.copy(alpha = 0.6f)
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
                colors = listOf(color.copy(alpha = 0.30f * (0.4f + energy)), Color.Transparent),
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
        // Puntos que orbitan despacio: lo que hace que parezca vivo en reposo.
        for (dot in 0 until ORBITERS) {
            val angle = wave * (if (dot % 2 == 0) 1f else -0.7f) + dot * 2f * PI.toFloat() / ORBITERS
            val radius = base * (0.62f + 0.08f * dot % 3)
            drawCircle(
                color = secondary.copy(alpha = 0.35f + 0.4f * energy),
                radius = base * 0.022f,
                center = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle)),
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
        // El centro: un degradado de la marca, late al hablar.
        val pulse = 1f + 0.12f * energy * sin(wave * 5)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, color.copy(alpha = 0.75f), secondary.copy(alpha = 0.5f)),
                center = center,
                radius = base * 0.26f * pulse,
            ),
            radius = base * 0.26f * pulse,
            center = center,
        )
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
        // Solo lo que contesta: un subtítulo de instrucciones no sirve de ayuda.
        voice.answer?.let { said ->
            Text(
                text = said,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 3 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** En compacto: la parada en curso y la siguiente, con su foto. */
@Composable
internal fun CurrentStops(tour: TourUi) {
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
        PlaceThumb(stop.place, if (strong) THUMB else THUMB * 0.75f)
        Spacer(Modifier.width(d.spaceS))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stop.place.name,
                style = if (strong) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (stop.until.isNotEmpty()) "${stop.time} – ${stop.until}" else stop.time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Lo de al lado del asistente, con pantalla completa: el itinerario si lo hay;
 * si no, lo buscado; si no, lo cercano. Siempre con fotos.
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
    onAdd: ((Place) -> Unit)? = null,
) {
    val d = CarTheme.dimens
    val driving = mode == DrivingMode.DRIVING
    Column(modifier) {
        // Solo un recorrido en marcha: lo propuesto o guardado se ve en el mapa,
        // y aquí era una vista de paso, sin manera de cerrarla.
        if (tour != null && !tour.proposed && !tour.saved) {
            TourPanel(tour, Modifier.fillMaxSize())
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = search?.title ?: stringResource(if (driving) R.string.section_driving else R.string.section_parked),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (search != null) CarSecondaryButton(label = stringResource(R.string.search_clear), onClick = onClearSearch, compact = true)
        }
        if (!driving && search == null && shortcuts.isNotEmpty()) {
            Spacer(Modifier.height(d.spaceS))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(d.spaceXs)) {
                shortcuts.forEach { CarSecondaryButton(label = it.label, onClick = it.onClick, compact = true) }
            }
        }
        Spacer(Modifier.height(d.spaceS))
        val shown = (search?.places ?: nearby).take(if (driving) DRIVING_ITEMS else PARKED_ITEMS)
        if (shown.isEmpty()) {
            Text(
                text = stringResource(R.string.nothing_around),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // Dos filas de tarjetas a la vista: la tercera asomando dice que hay más.
            val cardHeight = (maxHeight - d.spaceS) / 2.15f
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(d.spaceS),
                verticalArrangement = Arrangement.spacedBy(d.spaceS),
                userScrollEnabled = !driving,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { place ->
                    PhotoCard(
                        place = place,
                        distance = formatDistance(origin.distanceTo(place.position)),
                        hasStory = place.kind == null && place.summary != null,
                        onSelect = { onSelect(place) },
                        onListen = { onListen(place) },
                        onNavigate = { onNavigate(place) },
                        modifier = Modifier.fillMaxWidth().height(cardHeight),
                        onAdd = onAdd?.let { add -> { add(place) } },
                    )
                }
            }
        }
    }
}

/**
 * El itinerario: la foto de la parada que toca como portada, y debajo la línea
 * de tiempo, con cada parada unida a la siguiente, su hora y la comida en su sitio.
 */
@Composable
private fun TourPanel(tour: TourUi, modifier: Modifier) {
    val d = CarTheme.dimens
    val focus = tour.stops.getOrNull(tour.current.coerceAtLeast(0)) ?: tour.stops.first()
    Column(
        modifier
            .clip(RoundedCornerShape(d.cardCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxWidth().weight(HERO_WEIGHT)) {
            PlaceImage(focus.place, Modifier.fillMaxSize(), initial = false)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(d.spaceS)) {
                Text(
                    text = stringResource(if (tour.proposed) R.string.tour_proposed else R.string.tour_active).uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = tour.summary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f - HERO_WEIGHT).padding(horizontal = d.spaceS),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = d.spaceS),
        ) {
            items(tour.stops.size) { index ->
                val stop = tour.stops[index]
                val state = when {
                    tour.proposed -> StopState.UPCOMING
                    index < tour.current -> StopState.DONE
                    index == tour.current -> StopState.CURRENT
                    else -> StopState.UPCOMING
                }
                TimelineRow(stop, state, first = index == 0, last = index == tour.stops.lastIndex && !stop.lunchAfter)
                if (stop.lunchAfter) TimelineLunch(last = index == tour.stops.lastIndex)
            }
        }
    }
}

private enum class StopState { DONE, CURRENT, UPCOMING }

@Composable
private fun TimelineRow(stop: TourStopUi, state: StopState, first: Boolean, last: Boolean) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        // La raíl: une esta parada con la anterior y con la siguiente.
        Box(Modifier.width(RAIL).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxHeight()) {
                TimelineRail(Modifier.fillMaxWidth().weight(1f), if (first) Color.Transparent else muted.copy(alpha = 0.35f))
                TimelineRail(Modifier.fillMaxWidth().weight(1f), if (last) Color.Transparent else muted.copy(alpha = 0.35f))
            }
            StopDot(color = if (state == StopState.DONE) muted else accent, filled = state != StopState.UPCOMING, size = DOT)
        }
        Spacer(Modifier.width(d.spaceS))
        PlaceThumb(stop.place, THUMB, Modifier.padding(vertical = d.spaceXs))
        Spacer(Modifier.width(d.spaceS))
        Column(Modifier.weight(1f)) {
            Text(
                text = stop.place.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (state == StopState.CURRENT) FontWeight.SemiBold else FontWeight.Normal,
                color = if (state == StopState.DONE) muted else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (stop.stay.isNotEmpty()) {
                Text(stop.stay, style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        Text(
            text = if (stop.until.isNotEmpty()) "${stop.time} – ${stop.until}" else stop.time,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state == StopState.CURRENT) accent else muted,
        )
    }
}

@Composable
private fun TimelineLunch(last: Boolean) {
    val d = CarTheme.dimens
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(RAIL).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxHeight()) {
                TimelineRail(Modifier.fillMaxWidth().weight(1f), muted.copy(alpha = 0.35f))
                TimelineRail(Modifier.fillMaxWidth().weight(1f), if (last) Color.Transparent else muted.copy(alpha = 0.35f))
            }
            StopDot(color = MaterialTheme.colorScheme.secondary, filled = true, size = DOT * 0.7f)
        }
        Spacer(Modifier.width(d.spaceS))
        Text(
            text = stringResource(R.string.tour_lunch),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(vertical = d.spaceS),
        )
    }
}

/**
 * La vista del itinerario sobre el mapa: a la izquierda, en un panel
 * translúcido, la línea de tiempo y qué toca decir; abajo, el asistente. Se
 * reorganiza de viva voz y el mapa de detrás cambia con ella.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.ItineraryOverlay(
    tour: TourUi,
    voice: VoiceUi,
    speaking: Boolean,
    onClose: () -> Unit,
) {
    val d = CarTheme.dimens
    val glass = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    Column(
        Modifier
            .then(panelPlacement())
            .panelInsetsPadding()
            // Del borde de la barra de estado a casi el de la pantalla: todo el alto.
            .padding(panelMargins())
            .clip(RoundedCornerShape(d.cardCorner))
            .background(glass)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(d.cardCorner))
            .padding(d.spaceS),
    ) {
        // Las paradas y el total; de la voz, solo el punto: el panel es del itinerario.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tour.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            VoiceStatusChip(voice.status, labelled = false)
        }
        if (tour.duration.isNotEmpty()) {
            Text(
                text = tour.duration,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(d.spaceS))
        // Al reordenar, de vuelta arriba: si no, la parada que pasó a ser la
        // primera quedaba por encima de lo visible y parecía que no había cambiado.
        val list = androidx.compose.foundation.lazy.rememberLazyListState()
        androidx.compose.runtime.LaunchedEffect(tour.stops.map { it.place.id }) { list.animateScrollToItem(0) }
        // Las paradas entran una tras otra al abrirse; al reordenar, cada una se
        // desliza a su puesto en vez de saltar.
        val entrance = rememberListEntrance(tour.stops.isNotEmpty())
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list) {
            items(tour.stops.size, key = { tour.stops[it].place.id }) { index ->
                val stop = tour.stops[index]
                Column(Modifier.animateItem().entering(entrance, index)) {
                    OverlayStopRow(stop, first = index == 0, last = index == tour.stops.lastIndex && !stop.lunchAfter)
                    if (stop.lunchAfter) TimelineLunch(last = index == tour.stops.lastIndex)
                }
            }
        }
    }
    Box(Modifier.align(Alignment.TopEnd).panelInsetsPadding().padding(end = d.spaceM)) {
        CarSecondaryButton(label = stringResource(R.string.close_view), onClick = onClose)
    }

    // Lo dicho y lo contestado, como un aviso sobre el mapa que se va solo. Sin
    // instrucciones: como ayuda no funcionaban.
    val toast = voice.answer ?: voice.heard?.let { "“$it”" }
    androidx.compose.animation.AnimatedVisibility(
        visible = toast != null,
        enter = androidx.compose.animation.fadeIn(Motion.settle(Motion.reduced)) +
            androidx.compose.animation.slideInVertically(Motion.settle(Motion.reduced)) { it / 2 },
        exit = androidx.compose.animation.fadeOut(Motion.exit(Motion.reduced)),
        // Sobre el mapa: a la derecha del panel, o arriba si el panel va abajo.
        modifier = (
            if (CarTheme.metrics.stacked) {
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
            } else {
                Modifier.align(Alignment.BottomEnd).fillMaxWidth(1f - CarTheme.metrics.overlayShare)
            }
            )
            .panelInsetsPadding()
            .padding(d.spaceM),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = d.spaceM, vertical = d.spaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (speaking) {
                AssistantCore(status = voice.status, speaking = true, modifier = Modifier.size(TOAST_CORE))
                Spacer(Modifier.width(d.spaceS))
            }
            Text(
                text = toast.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Una parada en el panel estrecho: la hora y la estancia bajo el nombre, que así cabe entero. */
@Composable
private fun OverlayStopRow(stop: TourStopUi, first: Boolean, last: Boolean) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(RAIL).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxHeight()) {
                TimelineRail(Modifier.fillMaxWidth().weight(1f), if (first) Color.Transparent else muted.copy(alpha = 0.35f))
                TimelineRail(Modifier.fillMaxWidth().weight(1f), if (last) Color.Transparent else muted.copy(alpha = 0.35f))
            }
            StopDot(color = accent, filled = false, size = DOT)
        }
        Spacer(Modifier.width(d.spaceS))
        PlaceThumb(stop.place, THUMB, Modifier.padding(vertical = d.spaceXs))
        Spacer(Modifier.width(d.spaceS))
        Column(Modifier.weight(1f)) {
            Text(stop.place.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOf(if (stop.until.isNotEmpty()) "${stop.time} – ${stop.until}" else stop.time, stop.stay)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val TOAST_CORE: Dp @Composable @ReadOnlyComposable get() = CarTheme.metrics.grown(28.dp)
/** Lo que se queda la ayuda al abrir la vista del itinerario. */

/** Cuánto del ancho se lleva la columna del asistente con pantalla completa. */
private const val ASSISTANT_WEIGHT = 0.36f
/** Lo que ocupa la portada del itinerario. */
private const val HERO_WEIGHT = 0.38f
private const val RINGS = 3
private const val ORBITERS = 6
private const val CYCLE_MS = 6_000
private const val DRIVING_ITEMS = 4
private const val PARKED_ITEMS = 12
private const val COMPACT_NEARBY = 2
private val MAX_CORE: Dp @Composable @ReadOnlyComposable get() = CarTheme.metrics.grown(360.dp)
private val THUMB: Dp @Composable @ReadOnlyComposable get() = CarTheme.metrics.grown(52.dp)
private val RAIL: Dp @Composable @ReadOnlyComposable get() = CarTheme.metrics.grown(20.dp)
private val DOT: Dp @Composable @ReadOnlyComposable get() = CarTheme.metrics.grown(14.dp)
