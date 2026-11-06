package com.secretworld.auto.feature.discover

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secretworld.auto.core.designsystem.CarButton
import com.secretworld.auto.core.designsystem.CarSecondaryButton
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.Motion
import com.secretworld.auto.core.designsystem.entering
import com.secretworld.auto.core.designsystem.panelInsetsPadding
import com.secretworld.auto.core.designsystem.pressable
import com.secretworld.auto.core.designsystem.rememberListEntrance
import com.secretworld.auto.domain.Place
import kotlin.math.cos
import kotlin.math.max

/** Un itinerario guardado, como lo enseña la ventana. */
data class SavedItineraryUi(
    val id: String,
    /** De dónde sale: "India Catalina". */
    val title: String,
    /** Hasta dónde llega, si es otro sitio: "Convento de Santo Domingo". */
    val destination: String?,
    /** "5 paradas". */
    val count: String,
    /** "2 horas y 47". */
    val duration: String,
    /** "3 nov". */
    val date: String,
    val stops: List<Place>,
)

/**
 * "Mis itinerarios" como la selección de nivel de un videojuego: al fondo, el
 * mapa con el itinerario elegido —la cámara vuela a cada uno al cambiar—; a la
 * izquierda, en un panel translúcido, la lista. El elegido se resalta y lleva
 * sus acciones; arriba, empezar uno nuevo. Cada uno lleva su número para
 * elegirlo de viva voz ("el segundo").
 */
@Composable
fun BoxScope.ItineraryLibraryOverlay(
    items: List<SavedItineraryUi>,
    selected: Int,
    voice: VoiceUi,
    onSelect: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onStart: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onSuggest: () -> Unit,
    onBuild: () -> Unit,
    onClose: () -> Unit,
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
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            VoiceStatusChip(voice.status, labelled = false)
        }
        voice.answer?.let { said ->
            Text(
                text = said,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(d.spaceS))
        // Empezar uno nuevo, siempre a mano.
        Row(horizontalArrangement = Arrangement.spacedBy(d.spaceS)) {
            CarButton(label = "+ " + stringResource(R.string.library_suggested), onClick = onSuggest, dense = true, modifier = Modifier.weight(1f))
            CarSecondaryButton(label = "+ " + stringResource(R.string.library_manual), onClick = onBuild, dense = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(d.spaceS))
        val list = rememberLazyListState()
        LaunchedEffect(selected) { if (selected in items.indices) list.animateScrollToItem(selected) }
        // Como los niveles de un juego: entran uno tras otro al abrir la ventana.
        val entrance = rememberListEntrance(items.isNotEmpty())
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(d.spaceXs)) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                LibraryRow(
                    modifier = Modifier.animateItem().entering(entrance, index),
                    number = index + 1,
                    item = item,
                    selected = index == selected,
                    onClick = { if (index == selected) onOpen(index) else onSelect(index) },
                    onOpen = { onOpen(index) },
                    onStart = { onStart(index) },
                    onDelete = { onDelete(index) },
                )
            }
        }
    }
    Box(Modifier.align(Alignment.TopEnd).panelInsetsPadding().padding(d.spaceM)) {
        CarSecondaryButton(label = stringResource(R.string.close_view), onClick = onClose, compact = true)
    }
}

/**
 * Una fila: el número, el recorrido en miniatura, de dónde sale y cuánto es.
 * La elegida se ilumina y despliega sus acciones; tocarla otra vez la abre.
 */
@Composable
private fun LibraryRow(
    number: Int,
    item: SavedItineraryUi,
    selected: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit,
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
            Box(
                Modifier.size(BADGE).clip(CircleShape).background(if (selected) accent else accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else accent,
                )
            }
            Spacer(Modifier.width(d.spaceS))
            Box(
                Modifier.size(SKETCH).clip(RoundedCornerShape(d.cardCorner / 2)).background(Color.Black.copy(alpha = 0.55f)),
            ) {
                RouteSketch(item.stops, Modifier.fillMaxSize().padding(8.dp), small = true)
            }
            Spacer(Modifier.width(d.spaceS))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.destination?.let {
                    Text(
                        text = stringResource(R.string.library_until, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${item.count} · ${item.duration} · ${item.date}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.height(d.spaceXs))
            Row(horizontalArrangement = Arrangement.spacedBy(d.spaceS), verticalAlignment = Alignment.CenterVertically) {
                CarButton(label = stringResource(R.string.library_start), onClick = onStart, dense = true, modifier = Modifier.weight(1f))
                CarSecondaryButton(label = stringResource(R.string.library_edit), onClick = onOpen, dense = true, modifier = Modifier.weight(1f))
                RoundAction(Glyph.DELETE, onDelete, filled = false, diameter = DELETE)
            }
        }
    }
}

/**
 * El recorrido en pequeño: las paradas donde están de verdad, unidas en orden
 * con una estela dorada; la primera, más grande. Sin mapa: es un dibujo, y así
 * cada itinerario tiene su forma.
 */
@Composable
private fun RouteSketch(stops: List<Place>, modifier: Modifier, small: Boolean = false) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier) {
        if (stops.isEmpty()) return@Canvas
        val lat = stops.map { it.position.latitude }
        val lon = stops.map { it.position.longitude }
        val midLat = (lat.min() + lat.max()) / 2
        val squash = cos(Math.toRadians(midLat))
        val spanX = (lon.max() - lon.min()) * squash
        val spanY = lat.max() - lat.min()
        val span = max(max(spanX, spanY), 1e-6)
        val scale = minOf(size.width, size.height) / span
        // Centrado en el recuadro, con la misma escala en los dos ejes: la forma
        // es la del recorrido, no la del rectángulo.
        val offsetX = (size.width - spanX * scale) / 2
        val offsetY = (size.height - spanY * scale) / 2
        val points = stops.map { stop ->
            Offset(
                (offsetX + (stop.position.longitude - lon.min()) * squash * scale).toFloat(),
                (offsetY + (lat.max() - stop.position.latitude) * scale).toFloat(),
            )
        }.let { if (stops.size == 1) listOf(Offset(size.width / 2, size.height / 2)) else it }
        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, accent.copy(alpha = 0.28f), style = Stroke(width = if (small) 7f else 14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, accent, style = Stroke(width = if (small) 2.5f else 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        points.forEachIndexed { index, point ->
            val radius = (if (index == 0) 13f else 9f) * (if (small) 0.5f else 1f)
            drawCircle(accent.copy(alpha = 0.3f), radius = radius * 2f, center = point)
            drawCircle(accent, radius = radius, center = point)
            drawCircle(ink, radius = radius * 0.4f, center = point)
        }
    }
}

private const val PANEL_SHARE = 0.40f
private val BADGE = 30.dp
private val SKETCH = 64.dp
private val DELETE = 40.dp
