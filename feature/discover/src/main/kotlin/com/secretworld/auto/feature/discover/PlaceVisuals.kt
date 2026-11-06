package com.secretworld.auto.feature.discover

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.secretworld.auto.core.designsystem.Motion
import com.secretworld.auto.core.designsystem.pressable
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
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressable(interaction, Motion.PRESSED_ROW_SCALE)
            .clip(RoundedCornerShape(d.cardCorner))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onSelect),
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

internal enum class Glyph { LISTEN, GO, SETTINGS, ADD, DELETE, ROUTE, SIGHT, FOOD, FUEL, PARKING, BED, CROSS, CASH, MIC_OFF }

/** Los botones de una tarjeta: visibles sin tapar la foto. */
private val ACTION: Dp = 40.dp

/** Un botón redondo con icono dibujado: escuchar (triángulo) o ir (flecha). */
@Composable
internal fun RoundAction(glyph: Glyph, onClick: () -> Unit, filled: Boolean, diameter: Dp = CarTheme.dimens.minTouchTarget) {
    val primary = MaterialTheme.colorScheme.primary
    val background = if (filled) primary else Color.Black.copy(alpha = 0.45f)
    val ink = if (filled) MaterialTheme.colorScheme.onPrimary else Color.White
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressable(interaction)
            .size(diameter)
            .clip(CircleShape)
            .background(background)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, ink, diameter * 0.42f)
    }
}


/** El icono dibujado de [glyph], en [ink]: en los botones redondos y en la barra de arriba. */
@Composable
internal fun GlyphIcon(glyph: Glyph, ink: Color, extent: Dp) {
    // "extent" y no "size": dentro del Canvas, size es el del lienzo.
    Canvas(Modifier.size(extent)) {
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
            Glyph.ROUTE -> {
                // Tres paradas unidas: un itinerario.
                val a = Offset(size.width * 0.12f, size.height * 0.82f)
                val b = Offset(size.width * 0.5f, size.height * 0.3f)
                val c = Offset(size.width * 0.88f, size.height * 0.62f)
                val w = size.minDimension * 0.11f
                drawLine(ink, a, b, strokeWidth = w)
                drawLine(ink, b, c, strokeWidth = w)
                listOf(a, b, c).forEach { drawCircle(ink, radius = size.minDimension * 0.16f, center = it) }
            }
            Glyph.SIGHT -> {
                // Un monumento: frontón, tres columnas y la base.
                val w = size.width
                val h = size.height
                val roof = Path().apply {
                    moveTo(w * 0.5f, 0f)
                    lineTo(w, h * 0.28f)
                    lineTo(0f, h * 0.28f)
                    close()
                }
                drawPath(roof, ink)
                listOf(0.16f, 0.5f, 0.84f).forEach { x ->
                    drawLine(ink, Offset(w * x, h * 0.36f), Offset(w * x, h * 0.84f), strokeWidth = w * 0.13f)
                }
                drawLine(ink, Offset(0f, h * 0.94f), Offset(w, h * 0.94f), strokeWidth = h * 0.12f)
            }
            Glyph.FOOD -> {
                // Tenedor y cuchillo.
                val w = size.width
                val h = size.height
                val s = w * 0.09f
                listOf(0.12f, 0.26f, 0.40f).forEach { x -> drawLine(ink, Offset(w * x, 0f), Offset(w * x, h * 0.32f), strokeWidth = s) }
                drawLine(ink, Offset(w * 0.12f, h * 0.36f), Offset(w * 0.40f, h * 0.36f), strokeWidth = s * 1.4f)
                drawLine(ink, Offset(w * 0.26f, h * 0.36f), Offset(w * 0.26f, h), strokeWidth = s * 1.3f)
                val blade = Path().apply {
                    moveTo(w * 0.72f, 0f)
                    quadraticTo(w * 0.95f, h * 0.2f, w * 0.84f, h * 0.52f)
                    lineTo(w * 0.72f, h * 0.52f)
                    close()
                }
                drawPath(blade, ink)
                drawLine(ink, Offset(w * 0.75f, h * 0.5f), Offset(w * 0.75f, h), strokeWidth = s * 1.3f)
            }
            Glyph.FUEL -> {
                // Un surtidor con su manguera.
                val w = size.width
                val h = size.height
                drawRoundRect(ink, topLeft = Offset(w * 0.08f, h * 0.08f), size = Size(w * 0.5f, h * 0.84f), cornerRadius = CornerRadius(w * 0.06f))
                drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(w * 0.17f, h * 0.18f), size = Size(w * 0.32f, h * 0.22f))
                val hose = Path().apply {
                    moveTo(w * 0.58f, h * 0.3f)
                    lineTo(w * 0.86f, h * 0.44f)
                    lineTo(w * 0.86f, h * 0.78f)
                    lineTo(w * 0.72f, h * 0.78f)
                }
                drawPath(hose, ink, style = Stroke(width = w * 0.09f))
            }
            Glyph.PARKING -> {
                // Una "P" en su cuadro.
                val w = size.width
                val h = size.height
                drawRoundRect(ink, size = size, cornerRadius = CornerRadius(w * 0.18f), style = Stroke(width = w * 0.1f))
                drawLine(ink, Offset(w * 0.38f, h * 0.24f), Offset(w * 0.38f, h * 0.78f), strokeWidth = w * 0.12f)
                drawArc(ink, -90f, 180f, false, topLeft = Offset(w * 0.24f, h * 0.24f), size = Size(w * 0.44f, h * 0.3f), style = Stroke(width = w * 0.11f))
            }
            Glyph.BED -> {
                // Una cama, de lado.
                val w = size.width
                val h = size.height
                drawLine(ink, Offset(w * 0.06f, h * 0.15f), Offset(w * 0.06f, h * 0.92f), strokeWidth = w * 0.11f)
                drawRect(ink, topLeft = Offset(w * 0.06f, h * 0.55f), size = Size(w * 0.9f, h * 0.18f))
                drawLine(ink, Offset(w * 0.94f, h * 0.55f), Offset(w * 0.94f, h * 0.92f), strokeWidth = w * 0.11f)
                drawCircle(ink, radius = w * 0.12f, center = Offset(w * 0.28f, h * 0.4f))
                drawRoundRect(ink, topLeft = Offset(w * 0.45f, h * 0.3f), size = Size(w * 0.5f, h * 0.22f), cornerRadius = CornerRadius(w * 0.08f))
            }
            Glyph.CROSS -> {
                // La cruz de farmacia.
                val w = size.width
                val h = size.height
                drawRect(ink, topLeft = Offset(w * 0.34f, 0f), size = Size(w * 0.32f, h))
                drawRect(ink, topLeft = Offset(0f, h * 0.34f), size = Size(w, h * 0.32f))
            }
            Glyph.CASH -> {
                // Un billete.
                val w = size.width
                val h = size.height
                drawRoundRect(ink, topLeft = Offset(0f, h * 0.2f), size = Size(w, h * 0.6f), cornerRadius = CornerRadius(w * 0.08f), style = Stroke(width = w * 0.09f))
                drawCircle(ink, radius = w * 0.13f, center = Offset(w / 2, h / 2))
            }
            Glyph.MIC_OFF -> {
                // Un micrófono —la cápsula, el soporte y el pie— tachado.
                val w = size.width
                val h = size.height
                val s = w * 0.1f
                drawRoundRect(ink, topLeft = Offset(w * 0.34f, 0f), size = Size(w * 0.32f, h * 0.58f), cornerRadius = CornerRadius(w * 0.16f))
                drawArc(ink, 0f, 180f, false, topLeft = Offset(w * 0.18f, h * 0.22f), size = Size(w * 0.64f, h * 0.52f), style = Stroke(width = s))
                drawLine(ink, Offset(w / 2, h * 0.74f), Offset(w / 2, h * 0.92f), strokeWidth = s)
                drawLine(ink, Offset(w * 0.32f, h * 0.95f), Offset(w * 0.68f, h * 0.95f), strokeWidth = s)
                drawLine(ink, Offset(w * 0.08f, h * 0.06f), Offset(w * 0.92f, h * 0.94f), strokeWidth = s * 1.2f)
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
