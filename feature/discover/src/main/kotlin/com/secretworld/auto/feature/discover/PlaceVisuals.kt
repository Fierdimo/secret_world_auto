package com.secretworld.auto.feature.discover

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.domain.Place

/**
 * Un lugar con su foto como protagonista: Secret World se elige mirando, y en un
 * coche la imagen hace la mitad del trabajo. El nombre va sobre un velo oscuro
 * para leerse con cualquier foto; las acciones son dos iconos, y tocar la
 * tarjeta abre la ficha.
 */
@Composable
internal fun PhotoCard(
    place: Place,
    distance: String,
    hasStory: Boolean,
    onSelect: () -> Unit,
    onListen: () -> Unit,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    /** Añadirlo al itinerario —o empezar uno con él—; sin esto, no sale el botón. */
    onAdd: (() -> Unit)? = null,
) {
    val d = CarTheme.dimens
    Box(
        modifier
            .clip(RoundedCornerShape(d.cardCorner))
            .clickable(onClick = onSelect),
    ) {
        // Sin la inicial de fondo: con el nombre encima, se leían las dos a la vez.
        PlaceImage(place, Modifier.fillMaxSize(), initial = false)
        // El velo: de transparente arriba a casi negro abajo, donde va el texto.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f))),
        )
        // Las acciones arriba a la derecha, pequeñas: el nombre se queda todo el ancho.
        Row(
            Modifier.align(Alignment.TopEnd).padding(d.spaceXs),
            horizontalArrangement = Arrangement.spacedBy(d.spaceXs),
        ) {
            if (hasStory) RoundAction(Glyph.LISTEN, onListen, filled = true, diameter = ACTION)
            onAdd?.let { RoundAction(Glyph.ADD, it, filled = false, diameter = ACTION) }
            RoundAction(Glyph.GO, onNavigate, filled = !hasStory, diameter = ACTION)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(d.spaceS),
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(distance, place.kind).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** La foto del lugar o, si no tiene, un fondo con su inicial: nunca un hueco gris. */
@Composable
internal fun PlaceImage(place: Place, modifier: Modifier, initial: Boolean = true) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(
        modifier.background(Brush.linearGradient(listOf(secondary.copy(alpha = 0.55f), primary.copy(alpha = 0.45f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (initial) {
            Text(
                text = place.name.firstOrNull { it.isLetter() }?.uppercase().orEmpty(),
                style = MaterialTheme.typography.displayMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        if (place.imageUrl != null) {
            AsyncImage(
                model = place.imageUrl,
                contentDescription = place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Miniatura redonda para listas y la línea de tiempo. */
@Composable
internal fun PlaceThumb(place: Place, size: Dp, modifier: Modifier = Modifier) {
    PlaceImage(place, modifier.size(size).clip(RoundedCornerShape(size / 4)))
}

internal enum class Glyph { LISTEN, GO, SETTINGS, ADD, DELETE }

/** Los botones de una tarjeta: visibles sin tapar la foto. */
private val ACTION: Dp = 40.dp

/**
 * Los ajustes: un solo botón discreto, en una esquina. Sustituye a la barra que
 * había al pie —simulador, silencio—, que ahora vive dentro de los ajustes.
 */
@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) { RoundAction(Glyph.SETTINGS, onClick, filled = false, diameter = CarTheme.dimens.minTouchTarget * 0.8f) }
}

/** Un botón redondo con icono dibujado: escuchar (triángulo) o ir (flecha). */
@Composable
internal fun RoundAction(glyph: Glyph, onClick: () -> Unit, filled: Boolean, diameter: Dp = CarTheme.dimens.minTouchTarget) {
    val primary = MaterialTheme.colorScheme.primary
    val background = if (filled) primary else Color.Black.copy(alpha = 0.45f)
    val ink = if (filled) MaterialTheme.colorScheme.onPrimary else Color.White
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter * 0.42f)) {
            when (glyph) {
                Glyph.LISTEN -> {
                    val path = Path().apply {
                        moveTo(size.width * 0.18f, 0f)
                        lineTo(size.width, size.height / 2)
                        lineTo(size.width * 0.18f, size.height)
                        close()
                    }
                    drawPath(path, ink)
                }
                Glyph.SETTINGS -> {
                    // Un engranaje: el aro y ocho dientes.
                    val c = Offset(size.width / 2, size.height / 2)
                    val r = size.minDimension / 2
                    for (tooth in 0 until 8) {
                        val a = Math.toRadians(tooth * 45.0)
                        val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                        drawLine(ink, c + dir * (r * 0.62f), c + dir * r, strokeWidth = r * 0.32f)
                    }
                    drawCircle(ink, radius = r * 0.58f, center = c, style = Stroke(width = r * 0.26f))
                }
                Glyph.ADD -> {
                    // Un "+": añadir al itinerario.
                    val w = size.minDimension * 0.16f
                    drawLine(ink, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = w)
                    drawLine(ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = w)
                }
                Glyph.DELETE -> {
                    // Una "×": quitar.
                    val w = size.minDimension * 0.14f
                    drawLine(ink, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = w)
                    drawLine(ink, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = w)
                }
                Glyph.GO -> {
                    // Una flecha de navegación, inclinada hacia arriba a la derecha.
                    val path = Path().apply {
                        moveTo(size.width, 0f)
                        lineTo(size.width * 0.58f, size.height)
                        lineTo(size.width * 0.44f, size.height * 0.56f)
                        lineTo(0f, size.height * 0.42f)
                        close()
                    }
                    drawPath(path, ink)
                }
            }
        }
    }
}

/** Una línea vertical que une las paradas de la línea de tiempo. */
@Composable
internal fun TimelineRail(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        drawLine(
            color = color,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = 3.dp.toPx(),
        )
    }
}

/** El círculo vacío de una parada que aún no ha llegado. */
@Composable
internal fun StopDot(color: Color, filled: Boolean, size: Dp) {
    Canvas(Modifier.size(size)) { 
        if (filled) {
            drawCircle(color)
        } else {
            drawCircle(color, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
