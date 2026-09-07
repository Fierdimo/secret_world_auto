package com.secretworld.auto.feature.discover

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.VehicleState

/**
 * El mapa, dibujado por nosotros.
 *
 * **Por qué no hay tiles**: embeber un mapa en automoción es un contrato de
 * licencia aparte, y el mapa nativo del vehículo depende de una librería cliente
 * que Pleos todavía no ha entregado. Poner tiles de cualquier proveedor para una
 * demostración sería fingir que ese problema está resuelto.
 *
 * Lo que sí demuestra, que es lo que importa: **en un coche el mapa es el lienzo,
 * no una pantalla a la que se entra.** Está siempre presente y la información se
 * acopla a su lado en vez de taparlo.
 */
@Composable
fun MapCanvas(
    vehicle: VehicleState,
    route: List<Coordinates>,
    places: List<Place>,
    highlighted: String?,
    modifier: Modifier = Modifier,
    panelCoverFraction: Float = 0f,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()

    // El mapa dibuja en pixeles, así que sus medidas también tienen que escalar.
    // Con constantes fijas, en una pantalla de coche los marcadores salen del
    // tamaño de una cabeza de alfiler.
    val metrics = CarTheme.metrics
    val density = LocalDensity.current
    val style = with(density) {
        MapStyleMetrics(
            markerRadius = metrics.scaled(11.dp).toPx(),
            markerStroke = metrics.scaled(3.5.dp).toPx(),
            haloRadius = metrics.scaled(28.dp).toPx(),
            vehicleSize = metrics.scaled(18.dp).toPx(),
            routeWidth = metrics.scaled(18.dp).toPx(),
            routeCentre = metrics.scaled(2.dp).toPx(),
            gridStep = metrics.scaled(150.dp).toPx(),
            labelGap = metrics.scaled(16.dp).toPx(),
            labelSize = metrics.scaled(13.sp),
        )
    }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val projection = Projection(
                centre = vehicle.position,
                headingDegrees = vehicle.headingDegrees ?: 0.0,
                metresPerPixel = scaleFor(vehicle.position, places, size),
                size = size,
                panelCoverFraction = panelCoverFraction,
            )

            drawRect(color = colors.background)
            drawGrid(colors.outline.copy(alpha = 0.15f), style.gridStep)
            drawRoute(route, projection, colors.outline, style)
            drawMarkers(places, projection, colors, measurer, highlighted, style)
            drawVehicle(colors.primary, projection.anchor, style.vehicleSize)
        }
    }
}

/**
 * Escala automática: el mapa se ajusta para que quepan los lugares que importan.
 *
 * Con una escala fija, en el centro de Roma —donde hay veinte lugares en trescientos
 * metros— los marcadores se amontonan en un pegote ilegible, y en autopista quedan
 * todos fuera de pantalla. Ajustar al contenido resuelve los dos casos.
 */
private fun scaleFor(centre: Coordinates, places: List<Place>, size: Size): Double {
    if (places.isEmpty() || size.minDimension <= 0f) return DEFAULT_METRES_PER_PIXEL
    val farthest = places.take(VISIBLE_PLACES)
        .maxOf { centre.distanceTo(it.position) }
        .coerceAtLeast(MIN_SPAN_METRES)
    // El radio útil es poco más de un tercio del alto, porque el vehículo va abajo.
    return (farthest / (size.minDimension * 0.38)).coerceIn(1.5, 60.0)
}

/**
 * Proyección plana centrada en el vehículo y **rotada según el rumbo**, para que la
 * marcha apunte siempre hacia arriba. Es lo que hace cualquier navegador de coche,
 * y por una buena razón: el conductor compara lo que ve por el parabrisas con lo
 * que ve en la pantalla, y rotar mentalmente cuesta atención.
 */
private class Projection(
    private val centre: Coordinates,
    headingDegrees: Double,
    private val metresPerPixel: Double,
    private val size: Size,
    private val panelCoverFraction: Float,
) {
    private val cos = Math.cos(Math.toRadians(-headingDegrees))
    private val sin = Math.sin(Math.toRadians(-headingDegrees))

    /**
     * El vehículo no va centrado: va **abajo** para dejar ver lo que viene, y
     * **desplazado a la derecha** para no quedar detrás del panel. El centro útil
     * es el de la parte visible, no el de la pantalla.
     */
    val anchor = Offset(
        x = size.width * (panelCoverFraction + (1f - panelCoverFraction) / 2f),
        y = size.height * 0.72f,
    )

    fun toOffset(point: Coordinates): Offset {
        val north = (point.latitude - centre.latitude) * METRES_PER_DEGREE
        val east = (point.longitude - centre.longitude) *
            METRES_PER_DEGREE * Math.cos(Math.toRadians(centre.latitude))

        val rotatedEast = east * cos - north * sin
        val rotatedNorth = east * sin + north * cos

        return Offset(
            x = anchor.x + (rotatedEast / metresPerPixel).toFloat(),
            y = anchor.y - (rotatedNorth / metresPerPixel).toFloat(),
        )
    }
}

/** Todas las medidas del mapa, ya escaladas a la pantalla y en pixeles. */
private class MapStyleMetrics(
    val markerRadius: Float,
    val markerStroke: Float,
    val haloRadius: Float,
    val vehicleSize: Float,
    val routeWidth: Float,
    val routeCentre: Float,
    val gridStep: Float,
    val labelGap: Float,
    val labelSize: androidx.compose.ui.unit.TextUnit,
)

private fun DrawScope.drawGrid(color: Color, step: Float) {
    var x = 0f
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

private fun DrawScope.drawRoute(
    route: List<Coordinates>,
    projection: Projection,
    color: Color,
    style: MapStyleMetrics,
) {
    if (route.size < 2) return
    val path = Path()
    route.forEachIndexed { index, point ->
        val at = projection.toOffset(point)
        if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
    }
    // Dos trazos: el ancho hace de calzada, el fino de línea central.
    drawPath(path, color.copy(alpha = 0.5f), style = Stroke(width = style.routeWidth))
    drawPath(path, color.copy(alpha = 0.85f), style = Stroke(width = style.routeCentre))
}

/**
 * Marcadores con **descarte de etiquetas solapadas**.
 *
 * Sin esto, veinte lugares del centro de Roma escriben sus nombres unos encima de
 * otros y no se lee ninguno. Se etiqueta el resaltado siempre, y luego los demás
 * solo si su etiqueta no pisa a otra ya dibujada. Es preferible una etiqueta menos
 * que una maraña.
 */
private fun DrawScope.drawMarkers(
    places: List<Place>,
    projection: Projection,
    colors: ColorScheme,
    measurer: TextMeasurer,
    highlighted: String?,
    style: MapStyleMetrics,
) {
    val taken = mutableListOf<androidx.compose.ui.geometry.Rect>()
    val ordered = places.take(VISIBLE_PLACES)
        .sortedByDescending { it.id == highlighted } // el resaltado primero: nunca pierde su etiqueta

    ordered.forEach { place ->
        val at = projection.toOffset(place.position)
        if (at.x < -100 || at.x > size.width + 100 || at.y < -100 || at.y > size.height + 100) return@forEach

        val isHighlighted = place.id == highlighted
        val accent = if (isHighlighted) colors.primary else colors.secondary

        if (isHighlighted) {
            drawCircle(accent.copy(alpha = 0.16f), radius = style.haloRadius, center = at)
        }
        drawCircle(colors.background, radius = style.markerRadius, center = at)
        drawCircle(accent, radius = style.markerRadius, center = at, style = Stroke(width = style.markerStroke))

        val label = measurer.measure(
            text = place.name.take(MAX_LABEL_CHARS),
            style = TextStyle(
                fontSize = style.labelSize,
                color = if (isHighlighted) colors.onBackground else colors.onSurfaceVariant,
            ),
        )
        val topLeft = Offset(at.x + style.labelGap, at.y - label.size.height / 2f)
        val box = androidx.compose.ui.geometry.Rect(
            left = topLeft.x - 6f,
            top = topLeft.y - 4f,
            right = topLeft.x + label.size.width + 6f,
            bottom = topLeft.y + label.size.height + 4f,
        )
        // Una etiqueta cortada por el borde se lee peor que ninguna.
        if (box.right > size.width - 8f) return@forEach
        if (taken.any { it.overlaps(box) }) return@forEach

        taken += box
        drawText(label, topLeft = topLeft)
    }
}

/** El vehículo: un triángulo mirando arriba, porque el mapa ya está rotado. */
private fun DrawScope.drawVehicle(color: Color, at: Offset, size: Float) {
    drawCircle(color.copy(alpha = 0.14f), radius = size * 2.2f, center = at)
    translate(at.x, at.y) {
        val path = Path().apply {
            moveTo(0f, -size)
            lineTo(size * 0.72f, size * 0.85f)
            lineTo(0f, size * 0.46f)
            lineTo(-size * 0.72f, size * 0.85f)
            close()
        }
        drawPath(path, color)
    }
}

private const val METRES_PER_DEGREE = 111_320.0
private const val DEFAULT_METRES_PER_PIXEL = 6.0
private const val MIN_SPAN_METRES = 400.0
private const val VISIBLE_PLACES = 10
private const val MAX_LABEL_CHARS = 22
