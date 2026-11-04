package com.secretworld.auto.feature.discover

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
                modifier = Modifier.padding(top = d.spaceXs).animateContentSize(),
            )
        }
        Spacer(Modifier.height(d.spaceS))
        // Una sola fila: pestañas de texto, no botones; la activa, subrayada.
        val tabMatch = shortcuts.firstOrNull { it.label.equals(activeSearch, ignoreCase = true) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(d.spaceM)) {
            Tab(stringResource(R.string.tab_around), active = activeSearch == null, onClick = onClearSearch)
            shortcuts.forEach { Tab(it.label, active = it === tabMatch, onClick = it.onClick) }
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
        val places = search?.places ?: nearby
        val list = rememberLazyListState()
        LaunchedEffect(selected?.id) {
            val at = places.indexOfFirst { it.id == selected?.id }
            if (at >= 0) list.animateScrollToItem(at)
        }
        if (places.isEmpty()) {
            Text(
                text = stringResource(R.string.nothing_within, radiusKm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = d.spaceS),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(d.spaceXs)) {
            items(places, key = { it.id }) { place ->
                PlaceLine(
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
) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(d.cardCorner * 0.75f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(d.spaceXs)
            .animateContentSize(),
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

/** Una pestaña de la lista: texto, y la activa con su raya debajo. */
@Composable
private fun Tab(label: String, active: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(2.dp).width(24.dp).clip(RoundedCornerShape(1.dp)).background(if (active) accent else Color.Transparent))
    }
}

private const val PANEL_SHARE = 0.40f
private val THUMB = 44.dp
private val ACTION = 36.dp
