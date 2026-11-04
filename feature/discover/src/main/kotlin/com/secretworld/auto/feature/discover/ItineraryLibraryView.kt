package com.secretworld.auto.feature.discover

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Los itinerarios guardados, para empezar uno o verlo antes. Todas las
 * tarjetas iguales: arriba, el recorrido dibujado con sus paradas numeradas
 * —se reconoce de un vistazo, tenga fotos o no—; abajo, de dónde sale, hasta
 * dónde llega, cuánto es, y empezar. Tocar la tarjeta lo abre en el mapa. Se
 * elige también de viva voz ("el segundo"), por eso cada una lleva su número.
 */
@Composable
fun ItineraryLibraryView(
    items: List<SavedItineraryUi>,
    voice: VoiceUi,
    onOpen: (String) -> Unit,
    onStart: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = CarTheme.dimens
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Solo la barra de estado: con toda la zona segura —el hueco de la
            // cámara a la izquierda, la barra de gestos abajo— quedaban bandas
            // vacías y las tarjetas no cabían.
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .padding(horizontal = d.spaceM, vertical = d.spaceS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.library_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(d.spaceS))
                    VoiceStatusChip(voice.status, labelled = false)
                }
                // Lo que se contesta, si se contesta algo ("Borrado."); nada de instrucciones.
                voice.answer?.let { said ->
                    Text(
                        text = said,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CarSecondaryButton(label = stringResource(R.string.close_view), onClick = onClose, compact = true)
        }
        Spacer(Modifier.height(d.spaceM))
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.library_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        // La misma altura para todas, y que quepa entera en lo que queda de
        // pantalla: en un móvil apaisado, 300 dp se salían por abajo.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cardHeight = minOf(maxHeight, CARD_HEIGHT).coerceAtLeast(CARD_MIN_HEIGHT)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(CARD_MIN),
                horizontalArrangement = Arrangement.spacedBy(d.spaceM),
                verticalArrangement = Arrangement.spacedBy(d.spaceM),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    SavedCard(
                        number = index + 1,
                        item = item,
                        onOpen = { onOpen(item.id) },
                        onStart = { onStart(item.id) },
                        onDelete = { onDelete(item.id) },
                        modifier = Modifier.height(cardHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedCard(
    number: Int,
    item: SavedItineraryUi,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(d.cardCorner)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accent.copy(alpha = 0.22f), shape)
            .clickable(onClick = onOpen),
    ) {
        // Arriba, el recorrido: la foto de una parada, oscurecida, y encima la
        // ruta dibujada con sus paradas.
        Box(Modifier.fillMaxWidth().weight(HERO_WEIGHT)) {
            val cover = item.stops.firstOrNull { it.imageUrl != null } ?: item.stops.first()
            PlaceImage(cover, Modifier.fillMaxSize(), initial = false)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.85f))),
            )
            RouteSketch(item.stops, Modifier.fillMaxSize().padding(horizontal = d.spaceL, vertical = d.spaceM))
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(d.spaceS)
                    .size(BADGE)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.align(Alignment.TopEnd).padding(d.spaceXs)) {
                RoundAction(Glyph.DELETE, onDelete, filled = false, diameter = DELETE)
            }
        }
        // Abajo, de más a menos: de dónde sale, hasta dónde, cuánto es, cuándo.
        Column(Modifier.fillMaxWidth().weight(1f - HERO_WEIGHT).padding(d.spaceS)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.destination?.let { stringResource(R.string.library_until, it) }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${item.count} · ${item.duration}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
                CarButton(label = stringResource(R.string.library_start), onClick = onStart, dense = true)
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
private fun RouteSketch(stops: List<Place>, modifier: Modifier) {
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
            drawPath(path, accent.copy(alpha = 0.28f), style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, accent, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        points.forEachIndexed { index, point ->
            val radius = if (index == 0) 13f else 9f
            drawCircle(accent.copy(alpha = 0.3f), radius = radius * 2f, center = point)
            drawCircle(accent, radius = radius, center = point)
            drawCircle(ink, radius = radius * 0.4f, center = point)
        }
    }
}

private val CARD_MIN = 340.dp
private val CARD_HEIGHT = 300.dp
private val CARD_MIN_HEIGHT = 220.dp
private const val HERO_WEIGHT = 0.48f
private val BADGE = 36.dp
private val DELETE = 36.dp
