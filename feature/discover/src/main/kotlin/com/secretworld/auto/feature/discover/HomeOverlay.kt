package com.secretworld.auto.feature.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import com.secretworld.auto.core.designsystem.entering
import com.secretworld.auto.core.designsystem.pressable
import com.secretworld.auto.core.designsystem.rememberListEntrance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.Motion
import com.secretworld.auto.core.designsystem.panelInsetsPadding
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Place

/**
 * El inicio sobre el mapa, como "Mis itinerarios": al fondo, lo de alrededor
 * —o lo buscado— en el mapa; a la izquierda, un panel translúcido con dónde
 * se está, qué se le puede pedir y la lista de sitios. Elegir uno hace volar la
 * cámara a él; tocarlo otra vez abre su ficha. El núcleo de la voz no está
 * aquí: queda solo su punto, y el núcleo para la ventana flotante.
 */
@Composable
fun BoxScope.HomeOverlay(
    city: String?,
    offline: Boolean,
    voice: VoiceUi,
    nowPlaying: NowPlaying?,
    notices: List<PermissionNotice>,
    tour: TourUi?,
    search: SearchUi?,
    /** Buscando: la lista se vacía mientras el mapa rastrea; lo anterior confundía. */
    searching: Boolean,
    nearby: List<Place>,
    origin: Coordinates,
    navigation: NavigationUi?,
    /** Las búsquedas de un toque, como pestañas que filtran la lista. */
    shortcuts: List<SearchShortcut>,
    /** Lo que se está buscando, para marcar su pestaña; `null` es "Qué ver". */
    activeSearch: String?,
    /** El radio de acción, para decir que no hay nada dentro. */
    radiusKm: Int,
    selected: Place?,
    onSelect: (Place) -> Unit,
    onDetails: (Place) -> Unit,
    onListen: (Place) -> Unit,
    onNavigate: (Place) -> Unit,
    onAdd: (Place) -> Unit,
    onStopNarration: () -> Unit,
    onStopNavigation: () -> Unit,
    onClearSearch: () -> Unit,
) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val glass = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
    val shape = RoundedCornerShape(d.cardCorner)
    Column(
        Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .fillMaxWidth(PANEL_SHARE)
            .panelInsetsPadding()
            .padding(d.spaceM)
            .clip(shape)
            .background(glass)
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .padding(d.spaceS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { WhereHeader(city, offline) }
            VoiceStatusChip(voice.status, labelled = false)
        }
        // Lo que se acaba de oír o contestar, mientras dura; nada fijo.
        (voice.answer ?: voice.heard?.let { "“$it”" })?.let { said ->
            Text(
                text = said,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = d.spaceXs).animateContentSize(Motion.settle(Motion.reduced)),
            )
        }
        Spacer(Modifier.height(d.spaceS))
        // Una sola fila: cada sección es su icono, y la activa se despliega con su nombre.
        val tabMatch = shortcuts.firstOrNull { it.label.equals(activeSearch, ignoreCase = true) }
        // La pestaña cambia al tocarla, no cuando llegan los resultados: entonces
        // el mapa está dibujando decenas de puntos y la animación no se veía.
        var chosen by remember { mutableStateOf(tabMatch?.label ?: AROUND.takeIf { activeSearch == null }) }
        LaunchedEffect(activeSearch, tabMatch?.label) { chosen = tabMatch?.label ?: AROUND.takeIf { activeSearch == null } }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Lo pedido, cuando la pestaña ya se ha desplegado: buscar a la vez
            // ocupaba el teléfono —voz, red, mapa— y la animación iba a saltos.
            val scope = rememberCoroutineScope()
            fun choose(key: String, action: () -> Unit) {
                if (chosen == key) return
                chosen = key
                scope.launch {
                    delay(TAB_RESIZE_MS.toLong())
                    action()
                }
            }
            Tab(Glyph.SIGHT, stringResource(R.string.tab_around), active = chosen == AROUND) { choose(AROUND, onClearSearch) }
            shortcuts.forEach { shortcut ->
                Tab(glyphOf(shortcut.category), shortcut.label, active = chosen == shortcut.label) { choose(shortcut.label, shortcut.onClick) }
            }
        }
        // Una búsqueda dicha que no es una pestaña ("shawarma"): su título.
        if (search != null && tabMatch == null) {
            Text(
                text = search.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = d.spaceXs),
            )
        }
        if (tour != null && !tour.proposed && !tour.saved) {
            Spacer(Modifier.height(d.spaceS))
            CurrentStops(tour)
        }
        Spacer(Modifier.height(d.spaceS))
        val places = if (searching) emptyList() else search?.places ?: nearby
        val list = rememberLazyListState()
        LaunchedEffect(selected?.id) {
            val at = places.indexOfFirst { it.id == selected?.id }
            if (at >= 0) list.animateScrollToItem(at)
        }
        if (places.isEmpty() && !searching) {
            Text(
                text = stringResource(R.string.nothing_within, radiusKm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = d.spaceS),
            )
        }
        // La lista entra de nuevo con cada sección y cuando llegan sus sitios; no
        // cuando cambian unos pocos al moverse el coche.
        val entrance = rememberListEntrance(search?.title, places.isNotEmpty())
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(d.spaceXs)) {
            itemsIndexed(places, key = { _, place -> place.id }) { index, place ->
                PlaceLine(
                    modifier = Modifier.animateItem().entering(entrance, index),
                    place = place,
                    origin = origin,
                    selected = place.id == selected?.id,
                    onClick = { if (place.id == selected?.id) onDetails(place) else onSelect(place) },
                    onListen = { onListen(place) },
                    onAdd = { onAdd(place) },
                    onNavigate = { onNavigate(place) },
                )
            }
        }
        notices.forEach { notice ->
            Spacer(Modifier.height(d.spaceXs))
            PermissionCard(notice)
        }
        if (navigation != null) {
            Spacer(Modifier.height(d.spaceXs))
            NavigationCard(navigation, onStopNavigation)
        }
        if (nowPlaying != null) {
            Spacer(Modifier.height(d.spaceXs))
            NowPlayingCard(nowPlaying, onStopNarration)
        }
    }
}

/** Un sitio de la lista: su miniatura, el nombre y la distancia; el elegido, iluminado y con sus acciones. */
@Composable
private fun PlaceLine(
    place: Place,
    origin: Coordinates,
    selected: Boolean,
    onClick: () -> Unit,
    onListen: () -> Unit,
    onAdd: () -> Unit,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(d.cardCorner * 0.75f)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressable(interaction, Motion.PRESSED_ROW_SCALE)
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) accent else Color.Transparent, shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(d.spaceXs)
            .animateContentSize(Motion.settle(Motion.reduced)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlaceThumb(place, THUMB)
            Spacer(Modifier.width(d.spaceS))
            Column(Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = if (selected) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(formatDistance(origin.distanceTo(place.position)), place.kind).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Las acciones, solo en el elegido y en la misma línea: nada apilado.
            if (selected) {
                Spacer(Modifier.width(d.spaceXs))
                Row(horizontalArrangement = Arrangement.spacedBy(d.spaceXs)) {
                    if (place.kind == null && place.summary != null) RoundAction(Glyph.LISTEN, onListen, filled = true, diameter = ACTION)
                    RoundAction(Glyph.ADD, onAdd, filled = false, diameter = ACTION)
                    RoundAction(Glyph.GO, onNavigate, filled = false, diameter = ACTION)
                }
            }
        }
    }
}

/**
 * Una sección de la lista, sencilla para que quepan muchas: un icono suelto; la
 * activa, su nombre con la raya debajo.
 */
@Composable
private fun Tab(glyph: Glyph, label: String, active: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .pressable(interaction)
            .clip(RoundedCornerShape(4.dp))
            // Sin el recuadro gris del toque: la raya ya dice cuál está activa.
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .defaultMinSize(minWidth = TAB_TOUCH)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // La activa, solo su nombre: el icono ya no hace falta para saber cuál es.
        // Icono y nombre entran y salen cada uno por su lado, con la misma curva:
        // el que se va se recoge mientras el nuevo se despliega, sin saltos.
        val reduced = Motion.reduced
        Row(Modifier.height(TAB_LINE), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = !active,
                enter = fadeIn(Motion.settle(reduced)) + expandHorizontally(Motion.settle(reduced)),
                exit = fadeOut(Motion.exit(reduced)) + shrinkHorizontally(Motion.settle(reduced)),
            ) {
                GlyphIcon(glyph, ink, TAB_ICON)
            }
            AnimatedVisibility(
                visible = active,
                enter = fadeIn(Motion.settle(reduced)) + expandHorizontally(Motion.settle(reduced), expandFrom = Alignment.Start),
                exit = fadeOut(Motion.exit(reduced)) + shrinkHorizontally(Motion.settle(reduced), shrinkTowards = Alignment.Start),
            ) {
                Text(
                    text = label.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // La raya crece desde el centro en la capa de dibujo: el layout no cambia.
        val underline by animateFloatAsState(if (active) 1f else 0f, Motion.settle(reduced), label = "raya")
        Box(
            Modifier
                .height(2.dp)
                .width(UNDERLINE)
                .graphicsLayer {
                    scaleX = underline
                    alpha = underline
                }
                .clip(RoundedCornerShape(1.dp))
                .background(accent),
        )
    }
}

/** El icono de cada búsqueda de un toque. */
private fun glyphOf(category: String?): Glyph = when (category) {
    "restaurant" -> Glyph.FOOD
    "gas_station" -> Glyph.FUEL
    "parking_lot" -> Glyph.PARKING
    "hotel" -> Glyph.BED
    "pharmacy" -> Glyph.CROSS
    "atm" -> Glyph.CASH
    else -> Glyph.SIGHT
}

private const val PANEL_SHARE = 0.40f
/** La pestaña de "Qué ver", que no es una búsqueda. */
private const val AROUND = "\u0000around"
private val THUMB = 44.dp
private val ACTION = 36.dp
private val TAB_ICON = 20.dp
private val UNDERLINE = 24.dp
/** El alto de una línea de texto: icono y nombre ocupan lo mismo y la fila no salta. */
private val TAB_LINE = 22.dp
/** Lo que tarda en asentarse la pestaña: la búsqueda espera a que termine. */
private const val TAB_RESIZE_MS = 380
/** Lo mínimo que se toca bien con el dedo; nada más, para que quepan. */
private val TAB_TOUCH = 36.dp
